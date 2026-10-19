#!/usr/bin/env python3
"""Captures the guide's screenshots on a running emulator into raw/<locale>/.

The captures come in series. Each series starts from clean data, with the sections it needs written straight into the
app's database, so one screenshot can be retaken without replaying the whole rally.
See README.md for the requirements and for what each capture is used for.

Usage: capture.py [--build] [--series NAME[,NAME...]] [--shot NAME] [--list]
  --series   run only these series
  --shot     run only the series that captures this screenshot (for example 06-countdown)
  --list     print the series and their screenshots
"""
import os, re, subprocess, sys, time

import ui
from ui import S, adb, nodes, tap_node, match

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PKG = "com.h0tk3y.rally.debug"
SECTIONS = {
    "en": dict(cal="Calibration route", tc0="Leg 1 · TC0 → TC1", tc1="Leg 1 · TC1 → TC2", tc2="Leg 1 · TC2 → TC3",
               new="Leg 1 - TC3 to TC4"),
    "ru": dict(cal="Тарировочный участок", tc0="Этап 1 · КВ0 → КВ1", tc1="Этап 1 · КВ1 → КВ2", tc2="Этап 1 · КВ2 → КВ3",
               new=None),  # adb cannot type Cyrillic
}[ui.LOCALE]

# The main section, TC1 → TC2: 40 km in 50 minutes (48 km/h) from 10:12:00, with a regularity inside.
# These are its roadmaps at the points where the series start; race mode records them the same way when you drive.
TC1_NEW = "0.0 setavg 48 atime 10:12:00\n40.0"  # as created from the road book
TC1_PASSED = "0.0 setavg 48 atime 10:12:00\n3.15\n7.8\n40.0"  # two road-book points added on the way
TC1_DRIVEN = ("0.0 setavg 48 atime 10:12:00\n3.15\n7.8\n12.4 setavg 36\n17.3 thenavg 40\n21.05 thenavg 30\n"
              "24.85 endavg\n28.4\n33.75\n40.0")  # after the regularity and the rest of the section
TC1_HERO = ("0.0 setavg 48 atime 10:12:00\n1.45\n3.15\n5.2\n6.35\n7.8\n9.1\n10.6\n11.75\n40.0")  # many road-book points
TC2_OK = "0.0 setavg 50 atime 11:20:00\n36.8"
TC2_BROKEN = "0.0 setavg 50 atime 11:20:00\n18.4 endavg\n25.0\n36.8"  # an error: positions after the stretch ended
KEY_UP, KEY_DOWN = (131, 1755), (405, 1755)  # the editor's ▲ and ▼ keys have no text to find them by


def sh(*cmd):
    return adb("shell", *cmd)


def wait(s):
    time.sleep(s)


def tap(text, idx=0, exact=False):
    """Taps an element by its text or description; without `exact`, a partial match counts if nothing matches fully."""
    for _ in range(10):  # the screen may still be changing (a keyboard, an animation)
        ms = [n for n in nodes() if text in (n["text"], n["desc"])] if exact else match(text)
        if ms:
            break
        wait(0.4)
    else:
        sys.exit(f"not found on screen: {text!r}")
    tap_node(ms[idx]); wait(0.15)  # animations are off (setup_device), so the screen settles quickly


def xy(x, y):
    sh("input", "tap", str(x), str(y)); wait(0.6)


def back():
    sh("input", "keyevent", "KEYCODE_BACK"); wait(0.8)


def back_to_list():
    """Presses Back until the section list shows: the first Back may only close the keyboard."""
    for _ in range(3):
        if match(SECTIONS["cal"]):  # only the list shows it; a section shows its own name in the title
            return
        back()
    sys.exit("could not get back to the section list")


def hide_keyboard():
    """Presses Back only if the keyboard is shown: otherwise Back would leave the screen."""
    if "mInputShown=true" in sh("dumpsys", "input_method"):
        back()


def shot(name):
    """Saves the screen to raw/<name>.png and its UI tree to raw/<name>.xml (annotate.py finds the elements in it)."""
    os.makedirs(ui.OUT, exist_ok=True)
    xml = ui.dump_xml()
    if "<node" not in xml:
        sys.exit(f"{name}: empty UI tree")
    open(os.path.join(ui.OUT, name + ".xml"), "w").write(xml)
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
    """Types into the idx-th text field, one character at a time (fast input loses characters in filtered fields)."""
    tap_node(edit_texts()[idx]); wait(0.4)
    for k, word in enumerate(text.split(" ")):
        if k:
            sh("input", "text", "%s"); wait(0.1)
        for ch in word:
            sh("input", "text", ch); wait(0.1)
    wait(0.2)


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


def screenshot_mode(**extras):
    """Sends the debug app's screenshot-mode broadcast: on=True hides the simulation slider and shows OBD ✔."""
    args = ["am", "broadcast", "-a", ui.MODE]
    for k, v in extras.items():
        args += ["--ez", k, "true" if v else "false"] if isinstance(v, bool) else ["--ei", k, str(v)]
    sh(*args)


def slider(kmh):
    """Sets the simulated speed, as the hidden simulation slider would."""
    screenshot_mode(speed=kmh)


def swipe_speed_row(fraction=1.0, backwards=False):
    """Scrolls the row of speeds sideways, by default all the way to the round speed buttons. The swipe starts on the
    leftmost visible speed field: on a tablet the list is at the same height, and a swipe there would select a row."""
    fields = [n for n in nodes() if re.fullmatch(r"\d+%|=?" + re.escape(S("perHourSuffix")) + " →", n["text"])]
    field = min(fields, key=lambda n: n["b"][0])["b"]
    y = (field[1] + field[3]) // 2
    right = max(n["b"][2] for n in nodes() if n["b"][1] <= y <= n["b"][3] and n["b"][0] >= field[0])
    distance = int((right - field[0] - 120) * fraction)
    x0, x1 = (right - 60, right - 60 - distance) if not backwards else (field[0] + 60, field[0] + 60 + distance)
    sh("input", "swipe", str(x0), str(y), str(x1), str(y), "500"); wait(0.8)


SWIPE_SLOP = 16  # pixels of a swipe that go into the touch slop before the row starts to move


def scroll_speed_row(delta, tries=4):
    """Scrolls the row of speeds left by `delta` pixels (right if negative), with slow swipes that do not fling,
    measuring the "100%"-or-"80%" percentage field after each one."""
    def percent_x():
        return [n["b"] for n in nodes() if re.fullmatch(r"\d+%", n["text"]) and n["cls"] == "EditText"][0]
    target = percent_x()[0] - delta
    for _ in range(tries):
        x0, y0, x1, y1 = percent_x()
        rest = x0 - target
        if abs(rest) <= 1:
            return
        y = (y0 + y1) // 2
        length = rest + (SWIPE_SLOP if rest > 0 else -SWIPE_SLOP)
        start = x1 + 500  # on the round buttons: touching a text field would focus it
        sh("input", "swipe", str(start), str(y), str(start - length), str(y), "1500"); wait(0.5)


def tidy_speed_row():
    """Scrolls the row of speeds so that the percentage field shows near the left and no button is cut off at either
    end: the edges of the row fall into gaps between the elements."""
    ns = nodes()
    percent = [n["b"] for n in ns if re.fullmatch(r"\d+%", n["text"]) and n["cls"] == "EditText"][0]
    band = [n["b"] for n in ns if n["b"][1] <= (percent[1] + percent[3]) // 2 <= n["b"][3]]
    viewport = max((b for b in band if b[0] > percent[0] - 400), key=lambda b: b[2] - b[0])  # the row itself
    left, right = viewport[0], viewport[2]
    items = [b for b in band if b != viewport and b[2] - b[0] < 400 and b[0] >= left - 1000]
    def clean(d):
        shifted = [(b[0] - d, b[2] - d) for b in items]
        straddles = any(a - 8 < edge < z + 8 for a, z in shifted for edge in (left, right))  # 8 px for swipe errors
        return not straddles and percent[0] - d >= left
    candidates = [d for d in range(-400, 401) if clean(d)]
    if candidates:
        scroll_speed_row(min(candidates, key=lambda d: percent[0] - d - left))  # the percentage nearest the left


def speed_row_to_end(tries=5):
    """Flings the row of speeds to its end, where its last button, 110, sits at the right edge."""
    for _ in range(tries):
        ns = nodes()
        last = [n["b"] for n in ns if n["text"] == "110"]
        button = [n["b"] for n in ns if re.fullmatch(r"\d{2,3}", n["text"]) and n["b"][2] - n["b"][0] < 120][0]
        y = (button[1] + button[3]) // 2
        screen = max(n["b"][2] for n in ns if n["b"][0] == 0)  # the app's width: off-screen buttons reach beyond it
        right = max(n["b"][2] for n in ns if n["b"][1] <= y <= n["b"][3] and n["b"][2] <= screen)
        if last and last[0][2] <= right:
            return
        sh("input", "swipe", str(right - 60), str(y), str(right - 560), str(y), "100"); wait(0.8)


def launch():
    sh("am", "start", "-W", "--activity-clear-task", "-n", PKG + "/com.h0tk3y.rally.android.MainActivity"); wait(1)
    screenshot_mode(on=True)  # the mode lives in the app's process, so turn it on after each start


def seed_sql(tc1, tc2=TC2_OK):
    q = lambda v: v.replace("'", "''")
    rows = [(SECTIONS["cal"], "0.0 setavg 60\n10.0"), (SECTIONS["tc0"], "0.0 setavg 45 atime 09:30:00\n22.5"),
            (SECTIONS["tc1"], tc1), (SECTIONS["tc2"], tc2)]
    return ("INSERT INTO Section(name, serializedPositions) VALUES\n" +
            ",\n".join(f"('{q(n)}', '{q(p)}')" for n, p in rows) + ";\n")


def reset_data(hms, tc1=TC1_NEW, tc2=TC2_OK):
    """Kills the app, so that race mode forgets its timers, and replaces all sections and events."""
    if "app.db" not in sh(f"run-as {PKG} ls databases 2>/dev/null"):
        launch()  # on a fresh device, the first launch creates the database
    sh("am", "force-stop", PKG)
    # One file per device: capture_all.py seeds several emulators at the same time
    seed = os.path.join(HERE, "tmp", f"seed-{os.environ['ANDROID_SERIAL']}.sql")
    os.makedirs(os.path.dirname(seed), exist_ok=True)
    open(seed, "w").write(seed_sql(tc1, tc2))
    adb("push", seed, "/data/local/tmp/seed.sql")
    sh("chmod", "644", "/data/local/tmp/seed.sql")
    sh(f"run-as {PKG} sh -c 'sqlite3 databases/app.db \"DELETE FROM Event; DELETE FROM Section; "
       f"DELETE FROM sqlite_sequence;\" && sqlite3 databases/app.db < /data/local/tmp/seed.sql'")
    set_time(hms)
    launch()
    if match(S("cancel")):  # a dialog restored from an earlier run
        tap(S("cancel"))


def build_apk():
    """Builds the debug app without the "DEBUG" prefix in the title, so it looks like the release."""
    f = os.path.join(ROOT, "android/src/debug/res/values/strings.xml")
    original = open(f).read()
    try:
        open(f, "w").write(original.replace(">DEBUG h0tk3y", ">h0tk3y"))
        subprocess.run(["./gradlew", ":android:assembleDebug", "-q"], cwd=ROOT, check=True)
    finally:
        open(f, "w").write(original)


def build_and_install():
    build_apk()
    adb("install", "-r", os.path.join(ROOT, "android/build/outputs/apk/debug/android-debug.apk"))


def setup_device(font_scale="0.85"):
    if sh("getprop", "debug.rally.screenshots").strip() == f"{ui.LOCALE}-{font_scale}-noanim":
        return  # already set up since the last boot (demo mode and these settings last until a reboot)
    if sh("id", "-u").strip() != "0":  # restarting adbd as root takes a couple of seconds: only when needed
        adb("root"); wait(2); adb("wait-for-device")
    sh("settings", "put", "system", "font_scale", font_scale)
    for scale in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        sh("settings", "put", "global", scale, "0")  # no animations: a tap's result shows at once (Compose follows these)  # 0.85 keeps long labels from clipping on the phone
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
    sh("dumpsys", "deviceidle", "whitelist", "+" + PKG)  # exempt from battery optimization: no one-time battery hint
    for p in ["ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN", "POST_NOTIFICATIONS"]:
        sh("pm", "grant", PKG, "android.permission." + p)
    sh("setprop", "debug.rally.screenshots", f"{ui.LOCALE}-{font_scale}-noanim")


# Steps used by several series --------------------------------------------------------------------------------------

RACE_CONFIG_MARKER = "/data/local/tmp/rally-race-config"  # the settings survive reset_data, so set them once


def configure_for_race():
    """From the section list: Simulation as the distance source, an allowance, and this device as the driver's device."""
    if sh("ls", RACE_CONFIG_MARKER).strip() == RACE_CONFIG_MARKER:
        return
    tap(S("showMenu")); tap(S("settings")); wait(1)
    tap(S("telemetrySourceSimulation"))
    sh("input", "swipe", "540", "1800", "540", "900", "300"); wait(0.8)
    tap(S("allowanceT10Down"))
    # The address field is the one around its label; the speed buttons field comes after it
    label = next(n for n in nodes() if n["text"] == S("settingsTheOtherDeviceIpAddress"))
    address = next(i for i, n in enumerate(edit_texts()) if n["b"][1] <= label["b"][1] < n["b"][3])
    clear(address); fill(address, "127.0.0.1")
    back_to_list()
    sh("touch", RACE_CONFIG_MARKER)


def open_race(hms, tc1):
    """Starts from clean data with the given roadmap, opens TC1 → TC2 and turns race mode on."""
    reset_data(hms, tc1)
    configure_for_race()
    tap(SECTIONS["tc1"]); wait(1)
    tap(S("buttonRaceMode")); wait(1.5)


def start_section(at="10:11:58"):
    """Starts the section timer from 0.0 at 10:12:00, with the clock set to `at` first, so that the event log shows the
    real start. The series then move the clock on with goto()."""
    tap("0.0")
    set_time(at)
    tap(S("raceRowButtonOnTime", "10:12:00"), 0)


def remember_speed(kmh):
    """Sets the speed of the next regularity with the v button; returns with the row of speeds open."""
    tap(S("vSpeedButton")); tap("=" + S("perHourSuffix") + " →"); sh("input", "text", str(kmh)); wait(0.5)
    tap(str(kmh), 0)  # the round button next to the field
    hide_keyboard()


# The series ----------------------------------------------------------------------------------------------------------

def series_settings():
    reset_data("10:05:00")
    sh("rm", "-f", RACE_CONFIG_MARKER)  # this series changes the source; configure_for_race() sets it back
    tap(S("showMenu")); tap(S("settings")); wait(1)
    tap(S("telemetrySourceObd"))
    clear(0); fill(0, "00:1D:A5:68:98:8B")
    clear(1); fill(1, "1.018"); hide_keyboard(); tap(S("saveButton"))
    back_to_list(); tap(S("showMenu")); tap(S("settings")); wait(1)  # reopen, so that no field has the focus
    shot("10-settings-obd")
    back_to_list()
    configure_for_race()  # leave the device ready for the race series


def series_list():
    reset_data("10:05:00")
    shot("01-section-list")
    tap(S("createSection")); wait(1)
    if SECTIONS["new"]:
        fill(0, SECTIONS["new"])
    fill(1, "28.6"); fill(2, "0:38"); fill(4, "12:05"); hide_keyboard()
    shot("04-create-section")
    tap(S("cancel"))


def series_start():
    open_race("10:11:40", TC1_NEW)
    shot("06-race-not-started")
    start_section(at="10:11:50")
    wait(2)  # the early/late value appears a moment after the start
    shot("06-countdown")


def series_liaison():
    """The section timer runs; the co-driver remembers the speed of the coming regularity."""
    open_race("10:24:00", TC1_PASSED)
    start_section()
    slider(53)
    goto(9.6, "10:24:10")
    remember_speed(36)
    shot("06-v-remembered")
    tap("36", 0)  # the v button closes the row
    tap("7.8")  # select the last passed position
    wait(1)
    shot("06-section-timer")


def series_regularity():
    """The regularity: start at 12.4, speed changes at 17.3 and 21.05, finish at 24.85; then the end of the section."""
    open_race("10:27:00", TC1_PASSED)
    start_section()
    slider(53)
    remember_speed(36)
    tap("36", 0)
    goto(12.4, "10:27:22")
    tap(S("nowAtPosition", "12.4"), 0)
    slider(35)
    goto(14.0, "10:30:02"); wait(2)
    shot("06-regularity")
    swipe_speed_row()
    goto(17.3, "10:35:32"); tap("40", exact=True)
    slider(38)
    goto(21.05, "10:41:10"); tap("30", exact=True)
    slider(27)
    goto(24.85, "10:48:46")
    tap(S("raceButtonFinishRace")); wait(1)
    shot("06-finished")
    slider(70)
    goto(40.0, "11:01:58")
    tap(S("raceButtonStop")); wait(1)
    tap(S("eventLog")); wait(1)
    shot("06-event-log")
    back()


def series_end():
    """The rest of the section after the regularity, the driver's screen, and the end of the section."""
    open_race("10:56:00", TC1_DRIVEN)
    start_section()
    slider(70)
    goto(33.75, "10:56:32")
    tap("40.0", -1)
    back_to_list()
    tap(S("showMenu")); tap(S("driverHud")); wait(3)  # the driver's device address is 127.0.0.1: this device
    shot("11-driver-hud")
    tap(S("showMenu")); tap(S("buttonSwapListAndRacePanel")); wait(1.5)
    shot("11-driver-hud-swapped")
    tap(S("showMenu")); tap(S("buttonSwapListAndRacePanel")); wait(1)  # the choice is saved: swap back
    back_to_list()
    tap(SECTIONS["tc1"]); wait(1)
    goto(40.0, "11:01:58")
    tap(S("raceButtonStop")); wait(1)
    shot("06-stopped")
    tap("40.0", -1); wait(0.5)
    shot("14-section-times")
    tap(S("showMenu"))
    shot("05-menu-race-on")
    tap(S("buttonHideRacePanel")); wait(1)
    shot("05-section-rows")
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
    sh("settings", "put", "system", "user_rotation", "1"); wait(2.5)
    shot("06-landscape")
    sh("settings", "put", "system", "user_rotation", "0"); wait(2)


def series_hero():
    """On the tablet: a regularity in progress, full of road-book points, with a speed change by a percentage of the
    speed limit just entered and an earlier position selected. The speed fields are captured on the way."""
    open_race("10:27:00", TC1_HERO)
    start_section()
    slider(53)
    remember_speed(36)
    tap("36", 0)
    goto(12.4, "10:27:22")
    tap(S("nowAtPosition", "12.4"), 0)
    slider(36)
    goto(13.0, "10:28:22"); wait(1)
    shot("06-speed-buttons")  # the tablet fits all the speed fields
    swipe_speed_row()
    goto(14.6, "10:31:02"); tap(S("buttonAddPassedPosition"))
    goto(16.1, "10:33:32"); tap(S("buttonAddPassedPosition"))
    goto(17.3, "10:35:32"); tap("40", exact=True)  # 40 km/h from here
    goto(18.3, "10:37:02"); tap(S("buttonAddPassedPosition"))
    goto(19.2, "10:38:23"); tap(S("buttonAddPassedPosition"))
    goto(20.1, "10:39:44"); tap(S("buttonAddPassedPosition"))
    goto(21.05, "10:41:10"); tap("30", exact=True)  # 30 km/h from here
    goto(22.6, "10:44:16"); tap(S("buttonAddPassedPosition"))
    # The road book: 80% of the speed limit from here; the sign says 50, so 40 km/h.
    goto(23.4, "10:45:52")
    tap("100%"); sh("input", "text", "80"); hide_keyboard()
    speed_row_to_end()  # typing scrolled the row back to the field
    tap("50", exact=True)
    slider(41)
    goto(23.9, "10:46:37")
    wait(1)  # a tap right after the distance edit only closes the keyboard
    tap("19.2")  # an earlier position, not the last passed one (that would make the selection follow the car)
    speed_row_to_end()  # the last button, 110, at the right edge: nothing cut off there
    wait(1)
    shot("00-hero")


def series_editor():
    reset_data("11:05:00", TC1_DRIVEN, TC2_BROKEN)
    tap(SECTIONS["tc1"]); wait(1)
    tap(S("buttonEdit")); wait(0.6)
    for _ in range(5):
        xy(*KEY_DOWN)  # from 0.0 down to 21.05, with the keys: a tap would show the cursor handle
    shot("16-editor")
    tap(S("buttonCalculate"))
    back_to_list()
    # An error, seeded: TC2 → TC3 has positions after its only stretch has ended.
    tap(SECTIONS["tc2"]); wait(1)
    shot("15-error")
    back_to_list()


DEVICES = {  # the emulators, see README.md; override with RALLY_PHONE and RALLY_TABLET
    "phone": os.environ.get("RALLY_PHONE", "emulator-5560"),
    "tablet": os.environ.get("RALLY_TABLET", "emulator-5562"),
}
TABLET_SERIES = {"hero"}  # every other series runs on the phone

SERIES = {  # name: (function, the screenshots it captures)
    "hero": (series_hero, ["06-speed-buttons", "00-hero"]),
    "settings": (series_settings, ["10-settings-obd"]),
    "list": (series_list, ["01-section-list", "04-create-section"]),
    "start": (series_start, ["06-race-not-started", "06-countdown"]),
    "liaison": (series_liaison, ["06-v-remembered", "06-section-timer"]),
    "regularity": (series_regularity, ["06-regularity", "06-finished", "06-event-log"]),
    "end": (series_end, ["11-driver-hud", "11-driver-hud-swapped", "06-stopped", "14-section-times", "05-menu-race-on",
                         "05-section-rows", "06-backwards", "06-distance-edit", "06-landscape"]),
    "editor": (series_editor, ["16-editor", "15-error"]),
}


def main():
    args = sys.argv[1:]
    def opt(name):
        return args[args.index(name) + 1] if name in args else None
    if "--list" in args:
        for name, (_, shots) in SERIES.items():
            print(f"{name}: {', '.join(shots)}")
        return
    names = list(SERIES)
    if opt("--series"):
        names = opt("--series").split(",")
    if opt("--shot"):
        names = [n for n, (_, shots) in SERIES.items() if opt("--shot") in shots]
    unknown = [n for n in names if n not in SERIES]
    if unknown or not names:
        sys.exit(f"unknown series or screenshot: {unknown or opt('--shot')}; see --list")
    prepared = set()
    for name in names:
        device = "tablet" if name in TABLET_SERIES else "phone"
        os.environ["ANDROID_SERIAL"] = DEVICES[device]
        if device not in prepared:
            if "--build" in args:
                build_and_install()
            setup_device("1.0" if device == "tablet" else "0.85")
            prepared.add(device)
        print("series", name, "on", DEVICES[device])
        SERIES[name][0]()


if __name__ == "__main__":
    main()
