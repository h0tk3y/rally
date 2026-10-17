# h0tk3y's Rally Tool: user guide

This guide is for the co-driver in a TSD (time–speed–distance) rally. It explains every part of the app: how you enter a roadmap, how the app calculates the times, and how you use it in the car.

You do not need any computer knowledge. You only need to know how a TSD rally works: road books, liaisons, timed stretches, set average speeds and time controls.

## Contents

1. [What the app does](#1-what-the-app-does)
2. [Words used in this guide](#2-words-used-in-this-guide)
3. [Quick start](#3-quick-start)
4. [The section list](#4-the-section-list)
5. [The section screen](#5-the-section-screen)
6. [The roadmap: positions and marks](#6-the-roadmap-positions-and-marks)
7. [How the app calculates the times](#7-how-the-app-calculates-the-times)
8. [Errors and warnings](#8-errors-and-warnings)
9. [Editing positions](#9-editing-positions)
10. [Race mode](#10-race-mode)
11. [A rally day with the app](#11-a-rally-day-with-the-app)
12. [Measuring the distance](#12-measuring-the-distance)
13. [Settings](#13-settings)
14. [The driver's screen (Driver HUD)](#14-the-drivers-screen-driver-hud)
15. [Phones, tablets and turning the phone](#15-phones-tablets-and-turning-the-phone)
16. [Saving, copying and sharing](#16-saving-copying-and-sharing)
17. [Roadmap text format (reference)](#17-roadmap-text-format-reference)
18. [Questions and problems](#18-questions-and-problems)

---

## 1. What the app does

The app does three jobs.

1. **It holds your roadmap.** You type in the points from the road book: the distance of each point, where a set average speed starts, changes and ends, and the time of day at a point.
2. **It calculates the times.** For each point it shows how long after the start you must pass it, and at what time of day. When the road book gives a special speed for a short stretch inside a longer one, the app also tells you what speed to drive on the rest.
3. **It helps you in the car.** In race mode it measures the distance you drive (from GPS, from the car through a small reader plugged into the car, or from a driving game for training). It shows the clock, your distance, and how many seconds you are early or late. It can also send all of this to a second phone for the driver.

---

## 2. Words used in this guide

| Word | Meaning in this app |
|---|---|
| **Section** | One list of points that you time together, for example a stretch from one time control to the next. The app keeps many sections. |
| **Position** | One line of a section: a point on the road, with its distance in km. A position can carry marks. |
| **Roadmap** | The app's copy of your road book: all the positions of a section, in order of distance. |
| **Mark** | Extra information on a position. The app writes the marks in short form: `setavg` (set average speed starts), `thenavg` (set average speed changes), `endavg` (set average speed ends), `atime` (time of day) and `odo` (odometer reading). |
| **Stretch** | Part of the road from the point where a set average speed starts to the point where it ends. |
| **Inner stretch** | A stretch inside a longer stretch, with its own speed. The app's messages call these "subs". |
| **Time of day** | The real time on the rally clock at a position, for example 10:00:00. The app calls it `atime` ("astronomical time"). |
| **View mode** | The normal mode. The app shows the times it calculated. You cannot change the positions. |
| **Edit mode** | The mode where you change the positions with the app's own keypad. |
| **Race mode** | The mode you use in the car. A panel (the **race panel**) appears below the list with the clock, the distance and how early or late you are. |
| **Section timer** | In race mode, a clock that times the whole section at its overall average speed. You start it from the row of buttons called **Go**. |
| **Race timer** | In race mode, a clock that times a regularity, where each set average speed counts. You start it from the row of buttons called **Race**. |
| **Distance source** | Where the app gets the distance and speed: GPS, a reader in the car, a driving game, or a test slider. |

---

## 3. Quick start

1. On the first screen, tap **+** (Create section).
2. Type a name, the length of the section, and the average speed or the time. Type the start time too, if you know it. Tap **Save**. The app opens the new section.
3. Tap **Edit**. Add your positions with the keypad at the bottom (see [Editing positions](#9-editing-positions)). Give the last position an `endavg` with the **END** key (see [What race mode changes in your roadmap](#106-what-race-mode-changes-in-your-roadmap) for why). Tap **Calculate** when you are done.
4. Check the times on the right of each position.
5. Make a copy of the section with **Duplicate this section** in the menu, and drive with the copy. Race mode writes into the roadmap (see [What race mode changes in your roadmap](#106-what-race-mode-changes-in-your-roadmap)), so keep the first section clean.
6. Before the start, tap **Race mode**. Allow everything the phone asks.
7. Tap the start position in the list. In the race panel, choose a row:
   - the **Race** row for a regularity, where each set average speed counts;
   - the **Go** row to time the whole section at one overall average speed, for example a liaison.
8. In that row, tap **On 10:00:00** to start at the time of day of the position (this button is there only if the position has a time of day), or **Now** to start this second.
9. Drive. Watch the clock, the distance and the early/late value. Correct the distance at known points with **-0.1** and **+0.1**, or with the phone's volume buttons (they work only while a timer runs).
10. At the end of a regularity, tap **Finish race**. The section timer keeps running (the app starts it with the race timer). At the end of the section, tap **Stop**.

---

## 4. The section list

This is the first screen. It shows the names of all your sections. Tap a name to open the section.

In the top bar:

- **+** creates a new section.
- The menu (three dots) holds:
  - **Driver HUD**: turns this phone into the driver's screen. See [The driver's screen](#14-the-drivers-screen-driver-hud).
  - **Import section**: creates a section from text.
  - **Settings**.

You cannot delete a section from this list. Open it and use its menu (see [The section menu](#53-the-section-menu)).

### 4.1 Create a section

The **Create section** window has these fields:

| Field | What to type |
|---|---|
| **New section name** | Any name. Two sections cannot have the same name. |
| **Distance** | The length of the section in km, for example `60.0`. |
| **Time HH:MM(:SS)** | How long the section should take. |
| **Average speed** | The set average speed in km/h. |
| **Start at HH:MM(:SS)** | The time of day of the start. You may leave it empty. |

You type either the time or the speed, and the app calculates the other from the distance. The label of the field you typed in last turns bold: the app uses that value and calculates the other one. For example, type the distance `30`, then the speed `60`: the time becomes 30 minutes. If you then type the time `0.40`, the time label turns bold and the speed becomes 45.

An empty field uses the grey example value shown in it: 60.0 km, 01:00 (one hour) and 60 km/h. So if you leave the distance empty, the section is 60 km long.

**How to type a time.** These fields have no colon key. Put a dot between the hours, the minutes and the seconds:

- `1.30` means 1 hour 30 minutes.
- `0.45` means 45 minutes.
- `10.05.30` means 10:05:30.

Minutes are whole numbers, so `1.5` means 1 hour 5 minutes, not 1 hour 30 minutes, and `0.4` means 4 minutes, not 40. A number with no dot, such as `45`, is wrong. The field gets a red line under it, but **Save** still works, and the app then uses one hour. So check for red lines before you tap **Save**.

Tap **Save**. The new section has two positions:

- the start, at 0.0 km, with the average speed (and the start time, if you typed one);
- the end, at the distance you typed.

The app then opens the section. The end position has no `endavg`. Before you use race mode, give it one (see [What race mode changes in your roadmap](#106-what-race-mode-changes-in-your-roadmap)).

### 4.2 Import a section

**Import section** creates a section from text, for example text you copied with **Copy as text** on another phone.

Type a name, and paste the text into **Section content**. Each position is one line. See [Roadmap text format](#17-roadmap-text-format-reference).

If the text has a mistake, the field turns red, says **Invalid input**, and you cannot save. Common mistakes:

- an empty line;
- two spaces between words;
- a word the app does not know.

---

## 5. The section screen

The section screen shows the roadmap as a list, one row per position. At the top are the section name, the **Edit** (or **Calculate**) button and the menu.

### 5.1 What a row shows

From left to right:

1. **The distance** in km. If the section has an `odo` mark, the odometer reading appears under it in grey (see [Odometer readings](#77-odometer-readings-odo)).
2. **The marks:**
   - `setavg` and a speed: a set average speed starts here.
   - `thenavg` and a speed: the set average speed changes here.
   - After either of these, a percentage and **%**, if the speed is a percentage of a base speed.
   - `endavg`: a set average speed ends here. After it, in grey, the app shows the speed that ends.
   - `atime` and a time of day.

   The small number after `setavg`, `thenavg` or `endavg` (for example `setavg1` and `endavg1`) shows which marks belong together: a `setavg` and the `endavg` that ends it have the same number.
3. **➠ and a speed** in grey, for example `➠75.0`. This is the speed to drive on the rest of a stretch, outside its inner stretches. See [The ➠ speed](#75-the--speed).
4. **Warnings** in red, for example `🚫late by 10:00`.
5. **On the right, the times:**
   - one or more times in **minutes and seconds** from a start, for example `18:00` or `10:00 18:00`. These are not times of day: `18:00` means 18 minutes, and `90:00` means 1 hour 30 minutes;
   - then the **time of day**, with hours, minutes and seconds, for example `10:18:00`;
   - then an allowance such as `-1`, if you turned it on in Settings (see [Allowance](#711-allowance)).

   See [Several times on one row](#73-several-times-on-one-row) for what each time means.

Some rows are lighter than others. These are helper points that the app adds by itself (see [Helper points](#78-helper-points-s)).

### 5.2 View mode and edit mode

The button at the top switches between the two modes.

**View mode** (the button says **Edit**):

- shows the times, the ➠ speeds, the warnings and all errors;
- if the roadmap has errors, shows a red bar at the top, **Failed to calculate because of errors**, and no times at all;
- shows the helper points.

**Edit mode** (the button says **Calculate**):

- shows the keypad at the bottom;
- hides the times and most errors. It still shows one error: a distance that is smaller than one before it.

**Selecting a row.** Tap a row to select it. It turns pale yellow. In edit mode, the keypad works on the selected row. In race mode, you start from the selected row, and the panel shows how far you are from it.

### 5.3 The section menu

The menu (three dots) holds:

| Item | What it does |
|---|---|
| **Race mode** | You see this when the race panel shows **Not connected to race service**. It turns race mode on again. |
| **Show race panel** / **Hide race panel** | You see one of these when race mode is on. **Hide race panel** hides the panel, but the clock keeps running. **Show race panel** brings it back. |
| **Drop race state and stop** | You see this when race mode is on and no timer runs (not started, or stopped). It ends race mode and clears the timers. |
| **Delete this section** | The first tap changes it to **Tap again to delete**. The second tap deletes the section. You cannot undo this. |
| **Duplicate this section** | Makes a copy under a new name and opens it. |
| **Rename this section** | Changes the name. |
| **Copy as text** | Copies the roadmap as text. You can paste it into a message, or into **Import section** on another phone. |
| **Settings** | Opens Settings. Open Settings from here after you drive an odometer check route, so you can calibrate (see [Calibration](#132-calibration)). |

---

## 6. The roadmap: positions and marks

A position is a distance with some marks. This chapter explains each mark. The next chapter shows how the app uses them to calculate the times.

### 6.1 Distance

The distance from the start of the section, in km, with up to three decimals: `12.345`.

The app keeps the positions in order of distance: if you change a distance, the row moves to its place. Two positions may have the same distance.

### 6.2 `setavg`: a set average speed starts

`setavg 60` means "from here, the set average speed is 60 km/h".

If a `setavg` comes while another stretch is still open, it starts an inner stretch. See [Inner stretches](#72-inner-stretches).

### 6.3 `thenavg`: the set average speed changes

`thenavg 40` means "from here, the set average speed is 40 km/h". The clock of the stretch keeps running; only the speed changes.

### 6.4 `endavg`: a set average speed ends

`endavg` ends the stretch that started most recently and is still open. After `endavg`, the app shows in grey the speed that ends here.

You may write a speed after `endavg` (for example `endavg 60`). The app keeps it, but does not use it.

For the times, the last stretch of a section does not need an `endavg`: it runs to the last position. But race mode needs it, so always end the section with `endavg` on its last position (see [What race mode changes in your roadmap](#106-what-race-mode-changes-in-your-roadmap)).

### 6.5 Speed as a percentage

Some rallies give speeds as a percentage of a base speed. The app shows such a speed as the base speed and the percentage: `60.0 110 %`. The real speed is the base speed times the percentage: 60 × 110% = 66 km/h.

### 6.6 `atime`: time of day

`atime 10:00:00` means "at this position, the rally clock shows 10:00:00". The app then calculates the time of day at the other positions.

A section can have **only one** time of day. When you give a time of day to one position, the app removes it from all the others. So if your road book gives a time of day at several time controls, make a separate section for each part that starts at a given time.

### 6.7 `odo`: odometer reading

`odo 5.0` means "at this position, the car's odometer shows 5.0 km". The app then shows in grey what the odometer should show at the next positions. This does not change any times. The keypad has no key for `odo`: you can only add it in the text (see [Roadmap text format](#17-roadmap-text-format-reference)).

### 6.8 `s`: helper points

`s 0.5 3` on a position means "add 3 helper points, every 0.5 km after this position". You can only add it in the text. See [Helper points](#78-helper-points-s).

---

## 7. How the app calculates the times

### 7.1 One average speed

The simplest roadmap has one `setavg`:

```
0.0   setavg 60
30.0
90.0
```

At 60 km/h, 1 km takes 1 minute. So the times are:

| Position | Time |
|---|---|
| 0.0 | 00:00 |
| 30.0 | 30:00 |
| 90.0 | 90:00 |

Times are minutes and seconds from the start. Minutes can go above 59: `90:00` means 1 hour 30 minutes.

**Changing the speed with `thenavg`.** The clock keeps running when the speed changes:

```
0.0   setavg 60
10.0  thenavg 30
20.0  endavg
```

| Position | Time | Why |
|---|---|---|
| 0.0 | 00:00 | |
| 10.0 | 10:00 | 10 km at 60 km/h |
| 20.0 | 30:00 | then 10 km at 30 km/h takes 20 more minutes |

### 7.2 Inner stretches

There are two ways to enter a lower speed for part of the road. Which one is right depends on the road book:

- **The speed simply changes.** For example: "60 km/h; from km 10, 30 km/h through the village; from km 15, 60 km/h again." Each part takes the time of its own speed, and the total time grows. Use `thenavg`:

  ```
  0.0   setavg 60
  10.0  thenavg 30
  15.0  thenavg 60
  30.0
  ```

  This takes 10 + 10 + 15 = 35 minutes.

- **A special speed inside a fixed total time.** For example: "km 0 to 30 at 60 km/h (30 minutes); inside it, km 10 to 15 at 30 km/h." The 30 minutes for the whole stretch do not change, so you must drive faster on the rest. This is an **inner stretch**. Put a `setavg` and an `endavg` inside the longer stretch:

```
0.0   setavg 60
10.0  setavg 30
15.0  endavg
30.0
```

This means:

- The **longer stretch** is 0.0 to 30.0 at 60 km/h. The whole 30 km must take **30 minutes**.
- The **inner stretch** is 10.0 to 15.0 at 30 km/h. These 5 km must take **10 minutes**.
- That leaves **20 minutes** for the other **25 km**. So you must drive those 25 km at 25 ÷ 20 × 60 = **75 km/h**.

The app shows:

| Position | Times | ➠ |
|---|---|---|
| 0.0 `setavg 60` | 00:00 | ➠75.0 |
| 10.0 `setavg 30` | 08:00 | |
| 15.0 `endavg` | 10:00 18:00 | ➠75.0 |
| 30.0 | 30:00 | |

How to read this:

- At 0.0 the app tells you to drive at 75 km/h (➠75.0) until the inner stretch starts.
- You reach 10.0 at 08:00 (10 km at 75 km/h).
- The inner stretch takes 10:00, so you reach 15.0 at 18:00 from the start.
- At 15.0 the app again tells you to drive at 75 km/h until the end.
- You reach 30.0 at 30:00, which is the time of the whole stretch at 60 km/h.

You can put an inner stretch inside another inner stretch. The app calculates each level in the same way.

**The pattern `setavg` – `thenavg` – `endavg`.** You can change the speed inside an inner stretch with `thenavg`. The clock of the inner stretch keeps running:

```
0.0   setavg 60
10.0  setavg 30
15.0  thenavg 90
24.0  endavg
30.0
```

- The inner stretch is 10.0 to 24.0: 5 km at 30 km/h (10 minutes), then 9 km at 90 km/h (6 minutes). That is 16 minutes.
- The longer stretch still takes 30 minutes in total. That leaves 14 minutes for the other 16 km (0.0 to 10.0 and 24.0 to 30.0), so the ➠ speed is 16 ÷ 14 × 60 = 68.6 km/h.

The rows show:

| Position | Times | ➠ |
|---|---|---|
| 0.0 `setavg 60` | 00:00 | ➠68.6 |
| 10.0 `setavg 30` | 08:45 | |
| 15.0 `thenavg 90` | 10:00 18:45 | |
| 24.0 `endavg` | 16:00 24:45 | ➠68.6 |
| 30.0 | 30:00 | |

### 7.3 Several times on one row

A position inside an inner stretch shows one time for each stretch it lies in, from left to right:

1. the time from the start of the smallest stretch around it;
2. the time from the start of the next, larger stretch;
3. … and so on;
4. last, the time from the start of the section.

So in `10:00 18:00`, `10:00` is the time from the start of the inner stretch, and `18:00` is the time from the start of the section.

The position where an inner stretch starts (with `setavg`) shows only the times of the larger stretches around it, not its own 00:00. (Stretches that follow one another, as in the next part, are different: each one starts at 00:00.)

### 7.4 One stretch after another

You can also end a stretch and start a new one at the same distance, each with its own time. Write two positions with the same distance, `endavg` first:

```
0.0   setavg 60
5.0
10.0  endavg
10.0  setavg 30
15.0
20.0  endavg
```

The app treats the whole section as one stretch that holds the two stretches. The rows show:

| Position | Times |
|---|---|
| 0.0 `setavg 60` | 00:00 |
| 5.0 | 05:00 |
| 10.0 `endavg` | 10:00 |
| 10.0 `setavg 30` | 00:00 10:00 |
| 15.0 | 10:00 20:00 |
| 20.0 `endavg` | 20:00 30:00 |

In the second stretch, each row shows two times: the time in that stretch, and the time from the start of the section. Its `setavg` row shows 00:00, because a new stretch starts there. In the first stretch, the two times are the same, so the app shows only one.

The new `setavg` must be at exactly the same distance as the `endavg` before it. If there is a gap, the app shows the error `outer interval not covered by subs` (see [Errors and warnings](#8-errors-and-warnings)).

### 7.5 The ➠ speed

The grey **➠** value is the speed to drive on the parts of a stretch that are outside its inner stretches (see the example in [Inner stretches](#72-inner-stretches)). The app shows it:

- at the start of a stretch that has inner stretches, and
- at the end of each inner stretch: "from here, drive at this speed".

It does not show it at the last position of the section, or at a `thenavg` inside an inner stretch: there you stay inside the inner stretch, so the rest of the road does not start yet.

If the inner stretches need more time than the whole longer stretch allows, the app cannot give a useful ➠ speed. It shows the warning `🚫late by` instead (see [Errors and warnings](#8-errors-and-warnings)).

### 7.6 Time of day (`atime`)

When one position has a time of day, the app calculates the time of day at each position **after** it: that time of day plus the time it takes to drive there. Positions before it show no time of day in view mode.

For example, with `setavg 60` at 0.0 and `atime 10:00:00` at 10.0, the position at 20.0 shows `10:10:00`.

When the race panel is open and no race timer runs, the app also shows the time of day at the positions **before** the time of day, calculated from the overall average speed (see [Section times in race mode](#79-section-times-in-race-mode)).

After midnight the time of day starts again from 00:00:00. The app does not show the day.

### 7.7 Odometer readings (`odo`)

From a position with `odo`, each later position shows in grey what the odometer should read there:

> odometer reading = the `odo` value + (distance of this position − distance of the `odo` position) × calibration factor

The app takes the calibration factor from Settings (see [Calibration](#132-calibration)). It uses it here whatever distance source you use, because it describes how your car's odometer differs from the road book. If you use GPS and do not know this for your car, leave the factor at 1. A later `odo` mark starts the count again. Positions before the first `odo` show no odometer reading.

### 7.8 Helper points (`s`)

`s 0.5 3` on the position at 0.0 adds lighter rows at 0.5, 1.0 and 1.5 km. The app calculates times for them like for any position. It skips a helper point if a real position already has that distance, or if it lies at or past the end of the section.

Helper points appear only in view mode, and only when the roadmap has no errors. The app does not save them as positions.

### 7.9 Section times in race mode

When the race panel is open and no race timer runs, the list shows **section times** instead of the normal times. The app calculates section times as if you drove the whole section at its overall average speed, with no inner stretches:

> overall average speed = section length ÷ total time of the section

These are the times that the section timer uses. When a race timer starts, the list goes back to the normal times.

> **Remember:** the screen does not mark which kind of times you see. Whenever the race panel is open and no race timer runs (before the start, while only the section timer runs, after **Stop**), every time on the rows is a section time.

### 7.10 Rounding

- The app calculates times exactly, and rounds them to whole seconds only when it shows them. For example, 1.234 km at 47 km/h takes 94.52 seconds, and the app shows `01:35`.
- Distances have three decimals.
- The ➠ speed has one decimal.

### 7.11 Allowance

Some rallies give an allowance in minutes that grows with the time of the section. Turn it on in Settings, under **Allowance**:

| Setting | Rule | Examples |
|---|---|---|
| **None** | No allowance. | |
| **⌊t/10⌋** | The time in minutes divided by 10, **rounded down**: one minute for each full 10 minutes. | 9 min → 0; 10 min → 1; 11 min → 1 |
| **⌈t/10⌉** | The time in minutes divided by 10, **rounded up**: one minute for each 10 minutes or part of 10 minutes. | 9 min → 1; 10 min → 1; 11 min → 2 |

The app uses the time from the start of the section (the last time on the row). It shows the allowance as `-N` at the end of the row: `-2` means an allowance of 2 minutes at that position. It does not show it while a race timer runs.

While the section timer (Go) runs, the race panel shows the allowance as `(A:N)` next to the early/late value. That value turns red when you are **early** by more than N minutes. See [What the race panel shows](#105-what-the-race-panel-shows).

---

## 8. Errors and warnings

When the roadmap has an error, the app cannot calculate the times. In view mode it shows **Failed to calculate because of errors** at the top, and a red message on each row with an error.

| Message | What is wrong | How to fix it |
|---|---|---|
| `< 5.0` | This distance is smaller than an earlier one (5.0 here). | Correct the distance. This is the only error the app shows in edit mode too. |
| `outside intervals` | This position is not inside any stretch: it comes before the first `setavg`, or after an `endavg` that ended the last open stretch. The app shows this on the position itself. (A `setavg` at the same distance as that `endavg` starts a new stretch, as in [One stretch after another](#74-one-stretch-after-another); that is not an error.) | Add a `setavg` before the position, or remove the `endavg` that ends the stretch too early. |
| `no setavg for this position` | This `endavg` has no open stretch to end. | Remove the `endavg`, or add the missing `setavg`. |
| `outer interval not covered by subs` | After a stretch ends, the next stretch starts at a different distance, so part of the section is not inside any stretch. The app shows this on the `endavg` before the gap. ("Subs" means the stretches inside the section.) | Put the next `setavg` at the same distance as the `endavg` before it, or remove the extra `endavg`. |

There is also one warning. It does not stop the calculation:

| Message | What it means |
|---|---|
| `🚫late by 10:00` | The inner stretches need more time than the whole longer stretch allows. Here they need 10 minutes more. Even with no time at all on the rest of the road, you cannot make it. The times in this stretch are not useful. Check the speeds and distances. |

A speed of 0 gives the time `∞`. This means the app cannot calculate a time.

---

## 9. Editing positions

Tap **Edit**. A keypad appears at the bottom of the screen. The phone's own keyboard does not appear. The app saves every change immediately; there is no Save button.

Tap a row to select it. Tap a value in the row (the distance, the speed, the percentage or the time of day) to start typing in it. A blinking line, the **cursor**, shows where your next digit goes.

You can switch to edit mode while a timer runs in race mode. The keypad then takes the place of the race panel, and the timer keeps running. Tap **Calculate**, then **Show race panel** in the menu, to see the panel again.

### 9.1 The keys

| Key | What it does |
|---|---|
| **DEL ╳** | Deletes the selected position. The next position becomes selected. |
| **ADD** | Adds a new position at 0.0 km below the selected one. Type its distance; the row then moves to its place. Until you do, the app shows the error `<`, because 0.0 is smaller than the distance above it. |
| **ATIME** | Gives the position a time of day (00:00:00), ready for you to type. Press it again while you type in the time to remove it. A section can have only one time of day, so this removes it from the other positions. |
| **SET** | Makes the position a `setavg`, ready for you to type the speed. Press it again while you type in the speed or the percentage to remove it. |
| **THEN** | The same as **SET**, but for `thenavg`. |
| **END** | Makes the position an `endavg`, or removes the `endavg` if it has one. |
| **%** | Adds a percentage (100%) to the speed, ready for you to type. Press it again while you type in the percentage to remove it. Works only on a position with **SET** or **THEN**. |
| **_%** | Shows the last percentage you typed, for example **80%**. Press it to give the selected position that percentage. |
| **▲ ▼** | Select the position above or below. |
| **◀ ▶** | Move the cursor left or right. At the start or end of a value, they jump to the previous or next value in the row. |
| **0–9** | Type a digit at the cursor. |
| **.** | Puts the decimal point at the cursor. Works in a distance, a speed or a percentage, not in a time of day. |
| **⌫** | Deletes the digit to the left of the cursor. |

A position can have only one of `setavg`, `thenavg` and `endavg`. If you press **SET** on a `thenavg` position, it becomes a `setavg` with the same speed.

To end one stretch and start the next at the same point, use two positions with the same distance: one with **END**, then one with **SET** (see [One stretch after another](#74-one-stretch-after-another)).

When you cannot use a key, the app shows it pale, and it does nothing. For example, **▲** is pale on the first position.

### 9.2 Typing numbers

- Distances, speeds and percentages have at most three decimals. The app ignores extra digits.
- A number cannot start with 0 before other digits (no `05.0`). On `0.0`, a new digit replaces the 0: `0.0` and then `5` gives `5.0`.
- An empty speed counts as 0, and an empty percentage counts as 100%.

Example: to change `0.0` to `12.5`, tap the distance. The cursor stands before the dot (`0|.0`). Type `1` and `2`: you get `12.0`. Press **▶** to move the cursor past the dot (`12.|0`), and type `5`: you get `12.5`.

### 9.3 Typing a time of day

In a time of day, each digit replaces the digit under the cursor, and the cursor jumps over the colons. The app only lets you type a time that can exist: hours from 00 to 23, and minutes and seconds from 00 to 59. It shows pale the digits that would give a wrong time. **⌫** sets the digit to the left back to 0.

### 9.4 When a row moves

The list always stays in order of distance. When you change a distance, the row moves to its new place while you type, and stays selected. For example, if you change 3.0 to 39.0 by typing 9, the row jumps below 5.0. If you then press **⌫**, it moves back.

### 9.5 Deleting all positions

If you delete every position, the app shows a starting roadmap of `0.0 setavg 60` and `1.0` the next time you open the section.

---

## 10. Race mode

Race mode is what you use in the car. It measures the distance you drive and shows how early or late you are.

### 10.1 Turn race mode on

Make sure you are in view mode (the top button says **Edit**). Then:

- on a phone, tap the round **Race mode** button at the bottom right;
- on a tablet, tap **Race mode** in the top bar.

The first time, the phone asks you to allow the app to use your location, to find nearby devices (Bluetooth) and, on Android 14 and later, to show notifications. Allow all of them. If you refuse one, the app says **Please grant the required permissions** and does not start race mode.

The race panel then appears below the list (or beside it, if you turn the phone sideways).

### 10.2 Race mode keeps running

Race mode keeps running when you lock the phone, switch to another app, or go back to the section list. A notification shows its state, for example **Rally: In race** with the distance and the time. Tap it to go back to the app.

When you open the section again, the race panel opens by itself. If race mode runs in another section, the panel says **Race going in another section** and shows **Go to section**.

The screen stays on while a timer runs and the race panel is open. If you hide the race panel, the screen may turn off as usual.

> **Warning.** If the phone closes the app by itself (for example to save battery), race mode starts again but forgets the timers. In the phone's settings, open the list of apps, choose this app, open **Battery**, and choose the option without limits (often called **Unrestricted**). Keep the phone charged. See [The phone closed the app](#18-questions-and-problems) for how to carry on.

If the panel shows **Not connected to race service**, the part of the app that measures and times in the background is not running. The round **Race mode** button is hidden while the panel shows, so open the menu and tap **Race mode** there.

To hide the panel without stopping, use **Hide race panel** in the menu. To end race mode, first stop the timers (see below), then use **Drop race state and stop** in the menu.

### 10.3 The two timers: Go and Race

The app has two timers:

- The **section timer** (started with **Go**) times the **whole section** at its overall average speed (section length ÷ section time). It ignores inner stretches and speed changes. Use it, for example, for a liaison with a target time. The panel shows a stopwatch icon. Its early/late value compares you with an even speed over the whole section, not with the ➠ speed, so after an inner stretch the two can disagree.
- The **race timer** (started with **Race**) times a **timed stretch** where each `setavg` and `thenavg` counts. Use it for a regularity. The panel shows a flag icon.

Both timers use the **phone's clock**. Before the rally, set the phone's time to automatic and compare it with the official rally clock.

A race timer can run inside the section timer: for example a regularity inside a liaison. When the race timer finishes, the section timer keeps running. If you start a race timer while no section timer runs, the app starts the section timer at the same moment.

### 10.4 The race states

The panel looks different in each state:

| State | The panel shows | Buttons | Leads to |
|---|---|---|---|
| **Not started** | **Race not started** | the Go row, the Race row | section timer, or race timer |
| **Section timer runs** | clock, early/late, distance, speed | the **Start race:** row, **Undo finish race** (only after a race), **Stop** | race timer, or stopped |
| **Race timer runs** | clock, early/late, distance, speed and average speed, the speed buttons | **Finish race**, **Finish and start** | section timer, or a new race timer |
| **Stopped** | **Stopped:** with the time and the distance | the Go row, the Race row, **Undo stop** | section timer, or race timer |
| **In another section** | **Race going in another section** | **Go to section** | |

#### Starting: the Go row and the Race row

When nothing runs or after you stop, the panel shows two rows. First tap the position you want to start from in the list. The rows then show its distance: **Go from position 0.0:** and **Race at position 0.0:**.

Each row has these buttons:

| Button | What it does |
|---|---|
| **Now** | Starts the clock this second, at the selected position. Your distance is set to the distance of the position. |
| **On 10:00:00** | You see this if the selected position has a time of day. Starts the clock at that time of day. If that time is still to come, the clock counts down (with a minus) until then. Your distance is set to the distance of the position. The app counts the distance from the moment you tap, also during the countdown: if the car moves before the start, correct the distance at the start line. |
| **On 10:00:00, keep ODO** | You see this only after **Stop**. The same as **On 10:00:00**, but your distance does not change. Use it when you are already past the start point and the distance in the app is right. ("ODO" here means the distance the app measured.) |

The **Go** row starts the section timer. The **Race** row starts the race timer.

When you start the race timer, the app gives it a set average speed. It uses, in this order:

1. the remembered speed, if you set one with the "v" button (see [Speed buttons](#108-speed-buttons-and-the-v-button));
2. the `setavg` on the selected position;
3. the speed of the roadmap at that point;
4. 60 km/h.

So if the app remembers a speed, it uses that speed instead of the speed in your roadmap. The app also remembers the last speed button you tapped in a race. Before **each** race, check the "v" button at the top of the panel: if it shows a speed you do not want, open it and tap **Clear**.

#### While the section timer runs

The **Start race:** row (or **Next race:** after a race) starts a race timer:

| Button | What it does |
|---|---|
| **Now from 12.34** | Starts a race now, from where you are. The number is your current distance, with two decimals. |
| **On 10:00:00 from 12.345** | You see this if the selected position has a time of day. Starts the race at that time, from the selected position (its distance, with three decimals). |
| **On 10:00:00 from 12.345, keep ODO** | The same, but your distance does not change. |
| **Now from 12.345** | You see this if the selected position has no time of day and its distance differs from the app's distance. Starts a race now, from the selected position (its distance, with three decimals), and sets your distance to it. If the two distances are the same, the app shows only **Now from 12.34**, which then does the same thing. |

Below them:

- **Undo finish race** (only after you finished a race) brings back the race you just finished, as if you had not finished it.
- **Stop** stops the section timer. The panel then shows **Stopped:** with the time and the distance.

#### While the race timer runs

- **Finish race** ends the race timer. The panel goes back to the section timer and shows **Finished:** with the race time and distance.
- **Finish and start** ends the race and immediately starts a new one from where you are. You cannot undo this.

The race timer does not end by itself, even after the last position: tap **Finish race**. Past the last position, the panel shows no early/late value.

#### After Stop

- **Undo stop** brings back the section timer, as if you had not stopped.
- The Go row and the Race row let you start again.

### 10.5 What the race panel shows

**The clock.** While the race timer runs, the time since the race start; otherwise, the time since the section start. It shows minutes and seconds, and from one hour on, hours too: `01:30:00`. (The rows show the same time as `90:00`.) Before the start time it counts down, with a minus.

**Early or late.** To the right of the clock. The app calculates the time you should have at your current distance, and compares it with the clock:

- `+5.3` means you are 5.3 seconds **late**;
- `-12.0` means you are 12 seconds **early**;
- from one minute on, it shows minutes and seconds: `+01:05`.

It does not show this value if the roadmap has errors.

**Allowance.** While the section timer runs, if you turned on an allowance in Settings, the panel shows `(A:N)`: the allowance in minutes at your distance. The early/late value turns red when you are early by more than the allowance.

**The distance.** Your current distance in km. If a position is selected, a smaller number shows how far you are from it: `+0.125` means 125 m past it, `-0.300` means 300 m before it.

**The speed.** `v=` is your speed now. While the race timer runs, `ṽ=` is your average speed since the race started.

**Which time on a row to compare with the clock.** The race start is the start of a stretch (the app gives it a `setavg`). So while the race timer runs, compare the clock with the time of that stretch on the row (see [Several times on one row](#73-several-times-on-one-row)): it is the first time on the row, unless the position also lies in a smaller stretch inside the race, such as a village. The last time on the row is always the time from the section start.

**The countdown in the list.** While the race timer runs, the selected row counts down to its time of day: `-15` (seconds) or `-01:30`. When that time has passed, the countdown disappears.

**The green row.** While a timer runs, the list marks in green the last position you passed. To make the selection follow the car, tap the green row: the selection (yellow) then moves with you, and the list scrolls with it. Tap another row to stop this.

**What a minus means.** The minus sign appears in several places:

| Where | Example | Meaning |
|---|---|---|
| The clock | `-00:45` | 45 seconds until the start. |
| Early/late | `-12.0` | 12 seconds early. |
| Distance to the selected row | `-0.300` | 300 m before the selected position. |
| Countdown in the list | `-15` | 15 seconds until the time of day of the selected row. |
| Allowance in the list | `-2` | An allowance of 2 minutes at this position. |

### 10.6 What race mode changes in your roadmap

Race mode writes into the roadmap of the section. Know this before you drive:

- **Starting a timer** with **Now** or **On…** gives the start position a time of day: the start time. A section has only one time of day, so the app removes the time of day you typed.
- **Starting the race timer** also gives the start position a `setavg` with the race speed. Sometimes the app adds a new position at the start point for this.
- **Finish race** adds a position at the finish distance. Then it counts the `setavg` and `endavg` marks in the whole section (with the `setavg` it wrote at the race start). If more stretches start than end, it gives the new position an `endavg`. Then the app gives the start of the section its start time back.
- **Undo finish race** removes the position that **Finish race** added. **Undo stop** changes nothing in the roadmap.
- **A speed button** during a race adds a position with `thenavg` (see [Speed buttons](#108-speed-buttons-and-the-v-button)).
- **Driving backwards** in a race removes **every** `thenavg` at the points you drive back over, also the ones you typed from the road book (see [Going backwards](#107-correcting-the-distance)).
- **Add passed position** adds a position.

> **Important: end the longest stretch with `endavg`.** If the stretch of the whole section has no `endavg` at its last position, **Finish race** ends it at the finish point instead. All the positions after that point then show `outside intervals`, the red error bar appears, and the early/late value disappears for the rest of the section. To avoid this, always put `endavg` on the last position of the section.
>
> If the red bar appears after **Finish race** anyway, switch to edit mode, find the position at the finish distance, and remove its `endavg` with **END** (or delete the position with **DEL ╳**). The timers keep running while you edit.

So make a copy of the section before each run (**Duplicate this section**), and drive with the copy. After the run, you can delete the copy.

### 10.7 Correcting the distance

The measured distance is never perfect. Correct it at known points, for example at a road-book point with an exact distance:

- **-0.1** and **+0.1** change the distance by 100 m.
- The phone's **volume buttons** change it by 10 m (up adds, down subtracts).
- **Tap the distance** to type the exact value. Tap **✓** to use it, or **✕** to cancel. If a position is selected, the button **Set to position 12.345** sets the distance to that position.

**Going backwards.** If you miss a turn and drive back, tap the **↶** button (Switch direction). It turns red, and the distance now goes down as you drive. Tap it again when you drive forward again. While you drive back in a race, the app removes every `thenavg` mark at the points you drive back over (but not before the start of the race), also the ones you typed. After a wrong turn, check the `thenavg` marks against the road book, and type back any you lost. If sounds are on, the app beeps every 3 seconds while the distance goes backwards.

### 10.8 Speed buttons and the "v" button

While the race timer runs, the panel shows a row of round speed buttons: **5, 10, 20, 30 … 110**. Some road books give a new speed at a point you only see on the road, for example a sign. When you reach that point, tap the speed. The app:

- adds a position at your current distance with `thenavg` and that speed, so the times from here use the new speed;
- remembers this speed.

More fields in that row:

- **The percentage field** (with **×** after it): if the rally gives speeds as a percentage, type it here. The speed buttons then give that percentage of their speed.
- **The "/h →" field and its button**: type any speed, then tap the round button next to it. It also uses the percentage.
- **The "=/h →" field and its button**: type an exact speed. This one does not use the percentage.

If you tap the wrong speed, tap the right one straight after: it adds another position with the right speed. Between the two taps the app used the wrong speed, so the times are a little off; the sooner you correct it, the smaller the error. Later, in edit mode, delete the wrong position.

**The "v" button** at the top of the panel opens the same row when no race timer runs. There, a speed is only **remembered**: the next race timer starts with it as its `setavg`, instead of the speed in your roadmap. **Clear** forgets it. The button shows the remembered speed (for example **60**), and the percentage next to it if it is not 100%.

### 10.9 Adding a position as you pass it

The **Add passed position** button (a pin with a plus) adds a position at your current distance. Use it to mark a point that is not in the road book, or that you want to check later. It works while a timer runs, and after **Stop**.

### 10.10 Event log

The **Event log** button (a clock with an arrow, at the top of the race panel) opens a list of what happened in this section: when the section timer and the race timers started and finished, and each undo. Each line shows the distance and the time of day. Finished timers also show how long they ran.

The bin button (**Delete events**) deletes the whole log immediately, without asking.

### 10.11 Sounds

Turn sounds on in Settings (**Play sounds on race mode actions**). The app then beeps:

- when a timer starts;
- when a race finishes, and when you stop;
- when it adds a position (with **Add passed position** or a speed button);
- when you switch to driving backwards, and every 3 seconds after that, until you drive forward again.

---

## 11. A rally day with the app

### 11.1 The night before

1. Make one section for each part of the road book that starts at a given time of day, for example from one time control to the next. A section can hold only one time of day.
2. Enter the positions: the start with `setavg` and the time of day, the speed changes with `thenavg`, the inner stretches with `setavg` and `endavg`, and an `endavg` on the last position.
3. Check the times against the road book.
4. Make a copy of each section to drive with (**Duplicate this section**).

### 11.2 Before the start

- Set the phone's time to automatic, and compare it with the official clock.
- Let the app run without battery limits, and connect the phone to power.
- Turn race mode on, and check the distance source at the top of the race panel (for example **GPS ✔**). See [Measuring the distance](#12-measuring-the-distance).
- If the driver uses a second phone, check that it shows **Data ✔**.
- Turn sounds on or off in Settings.
- If you set a remembered speed with the "v" button, check it, or tap **Clear**.

### 11.3 At the start

Select the start position. If it has the time of day of your start, tap **On 10:00:00** in the right row (Go for a liaison, Race for a regularity). The clock counts down to the start. If the marshal gives you a different minute, change the time of day of the start position in edit mode first, or tap **Now** at the moment of your start.

### 11.4 A regularity inside a liaison

Enter the regularity as a stretch inside the liaison (see [Inner stretches](#72-inner-stretches)), and end the liaison with `endavg` on its last position (see [What race mode changes in your roadmap](#106-what-race-mode-changes-in-your-roadmap)).

1. At the start of the liaison, start the section timer with the **Go** row: **On 10:00:00** at the liaison's start time.
2. Before the regularity, check the "v" button. Tap **Clear** if it shows a speed.
3. At the start of the regularity, select its start position, and tap **Now from …** (with three decimals) at the moment you start. The **On …** buttons do not help here: the section has only one time of day, and the liaison start holds it.
4. At the end of the regularity, tap **Finish race**. The section timer keeps running for the rest of the liaison, and the liaison start gets its time of day back.
5. At the end of the liaison, tap **Stop**.

### 11.5 Moving to the next section

Race mode times one section at a time. At the end of a section:

1. Tap **Finish race** (if a race timer runs), then **Stop**.
2. Open the menu and tap **Drop race state and stop**.
3. Go back to the section list and open the next section.
4. Tap **Race mode**, select the start position and start as in [At the start](#113-at-the-start).

Do this while you wait at the time control, before your next start time.

### 11.6 Stops inside a regularity

The app has no pause. If a marshal stops you and gives you a new start time, tap **Finish race** while you wait. Then select your position and start a new race with **Now from …** at the moment you start again.

### 11.7 On the road

- **Known points.** When you pass a point with an exact distance in the road book, correct the distance (see [Correcting the distance](#107-correcting-the-distance)).
- **Odometer checks.** If you entered `odo` marks, the grey numbers under the distances show what the car's odometer should read. Compare them with the car.
- **Secret checks.** The early/late value is correct at any point of the road, not only at the positions. Watch it all the time. To note a point where you saw a check, tap **Add passed position**.
- **Mistakes.** For a wrong turn, use **↶** (see [Going backwards](#107-correcting-the-distance)). For a wrong speed button, see [Speed buttons](#108-speed-buttons-and-the-v-button). If you tapped **Finish race** too early, tap **Undo finish race**. If you tapped **Stop** too early, tap **Undo stop**.

### 11.8 After the stage

- Open the **Event log** to see when each timer started and finished, and how long it ran.
- The section you drove with now holds the extra positions and marks from race mode. Delete it, or keep it to look at later. Your clean copy is unchanged.

---

## 12. Measuring the distance

Choose the source in Settings, under **Telemetry source**. You can change it while you drive.

| Source | How it works |
|---|---|
| **GPS** | Uses the phone's location. You need nothing else. It works best with a clear view of the sky. Calibration does not change the GPS distance. |
| **OBD over Bluetooth** | Reads the car's speed from a small reader (sold as "ELM327") that you plug into the car's diagnostic socket, usually under the dashboard. The app adds up the distance from the speed. Pair the reader with the phone first: plug it in, turn on the ignition, and choose the reader in the phone's Bluetooth settings. |
| **OutGauge** | Receives the speed from a driving game on a computer (for example BeamNG) over Wi-Fi. For training at home. |
| **Simulation** | For testing. A **Simulate speed:** slider in the race panel sets the speed from 0 to 100 km/h. |

The race panel shows the state of the source at the top. Tap it to open Settings. Orange text means a problem.

| Text | Meaning |
|---|---|
| **GPS ✔ (9 sat)** | Good GPS signal, from 9 satellites. |
| **GPS ± (5 sat)** | Weak GPS signal. The app adds up the distance from the speed instead of the location. |
| **GPS: no position (3 sat)** | No usable GPS signal. The speed shows as 0. |
| **GPS: waiting (0 sat)** | Waiting for the first GPS signal. If this stays for long, check that the app may use your location. |
| **OBD: connecting…** | Connecting to the reader in the car. |
| **OBD ✔** | The reader works. |
| **OBD: permissions!** | The app may not use Bluetooth. Allow it in the phone's settings. |
| **OBD: no MAC!** | No reader is chosen in Settings. |
| **OutGauge: waiting…** | Waiting for data from the game. |
| **OutGauge ✔** | Data from the game arrives. |
| **OutGauge: delayed** | No data for 2 seconds. The speed shows as 0. |
| **OutGauge: error** | The app cannot receive data, for example because another app uses the same port. |
| **Simulation ✔** | The test slider is on. |
| **Telemetry not initialized** | No source is running yet. |

How the app treats GPS: it ignores very bad signals. When the signal is weak, it uses the speed. While the car stands still, the GPS location still moves a little; so that this does not add distance, the app stops adding distance when the car has not moved for 3 seconds. ("sat" in the texts above means satellites.)

---

## 13. Settings

The app saves each setting immediately, except the calibration factor, which has its own **Save** button.

### 13.1 Telemetry source

See [Measuring the distance](#12-measuring-the-distance).

- With **OBD over Bluetooth**, tap the search button next to **Bluetooth MAC address** and choose your reader from the list. (The MAC address is the reader's code; you can also type it.)
- With **OutGauge**, type the **UDP port** (a number from 1024 to 65535; 4444 if you do not change it). In the game, set the same port and this phone's address, which the app shows below the field.

### 13.2 Calibration

The car measures distance a little differently from the road book. The **calibration factor** describes the difference:

> calibration factor = distance the car measures ÷ distance in the road book

For example, if the road book says 10.00 km and the car measures 10.20 km, the factor is 1.02. The app divides the speed from the reader (or the game) by this factor, and multiplies the odometer readings in the list by it. It does not change the GPS distance.

The calibration fields appear in Settings only when the source is **OBD over Bluetooth**. To set a factor while you use GPS or OutGauge (for example, to get the right odometer readings in the list), choose OBD, set the factor, and choose your source again.

**To calibrate on an odometer check route** (with **OBD over Bluetooth** as the source):

1. In Settings, set the calibration factor to **1** and tap **Save**. (The app calculates the new factor from the distance it measured. With an old factor, that distance is already corrected, and the new factor comes out wrong.)
2. Make a section for the check route, starting at 0.0.
3. At the start of the route, turn race mode on, select the 0.0 position and tap **Now** in the Go row.
4. Drive the route, and stop the car at its end. You may tap **Stop**, but do not use **Drop race state and stop** before you calibrate.
5. Open **Settings** from the section menu (three dots), not from the section list. Settings now shows **Odometer distance:**. In this field, "odometer" means the distance the app measured from the reader, not the car's dashboard.
6. Type the exact distance from the road book in **Exact ODO check distance**. The app fills in the calibration factor.
7. Tap **Save**.

### 13.3 Send data to the driver's device

Type the address of the driver's phone in **The driver's device IP address**. See [The driver's screen](#14-the-drivers-screen-driver-hud).

### 13.4 Allowance

See [Allowance](#711-allowance).

### 13.5 Race mode

**Play sounds on race mode actions** turns the beeps on or off. See [Sounds](#1011-sounds).

---

## 14. The driver's screen (Driver HUD)

A second phone can show the driver the same list and race panel as you see, but without any buttons. ("HUD" means "head-up display": a display for the driver.) Both phones must be on the same Wi-Fi network. In the car, you can make one: turn on the mobile hotspot on your phone (in the phone's settings, under network or connections; it shares your internet over Wi-Fi), and connect the driver's phone to it.

**On the driver's phone:**

1. On the section list, open the menu and choose **Driver HUD**. The driver's phone does not need any sections.
2. The screen shows this phone's network address: numbers like **• 192.168.1.23 (Wi-Fi)**. Tap it to copy it.

**On your phone:**

1. Open **Settings**, and type that address in **The driver's device IP address**. The address can change from day to day, so check it before each rally.
2. Turn race mode on in a section.

The driver's phone now shows:

- the section and its positions, with your selected row and the green row;
- the race panel: the clock, early/late, the distance, the distance to the selected row, the speed;
- the countdown on the selected row.

The driver's phone does not show the allowance, and has no buttons to change anything. The state of the link shows at the top:

| Text | Meaning |
|---|---|
| **Data: waiting** | No data yet. Check the address, the Wi-Fi, and that race mode is on on your phone. |
| **Data ✔** | Data arrives. |
| **Data ???** | No new data for more than a second. |
| **Data: incompatible version** | The two phones have different versions of the app. Install the same version on both. |

Leave the Driver HUD screen to stop it.

---

## 15. Phones, tablets and turning the phone

- **On a phone**, the **Race mode** button is a round button at the bottom right. The list leaves space at the bottom so the button does not cover the last row.
- **On a tablet**, the **Race mode** button is in the top bar. The buttons at the top of the race panel move to its bottom and get bigger.
- **When you turn the phone sideways**, the list and the race panel (or the keypad) sit side by side.

The app uses light or dark colours, like the rest of the phone. It uses the phone's language: English or Russian.

---

## 16. Saving, copying and sharing

- The app saves every change immediately.
- **Copy as text** copies the roadmap, so you can send it in a message. On another phone, use **Import section** to make a section from it.
- **Duplicate this section** makes a copy. Give it a new name: two sections cannot have the same name.
- **Rename this section** changes the name.
- **Delete this section** asks you to tap twice. You cannot undo it.

**To add `odo` or `s` marks to a section you already have:** use **Copy as text**, paste the text into a notes app, add the marks, copy the text again, and use **Import section** with a new name. Then delete the old section if you no longer need it.

---

## 17. Roadmap text format (reference)

You need this only for **Import section**, or to read what **Copy as text** gives you.

- One position per line: the distance, then the marks, with **one** space between words.
- No empty lines.
- A line that starts with `//` is a note. The app ignores it, and drops all notes the first time you change the section.

| Mark | Example | Meaning |
|---|---|---|
| `setavg <speed>` | `0.0 setavg 60` | A set average speed starts. |
| `thenavg <speed>` | `10.0 thenavg 45` | The set average speed changes. |
| `endavg` | `20.0 endavg` | A set average speed ends. If you write `endavg` without a speed, put it last on the line. |
| `atime <time>` | `0.0 setavg 60 atime 10:00:00` | Time of day: hours, minutes and seconds. `10:00` alone is wrong. |
| `odo <km>` | `2.0 odo 5.05` | The car's odometer reading here. |
| `s <step> <count>` | `0.0 setavg 60 s 0.5 3` | Add `count` helper points, one every `step` km. |

A speed can be written as:

- `60`: 60 km/h;
- `1.0/1:30`: 1.0 km in 1 minute 30 seconds, which is 40 km/h (only in text: the keypad takes speeds in km/h);
- `60;110%`: 110% of 60 km/h, which is 66 km/h.

One line can have only one of `setavg`, `thenavg` and `endavg`. A section can have only one `atime`.

When the app saves the text, it writes speeds in km/h (so `1.0/1:30` becomes `40.0`), and times of day with a `0:` in front (`atime 0:10:00:00`). The `0:` is a day number that the app keeps for its own use; you do not need to type it. Both forms import again without problems.

A full example:

```
0.0 setavg 60 atime 10:00:00
5.25
10.0 setavg 30
15.0 endavg
22.4 thenavg 50;110%
30.0
```

---

## 18. Questions and problems

**The times are gone and the screen shows a red bar.**
The roadmap has an error. Look for red messages on the rows, and see [Errors and warnings](#8-errors-and-warnings).

**The times changed when I opened the race panel.**
With the race panel open and no race timer running, the list shows section times. See [Section times in race mode](#79-section-times-in-race-mode).

**I cannot type a colon when I create a section.**
Use dots: `1.30` for 1 hour 30 minutes. See [Create a section](#41-create-a-section).

**The time of day I typed is gone.**
Starting a timer replaces it with the start time. See [What race mode changes in your roadmap](#106-what-race-mode-changes-in-your-roadmap).

**The early/late value does not show.**
The roadmap has errors, or the app has no time for your distance. Correct the roadmap first.

**The distance does not grow.**
Check the text at the top of the race panel (see [Measuring the distance](#12-measuring-the-distance)). For GPS, wait for **GPS ✔**. For OBD, check the reader in Settings and that it has power.

**The volume buttons change the volume instead of the distance.**
They change the distance only while the race panel is open and a timer runs. After you type in a field of the race panel, they may change the volume again.

**The driver's phone says "Data: waiting".**
Check that both phones are on the same Wi-Fi, that the address on your phone is right, and that race mode is on on your phone.

**I started at the wrong position.**
There is no undo for a start. After the section timer starts, tap **Stop**, select the right position, and start again. In a race, tap **Finish race**, then start a new race from the right position. Afterwards, check the positions: race mode adds marks to the roadmap (see [What race mode changes in your roadmap](#106-what-race-mode-changes-in-your-roadmap)).

**The phone closed the app, and the timers are gone.**
Race mode forgets the timers when the phone closes the app. To continue:

1. Open the **Event log** to find the time of day when the timer you need started. (The log stays after the app closes.)
2. In edit mode, give the start position of that timer this time of day with **ATIME**.
3. Turn race mode on, select that start position, and tap **On 10:00:00** in the same row as before. The clock is right again, but your distance is now the start distance.
4. Tap the distance in the race panel, and type your real distance (for example from the last road-book point you passed).

If you were in a regularity inside a liaison, the app can bring back only one timer correctly. Restore the race timer: starting it also starts the section timer, but from the regularity start, so the section timer is not right for the rest of the liaison.
