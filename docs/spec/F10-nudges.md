# F10 — Nudges, notification batching, wake alarm

## What the original does
- A companion character gives friendly nudges and motivation. `[listing]` Arcify **does not** reuse it; any mascot would be original and is a later UI decision.
- Several nudge notification types `[manifest]`: a "guilt-trip" reminder when a focus session is abandoned or skipped, education-app reminders, a standing usage notification, and an after-session group nudge.
- **Notification batching** (not on the listing) `[manifest]` `[static]`: a notification-listener service holds notifications and delivers them in batches. A small allowlist of apps can deliver instantly.
- **Wake-up alarm** (module "c68") `[manifest]` `[static]`: full-screen alarm with snooze and sound playback, restored after reboot. It's tied to promotional passes there.
- An AI-coach feature with app-category mappings exists. `[static]` Out of scope for now.

## Arcify design `[arcify]`
- **Nudges**: local rules only, no server. For example: "No focus yet today and it's after your usual start time", "Limit 80 % used", "Streak at risk".
- **F13 Notification batching** (new, optional): user picks apps whose notifications are held and delivered at chosen times (e.g. every hour or at :00). Allowlist for instant apps. Requires notification-listener access.
- **F14 Study wake alarm** (new, optional): an alarm that can only be dismissed by starting a focus session or completing a short task. Snooze is limited.
- Streaks: `StreakDay(date, kind = FOCUS_GOAL|LIMITS|SHORTS_FREE)`.

## Implementation (M6)
- **Nudges** (pure, tested; `core/.../nudge/Nudges.kt`). Each fires at most once a day, and can be switched off in Schedule:
  - **Start focus**: after 17:00 with no focus yet today.
  - **Streak at risk**: after 20:00 when a focus-goal streak of 2 or more days would break tonight.
- **Runner**: `NudgeRunner` is called by the hourly `ArcifyWorker` (WorkManager, with dependencies from a Hilt entry point).
- **Streaks are computed on the fly** from stored history (`StreakRules`); no `StreakDay` table is needed.
- **F13 notification batching and F14 study wake alarm**: the user confirmed both on 2026-11-05; built in M6b (below).

## Implementation (M6b)
### F13 Notification batching
- **Rule** (pure, tested; `core/.../batching/Batching.kt`): a notification is held only when batching is on, its app is on the user's list, it isn't ongoing, and its category isn't call, alarm, reminder, navigation, transport, stopwatch, progress, service, system or event. Arcify's own notifications are never held.
- **Delivery times**: every 30 min, 1 h, 2 h or 3 h, counted from local midnight (e.g. hourly = on the hour).
- **Listener**: `ArcifyNotificationListener` stores the app, title, text and time in Room (`held_notification`, version 5) and cancels the original.
- **Delivery**: `BatchDeliveryReceiver` (exact alarm) posts one summary per app, with the latest lines and a tap that opens the app, then deletes the rows. "Deliver now" does the same. Switching batching off delivers everything held, so nothing is stranded. The alarm is re-armed at boot.
- **UI**: Block → Notification batching: notification-access status, switch, interval, held count, apps list (add, remove, reset to none).
- **Privacy**: held text stays on the phone and is deleted as soon as it's delivered.

### F14 Study wake-up alarm
- **Rules** (pure, tested; `core/.../alarm/WakeAlarm.kt`): time, days (none = once), snoozes allowed (0–3), snooze length, focus length. `nextTrigger` finds the next ring. A one-time alarm switches itself off after ringing.
- **Arming**: only the earliest alarm is armed, with `setAlarmClock` (exact, allowed while idle, shown as the phone's next alarm). If exact alarms are denied, it falls back to an inexact alarm. Re-armed at app start, boot, time change and update, and after every edit.
- **Ringing**: `WakeAlarmService` (media-playback foreground service) loops the default alarm sound on the alarm stream and vibrates. It shows a full-screen notification that opens `WakeAlarmActivity` over the lock screen, and stops by itself after 10 minutes.
- **Turning it off**: Back does nothing. The choices are:
  - **Start focus**: starts a focus session of the alarm's length and opens Arcify.
  - **Snooze**: allowed only while snoozes are left.
  - **Solve three two-digit sums**: new sums every time.
- **UI**: Schedule → Wake-up alarms: list with on/off switches, plus an add/edit dialog (time, days, snoozes, snooze length, focus length, delete).
- **Storage**: Room `wake_alarm` (version 5).
