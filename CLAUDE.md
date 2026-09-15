# School Period Complication Project Guide

Wear OS complication and companion app for Robert's Galaxy Watch: shows the current school
period (`P1`, `Ntr`, `Lunch`) and a live countdown to the next bell.

## Build & Test Commands

- **Run the tests**: `.\gradlew.bat :app:testDebugUnitTest` (verified passing)
- **Build debug APK**: `.\gradlew.bat assembleDebug`
- **Build, test and install to the watch**: `.\deploy.ps1`

## ALWAYS DEPLOY TO THE WATCH AFTER CHANGING THE APP

Robert cannot see a change until it is on his wrist. So whenever code, resources or the
manifest here change, run `.\deploy.ps1` — it runs the tests, builds, installs, and reports the
version it read back *off the watch* rather than the version it thinks it built. Do this as
part of the normal shipping workflow, without asking. Then say plainly whether it reached the
watch, because sometimes it cannot:

- **The watch is unreachable.** The script says so and exits without installing. Report that;
  never describe the change as being on the watch.
- **The tests fail.** The script refuses to install, which is the point. Fix the tests.

Never install by hand with a remembered `adb connect <ip>:<port>`. **Every network detail about
this watch changes** — its IP, its randomized Wi-Fi MAC (a different one per radio band), and
its wireless-debugging port, which is reassigned whenever adbd restarts, including on any
Wi-Fi reconnect. The hardware serial `RFAX60DJKDZ` is the only stable identifier, which is why
`deploy.ps1` rediscovers the watch by mDNS on every run.

**`deploy.ps1` refuses to install on a device that is not a watch, and that check must stay.**
Robert's phone is usually connected to adb at the same time. The sibling watch-face repo's
auto-install rule only checked that *a* device was connected, and so pushed build after build
to the phone, where a watch face does nothing.

**If the watch stops accepting the connection, the pairing was cleared** — toggling wireless
debugging on the watch forgets this laptop. An open TCP port with a failed handshake is the
signature, and it is not the same as a refused port, which just means a stale mDNS record.
Only Robert can fix a lost pairing: he taps *Settings → Developer options → Wireless debugging
→ Pair new device* and reads out the 6-digit code, which exists nowhere but the watch screen.
Then `adb pair <ip>:<port> <code>`.

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

5. **The complication path must stay direct-boot-safe.** The service and its receiver are
   `directBootAware`, because otherwise the system refuses to bind them until the watch is
   unlocked and the slot draws empty after every reboot while the built-in complications are
   already filled. That is only legal while nothing on that path reads app storage — the schedule
   is a compiled-in table and there is no `SharedPreferences`, `DataStore` or database anywhere
   in this app. If one is ever added, it must not be read from the complication or the receiver
   unless it is moved to device-protected storage (`createDeviceProtectedStorageContext()`).

6. **`local.properties` stays out of git** — it is gitignored, and it holds the local SDK path.
