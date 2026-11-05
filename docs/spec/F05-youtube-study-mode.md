# F05 — YouTube Study Mode

## What the original does
- During focus, YouTube is usable **only for channels on the user's study list**. Other channels and Shorts are blocked. `[listing]`
- It's part of a "productive mode" module. That module has a channel allowlist, **suggested channels** and **channel search**, all fetched from the original's server. `[static]`
- An overlay/floating button for productive mode exists. `[manifest]`
- The number of study channels may be capped for free users. `[static]` Arcify has no cap.

## Arcify design `[arcify]`
- `StudyChannel(channelId?, handle?, displayName, addedAt, isDefault)`.
- **Default list**: well-known lo-fi / study-music channels, shipped as a bundled JSON. Initial set, to be verified by handle and channel ID during M4:
  - Lofi Girl
  - Chillhop Music
  - College Music
  - Lofi Records
  - The Jazz Hop Café
  - Chillhop Raccoon
  - STEEZYASFUCK
  - Homework Radio
- The list is **fully editable**: add by handle, URL or name; remove; reorder; "reset to defaults"; import/export JSON.
- Detection: on a YouTube watch page, read the channel name/handle node under the player. If it's not in the list and Study Mode is on, go back and show the overlay. Shorts stay governed by F04.
- Matching is case-insensitive on handle first, then display name.
- No network calls: adding a channel stores what the user typed. Optional verification happens on-device the next time that channel is seen.

## Implementation (M4)
- **Active** during focus phases of any session (strict or not) while the switch is on. During strict focus it also keeps YouTube itself usable, since its content is policed instead. Shorts are blocked while it's active.
- **Matching** (`StudyChannelPolicy`, tested): a compact form (lower case, letters and digits only), so "Lofi Girl" == "@LofiGirl". Input accepts a name, an @handle or a `youtube.com/@handle…` link.
- **Defaults** are seeded once on first run (`study_channel` table); "Reset to defaults" restores them. Handles for Lofi Records and Chillhop Raccoon are unknown, and **all handles are unverified**.
- **Channel detection** on watch pages (`YouTubeChannelDetector`) uses guessed hints (view-ID fragments, a "go to channel" description prefix). **⚠️ Unverified.** It fails open: if no channel is found, nothing is blocked.
