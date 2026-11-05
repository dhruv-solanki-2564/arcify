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
