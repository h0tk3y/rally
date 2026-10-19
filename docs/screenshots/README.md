# Screenshots for the user guide

The pictures in [`../images`](../images) are made in two steps:

1. `capture.py` drives the app on an emulator through one realistic rally section and saves full-screen captures to `raw/<locale>/`: a `.png` and the UI tree as `.xml` for each.
2. `annotate.py` finds the elements in the UI trees by their text or description, crops the captures, adds the numbered badges and frames, and writes `../images/*.png`.

Both steps are scripted, so after a UI change you can run them again and get the same pictures.

## Requirements

- A **dedicated** emulator. `capture.py` changes the device: it switches adb to root, sets the font scale to 0.85, turns automatic time off and moves the clock, turns on the status bar demo mode, and deletes the app's sections and events. Never point it at a device that is used for tests or for anything else.
- The emulator profile that the coordinates in `annotate.py` are made for: AVD `rally_docs`, Android 36 (Google APIs, arm64), 1080×2400 px, 420 dpi, `hw.keyboard = yes`. It is the same profile as `rally_perf`, with its own data.
- Python 3, ImageMagick 7 (`magick`) and the Arial fonts in `/System/Library/Fonts/Supplemental` (macOS).

Start the emulator on port 5560. The scripts use `ANDROID_SERIAL=emulator-5560` unless you set another one:

```bash
~/Library/Android/sdk/emulator/emulator -avd rally_docs -port 5560 -no-boot-anim
```

## Running

```bash
python3 capture.py --build
```

`--build` builds the debug app without the "DEBUG" prefix in its title and installs it (the source file is restored afterwards). Leave it out to reuse the installed app. A run takes about 4 minutes.

```bash
python3 annotate.py
```

Then look through `../images` before you commit. The marks follow the elements, but a renamed button or a new layout can make a query fail (the script stops and names it) or a badge land in an odd spot. `python3 ui.py dump` prints the elements on the current screen with their texts and bounds.

**Russian screenshots.** Set `RALLY_LOCALE=ru` for both scripts:

```bash
RALLY_LOCALE=ru python3 capture.py
```

```bash
RALLY_LOCALE=ru python3 annotate.py
```

The app is switched to Russian with a per-app locale (`cmd locale set-app-locales`), so the emulator's own language does not matter. The scripts find the UI texts by their Android string names in `values/strings.xml` and `values-ru/strings.xml`. The sections come from `seed-ru.sql`. Captures go to `raw/ru`, and pictures to `../images/ru`; the guide is `../user-guide.ru.md`. adb cannot type Cyrillic, so in the Russian Create section picture the name field shows its placeholder.

`raw/` and `tmp/` are not committed.

## The scenario

The data comes from `seed-en.sql` (or `seed-ru.sql`): four sections of "Leg 1", each with only a start and an end, as a co-driver creates them when the road book arrives. The main one is **Leg 1 · TC1 → TC2**: 40.0 km in 50 minutes (48 km/h), start at 10:12:00, with a regularity inside the liaison. The regularity and the passed positions are recorded live in race mode, so no screenshot shows a position that the car has not passed yet.

The car is simulated: the **Simulation** distance source gives the speed, and `goto()` moves the emulator clock and types the distance into the race panel, so the clock, the distance and the early/late value stay realistic.

| Clock | Distance | What happens | Captures |
|---|---|---|---|
| — | — | Settings: OBD over Bluetooth with the reader `00:1D:A5:68:98:8B` and the calibration factor 1.018 | `10-settings-obd` |
| — | — | Settings: Simulation, allowance ⌊t/10⌋, the driver's device `127.0.0.1` (the same device, for the Driver HUD) | `01-section-list` |
| — | — | The Create section window filled in with `Leg 1 - TC3 to TC4`, 28.6 km, `0:38`, start `12:05`, then cancelled | `04-create-section` |
| 10:11:40 | 0.0 | Race mode on in TC1 → TC2 | `06-race-not-started` |
| 10:11:52 | 0.0 | **On 10:12:00** in the Go row: the countdown | `06-countdown` |
| 10:15:52 | 3.15 | Add passed position | |
| 10:21:44 | 7.8 | Add passed position | |
| 10:24:10 | 9.6 | The v button: the speed 36 typed into "=/h →" and applied; then the passed position 7.8… selected | `06-v-remembered`, `06-section-timer` |
| 10:27:22 | 12.4 | **Now from …** in the Start regularity row | |
| 10:30:02 | 14.0 | The regularity runs; then the speed row swiped to the speed buttons | `06-regularity`, `06-speed-buttons` |
| 10:35:32 | 17.3 | Speed button **40** | |
| 10:41:10 | 21.05 | Speed button **30** | |
| 10:48:46 | 24.85 | **Finish regularity** | `06-finished` |
| 10:51:52 | 28.4 | Add passed position | |
| 10:56:32 | 33.75 | Add passed position; 40.0 selected; Driver HUD opened from the section list | `11-driver-hud` |
| 11:01:58 | 40.0 | **Stop**; the event log; 40.0 selected; the menu; Hide race panel | `06-stopped`, `06-event-log`, `14-section-times`, `05-menu-race-on`, `05-section-rows` |
| — | 40.0 | Show race panel, **Undo stop**, ↶ on and off, the distance tapped and cancelled, **Stop** | `06-backwards`, `06-distance-edit` |
| — | — | The device turned sideways | `06-landscape` |
| — | — | Drop race state and stop; the editor with 21.086 selected by the ▲ key | `16-editor` |
| — | — | In TC2 → TC3: ADD after 36.8 gives a position at 0.0, an error | `15-error` |

Not every capture is used; `capture.py` keeps them for later.

## Where each picture is used

Badges are teal circles with a number. The guide refers to them with the same badges as small images, `images/badges/<n>.png`, which `annotate.py` also makes: `<img src="images/badges/1.png" height="20" alt="1">`. The rules, applied by `annotate.py`:

- A frame marks a row, a field or a group of elements; a small button or icon gets only a badge.
- Every frame has the same padding around its element, and elements in one row get frames of the same height.
- A badge sits just left of its frame, vertically centred. If something is there, it goes above, then right, then below. Images with badges get a white margin on both sides, so elements at the screen edge have room.
- In `row-anatomy.png` the badges sit above the rows.

| Image | Made from | Guide section | Highlights |
|---|---|---|---|
| `section-list.png` | `01-section-list` | 4. The section list | ① **+**, ② the menu |
| `create-section.png` | `04-create-section` | 4.1 Create a section | ① the time row, with the bold label of the field typed last, ② the speed row, with the value the app calculated |
| `section-screen.png` | `05-section-rows` | 5. The section screen | ① **Edit**, ② **Race mode** |
| `row-anatomy.png` | `05-section-rows`, rows 17.329 and 24.872 | 5.1 What a row shows | ① distance, ② marks, ③ ➠ speed, ④ times, ⑤ time of day, ⑥ allowance |
| `section-menu.png` | `05-menu-race-on` | 5.2 The section menu | none |
| `timers.png` | `06-section-timer`, `06-regularity` | 6.4 The two timers | none: the two clocks, captioned |
| `race-states.png` | `06-race-not-started`, `06-section-timer`, `06-regularity`, `06-stopped` | 6.5 The race states | none: four captioned panels |
| `race-panel-regularity.png` | `06-regularity` | 6.5, While the regularity timer runs | ① flag and clock, ② average speed, ③ speed row, ④ **Finish regularity**, ⑤ **Finish and start** |
| `regularity-finished.png` | `06-finished` | 6.5, after Finish regularity | ① **Finished:**, ② the Next regularity row, ③ **Undo finish regularity** |
| `race-panel.png` | `06-section-timer` | 6.3 What the race panel shows | ① toolbar, ② clock, ③ allowance, ④ early/late, ⑤ distance and distance to the selected row, ⑥ ↶, -0.1 and +0.1, ⑦ speed, ⑧ the Start regularity row, ⑨ **Stop**, ⑩ the simulation slider |
| `landscape.png` | `06-landscape` | 6.3, the toolbar | ① the toolbar at the bottom |
| `distance-edit.png` | `06-distance-edit` | 6.6 Correcting the distance | a frame around the edit row |
| `backwards.png` | `06-backwards` | 6.6, Going backwards | a frame around the red ↶ |
| `speed-buttons.png` | `06-speed-buttons` | 6.7 Speed buttons | ① percentage, ② speed buttons |
| `remembered-speed.png` | `06-v-remembered` | 6.7, the "v" button | ① the v button with the remembered speed, ② the "=/h →" field and its button, ③ ✕ |
| `event-log.png` | `06-event-log` | 6.9 Event log | none |
| `countdown.png` | `06-countdown` | 7.4 At the start | ① the clock counting down, ② early/late |
| `settings-obd.png` | `10-settings-obd` | 10.1 Telemetry source | ① MAC address field, ② the search button, ③ calibration factor and Save |
| `driver-hud.png` | `11-driver-hud`, the list and the panel | 11. The driver's screen | ① the link state |
| `section-times.png` | `05-section-rows`, `14-section-times` | 14.8 Section times in race mode | none: two captioned lists |
| `error.png` | `15-error` | 17. Errors and warnings | ① the red bar, ② the row's error |
| `editor.png` | `16-editor` | 15.2 How the editor works | ① **Calculate**, ② the selected row, ③ the keypad |
