# Arcify

**Arcify** is a free, open-source Android app for focus and digital wellbeing: focus timers, app blocking, Reels/Shorts blocking, screen-time insights and study schedules. It runs entirely on your device, and nothing leaves your phone.

> Status: **early development** (bootstrap). See [`AGENT_MEMORY.md`](AGENT_MEMORY.md) for live progress.

Arcify is an independent, clean-room implementation. Its features are inspired by existing focus apps. It is not affiliated with, endorsed by, or derived from the code or assets of any of them.

## Planned features
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
- **Later:** optional multiplayer focus rooms and leaderboards on a self-hostable backend.

Every list (apps, sites, channels, browsers, toggles) is fully customizable.

## Building
Requirements: JDK 17+ (21 recommended) and the Android SDK (platform 35+).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Signed release APKs will be published on the GitHub Releases page.

## License
[GPL-3.0](LICENSE)
