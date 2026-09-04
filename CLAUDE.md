# School Period Complication Project Guide

Wear OS complication and companion app for Robert's Galaxy Watch: shows the current school
period (`P1`, `Ntr`, `Lunch`) and a live countdown to the next bell.

## Build & Test Commands

- **Run the tests**: `.\gradlew.bat :app:testDebugUnitTest` (verified passing)
- **Build debug APK**: `.\gradlew.bat assembleDebug`
- **Install to the watch**: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
  — always pass `-s <serial>`; see the sibling watch-face repo for finding the watch, which
  has no stable IP or port.

`ScheduleEngineTest.kt` (286 lines) covers the engine and passes. **Nothing runs it
automatically** — this repo has no CI, so those tests only run when someone remembers.

## Architecture

- **`engine/ScheduleEngine.kt`** — the schedule tables and the "what period is it" logic. Pure
  Kotlin, no Android imports, which is why it is the part that can be tested. Keep it that way.
- **`model/ScheduleModels.kt`** — `BellPeriod`, `ScheduleType`, `PeriodStatus`.
- **`complication/`** — the Wear OS data source and its refresh alarm.
- **`ui/MainActivity.kt`** — Compose Material3 full-day viewer.

## THE SCHEDULE HERE IS A COPY, AND IT IS CURRENTLY WRONG

**Source of truth: the "Bell Schedule" tab of the Wellness Center spreadsheet**, read by
`wellness-center-test/apps-script-sheet/Code.js` (`getSheetByName("Bell Schedule")`). That sheet
is what the official visit records are computed against, and it already carries every variant —
regular, Friday, early dismissal, assembly and finals.

The tables in `ScheduleEngine.kt` are a hand-copied subset of it, and so are `BELL_*` in
`viewsonic-dashboard/index.html`. As of 2026-09-03 the two copies disagree with each other:

| | Lobby dashboard | This repo |
|---|---|---|
| Period 7 | Mon–Thu 15:35–16:30, Fri 14:58–15:46 | **absent** |
| Friday Period 0 | 07:30–08:18 | **07:06–07:54** |
| Friday Teacher Collaboration | absent | 08:00–08:55 |
| Early dismissal / assembly / finals | all present | **absent — falls back to regular** |

So this watch currently shows the day ending an hour early, disagrees about Friday morning by
24 minutes, and is silently wrong for the whole of any early-dismissal, assembly or finals day.

**Before changing any time in `ScheduleEngine.kt`, read the sheet.** Do not copy from the
dashboard, and do not copy from here into the dashboard — both are downstream. Adding the
missing schedule variants means adding to `ScheduleType`, which currently knows only
`REGULAR`, `FRIDAY` and `WEEKEND`.

## Project Rules

1. **`ceilMinutes` rounds up on purpose.** A countdown reads "1m" for the whole final minute and
   only reaches "0m" when the bell actually rings. This matches how the watch face renders
   `TimeUnit.MINUTES` difference text, so the app and the complication never show different
   numbers at the same instant. Do not switch it to truncation.

2. **The complication hands the watch face a window, not a snapshot.** `windowStart` and
   `windowEnd` on `PeriodStatus` let the face tick a live countdown itself. Returning formatted
   text instead would freeze the number between updates.

3. **A refresh alarm fires at each status change.** The countdown needs no help, but the *label*
   ("P1" → "Pass" → "Lunch") only changes when the data source is asked for new data, and the
   platform's `UPDATE_PERIOD_SECONDS` is a throttled hint — a bell could come and go minutes
   before the label caught up. One alarm per status change closes that gap.

4. **Exact alarms are requested but not required.** API 31+ does not grant them to every app, so
   `canScheduleExactAlarms()` is checked and an inexact alarm is used otherwise. It still lands
   close to the bell and the countdown keeps ticking regardless. Never assume the exact path.

5. **`local.properties` stays out of git** — it is gitignored, and it holds the local SDK path.
