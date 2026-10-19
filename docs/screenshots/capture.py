#!/usr/bin/env python3
"""Replays the screenshot scenario on a running emulator and saves the captures to raw/.

See README.md for the requirements and for what each capture is used for.
Usage: capture.py [--build]
"""
import os, re, subprocess, sys, time

import ui
from ui import S, adb, nodes, tap_node, match

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PKG = "com.h0tk3y.rally.debug"
SECTIONS = {  # the names in seed-<locale>.sql
    "en": dict(tc1="Leg 1 · TC1 → TC2", tc2="Leg 1 · TC2 → TC3", new="Leg 1 - TC3 to TC4"),
    "ru": dict(tc1="Этап 1 · КВ1 → КВ2", tc2="Этап 1 · КВ2 → КВ3", new=None),  # adb cannot type Cyrillic
}[ui.LOCALE]
KEY_UP, KEY_DOWN = (131, 1755), (405, 1755)  # the editor's ▲ and ▼ keys have no text to find them by


def sh(*cmd):
    return adb("shell", *cmd)


def wait(s):
    time.sleep(s)


def tap(text, idx=0):
    for _ in range(4):  # the screen may still be changing (a keyboard, an animation)
        ms = match(text)
        if ms:
            break
        wait(0.7)
    else:
        sys.exit(f"not found on screen: {text!r}")
    tap_node(ms[idx]); wait(0.8)


def xy(x, y):
    sh("input", "tap", str(x), str(y)); wait(0.6)


def back():
    sh("input", "keyevent", "KEYCODE_BACK"); wait(0.8)


def shot(name):
    """Saves the screen to raw/<name>.png and its UI tree to raw/<name>.xml (annotate.py finds the elements in it)."""
    os.makedirs(ui.OUT, exist_ok=True)
    adb("shell", "uiautomator", "dump", "/data/local/tmp/ui.xml")
    open(os.path.join(ui.OUT, name + ".xml"), "w").write(adb("shell", "cat", "/data/local/tmp/ui.xml"))
    open(os.path.join(ui.OUT, name + ".png"), "wb").write(adb("exec-out", "screencap", "-p", binary=True))
    print("captured", name)


def demo(command, **extras):
    args = ["am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", command]
    for k, v in extras.items():
        args += ["-e", k, v]
    sh(*args)


def set_time(hms):
    """Sets the emulator clock (the timers use it) and the status bar clock."""
    h, m, s = hms.split(":")
    sh("date", time.strftime(f"%m%d{h}{m}%Y.{s}"))
    demo("clock", hhmm=h + m)


def edit_texts():
    return [n for n in nodes() if n["cls"] == "EditText"]


def fill(idx, text):
    """Types into the idx-th text field, one character at a time (fast input loses characters)."""
    tap_node(edit_texts()[idx]); wait(0.8)
    for k, word in enumerate(text.split(" ")):
        if k:
            sh("input", "text", "%s"); wait(0.3)
        for ch in word:
            sh("input", "text", ch); wait(0.3)
    wait(0.5)


def clear(idx):
    tap_node(edit_texts()[idx]); wait(0.6)
    sh("input", "keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_A"); wait(0.3)
    sh("input", "keyevent", "KEYCODE_DEL"); wait(0.5)


def distance_node():
    ns = [n for n in nodes() if n["click"] and n["cls"] == "TextView" and re.fullmatch(r"-?\d+\.\d{3}", n["text"])]
    return ns[0]


def goto(distance, hms):
    """Simulates driving: moves the clock to hms, then types the distance into the race panel."""
    set_time(hms); wait(0.5)
    tap_node(distance_node()); wait(0.6)
    sh("input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(8):
        sh("input", "keyevent", "KEYCODE_DEL")
    sh("input", "text", str(distance))
    tap(S("buttonApplyOdo"))


def slider(kmh):
    """Sets the Simulation speed slider (0–100 km/h)."""
    label = match(S("simulateSpeedPrefix"))[-1]["b"]
    value = [n for n in nodes() if re.fullmatch(r"\d+" + S("perHourSuffix"), n["text"]) and abs(n["b"][1] - label[1]) < 20][0]["b"]
    x0, x1 = label[2] + 47, value[0] - 48
    y = (label[1] + label[3]) // 2
    xy(int(x0 + (x1 - x0) * kmh / 100), y)


def launch():
    sh("am", "start", "-W", "--activity-clear-task", "-n", PKG + "/com.h0tk3y.rally.android.MainActivity"); wait(2.5)


def reset_data(hms):
    launch()  # on a fresh device, the first launch creates the database
    sh("am", "force-stop", PKG)
    adb("push", os.path.join(HERE, f"seed-{ui.LOCALE}.sql"), "/data/local/tmp/seed.sql")
    sh("chmod", "644", "/data/local/tmp/seed.sql")
    sh(f"run-as {PKG} sh -c 'sqlite3 databases/app.db \"DELETE FROM Event; DELETE FROM Section; "
       f"DELETE FROM sqlite_sequence;\" && sqlite3 databases/app.db < /data/local/tmp/seed.sql'")
    set_time(hms)
    launch()
    if match(S("cancel")):  # a dialog restored from an earlier run
        tap(S("cancel"))


def build_and_install():
    """Builds the debug app without the "DEBUG" prefix in the title, so it looks like the release."""
    f = os.path.join(ROOT, "android/src/debug/res/values/strings.xml")
    original = open(f).read()
    try:
        open(f, "w").write(original.replace(">DEBUG h0tk3y", ">h0tk3y"))
        subprocess.run(["./gradlew", ":android:assembleDebug", "-q"], cwd=ROOT, check=True)
    finally:
        open(f, "w").write(original)
    adb("install", "-r", os.path.join(ROOT, "android/build/outputs/apk/debug/android-debug.apk"))


def setup_device():
    adb("root"); wait(2); adb("wait-for-device")
    sh("settings", "put", "system", "font_scale", "0.85")
    sh("settings", "put", "global", "auto_time", "0")
    sh("settings", "put", "system", "accelerometer_rotation", "0")
    sh("settings", "put", "system", "user_rotation", "0")
    sh("settings", "put", "global", "sysui_demo_allowed", "1")
    demo("enter"); wait(1)
    demo("battery", level="100", plugged="false")
    demo("network", mobile="hide")
    demo("network", wifi="show", level="4", fully="true")
    demo("notifications", visible="false")
    demo("status", sync="hide", alarm="hide", location="hide")
    sh("cmd", "locale", "set-app-locales", PKG, "--locales", ui.LOCALE)
    for p in ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN", "POST_NOTIFICATIONS"]:
        sh("pm", "grant", PKG, "android.permission." + p)


def main():
    if "--build" in sys.argv:
        build_and_install()
    setup_device()
    reset_data("10:05:00")

    # Settings: the OBD reader for the screenshot, then Simulation, an allowance and the driver's device.
    tap(S("showMenu")); tap(S("settings")); wait(1)
    tap(S("telemetrySourceObd"))
    clear(0); fill(0, "00:1D:A5:68:98:8B")  # Settings survive reset_data: replace, do not append
    clear(1); fill(1, "1.018"); back(); tap(S("saveButton"))
    back(); tap(S("showMenu")); tap(S("settings")); wait(1)  # reopen, so that no field has the focus
    shot("10-settings-obd")
    tap(S("telemetrySourceSimulation"))
    sh("input", "swipe", "540", "1800", "540", "900", "300"); wait(0.8)
    tap(S("allowanceT10Down"))
    clear(len(edit_texts()) - 1); fill(len(edit_texts()) - 1, "127.0.0.1"); back()
    back()  # leave Settings
    shot("01-section-list")

    # The Create section window, filled in and cancelled.
    tap(S("createSection")); wait(1)
    if SECTIONS["new"]:
        fill(0, SECTIONS["new"])
    fill(1, "28.6"); fill(2, "0:38"); fill(4, "12:05"); back()
    shot("04-create-section")
    tap(S("cancel"))

    # The rally: section TC1 → TC2, 40 km at 48 km/h, start 10:12:00, with a regularity inside.
    tap(SECTIONS["tc1"]); wait(1)
    set_time("10:11:40")
    tap(S("buttonRaceMode")); wait(1.5)
    shot("06-race-not-started")
    set_time("10:11:52")
    tap(S("raceRowButtonOnTime", "10:12:00"), 0); wait(1)
    shot("06-countdown")

    slider(53)
    goto(3.15, "10:15:52"); tap(S("buttonAddPassedPosition"))
    goto(7.8, "10:21:44"); tap(S("buttonAddPassedPosition"))

    # Before the regularity: remember its speed, 36, with the v button.
    goto(9.6, "10:24:10")
    tap(S("vSpeedButton")); tap("=" + S("perHourSuffix") + " →"); sh("input", "text", "36"); wait(0.5)
    tap("36", 0)  # the round button next to the field
    back()  # hide the keyboard
    shot("06-v-remembered")
    tap("36", 0)  # the v button closes the row
    tap("7.8")  # select the last passed position (its exact distance differs from run to run)
    wait(1)
    shot("06-section-timer")

    # The regularity: start at 12.4, speed changes at 17.3 and 21.05, finish at 24.85.
    goto(12.4, "10:27:22")
    tap(S("nowAtPosition", "12.4"), 0)
    slider(35)
    goto(14.0, "10:30:02"); wait(2)
    shot("06-regularity")
    sh("input", "swipe", "1000", "1909", "100", "1909", "500"); wait(0.8)
    shot("06-speed-buttons")
    goto(17.3, "10:35:32"); tap("40")
    slider(38)
    goto(21.05, "10:41:10"); tap("30")
    slider(27)
    goto(24.85, "10:48:46")
    tap(S("raceButtonFinishRace")); wait(1)
    shot("06-finished")

    # The rest of the liaison.
    slider(70)
    goto(28.4, "10:51:52"); tap(S("buttonAddPassedPosition"))
    goto(33.75, "10:56:32"); tap(S("buttonAddPassedPosition"))

    # The driver's screen on the same device (the driver's device address is 127.0.0.1).
    tap("40.0", -1)
    back(); wait(1)
    tap(S("showMenu")); tap(S("driverHud")); wait(3)
    shot("11-driver-hud")
    back(); wait(1)
    tap(SECTIONS["tc1"]); wait(1)

    # The end of the section.
    goto(40.0, "11:01:58")
    tap(S("raceButtonStop")); wait(1)
    shot("06-stopped")
    tap(S("eventLog")); wait(1)
    shot("06-event-log")
    back(); wait(1)
    tap("40.0", -1); wait(0.5)
    shot("14-section-times")
    tap(S("showMenu"))
    shot("05-menu-race-on")
    tap(S("buttonHideRacePanel")); wait(1)
    shot("05-section-rows")

    # Correcting the distance and driving backwards, with the 40.0 time control selected.
    tap(S("showMenu")); tap(S("buttonShowRacePanel")); wait(1)
    tap(S("buttonUndoStop")); wait(1)
    tap("40.0", -1)
    slider(27)
    tap(S("switchDirectionHint")); wait(1.5)
    shot("06-backwards")
    tap(S("switchDirectionHint"))
    tap_node(distance_node()); wait(1)
    shot("06-distance-edit")
    tap(S("cancel"))
    tap(S("raceButtonStop")); wait(1)

    # The phone turned sideways.
    sh("settings", "put", "system", "user_rotation", "1"); wait(2.5)
    shot("06-landscape")
    sh("settings", "put", "system", "user_rotation", "0"); wait(2)

    # The editor, with a row selected by the ▲ key (a tap would show the cursor handle).
    tap(S("showMenu")); tap(S("buttonDropRaceState")); wait(1)
    tap(S("buttonEdit")); wait(0.6)
    for _ in range(4):
        xy(*KEY_UP)  # from 40.0 up to 21.086
    shot("16-editor")
    tap(S("buttonCalculate"))
    back(); wait(1)

    # An error: ADD after the last position of another section gives a position at 0.0 after 36.8.
    tap(SECTIONS["tc2"]); wait(1)
    tap(S("buttonEdit")); wait(0.6)
    xy(*KEY_DOWN); tap(S("keyAdd")); tap(S("buttonCalculate")); wait(1)
    shot("15-error")
    back()


if __name__ == "__main__":
    main()
