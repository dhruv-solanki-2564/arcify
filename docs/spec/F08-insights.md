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

## Implementation (M6)
- **History**: `usage_day` (per app per local day: foreground time + launches) and `unlock_day` (Room v4).
  - `UsageHistorySync` refreshes today and yesterday every time, and backfills older days only if missing. Android keeps detailed events for just a few days, so a partly expired day never overwrites a stored one.
  - Runs from the hourly `ArcifyWorker` and while the Insights screen is open.
- **Unlocks**: counted from usage events (`KEYGUARD_HIDDEN`, Android 9+). On older phones it shows "not available". No broadcast receiver needed.
- **Categories**: the default comes from Android's app category (social, video, game, news → distracting; productivity → productive; else neutral). The user overrides it by tapping the chip; the override is cleared when it matches the default (`app_category`).
- **Rules** (pure, tested): `InsightRules.split`, `focusByDay`; `StreakRules.streak` (today extends the streak once achieved, otherwise it counts up to yesterday), `limitDays`. `UsageAggregator.stats` adds launch counts.
- **Insights tab**:
  - Today: screen time, category split bar, focus vs goal, unlocks, Reels/Shorts watched.
  - Last 7 days: stacked bars by category, with focus minutes underneath. Drawn with plain Compose layouts; Vico isn't needed.
  - Streaks: focus goal, all limits kept (when limits exist), no Reels/Shorts.
  - Top apps, with category chips.
- **Widget** (Glance): one "Arcify today" widget showing focus vs goal, screen time and unlocks, with "Start focus" (your Pomodoro defaults, not strict) or "Open timer". It refreshes hourly and when a session ends.
- **Not yet**: tag distribution (needs the tag picker), per-app detail screen, separate unlock/calendar widgets.
