# F03 — App limits

## Behaviour
- **Daily time limits per app.** `[listing]` A default limit length exists. `[static]` The original suggests limits based on a percentage of current usage. `[static]`
- **Strict Mode** locks limits: once enabled, they can't be edited or disabled until the period ends. `[listing]`
- When a limit is reached, the block overlay appears. A configurable wait time exists before the app can be retried or extended. `[static]`
- **Reminders** before the limit is hit (per-app reminder settings). `[static]`
- **Streaks** for days limits were respected. `[listing]`
- Usage comes from the monitor foreground service + usage stats. `[manifest]`

## Rules `[arcify]`
- `AppLimit(packageName, dailyLimitMs, reminderAtPct?, strictUntil?, enabled)`.
- Usage per day = foreground time from `UsageStatsManager` events. It resets at local midnight. Timezone and time changes are handled (the original re-arms on time and timezone broadcasts `[manifest]`).
- While `strictUntil > now`, edits are rejected: no delete, no increase, no disable. Decreasing is allowed.
- On reaching the limit: overlay with time used. Optional one-time "5 more minutes" is disabled in strict mode.
- Streak day = every enabled limit respected. Stored as `StreakDay(date, kind = LIMITS)`.
- Defaults: none pre-selected. The setup flow suggests the user's top-3 most-used social apps.

## Implementation (M3)
- Rules (pure, tested): `core/.../block/LimitRules.kt` (status, strict changes), `BlockPolicy.kt` (decision), `usage/UsageAggregator.kt` (foreground time from activity events, including screen switches inside one app).
- Storage: `app_limit` table (Room v2, auto-migrated from v1). `BlockRepository` refuses changes that a strict lock forbids.
- Engine: `blocker/.../ArcifyAccessibilityService.kt`. It re-checks on every app switch, on strict-focus start/end and every 15 s.
- UI: Block tab (permissions, limits with presets 15 min to 2 h, "Lock for today" with confirmation, delete). Block screen with "Close app", which goes home.
- Reminder: one notification per app per day at 80 %.
- **Not yet**: the "5 more minutes" grace, streaks (with F08/F09 in M6), and the top-3 suggestions in onboarding.
