# Spec 00 — Overview

Source so far: the public Play Store listing of `ai.regainapp` (retrieved 2026-11-05). This will be refined with APK analysis in M1. Each feature gets its own spec file `Fxx-*.md`.

| ID | Feature | Status | Spec file |
|---|---|---|---|
| F1 | Focus timer: Timer / Stopwatch / Pomodoro, breaks, sounds, themes, tags | listing only | — |
| F2 | Strict focus: block early exit, hide home screen | listing only | — |
| F3 | App limits + Strict Mode lock + streaks | listing only | — |
| F4 | Reels/Shorts blocking + **Arcify extras** (IG per-surface toggles, counters, block-after-N) | listing only | — |
| F5 | YouTube Study Mode (channel allowlist) + **default lo-fi list** | listing only | — |
| F6 | Browser Study Mode + website/adult blocker | listing only | — |
| F7 | Block protection: uninstall, floating window, split screen | listing only | — |
| F8 | Screen-time insights | listing only | — |
| F9 | Schedule planner + streaks | listing only | — |
| F10 | Nudges / motivation | listing only | — |
| F11 | Multiplayer rooms, friends, leaderboards (deferred to M8) | listing only | — |
| F12 | Onboarding, permissions, settings | listing only | — |

## Listing facts (paraphrased)
- Positioned for students. The headline claims include reduced screen time, blocking distracting apps and studying with friends.
- Accessibility Service is declared for detecting and intervening in user-selected app features, such as Shorts blocking. The listing states that this data stays on the device.
- The data-safety section says no data is shared with third parties. Collected types are app activity, app info/performance and device IDs.
- The original has in-app purchases. Arcify does not.

## Arcify-only additions
See the plan section "Arcify additions to F4/F5". Summary:
- Instagram: separate block/allow toggles for reels opened from DMs, the Home feed, Search/Explore and the Reels tab.
- Reel and Shorts counters per day for the Instagram app, the YouTube app, Chrome and Brave.
- Optional "block after N reels/shorts today".
- A default YouTube study allowlist of well-known lo-fi/study-music channels, editable and resettable.
- Every list is customizable, with reset to defaults and JSON import/export.
