# F04 — Shorts & Reels blocking, counters, Instagram surfaces

## What the original does
- Blocks short-form video in **YouTube (Shorts), Instagram (Reels; also Instagram Lite), Snapchat (Spotlight), Facebook (Reels; also Facebook Lite)**. `[listing]` `[static]`
- **Detection is structural, not ID-based.** `[static]`
  - The service looks for the **vertical column of interaction controls** that short-video players render along one edge (the usual like / comment / share / remix style actions). It matches the accessibility text and content descriptions of those controls.
  - The matches are grouped into a "match key". It also checks where the cluster sits on screen; there's explicit handling when the player occupies the left half, i.e. split-screen.
- A few YouTube view IDs (mini-player, watch-panel scrim, logo, player controls) are used to tell regular video playback apart from Shorts. `[static]`
- On detection the original shows a Shorts-block overlay and/or navigates away. There are separate notification/overlay variants for a nudge and for strict mode. `[manifest]` `[static]`
- An optional **YouTube home-page diversion screen** exists, remotely toggled. `[static]`
- The original rewards blocking with in-app coins (gamification). `[static]` Arcify does not copy this; see the F10 streaks instead.

## Arcify features `[arcify]` — beyond the original
### 1. Instagram per-surface toggles
Four independent switches, each **Block** / **Allow**:

| Surface | How we tell it apart |
|---|---|
| **Reels tab** | The reels player is visible and the bottom navigation's reels tab is selected |
| **Home feed** | A reel is playing inline/fullscreen after navigation from the home tab |
| **Search / Explore** | The reel was opened from the explore grid or search results |
| **DMs** (reels friends send) | The reel was opened from a direct-message thread |

- Surface tracking keeps a short **navigation trail** per app (last N screens, classified by their structure). When a reel player appears, its surface is the most recent classified screen before it.
- If the surface can't be determined, the safest enabled rule applies (Block if any surface is blocked).
- Example: allow DMs and block the rest, so a friend's reel opens but swiping on into the endless feed triggers the block once the trail shows the user left the DM context.

### 2. Reel / Shorts counters
- Count each **distinct** short-form item viewed, per day, per source:
  - `instagram` (app), `youtube` (app), `chrome`, `brave`, plus any browser the user adds.
  - In browsers, detection uses the address bar (`instagram.com/reels/…`, `instagram.com/reel/…`, `youtube.com/shorts/…`, `m.youtube.com/shorts/…`) and counts URL changes.
  - In apps, an item change is detected when the player's content signature changes (caption/author text and bounds) after a scroll event.
- Dedupe: the same item re-entered within 10 s isn't counted twice.
- Displayed as a live chip in the notification, optional floating counter, daily history and charts in F08.
- Counting works even when the surface is **allowed**. That's the point: awareness without blocking.

### 3. Block after N
- Per source (and optionally per Instagram surface): "Allow up to **N** reels/shorts per day, then block".
- When the counter reaches N, the next item triggers the block overlay. That source/surface stays blocked until local midnight.
- With Strict Mode on, N can be lowered but not raised until the next day.

### 4. Customizable everything
- Which apps are monitored (package list, editable, including Lite and clone variants).
- Which browsers are monitored and their address-bar view IDs (editable advanced list, bundled defaults for Chrome and Brave).
- Detection rule set (bundled JSON: control labels per locale, minimum cluster size, edge position). Users can add labels for their language.

## Data
`ShortFormEvent(date, source, surface?, itemHash, at)`, `ShortFormRule(source, surface?, mode = BLOCK|ALLOW|LIMIT, limitN?)`, `MonitoredBrowser(package, urlBarIds[])`.

## Implementation (M4)
- **Rules** (pure, tested): `core/.../content/ShortForm.kt`.
  - `ShortFormPolicy`: a surface rule wins over the platform rule; an unknown Instagram surface uses the strictest Instagram rule.
  - Limit logic; strict locks only tighten.
  - `ShortFormDeduper`: an item counts once while it plays, and swiping straight back within 10 s doesn't recount.
- **Browser detection** (tested): `ShortFormUrls` recognises `instagram.com/reel(s)/…`, `youtube.com/shorts/…` and `facebook.com/reel(s)/…`. It reads the address bar via `MonitoredBrowser.urlBarIds` (defaults Chrome and Brave). Browser reels count as Instagram surface *unknown*.
- **In-app detection** (`blocker/.../content/Detectors.kt`):
  - `ShortFormDetector` looks for ≥3 interaction controls stacked in the right 28 % of a portrait screen. The item key is a hash of the caption/author text in the lower-left.
  - `IgSurfaceTracker` remembers the last non-player screen (selected bottom tab, or a DM thread with a message composer).
  - The heuristics are Arcify's own. **⚠️ Unverified on real apps.** The tests use hand-built layouts, not recordings.
- **Engine**: `ContentGuard` (tested) plus `ArcifyAccessibilityService`. Content, scroll and window events are read only for monitored packages, at most once every 300 ms. On a block the service presses Back and shows the block screen.
- **Storage**: Room v3. Tables `short_form_rule` (empty-string surface key = all surfaces) and `short_form_event` (per local day).
- **UI**: Block → Shorts & Reels.
  - Today's counts per app or browser.
  - Allow / Block / Limit (5–50 per day) per platform, with Instagram surface rows that can inherit the Instagram rule.
  - Lock for today.
- **Calibration aid**: a debug-only `SnapshotRecorder` saves redacted screen trees to `files/snapshots/`. Turn it on in the Shorts screen.
- **Not yet**:
  - Calibrating the detectors against recordings.
  - A live counter chip / floating counter.
  - User-editable detector labels per language.
  - JSON import/export of rules.
