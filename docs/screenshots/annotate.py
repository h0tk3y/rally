#!/usr/bin/env python3
"""Builds the annotated screenshots for docs/user-guide.md from raw/*.png and raw/*.xml.

Elements are found by their text or description in the UI tree saved with each capture, so the marks
follow the layout. The marks follow one set of rules:
- a frame has the same padding around every element, and elements in one row get frames of the same height;
- a badge sits on the top-left corner of its frame, or of its element when the element has no frame;
- badges in one row share the same height.

Usage: annotate.py [output dir]   (default: ../images)
"""
import os, re, subprocess, sys
import xml.etree.ElementTree as ET

import ui
from ui import S

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = ui.OUT  # raw/<locale>, see RALLY_LOCALE in ui.py
TMP = os.path.join(HERE, "tmp", ui.LOCALE)
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "images", *([] if ui.LOCALE == "en" else [ui.LOCALE]))
FONT = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_REG = "/System/Library/Fonts/Supplemental/Arial.ttf"
TEAL = "#1A9BA8"
TEAL_LINE = "rgba(26,155,168,0.55)"
SECTIONS = {"en": dict(tc2="Leg 1 · TC2 → TC3"), "ru": dict(tc2="Этап 1 · КВ2 → КВ3")}[ui.LOCALE]
CAPTIONS = {
    "en": dict(hidden="Race panel hidden: normal times", open="Race panel open: section times",
               section_timer="Section timer", regularity_timer="Regularity timer", not_started="Not started",
               section_runs="Section timer runs", regularity_runs="Regularity timer runs", stopped="Stopped",
               co_driver="The co-driver's device", driver="The driver's screen"),
    "ru": dict(hidden="Панель гонки скрыта: обычное время", open="Панель гонки открыта: время секции",
               section_timer="Таймер секции", regularity_timer="Таймер ДС", not_started="Гонка не запущена",
               section_runs="Идёт таймер секции", regularity_runs="Идёт таймер ДС", stopped="Остановка",
               co_driver="Устройство штурмана", driver="Экран пилота"),
}[ui.LOCALE]
PER_H = re.escape(S("perHourSuffix"))
PAD_X, PAD_Y = 10, 0  # frame padding around an element; element bounds already have some air above and below
BADGE_R = 30
os.makedirs(TMP, exist_ok=True)
os.makedirs(OUT, exist_ok=True)


# Geometry ------------------------------------------------------------------------------------------

class Box:
    def __init__(self, x0, y0, x1, y1):
        self.x0, self.y0, self.x1, self.y1 = x0, y0, x1, y1

    def __or__(self, o):
        return Box(min(self.x0, o.x0), min(self.y0, o.y0), max(self.x1, o.x1), max(self.y1, o.y1))

    def pad(self, dx, dy):
        return Box(self.x0 - dx, self.y0 - dy, self.x1 + dx, self.y1 + dy)

    @property
    def cy(self):
        return (self.y0 + self.y1) / 2

    def contains(self, o):
        return self.x0 <= o.x0 and self.y0 <= o.y0 and self.x1 >= o.x1 and self.y1 >= o.y1

    def area(self):
        return (self.x1 - self.x0) * (self.y1 - self.y0)


def union(*boxes):
    out = boxes[0]
    for b in boxes[1:]:
        out = out | b
    return out


# The UI tree of a capture --------------------------------------------------------------------------

class Screen:
    def __init__(self, name):
        self.name = name
        self.png = os.path.join(RAW, name + ".png")
        self.nodes = []
        for n in ET.parse(os.path.join(RAW, name + ".xml")).iter("node"):
            b = list(map(int, re.findall(r"\d+", n.get("bounds"))))
            self.nodes.append(dict(text=n.get("text", ""), desc=n.get("content-desc", ""),
                                   cls=n.get("class", "").split(".")[-1], click=n.get("clickable") == "true",
                                   focused=n.get("focused") == "true",
                                   box=Box(*b)))

    def find(self, text=None, desc=None, rx=None, cls=None, y=None, idx=0, button=False):
        """An element by its exact text or description, or a regex over its text; y limits its centre to a band.
        button=True returns the smallest clickable element around it (the whole button, not only its label)."""
        ms = [n for n in self.nodes
              if (text is None or n["text"] == text) and (desc is None or n["desc"] == desc)
              and (rx is None or re.fullmatch(rx, n["text"])) and (cls is None or n["cls"] == cls)
              and (y is None or y[0] <= n["box"].cy <= y[1])]
        if not ms:
            sys.exit(f"{self.name}: no element text={text!r} desc={desc!r} rx={rx!r} y={y}")
        box = ms[idx]["box"]
        if button:
            around = [n["box"] for n in self.nodes if n["click"] and n["box"].contains(box)]
            box = min(around, key=Box.area) if around else box
        return box

    def row(self, anchor, tol=40):
        """All visible elements whose centre is at the height of the anchor's centre."""
        bs = [n["box"] for n in self.nodes
              if (n["text"] or n["desc"] or n["click"]) and abs(n["box"].cy - anchor.cy) <= tol
              and n["box"].x1 - n["box"].x0 < 1000 and n["box"].y1 - n["box"].y0 < 200]
        return union(anchor, *bs)


# Drawing --------------------------------------------------------------------------------------------

def mg(*args):
    subprocess.run(["magick", *map(str, args)], check=True, stderr=subprocess.DEVNULL)


def t(name):
    return os.path.join(TMP, name + ".png")


def badge(n):
    p = os.path.join(TMP, f"badge-{n}.png")
    if not os.path.exists(p):
        s = 2 * BADGE_R + 8
        mg("-size", f"{s}x{s}", "xc:none", "-fill", TEAL, "-stroke", "white", "-strokewidth", 4,
           "-draw", f"circle {s // 2},{s // 2} {s // 2},4", "-fill", "white", "-stroke", "none", "-font", FONT,
           "-pointsize", 30 if n < 10 else 25, "-gravity", "center", "-annotate", "+0+1", str(n), p)
    return p


def mark(n, box, frame=True):
    return dict(n=n, box=box, frame=frame)


def align_rows(marks):
    """Elements whose centres are close in height form a row: their frames get the same top and bottom."""
    rows = []
    for m in sorted(marks, key=lambda m: m["box"].cy):
        if rows and abs(rows[-1][0]["box"].cy - m["box"].cy) < 45:
            rows[-1].append(m)
        else:
            rows.append([m])
    for row in rows:
        y0, y1 = min(m["box"].y0 for m in row), max(m["box"].y1 for m in row)
        for m in row:
            b = m["box"]
            m["box"] = Box(b.x0, y0, b.x1, y1)


SIDE = 2 * BADGE_R + 16  # white margin on both sides, so that badges of elements at the edge have room


def badge_spot(m, frames, others, side_order, canvas):
    """The badge goes just outside its frame: on the left, vertically centred, unless something is there."""
    f = frames[id(m)]
    r = BADGE_R + 4
    spots = dict(left=(f.x0 - r - 2, f.cy), right=(f.x1 + r + 2, f.cy), above=((f.x0 + f.x1) / 2, f.y0 - r - 2),
                 below=((f.x0 + f.x1) / 2, f.y1 + r + 2))
    for side in side_order:
        x, y = spots[side]
        spot = Box(x - r, y - r, x + r, y + r)
        if canvas.contains(spot) and not any(overlaps(spot, o) for o in others if o is not f):
            return x, y
    return spots[side_order[0]]


def overlaps(a, b):
    return a.x0 < b.x1 and b.x0 < a.x1 and a.y0 < b.y1 and b.y0 < a.y1


def render(screen, crop, marks, name, top=0, sides=("left", "above", "right", "below"), side_margin=True):
    """Crops the capture, adds white margins (`top` pixels above), and draws the frames and the badges."""
    align_rows(marks)
    m_side = SIDE if side_margin and marks else 0
    dx, dy = -crop.x0 + m_side, -crop.y0 + top
    w, h = crop.x1 - crop.x0 + 2 * m_side, crop.y1 - crop.y0 + top
    args = [screen.png, "-crop", f"{crop.x1 - crop.x0}x{crop.y1 - crop.y0}+{crop.x0}+{crop.y0}", "+repage",
            "-background", "white", "-gravity", "north", "-splice", f"0x{top}",
            "-gravity", "west", "-splice", f"{m_side}x0", "-gravity", "east", "-splice", f"{m_side}x0",
            "-gravity", "northwest", "-fill", "none", "-stroke", TEAL_LINE, "-strokewidth", 4]
    frames = {id(m): m["box"].pad(PAD_X, PAD_Y) for m in marks}
    # Everything a badge must not cover: the frames and the visible elements inside the crop, except its own.
    visible = [n["box"] for n in screen.nodes if (n["text"] or n["desc"]) and overlaps(n["box"], crop)
               and n["box"].x1 - n["box"].x0 < 1000]
    for m in marks:
        if m["frame"]:
            f = frames[id(m)]
            args += ["-draw", "roundrectangle %d,%d %d,%d 16,16" % (f.x0 + dx, f.y0 + dy, f.x1 + dx, f.y1 + dy)]
    placed = []
    for m in marks:
        f = frames[id(m)]
        others = [o for o in list(frames.values()) + placed if o is not f] + \
                 [v for v in visible if not f.contains(v)]
        canvas = Box(crop.x0 - m_side, crop.y0 - top, crop.x1 + m_side, crop.y1)
        x, y = badge_spot(m, frames, others, sides, canvas)
        r = BADGE_R + 4
        placed.append(Box(x - r, y - r, x + r, y + r))
        args += [badge(m["n"]), "-geometry", "+%d+%d" % (x + dx - r, y + dy - r), "-composite"]
    mg(*args, t(name))
    return t(name)


def caption(img, text, dst):
    mg(img, "-background", "white", "-fill", "#333333", "-font", FONT_REG, "-pointsize", 44,
       "-gravity", "south", "-splice", "0x90", "-annotate", "+0+22", text, dst)
    return dst


def border(img, dst):
    mg(img, "-bordercolor", "#cccccc", "-border", 3, dst)
    return dst


def save(img, name, scale):
    dst = os.path.join(OUT, name + ".png")
    mg(border(img, t(name + "-border")), "-resize", f"{scale}%", "-strip", "-colors", 256,
       "-define", "png:compression-level=9", dst)
    print(dst, os.path.getsize(dst) // 1024, "KB")


def band(screen, top, bottom, margin=24, x0=0, x1=1080):
    """A full-width crop from the top of one box to the bottom of another."""
    return Box(x0, max(top.y0 - margin, 0), x1, bottom.y1 + margin)


# The pictures ----------------------------------------------------------------------------------------

def hero():
    """The picture at the top of the guide, from the tablet."""
    s = Screen("00-hero")
    w, h = max(n["box"].x1 for n in s.nodes), max(n["box"].y1 for n in s.nodes)
    save(render(s, Box(0, 0, w, h), [], "hero"), "hero", 36)


def section_list():
    s = Screen("01-section-list")
    plus, menu = s.find(desc=S("createSection"), button=True), s.find(desc=S("showMenu"), button=True)
    last = s.find(text=SECTIONS["tc2"], button=True)
    save(render(s, Box(0, 75, 1080, last.y1), [mark(1, plus, False), mark(2, menu, False)], "list"),
         "section-list", 50)


def create_section():
    s = Screen("04-create-section")
    time_row = s.row(s.find(text=S("timeHhMmSs")))
    speed_row = s.row(s.find(rx=r".*" + PER_H, cls="EditText"))
    title = s.find(text=S("createSection"))
    save_btn = s.find(text=S("saveButton"), button=True)
    crop = Box(60, title.y0 - 60, 1020, save_btn.y1 + 50)
    save(render(s, crop, [mark(1, time_row), mark(2, speed_row)], "create"), "create-section", 50)


def section_screen():
    s = Screen("05-section-rows")
    edit = s.find(text=S("buttonEdit"), button=True)
    race = s.find(text=S("buttonRaceMode"), button=True)
    save(render(s, Box(0, 75, 1080, 2330), [mark(1, edit, False), mark(2, race, False)], "screen"),
         "section-screen", 40)


def row_anatomy():
    s = Screen("05-section-rows")
    imgs = []
    for i, (rx, parts) in enumerate([(r"17\.3\d*", "first"), (r"24\.8\d*", "second")]):
        dist = s.find(rx=rx, cls="EditText")
        row_y = (dist.y0, dist.y1)
        if parts == "first":
            marks_ = [mark(1, dist, False),
                      mark(2, union(s.find(rx=re.escape(S("labelThenAvg", "")) + r"\d", y=row_y), s.find(text="40.0", cls="EditText", y=row_y)), False),
                      mark(4, union(*[s.find(rx=r"\d\d:\d\d", y=row_y, idx=k) for k in (0, 1)]), False),
                      mark(5, s.find(rx=r"\d\d:\d\d:\d\d", y=row_y), False),
                      mark(6, s.find(rx=r"-\d", y=row_y), False)]
        else:
            marks_ = [mark(3, s.find(rx=r"➠.*", y=row_y), False)]
        imgs.append(render(s, Box(0, dist.y0 + 4, 1080, dist.y1 - 4), marks_, f"rows-{i}", top=72,
                           sides=("above",), side_margin=False))
    mg(*imgs, "-append", t("rows"))
    save(t("rows"), "row-anatomy", 50)


def section_menu():
    s = Screen("05-menu-race-on")
    hide, drop = s.find(text=S("buttonHideRacePanel"), button=True), s.find(text=S("buttonDropRaceState"), button=True)
    settings = s.find(text=S("settings"), button=True)
    crop = Box(hide.x0 - 40, 75, 1080, settings.y1 + 20)
    save(render(s, crop, [], "menu"), "section-menu", 50)


def timers():
    """The clock row of each timer, as two pictures that the guide shows side by side in a table."""
    for name, desc, out in (("06-section-timer", S("raceStatusSectionGoing"), "timer-section"),
                            ("06-regularity", S("raceStatusRaceGoing"), "timer-regularity")):
        s = Screen(name)
        icon, clock, early, dist = panel_parts(s, desc)
        save(render(s, Box(0, clock.y0 - 16, 1080, clock.y1 + 16), [], out), out, 40)


def panel_parts(s, timer_desc):
    """The parts of the race panel that the section timer and the regularity timer have in common."""
    icon = s.find(desc=timer_desc)
    clock = union(icon, s.find(rx=r"-?\d\d:\d\d(:\d\d)?", y=(icon.y0, icon.y1)))
    early = max((n["box"] for n in s.nodes if re.fullmatch(r"[+-]?[\d:.]+", n["text"])
                 and icon.y0 <= n["box"].cy <= icon.y1), key=lambda b: b.x0)  # the rightmost value: 0.0 has no sign
    dist = s.find(rx=r"-?\d+\.\d{3}", cls="TextView", y=(icon.y1, icon.y1 + 150))
    return icon, clock, early, dist


def race_panel():
    s = Screen("06-section-timer")
    icon, clock, early, dist = panel_parts(s, S("raceStatusSectionGoing"))
    toolbar = union(s.find(desc=S("eventLog"), button=True), s.find(desc=S("buttonAddPassedPosition"), button=True))
    allowance = s.find(rx=r"\(" + S("allowanceLetter") + r":\d+\)")
    delta = s.find(rx=r"[+-]\d+\.\d{3}", y=(dist.y0, dist.y1))
    correction = union(s.find(desc=S("switchDirectionHint")), s.find(text="-0.1"), s.find(text="+0.1"))
    speed = s.find(rx=r"v=.*")
    start = s.row(s.find(text=S("preLabelStartRace")))
    stop = s.find(text=S("raceButtonStop"), button=True)
    marks_ = [mark(1, toolbar), mark(2, clock), mark(3, allowance, False), mark(4, early), mark(5, dist | delta),
              mark(6, correction), mark(7, speed), mark(8, start), mark(9, stop, False)]
    save(render(s, band(s, toolbar, stop), marks_, "panel"), "race-panel", 50)  # without the simulation slider


def race_panel_regularity():
    s = Screen("06-regularity")
    icon, clock, early, dist = panel_parts(s, S("raceStatusRaceGoing"))
    avg = s.find(rx=r"ṽ=.*")
    speeds = s.row(s.find(text="=" + S("perHourSuffix") + " →"))
    finish, finish_start = s.find(text=S("raceButtonFinishRace"), button=True), s.find(text=S("raceButtonFinishAndStart"), button=True)
    marks_ = [mark(1, clock), mark(2, avg), mark(3, speeds), mark(4, finish, False), mark(5, finish_start, False)]
    save(render(s, band(s, clock, finish), marks_, "regularity"), "race-panel-regularity", 50)


def speed_buttons():
    """From the tablet, where the whole row of speed fields fits; the badges go above, clear of the × sign."""
    s = Screen("06-speed-buttons")
    exact = s.find(text="=" + S("perHourSuffix") + " →", button=True)
    row_y = (exact.y0, exact.y1)
    def round_button_right_of(field):
        return min((n["box"] for n in s.nodes if n["click"] and n["box"].x0 > field.x1 and row_y[0] <= n["box"].cy <= row_y[1]),
                   key=lambda b: b.x0)
    any_speed = s.find(text=S("perHourSuffix") + " →", button=True)
    percent = s.find(text="100%", button=True)
    speeds = union(*[s.find(text=str(v), button=True) for v in (5, 10, 20) if s.nodes])
    speeds = Box(speeds.x0, speeds.y0, max(n["box"].x1 for n in s.nodes if row_y[0] <= n["box"].cy <= row_y[1]), speeds.y1)
    marks_ = [mark(1, exact | round_button_right_of(exact)), mark(2, percent),  # numbered left to right
              mark(3, any_speed | round_button_right_of(any_speed)), mark(4, speeds)]
    crop = Box(exact.x0 - 20, exact.y0 - 20, max(n["box"].x1 for n in s.nodes), exact.y1 + 20)
    save(render(s, crop, marks_, "speeds", top=72, sides=("above",), side_margin=False), "speed-buttons", 45)


def remembered_speed():
    s = Screen("06-v-remembered")
    toolbar = union(s.find(desc=S("eventLog"), button=True), s.find(desc=S("buttonAddPassedPosition"), button=True))
    v = s.find(text="36", button=True, y=(toolbar.y0, toolbar.y1))
    field = s.find(text="36" + S("perHourSuffix"), button=True)
    applied = s.find(text="36", button=True, y=(field.y0, field.y1))
    clear = s.find(desc=S("buttonClearSpeedLimit"), button=True)
    marks_ = [mark(1, v, False), mark(2, field | applied), mark(3, clear, False)]
    save(render(s, band(s, toolbar, field), marks_, "remembered"), "remembered-speed", 50)


def regularity_finished():
    s = Screen("06-finished")
    finished = s.find(rx=re.escape(S("finishedTimeDistancePrefix")) + ".*")
    nxt = s.row(s.find(rx=re.escape(S("preLabelNextRace").strip()) + r"\s*"))
    undo = s.find(text=S("raceButtonUndoFinishRace"), button=True)
    marks_ = [mark(1, finished), mark(2, nxt), mark(3, undo, False)]
    save(render(s, band(s, finished, undo), marks_, "finished"), "regularity-finished", 50)


def distance_edit():
    s = Screen("06-distance-edit")
    apply_ = s.find(desc=S("buttonApplyOdo"), button=True)
    row = s.row(apply_)
    icon = s.find(desc=S("raceStatusSectionGoing"))  # the distance is a text field here, so no panel_parts()
    speed = s.find(rx=r"v=.*")
    save(render_frames_only(s, band(s, icon, speed), [row], "distance"), "distance-edit", 50)


def render_frames_only(s, crop, boxes, name):
    """A frame without a badge, where the text names the element directly."""
    dx, dy = -crop.x0, -crop.y0
    args = [s.png, "-crop", f"{crop.x1 - crop.x0}x{crop.y1 - crop.y0}+{crop.x0}+{crop.y0}", "+repage",
            "-fill", "none", "-stroke", TEAL_LINE, "-strokewidth", 4]
    for b in boxes:
        f = b.pad(PAD_X, PAD_Y)
        args += ["-draw", "roundrectangle %d,%d %d,%d 16,16" % (f.x0 + dx, f.y0 + dy, f.x1 + dx, f.y1 + dy)]
    mg(*args, t(name))
    return t(name)


def backwards():
    s = Screen("06-backwards")
    icon, clock, early, dist = panel_parts(s, S("raceStatusSectionGoing"))
    back = s.find(desc=S("switchDirectionHint"), button=True)
    save(render_frames_only(s, band(s, clock, dist), [back], "backwards"), "backwards", 50)


def countdown():
    s = Screen("06-countdown")
    icon, clock, early, dist = panel_parts(s, S("raceStatusSectionGoing"))
    save(render(s, band(s, clock, dist), [mark(1, clock), mark(2, early)], "countdown"), "countdown", 50)


def event_log():
    s = Screen("06-event-log")
    last = s.find(rx=re.escape(S("eventSectionFinished")) + ".*")
    save(render(s, Box(0, 75, 1080, last.y1 + 40), [], "log"), "event-log", 50)


def landscape():
    s = Screen("06-landscape")
    toolbar = union(s.find(desc=S("eventLog"), button=True), s.find(desc=S("buttonAddPassedPosition"), button=True))
    save(render(s, Box(0, 75, 2400, 1080), [mark(1, toolbar)], "landscape"), "landscape", 35)


def error():
    s = Screen("15-error")
    bar = s.find(text=S("failedToCalculateErrors"))
    bar = Box(0, bar.y0, 1080, bar.y1)
    msg = s.find(rx=r"< .*")
    save(render(s, Box(0, 75, 1080, msg.y1 + 50), [mark(1, bar), mark(2, msg, False)], "error"), "error", 50)


def settings_obd():
    s = Screen("10-settings-obd")
    mac = s.find(rx=r"[0-9A-F:]{17}", cls="EditText")
    search = [n["box"] for n in s.nodes if n["cls"] == "Button" and mac.y0 <= n["box"].cy <= mac.y1][0]
    factor = s.find(text="1.018", cls="EditText")
    save_btn = s.find(text=S("saveButton"), button=True)
    title = s.find(text=S("settingsTelemetrySource"))
    hint = s.find(rx=re.escape(S("calibrateFromRouteHint")[:20]) + ".*")
    marks_ = [mark(1, mac), mark(2, search, False), mark(3, factor | save_btn)]
    save(render(s, band(s, title, hint), marks_, "settings"), "settings-obd", 50)


def driver_hud():
    s = Screen("11-driver-hud")
    data = s.find(rx=re.escape(S("telemetryData").strip()) + r"\s*✔")
    save(render(s, Box(0, 75, 1080, 2330), [mark(1, data, False)], "hud"), "driver-hud", 40)


def driver_hud_swapped():
    s = Screen("11-driver-hud-swapped")
    save(render(s, Box(0, 75, 1080, 2330), [], "hud-swapped"), "driver-hud-swapped", 40)


def section_times():
    """The same rows with the race panel hidden and open, as two pictures for a table."""
    for name, out in (("05-section-rows", "section-times-normal"), ("14-section-times", "section-times-section")):
        s = Screen(name)
        first = s.find(text="0.0", cls="EditText")
        last = s.find(text="40.0", cls="EditText", idx=-1)
        save(render(s, Box(0, first.y0 - 8, 1080, last.y1 + 8), [], out), out, 40)


def race_states():
    """Four states of the race panel, as four pictures of the same height for a table; without the simulation slider."""
    crops = []
    for name, out in (("06-race-not-started", "race-state-not-started"), ("06-section-timer", "race-state-section-timer"),
                      ("06-regularity", "race-state-regularity"), ("06-stopped", "race-state-stopped")):
        s = Screen(name)
        toolbar = s.find(desc=S("eventLog"), button=True)
        sim = s.row(s.find(text=S("simulateSpeedPrefix")))
        above = [n["box"] for n in s.nodes if (n["text"] or n["desc"] or n["click"]) and toolbar.y0 <= n["box"].y0
                 and n["box"].y1 <= sim.y0 and n["box"].x1 - n["box"].x0 < 1000]
        crops.append((render(s, band(s, toolbar, union(*above)), [], out), out))
    height = max(int(subprocess.run(["magick", "identify", "-format", "%h", c], capture_output=True, text=True).stdout)
                 for c, _ in crops)
    for c, out in crops:
        mg(c, "-background", "white", "-gravity", "south", "-extent", f"1080x{height}", c)
        save(c, out, 40)


def snap_to_color(png, box, color, x=1000, reach=80):
    """The vertical extent of `color` at column x near `box`: the UI tree can be a few pixels off the screenshot."""
    out = subprocess.run(["magick", png, "-crop", f"1x{box.y1 - box.y0 + 2 * reach}+{x}+{box.y0 - reach}", "+repage",
                          "txt:-"], capture_output=True, text=True).stdout
    ys = [box.y0 - reach + int(m.group(1)) for m in re.finditer(r"^0,(\d+):.*#" + color, out, re.M)]
    return Box(box.x0, min(ys), box.x1, max(ys) + 1) if ys else box


def editor():
    s = Screen("16-editor")
    calc = s.find(text=S("buttonCalculate"), button=True)
    focused = [n["box"] for n in s.nodes if n["focused"] and n["cls"] == "EditText"][0]
    # The row itself: the text fields in a row are taller than the row, so frame the row's own clickable container.
    selected = min((n["box"] for n in s.nodes if n["click"] and n["box"].x1 - n["box"].x0 > 900
                    and n["box"].y0 <= focused.cy <= n["box"].y1), key=Box.area)
    selected = snap_to_color(s.png, selected, "FFFFCC")  # the yellow of the selected row
    keypad = union(s.find(text=S("keyRemove"), button=True), s.find(text="0", button=True))
    keypad = Box(12, keypad.y0 - 20, 1068, 2320)
    marks_ = [mark(1, calc, False), mark(2, selected), mark(3, keypad)]
    save(render(s, Box(0, 75, 1080, 2330), marks_, "editor"), "editor", 40)


def text_badges():
    """Badges for the guide's text, in the same style as on the pictures: images/badges/<n>.png, shown at 20 px."""
    out = os.path.join(HERE, "..", "images", "badges")  # shared by both languages
    os.makedirs(out, exist_ok=True)
    for n in range(1, 12):
        mg(badge(n), "-trim", "+repage", "-resize", "40x40", "-strip", os.path.join(out, f"{n}.png"))


if __name__ == "__main__":
  text_badges()
  for f in (hero, section_list, create_section, section_screen, row_anatomy, section_menu, timers, race_panel, race_panel_regularity,
          speed_buttons, remembered_speed, regularity_finished, distance_edit, backwards, countdown, event_log,
          landscape, error, settings_obd, driver_hud, driver_hud_swapped, section_times, race_states, editor):
      f()
