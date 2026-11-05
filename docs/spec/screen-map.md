# Screen map

The original uses Jetpack Compose, so screens aren't layout files. This map comes from the declared activities, the feature modules and the listing, and is **partial**. Visual design is deferred `[arcify]`.

## Original structure (observed)
- Main host activity (also the deep-link target). `[manifest]`
- Transparent overlays: block screen, Shorts block, permission steps, focus-tag picker, focus-timer overlay, accessibility explainer, block protection. `[manifest]`
- Subscription screen, image crop, alarm screen. `[manifest]`
- Feature modules: onboarding, focus timer (with group nudges), app limit, block shorts, block website, productive mode, app category, user theme, sign-in, invite flow, wall of love, impact banner, subscription, notification batching. `[static]`

## Arcify navigation `[arcify]`
```
Onboarding ─▶ Home (bottom nav)
              ├─ Focus      : mode picker → running session → summary (tag)
              ├─ Block      : App limits · Shorts & Reels (per app / IG surfaces / counters / block-after-N)
              │               · YouTube study list · Websites & browsers · Protection
              ├─ Schedule   : week calendar · add/edit session · daily goal
              ├─ Insights   : today · week · per-app detail
              └─ Settings   : permissions status · lists (reset/export/import) · Pomodoro · theme · about
Overlays (separate activities): BlockOverlay(reason), StrictFocusOverlay, PermissionGuideOverlay
Floating: TimerBubble, ReelCounterChip
Widgets: Usage, FocusStart, StudyGoal, StudyCalendar, Unlocks
```
