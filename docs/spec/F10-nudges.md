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
- **F13 notification batching and F14 study wake alarm**: the user confirmed both on 2026-11-05. Planned for M6b, together with the adult-site list.
