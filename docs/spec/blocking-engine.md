# Blocking engine (shared by F2–F7)

## What the original does
- **One accessibility service does all in-app blocking.** `[manifest]` It subscribes to every accessibility event type and requests window-content retrieval, interactive-window retrieval, view-ID reporting and inclusion of "not important" views. The event notification timeout is about half a second.
- **A separate foreground "monitor" service** tracks foreground app and usage time for limits and stats. `[manifest]` It runs with special-use foreground type and uses usage-stats access.
- **Blocking is shown as transparent, single-instance overlay activities** launched over the offending app. There's a general block screen (portrait and landscape variants), a Shorts/Reels block screen, and a block-protection screen. `[manifest]`
- **Floating bubbles** (draw-over-apps) show the running focus timer and the active blocks. `[manifest]`
- **Health checks.** Both services run a periodic heartbeat so the app can notice when Android has killed them. `[static]` The interval is a remotely tuned value.
- **Overlay memory.** After the user dismisses an overlay, a short "don't show again for N minutes" window applies. `[static]`
- **Rules come from the server.** Detection hints and lists (websites, suggested channels) are fetched and cached. `[static]`

## Arcify design `[arcify]`
- The `:blocker` module holds `ArcifyAccessibilityService`, which is the event router, plus pluggable **detectors**:
  - `ForegroundAppDetector`: package of the active window → app-limit / focus rules (F2, F3).
  - `ShortFormDetector`: per-app strategies (YouTube, Instagram, Snapchat, Facebook, browsers) → F4.
  - `YouTubeChannelDetector`: current channel on watch pages → F5.
  - `BrowserUrlDetector`: reads the address-bar node of supported browsers → F6.
  - `ProtectionDetector`: uninstall dialogs, Play Store app page for Arcify, accessibility/device-admin settings pages for Arcify, split-screen/floating windows → F7.
- Detectors are **pure functions over a snapshot** (`NodeSnapshot` tree: class, viewId, text, contentDescription, bounds, visible). Unit tests feed recorded snapshots, with no device needed.
- **Actions**: `GoBack`, `GoHome`, `ShowOverlay(reason)`, `CloseApp`, `CountItem(source, id)`.
- **Throttling.** Process at most one snapshot per window per ~300 ms. Skip events from Arcify's own package.
- **Heartbeat.** A WorkManager job checks that both services are alive. It nudges the user if accessibility was disabled.
- **All rules are local.** Every detector rule set ships as a bundled JSON default, is versioned, and can be overridden by the user. No server.
