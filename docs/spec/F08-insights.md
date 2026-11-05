# F08 — Screen-time insights & widgets

## What the original does
- Daily and weekly screen-time reports, **productive vs distracted time**, focus insights and tag distribution. `[listing]`
- Apps are categorised. Category mappings come from the server. `[static]`
- **Phone unlock count** tracking. `[manifest]` (there's a widget for it)
- **Home-screen widgets** `[manifest]`: usage (two sizes), focus timer, study goal, study calendar, unlock count. Plus a mascot goal widget and promotional widgets, which Arcify drops.
- Hourly usage aggregation exists. `[static]`

## Arcify design `[arcify]`
- Source: `UsageStatsManager` events, aggregated hourly into `UsageHour(date, hour, package, foregroundMs, launches)`.
- Unlocks: count screen-on + user-present broadcasts while the monitor service runs → `UnlockCount(date, count)`.
- Classification: `AppCategory(package, category = PRODUCTIVE|NEUTRAL|DISTRACTING)`. Bundled defaults by Play category heuristics, editable per app.
- Screens: Today (total, top apps, unlocks, reels/shorts counts from F04), Week (bars per day, productive vs distracting stack, focus minutes, tag pie).
- Widgets (Glance): Usage today, Focus quick-start, Study goal progress, Study calendar (week heatmap), Unlocks today.
- Charts: Vico.
