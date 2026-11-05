# Arcify

**Arcify** is a free, open-source Android app for focus and digital wellbeing: focus timers, app blocking, Reels/Shorts blocking, screen-time insights and study schedules. It runs entirely on your device, and nothing leaves your phone.

> Status: **early development**. The features below are built and being tested on a real phone. See [`AGENT_MEMORY.md`](AGENT_MEMORY.md) for live progress.

## Install
Download the latest `Arcify-<version>.apk` from the [Releases page](https://github.com/dhruv-solanki-2564/arcify/releases) and open it on your phone (Android 8.0+). Then open Arcify → Block and grant Accessibility and Usage access.

Arcify is an independent, clean-room implementation. Its features are inspired by existing focus apps. It is not affiliated with, endorsed by, or derived from the code or assets of any of them.

## Features
- **Focus timer:** Timer, Stopwatch and Pomodoro (short/long breaks), ambient sounds, tags.
- **Strict focus:** you can't exit early, and the home screen is hidden during deep focus.
- **App limits:** daily limits per app, with a Strict Mode that locks them until the period ends. Streaks.
- **Reels & Shorts blocking:** Instagram, YouTube, Snapchat, Facebook.
  - Instagram per-surface toggles: reels in **DMs**, **Home feed**, **Search/Explore**, **Reels tab**.
  - **Reel/Shorts counters** across the Instagram app, the YouTube app, Chrome and Brave.
  - **Block after N** reels/shorts per day.
- **YouTube Study Mode:** allow only your study channels. Ships with an editable lo-fi/study-music list.
- **Browser Study Mode & website blocker:** customizable allow/block lists and an adult-content filter.
- **Block protection:** uninstall prevention, floating-window and split-screen blocking.
- **Screen-time insights:** daily/weekly, productive vs distracted time, tag distribution.
- **Schedule planner:** repeating focus sessions, weekly calendar, streaks.
- **Notification batching:** hold notifications from chosen apps and get them together every 30 min to 3 h.
- **Study wake-up alarm:** turns off only by starting a focus session or solving a few sums, with limited snoozes.
- **Later:** optional multiplayer focus rooms and leaderboards on a self-hostable backend.

Every list (apps, sites, channels, browsers, toggles) is fully customizable.

## Building
Requirements: JDK 17+ (21 recommended) and the Android SDK (platform 36, build-tools 36.0.0).

```bash
./gradlew :core:testDebugUnitTest :blocker:testDebugUnitTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/` (app id `app.arcify.debug`). CI builds it for every push and pull request. Signed release APKs are built by GitHub Actions from version tags; see [docs/RELEASING.md](docs/RELEASING.md).

## Credits
The adult-site list comes from [StevenBlack/hosts](https://github.com/StevenBlack/hosts) (MIT). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License
[GPL-3.0](LICENSE)
