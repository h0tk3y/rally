# Screenshots for the user guide

The pictures in [`../images`](../images) are made in two steps:

1. `capture.py` drives the app on an emulator through one realistic rally section and saves full-screen captures to `raw/<locale>/`: a `.png` and the UI tree as `.xml` for each.
2. `annotate.py` finds the elements in the UI trees by their text or description, crops the captures, adds the numbered badges and frames, and writes `../images/*.png`.

Both steps are scripted, so after a UI change you can run them again and get the same pictures.

## Requirements

- **Dedicated** emulators. `capture.py` changes the device: it switches adb to root, sets the font scale to 0.85, turns the animations off, turns automatic time off and moves the clock, turns on the status bar demo mode, and deletes the app's sections and events. Never point it at a device that is used for tests or for anything else.
- The emulator profile that the coordinates in `annotate.py` are made for: AVD `rally_docs`, Android 36 (Google APIs, arm64), 1080×2400 px, 420 dpi, `hw.keyboard = yes`. It is the same profile as `rally_perf`, with its own data.
- Python 3, ImageMagick 7 (`magick`) and the Arial fonts in `/System/Library/Fonts/Supplemental` (macOS).

The `hero` series runs on a tablet emulator, `rally_docs_tablet`: the same profile, but 2560×1600 px at 320 dpi, landscape. `capture_all.py` spreads the other series over three phones, `rally_docs`, `rally_docs_2` and `rally_docs_3` (copies of one profile), on ports 5560, 5564 and 5566, and uses the tablet on port 5562. The phones can run without a window:

```bash
~/Library/Android/sdk/emulator/emulator -avd rally_docs -port 5560 -no-boot-anim
```

```bash
~/Library/Android/sdk/emulator/emulator -avd rally_docs_2 -port 5564 -no-boot-anim -no-window
```

```bash
~/Library/Android/sdk/emulator/emulator -avd rally_docs_3 -port 5566 -no-boot-anim -no-window
```

```bash
~/Library/Android/sdk/emulator/emulator -avd rally_docs_tablet -port 5562 -no-boot-anim
```

`--phones` and `--tablets` (comma-separated serials) take other emulators; for `capture.py` alone, `RALLY_PHONE` and `RALLY_TABLET` do.

## Running

```bash
python3 capture_all.py --build
```

It captures every series in both locales, one job per emulator at a time, and then runs `annotate.py` for both locales. With four emulators it takes about two minutes. `--build` builds the debug app without the "DEBUG" prefix in its title and installs it on all of them (the source file is restored afterwards). Leave it out to reuse the installed app. `--series` and `--locales` take comma-separated lists. Each job's output goes to `tmp/logs/<locale>-<series>.log`.

**Screenshot mode.** The debug app has a receiver for the broadcast `com.h0tk3y.rally.debug.SCREENSHOT_MODE` (see `ScreenshotMode.kt`); only adb's shell can send it. `capture.py` turns the mode on after each launch: the race panel hides the simulation slider and shows the Simulation source as `OBD ✔`. The same broadcast sets the simulated speed (`--ei speed 53`) and writes the app's UI tree as `uiautomator dump` XML (`--es dump ui.xml`) in a fraction of a second, where `uiautomator dump` takes about two. Release builds have no such receiver.

The scripts can also run one by one, for one emulator and one locale:

```bash
python3 capture.py --build
```

The captures come in independent series. Each series starts from clean data: `capture.py` writes the sections it needs straight into the app's database, so a series does not have to drive the road before it. To retake one screenshot, run only its series:

```bash
python3 capture.py --shot 06-countdown
```

`--series end,editor` runs the named series, and `--list` prints every series with its screenshots.

```bash
python3 annotate.py
```

Then look through `../images` before you commit. The marks follow the elements, but a renamed button or a new layout can make a query fail (the script stops and names it) or a badge land in an odd spot. `python3 ui.py dump` prints the elements on the current screen with their texts and bounds.

**Russian screenshots.** `capture_all.py` takes both locales. To run the scripts alone, set `RALLY_LOCALE=ru` for both:

```bash
RALLY_LOCALE=ru python3 capture.py
```

```bash
RALLY_LOCALE=ru python3 annotate.py
```

Setup also exempts the app from battery optimization, so the one-time battery hint does not appear. The app is switched to Russian with a per-app locale (`cmd locale set-app-locales`), so the emulator's own language does not matter. The scripts find the UI texts by their Android string names in `values/strings.xml` and `values-ru/strings.xml`. Captures go to `raw/ru`, and pictures to `../images/ru`; the guide is `../user-guide.ru.md`. adb cannot type Cyrillic, so in the Russian Create section picture the name field shows its placeholder.

The app icon in the guide's title, `../images/icon.png`, is rendered from the adaptive icon's vector drawables by `python3 icon.py` (it needs `rsvg-convert`).

`raw/` and `tmp/` are not committed.

## The scenario

All series use four sections of "Leg 1", as a co-driver creates them when the road book arrives. The main one is **Leg 1 · TC1 → TC2**: 40.0 km in 50 minutes (48 km/h), start at 10:12:00, with a regularity inside. Its roadmap differs by series (`TC1_NEW`, `TC1_PASSED`, `TC1_HERO`, `TC1_DRIVEN` in `capture.py`): each series starts with what race mode would have recorded up to that point, so no screenshot shows a position that the car has not passed yet.

The car is simulated: the **Simulation** distance source gives the speed (set by the screenshot-mode broadcast), and `goto()` moves the emulator clock and types the distance into the race panel, so the clock, the distance and the early/late value stay realistic. Race mode is started at 10:12:00 even when the clock is already later, so the section timer shows the time since the real start.

| Series | Starts with | What happens | Captures |
|---|---|---|---|
| `hero` (tablet) | `TC1_HERO` (eight road-book points before the regularity) | The speed fields at 13.0 km, before the row is scrolled; a regularity from 12.4 with passed points at 14.6, 16.1, 18.3, 19.2, 20.1 and 22.6, speed buttons **40** at 17.3 and **30** at 21.05, then 80% of a **50** sign at 23.4; at 23.9 km, the speed row scrolled to its end and 19.2 selected | `06-speed-buttons`, `00-hero` |
| `settings` | — | OBD over Bluetooth with the reader `00:1D:A5:68:98:8B` and the calibration factor 1.018; then Simulation, allowance ⌊t/10⌋ and the driver's device `127.0.0.1` | `10-settings-obd` |
| `list` | new sections | The section list; the Create section window filled in and cancelled | `01-section-list`, `04-create-section` |
| `start` | `TC1_NEW`, 10:11:40 | Race mode on; **On 10:12:00** in the Go row: the countdown | `06-race-not-started`, `06-countdown` |
| `liaison` | `TC1_PASSED` (3.15 and 7.8 passed) | At 9.6 km, 10:24:10: the speed 36 remembered with the v button; the passed position 7.8 selected | `06-v-remembered`, `06-section-timer` |
| `regularity` | `TC1_PASSED` | **Now from …** at 12.4 (10:27:22); speed buttons **40** at 17.3 and **30** at 21.05; **Finish regularity** at 24.85; **Stop** at 40.0; the event log | `06-regularity`, `06-finished`, `06-event-log` |
| `end` | `TC1_DRIVEN` (the whole section recorded) | At 33.75 km, 10:56:32, 40.0 selected: the Driver HUD, with **Swap views** on and off again; **Stop** at 40.0; the menu; Hide race panel; **Undo stop**, ↶ on and off, the distance tapped and cancelled; **Stop**; sideways again | `11-driver-hud`, `11-driver-hud-swapped`, `06-stopped`, `14-section-times`, `05-menu-race-on`, `05-section-rows`, `06-backwards`, `06-distance-edit`, `06-landscape` |
| `editor` | `TC1_DRIVEN` | The editor with 21.05 selected by the ▼ key; TC2 → TC3 seeded with positions after its only stretch ends (`TC2_BROKEN`): an error | `16-editor`, `15-error` |

Not every capture is used; `capture.py` keeps them for later.

## Where each picture is used

Pictures that belong together are separate files, shown side by side in a Markdown table with their captions as text. Badges are teal circles with a number. The guide refers to them with the same badges as small images, `images/badges/<n>.png`, which `annotate.py` also makes: `<img src="images/badges/1.png" height="20" alt="1">`. The rules, applied by `annotate.py`:

- A frame marks a row, a field or a group of elements; a small button or icon gets only a badge.
- Every frame has the same padding around its element, and elements in one row get frames of the same height.
- A badge sits just left of its frame, vertically centred. If something is there, it goes above, then right, then below. Images with badges get a white margin on both sides, so elements at the screen edge have room.
- In `row-anatomy.png` and `speed-buttons.png` the badges sit above the elements.

| Image | Made from | Guide section | Highlights |
|---|---|---|---|
| `hero.png` | `00-hero` (tablet) | the top of the guide | none |
| `section-list.png` | `01-section-list` | 4. The section list | ① **+**, ② the menu |
| `create-section.png` | `04-create-section` | 4.1 Create a section | ① the time row, with the bold label of the field typed last, ② the speed row, with the value the app calculated |
| `section-screen.png` | `05-section-rows` | 5. The section screen | ① **Edit**, ② **Race mode** |
| `row-anatomy.png` | `05-section-rows`, rows 17.3 and 24.85 | 5.1 What a row shows | badges only, no frames: ① distance, ② marks, ③ ➠ speed, ④ times, ⑤ time of day, ⑥ allowance |
| `section-menu.png` | `05-menu-race-on` | 5.2 The section menu | none |
| `timer-section.png`, `timer-regularity.png` | `06-section-timer`, `06-regularity` | 6.5 The two timers, in a table | none |
| `race-state-not-started.png`, `race-state-section-timer.png`, `race-state-regularity.png`, `race-state-stopped.png` | `06-race-not-started`, `06-section-timer`, `06-regularity`, `06-stopped` | 6.6 The race states, in a table | none; cropped above the simulation slider, padded to the same height |
| `race-panel-regularity.png` | `06-regularity` | 6.6, While the regularity timer runs | ① flag and clock, ② average speed, ③ speed row, ④ **Finish regularity**, ⑤ **Finish and start** |
| `regularity-finished.png` | `06-finished` | 6.6, after Finish regularity | ① **Finished:**, ② the Next regularity row, ③ **Undo finish** |
| `race-panel.png` | `06-section-timer` | 6.3 What the race panel shows | ① toolbar, ② clock, ③ allowance, ④ early/late, ⑤ distance and distance to the selected row, ⑥ ↶, -0.1 and +0.1, ⑦ speed, ⑧ the Regularity row, ⑨ **Stop** |
| `landscape.png` | `06-landscape` | 6.3, the toolbar | ① the toolbar at the bottom |
| `distance-edit.png` | `06-distance-edit` | 6.7 Correcting the distance | a frame around the edit row |
| `backwards.png` | `06-backwards` | 6.7, Going backwards | a frame around the red ↶ |
| `speed-buttons.png` | `06-speed-buttons` (tablet) | 6.8 Speed buttons | badges above, left to right: ① "=/h →" and its button, ② percentage, ③ "/h →" and its button, ④ speed buttons |
| `remembered-speed.png` | `06-v-remembered` | 6.8, the "v" button | ① the v button with the remembered speed, ② the "=/h →" field and its button, ③ ✕ |
| `event-log.png` | `06-event-log` | 6.10 Event log | none |
| `countdown.png` | `06-countdown` | 7.4 At the start | ① the clock counting down, ② early/late |
| `settings-obd.png` | `10-settings-obd` | 10.1 Telemetry source | ① MAC address field, ② the search button, ③ calibration factor and Save |
| `driver-hud.png` | `11-driver-hud` | 11. The driver's screen | ① the link state |
| `driver-hud-swapped.png` | `11-driver-hud-swapped` | 11, Swap views | none |
| `section-times-normal.png`, `section-times-section.png` | `05-section-rows`, `14-section-times` | 14.8 Section times in race mode, in a table | none |
| `error.png` | `15-error` | 17. Errors and warnings | ① the red bar, ② the row's error |
| `editor.png` | `16-editor` | 15.2 How the editor works | ① **Calculate**, ② the selected row, ③ the keypad |
