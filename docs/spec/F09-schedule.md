# F09 — Schedule planner

## What the original does
- Plan focus sessions for today and future days. Repeating schedules, weekly calendar view, streaks for consistency. `[listing]`
- Exact alarms and full-screen intents are declared, with boot/time/timezone re-arming. `[manifest]`
- A **study goal** (daily target) with a widget. `[manifest]`

## Arcify design `[arcify]`
- `ScheduledSession(id, title, tagId?, start LocalTime, durationMin, mode, strict, repeat = NONE|DAILY|WEEKDAYS|CUSTOM(daysMask), date?)`.
- At start time: a high-priority notification with "Start now". Optional auto-start when strict is chosen.
- Alarms via `AlarmManager.setExactAndAllowWhileIdle`, re-armed on boot, time change, timezone change and app update.
- Weekly calendar screen: days × sessions, with completed/missed state.
- `DailyGoal(minutes)`: streak day = goal met. Feeds F10.

## Implementation (M6)
- **Rules** (pure, tested; `core/.../schedule/Schedule.kt`):
  - Repeats: once (today/tomorrow), every day, weekdays, chosen days (bit mask).
  - `between` lists occurrences in time order; `next` is the next slot still ahead.
  - Status: **done** if a focus session started from 15 minutes before the slot until its end; otherwise upcoming, now or missed.
- **Alarms**: `ScheduleAlarms` keeps one exact alarm for the next slot across all sessions.
  - Uses `USE_EXACT_ALARM` (Android 13+) or `SCHEDULE_EXACT_ALARM` (Android 12 and below), and falls back to inexact if exact alarms are denied.
  - Re-armed on save/delete, app start, boot, time and timezone changes, and app update.
  - At the slot: **strict sessions start by themselves**; others get a "Time to focus" notification with **Start now**.
  - Pomodoro slots run as many focus + short-break cycles as fit.
- **Daily goal**: 30 min to 4 h, default 2 h (DataStore). Shown on the Focus screen, Insights and the widget.
- **Schedule tab**: goal chips, nudges switch, this week (Mon–Sun) with each slot's status, and add/edit/delete with a time picker, length, timer or Pomodoro, strict, and repeat.
- **⚠️ Not checked on a device yet**: alarm delivery under battery optimisation varies by phone brand.
