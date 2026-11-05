# Arcify functional spec

These files describe **what Arcify does**. They are written in our own words from:
- the public Play Store listing of the app that inspired the feature set,
- static analysis of that app's shipped APK (manifest, resource names, decompiled structure; see ADR 0001),
- our own design decisions.

Every statement carries a source tag:

| Tag | Meaning |
|---|---|
| `[listing]` | From the public Play Store description |
| `[manifest]` | Observed in the original's declared components/permissions/config |
| `[static]` | Observed in decompiled structure (class layout, constants, API calls). Not observed at runtime |
| `[inferred]` | Our reading of static facts; plausible but unverified |
| `[arcify]` | Our own design decision, which may differ from or go beyond the original |

No code, strings, artwork, sounds or config values from the original are reproduced here. Where the original fetches rules or lists from its server, Arcify ships local, user-editable defaults instead.

| File | Feature |
|---|---|
| [00-overview.md](00-overview.md) | Feature index |
| [F01-focus-timer.md](F01-focus-timer.md) | Timer / Stopwatch / Pomodoro |
| [F02-strict-focus.md](F02-strict-focus.md) | Strict focus, home-screen hiding |
| [F03-app-limits.md](F03-app-limits.md) | Daily app limits, strict lock, streaks |
| [F04-shorts-reels.md](F04-shorts-reels.md) | Shorts/Reels blocking, counters, IG surfaces |
| [F05-youtube-study-mode.md](F05-youtube-study-mode.md) | Channel allowlist |
| [F06-browser-websites.md](F06-browser-websites.md) | Browser study mode, website blocker |
| [F07-block-protection.md](F07-block-protection.md) | Uninstall / floating / split-screen guards |
| [F08-insights.md](F08-insights.md) | Screen-time insights, widgets |
| [F09-schedule.md](F09-schedule.md) | Schedule planner |
| [F10-nudges.md](F10-nudges.md) | Nudges, notification batching, wake alarm |
| [F11-online.md](F11-online.md) | Multiplayer (deferred) |
| [F12-onboarding-settings.md](F12-onboarding-settings.md) | Permissions walkthrough, settings |
| [blocking-engine.md](blocking-engine.md) | Shared accessibility/overlay architecture |
| [permissions.md](permissions.md) | Permission map |
| [data-model.md](data-model.md) | Entities |
| [screen-map.md](screen-map.md) | Screens and navigation |
