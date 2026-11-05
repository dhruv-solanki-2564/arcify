# Spec 00 — Overview

Sources: the public Play Store listing (retrieved 2026-11-05) and static analysis of the original's APK v73.6.2089 (M1). See [README](README.md) for the source tags and the clean-room rule.

| ID | Feature | Status | Spec |
|---|---|---|---|
| F1 | Focus timer: Timer / Stopwatch / Pomodoro, breaks, sounds, themes, tags | specified | [F01](F01-focus-timer.md) |
| F2 | Strict focus: block early exit, hide home screen | specified | [F02](F02-strict-focus.md) |
| F3 | App limits + strict lock + streaks | specified | [F03](F03-app-limits.md) |
| F4 | Shorts/Reels blocking + **Arcify extras** (IG per-surface toggles, counters, block-after-N) | specified | [F04](F04-shorts-reels.md) |
| F5 | YouTube Study Mode + **default lo-fi list** | specified | [F05](F05-youtube-study-mode.md) |
| F6 | Browser Study Mode + website/adult blocker | specified | [F06](F06-browser-websites.md) |
| F7 | Block protection: uninstall, floating window, split screen | specified | [F07](F07-block-protection.md) |
| F8 | Screen-time insights + widgets | specified | [F08](F08-insights.md) |
| F9 | Schedule planner + daily goal + streaks | specified | [F09](F09-schedule.md) |
| F10 | Nudges / motivation | specified | [F10](F10-nudges.md) |
| F11 | Multiplayer groups, leagues, reactions (deferred to M8) | specified (deferred) | [F11](F11-online.md) |
| F12 | Onboarding, permissions, settings | specified | [F12](F12-onboarding-settings.md) |
| F13 | *Candidate:* notification batching (in the original, not on its listing) | proposed, optional | [F10](F10-nudges.md) |
| F14 | *Candidate:* study wake-up alarm (in the original) | proposed, optional | [F10](F10-nudges.md) |

Cross-cutting: [blocking engine](blocking-engine.md) · [permissions](permissions.md) · [data model](data-model.md) · [screen map](screen-map.md)

## Key findings from M1
- The original is a **native Kotlin / Jetpack Compose** app, not Flutter or React Native.
- **One accessibility service** performs all in-app blocking. A separate foreground service tracks usage.
- **No VPN.** Website blocking reads the browser's address bar.
- **Shorts/Reels detection is structural.** It recognises the vertical column of interaction controls that short-video players draw, plus a few YouTube element IDs. This is what Arcify's detector, counters and Instagram surface logic build on.
- Home-screen hiding uses an optional **proxy launcher**. Uninstall friction uses **device admin** plus accessibility guards on installer, Play Store and system UI.
- Many lists and rules are **fetched from the original's server**: websites, suggested channels, app categories, tuning values. Arcify ships **local, editable defaults** instead.

## Dropped from the original
Subscriptions and paywalls, coins and rewards, ads and attribution, analytics, sign-in, the companion character and its art, promotional widgets and passes.
