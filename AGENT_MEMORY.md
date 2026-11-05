# Arcify — Agent Memory

> **Read this first in every session. Update it before ending a session and commit it.**
> This file is the single source of truth for "where did we leave off".

## 1. Now
- **Current milestone:** M6b code done on branch `m6b/batching-alarm-adult` (PR open, not merged): adult-site filter (StevenBlack, MIT), F13 notification batching, F14 study wake-up alarm. 131 unit tests pass and the debug APK builds. **Not yet installed on the phone** (it wasn't connected).
- **Next step:** install the M6b APK and check it on the phone:
  1. **Adult filter:** turn it on under Block → Websites & browsers and open a listed site in Chrome; it should be blocked. Add an exception and check it opens. Lock for today: the switch can't be turned off.
  2. **Wake alarm:** add one 2 minutes ahead in Schedule. Lock the phone. It should ring over the lock screen. Check snooze (count goes down), the sums, and Start focus.
  3. **Batching:** grant notification access, add an app (e.g. WhatsApp), choose 30 min. Its notifications should disappear and come back as one summary, or with "Deliver now". Calls must still ring.
  Then merge the PR and start M7 (signed release APK + GitHub Actions).
- **Still to check on the phone later:** split screen, home hiding (proxy launcher), schedule alarms after reboot, the widget, Insights over several days, Snapchat/Facebook detectors.
- **Old checklist (pre-device, kept for reference; mostly done) — when a phone is connected (M4b, calibration):**
  1. Install the debug APK, grant Accessibility + Usage access.
  2. Block → Shorts & Reels → turn on "Detector calibration".
  3. Use Instagram (Home, Explore, Reels tab, a DM thread, a reel opened from a DM), YouTube (Shorts, a normal video, a channel page) and Chrome/Brave (instagram.com/reels, youtube.com/shorts).
  4. Pull the recordings: `adb exec-out run-as app.arcify.debug tar c files/snapshots > snapshots.tar`. Review them (text is redacted, but descriptions remain) and turn the good ones into fixtures under `blocker/src/test/resources/fixtures/`.
  5. Tune `DetectorDefaults` (control words, IG tab labels and DM markers, YouTube channel hints) and the Chrome/Brave `url_bar` IDs until the fixture tests pass. Then turn the recorder off.
  6. **M5 checks on the phone**:
     - Set protection to "Always". Try the uninstall dialog, Settings → Apps → Arcify, Settings → Accessibility → Arcify blocker, and the device admin page; each should go Home with "Protection is on". Record these screens too.
     - Split screen with a blocked app.
     - Home hiding: choose a launcher, switch it on, set Arcify as the default home app, start a strict session and press Home (focus screen); during a break, Home opens the real launcher.
     - Emergency exit with the phrase.
  7. **M6 checks on the phone**: Insights after a day of use (screen time, unlocks, categories); schedule a session 2 minutes ahead (notification, Start now; strict auto-start); reboot and check the alarm still fires; add the widget and tap Start focus.
- **If no phone:** M6b can go ahead.
- **Decisions made (2026-11-05):** build F13 + F14; fetch and bundle the StevenBlack adult-only hosts list (MIT, with credit), with entries the user can switch off.
- **Not yet verified on a device (M2 + M3 + M4):** nothing has been installed anywhere (no phone connected). When a device is available:
  1. Install `app-debug.apk`.
  2. Timer: start, pause and stop a Pomodoro, and check the notification actions.
  3. Block tab: grant Accessibility + Usage access, add a 15-minute limit, use the app until blocked, try "Lock for today".
  4. Strict Pomodoro: open a non-allowed app, check it's blocked; press Home, check the focus screen returns.
  5. Chrome: open youtube.com/shorts/…, check the count appears in Block → Shorts & Reels; add a "Limit 3" rule and swipe past 3; add a blocked site.
- **For M4:** record real Instagram/YouTube/Chrome/Brave accessibility node trees on a device (needed for IG surfaces + address-bar IDs; the original has no IG-surface logic to learn from).
- **GitHub:** https://github.com/dhruv-solanki-2564/arcify (public, `main`)
- **APK under study:** `Regain-Study-Timer-for-Focus_73.6.2089_apkcube.apks` (v73.6.2089). It's a split bundle: `base.apk` (67.7 MB) + `split_config.arm64_v8a` + density splits. Copied to `arcify-re\apk\`.

## 2. Milestone checklist
- [x] **M0 Bootstrap**
  - [x] Identify target: `ai.regainapp` — "Regain: Study Timer for Focus", EpowerX Labs
  - [x] Install GitHub CLI (winget, v2.103.3)
  - [x] Android SDK cmdline-tools 16111833 (`%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`)
  - [x] SDK license accepted (user OK 2026-11-05); `platforms/android-36` 2.0.0, `build-tools/36.0.0`, `platform-tools` 37.0.1 installed
  - [x] JADX 1.5.6 (`C:\Users\Admin\Tools\jadx-1.5.6\bin\jadx.bat`)
  - [x] Local repo scaffold (`C:\Users\Admin\Projects\arcify`), GPL-3.0 LICENSE
  - [x] git identity: Dhruv Solanki <dhruv2004solanki@gmail.com> (repo-local)
  - [x] GitHub repo created and pushed: dhruv-solanki-2564/arcify (gh logged in as dhruv-solanki-2564)
- [x] **M1 RE → spec** (F1–F12 spec files, screen map, data model, permissions, ledger)
  - [x] Signing + stack identified: native Kotlin (likely Compose), not Flutter/RN
  - [x] Manifest analysed: components, permissions, accessibility config, device admin, launcher proxy (→ ledger)
  - [x] Per-DEX decompile of app code (classes, classes3, classes4; classes4 partial)
  - [x] Trace mechanisms per feature (Shorts detection, protection guards, browser, remote config, c68)
  - [x] Write docs/spec: README, F01–F12, blocking-engine, permissions, data-model, screen-map
- [x] **M2 Skeleton + Focus timer** (F1, F12): merged in PR #2 (925ae15); on-device check still pending
  - [x] Gradle 8.14.3 wrapper, AGP 8.12.0, Kotlin 2.2.10, Compose BOM 2025.08.00, Hilt 2.57, Room 2.7.2
  - [x] Modules `:app` (app.arcify), `:core`, `:blocker`; minSdk 26, compile/target 36
  - [x] `FocusTimer` pure state machine + `SessionRules` + `TimerController`; 17 unit tests passing
  - [x] Room DB (tag, focus_session; schema in `core/schemas/`), DataStore Pomodoro settings
  - [x] Foreground `FocusTimerService` + notification actions; Compose bottom nav + Focus screen; placeholders for other tabs
  - [x] `:blocker` `NodeSnapshot` model + 2 tests
  - [x] `assembleDebug` OK → `app/build/outputs/apk/debug/app-debug.apk` (12.4 MB, id `app.arcify.debug`)
  - [ ] Installed and checked on a device
- [x] **M3 Blocking engine + App limits + Strict focus** (F2, F3): merged in PR #3 (27e1912); on-device check pending
  - [x] `:core`:
    - `LimitRules` (status, strict-only-tighter), `BlockPolicy` (allow / limit / strict / return-to-focus), `UsageAggregator` (activity events → foreground time).
    - Room v2 with an auto-migration: `app_limit`, `strict_allowed_app`.
    - `BlockRepository`.
  - [x] `:blocker`:
    - `ArcifyAccessibilityService` (window-state events, 15 s re-check, strict start/end).
    - `UsageReader`, `AppCatalog` (launchable/home/always-allowed apps, activity filter), `BlockerPermissions`, `BlockScreenLauncher` interface.
  - [x] `:app`:
    - `BlockOverlayActivity` (limit / strict screens) and `AppBlockScreenLauncher` (Hilt binding).
    - Limit-reminder notifications.
    - Block tab: permissions, limits with lock-for-today, allowed apps with reset.
  - [x] 38 unit tests passing; `assembleDebug` OK (12.8 MB)
  - [ ] Installed and checked on a device
  - Deferred to M5: proxy launcher (`ProxyHomeActivity`) + hard-exit phrase. Deferred to M6: limit streaks, top-3 suggestions
- [x] **M4 Content blocking** (F4 incl. Instagram per-surface toggles + reel/shorts counters + block-after-N, F5 default lo-fi study list, F6): merged in PR #4 (d8eb4b2); calibration pending
  - [x] `:core` rules:
    - `ShortFormPolicy` (surface overrides, strictest-for-unknown, limit, strict-only-tighter), `ShortFormDeduper`.
    - `UrlNormalizer`/`ShortFormUrls`/`WebsitePolicy`, `StudyChannelPolicy` (compact matching, input parsing), `StudyModes`.
  - [x] Room v3: `short_form_rule`, `short_form_event`, `study_channel`, `website_rule`, `monitored_browser`. `ContentRepository` with one-time default seeding, strict locks, study switches, recorder flag.
  - [x] `:blocker`:
    - `NodeSnapshot` (+selected/editable/hint), `NodeSnapshots` capture.
    - `ShortFormDetector`, `IgSurfaceTracker`, `BrowserUrlDetector`, `YouTubeChannelDetector`, `DetectorDefaults`.
    - `ContentGuard`, `SnapshotJson`, debug `SnapshotRecorder`.
    - The service reads content for monitored packages (300 ms throttle). Study modes keep YouTube/browsers usable in strict focus.
  - [x] `:app`:
    - Block hub with 4 sections.
    - Shorts & Reels screen: counts, rules, IG surfaces, lock, recorder switch.
    - YouTube study screen; Websites & browsers screen.
    - Block screen: 4 new content reasons.
  - [x] 74 unit tests passing; `assembleDebug` OK (12.7 MB), no warnings
  - [ ] **Calibrate detectors with recordings from a real phone** (IG, YouTube, Snapchat, FB, Chrome/Brave IDs)
  - [ ] Adult-site list (needs the user's OK to fetch a third-party list), live counter chip, editable detector labels, JSON export
- [x] **M5 Block protection** (F7, plus the F02 proxy launcher and emergency exit): merged in PR #5 (5d6e47b); device checks pending
  - [x] `:core`: `ProtectionLevel`/`Commitments`/`ProtectionRules`, `HardExit`, `HomeRouting`, `FocusTimer.abandon` + `TimerController.abandon`; `CommitmentTracker`, `ProtectionRepository`
  - [x] `:blocker`: `ProtectionDetector` (installer / settings / Play Store), `ArcifyDeviceAdminReceiver` + `DeviceAdmin`. The service now:
    - closes protected screens (Home plus a block screen) while protection is active;
    - checks every app window (split screen, floating);
    - adds `typeWindowsChanged` + `flagRetrieveInteractiveWindows` to the config.
  - [x] `:app`: `ProxyHomeActivity` (ships disabled) + `LauncherChooserActivity` + `HomeSetup`; Protection screen; emergency exit on the Focus screen; PROTECTION block reason
  - [x] 88 unit tests passing; `assembleDebug` OK (13.2 MB), no warnings
  - [ ] Device checks: guards on several OEM settings apps, split screen, home hiding, device admin
- [ ] **M6 Insights + Schedule + Nudges** (F8, F9, F10): code done, awaiting PR merge + device checks
  - [x] `:core`: `UsageAggregator.stats` (launches), `InsightRules`, `StreakRules`, `ScheduleRules`, `NudgeRules`; Room v4 (`usage_day`, `unlock_day`, `app_category`, `scheduled_session`); `InsightsRepository`, `ScheduleRepository`; goal, nudges and last-sent dates in `TimerSettingsRepository`
  - [x] `:blocker`: `UsageReader.usage()` (apps + unlocks), `AppCatalog.defaultCategory`, `UsageHistorySync`
  - [x] `:app`:
    - `ArcifyWorker` (hourly: sync, nudges, widget) + `AppEntryPoint`.
    - `NudgeRunner`.
    - `ScheduleAlarms`, `ScheduledStarter`, alarm/start/re-arm receivers.
    - `TodayWidget` (Glance).
    - Insights and Schedule tabs; goal on the Focus screen.
  - [x] 100 unit tests passing; `assembleDebug` OK (14.8 MB)
  - [ ] Device checks (see "Now")
- [ ] **M6b** F13 notification batching, F14 study wake alarm, adult-site list: code done, PR open, device check pending
  - [x] Adult filter: `tools/build_adult_list.js`, `adult_domains.txt.gz` asset (64,297 domains), `AdultFilter` rules (parent-domain match, exceptions, lock for today), `AdultDomains` loader, checked first in `ContentGuard.inspectBrowser`, UI section on the Websites screen, counts as a commitment
  - [x] Room v5 (auto-migration): `wake_alarm`, `held_notification`; `WakeAlarmRepository`, `BatchingRepository`
  - [x] F14: `WakeAlarmRules`, `WakeTask`; `WakeAlarms` (setAlarmClock), `WakeAlarmReceiver`, `WakeAlarmService` (alarm-stream sound + vibration, 10 min cap), `WakeAlarmActivity` (lock screen; focus / snooze / sums); Schedule-tab section and dialog
  - [x] F13: `BatchingRules`, `ArcifyNotificationListener`, `BatchDelivery` + receiver; Block → Notification batching screen
  - [x] 131 unit tests passing; `assembleDebug` OK (15.7 MB)
  - [ ] Installed and checked on the phone
- [ ] **M7 APK delivery** (signed release, GitHub Actions, Releases)
- [ ] **M8 (later) Online features** (F11, self-hostable backend behind `SyncRepository`)

## 2b. Device testing (realme RMX3840, Android 16 / API 36, realme UI)
- **Round 1 (2026-11-05)** on branch `fix/device-round1`:
  - Timer, app limits and Chrome website blocks worked.
  - Strict focus blocked allowed apps; Shorts/Reels detection failed; realme's launcher uninstalled Arcify despite device admin; insights showed nothing.
- **Round 2 fixes:**
  - Settings was treated as a launcher (its `FallbackHome`, priority < 0, has the HOME category). Arcify inspected its own screens, so the uninstall guard fired on "Uninstall/Cancel" text in Arcify, causing flash loops.
  - Stray launcher window events stole "foreground"; now a launcher must own the active window.
  - Added per-app action cool-downs (2 s) and a block-screen repeat guard (1.5 s, no animation).
  - Instagram surfaces are now based on real IDs. An unknown surface uses the Instagram-wide rule, not the strictest one.
  - Recorder redacts long hints and descriptions.
- **Verified facts (from `uiautomator dump` and recordings; raw data never committed):**
  - Chrome address bar: `com.android.chrome:id/url_bar`, text like `reddit.com`, `m.youtube.com/shorts/<id>`.
  - YouTube Shorts: `reel_play_pause_button`, `reel_next_reel_button`; bottom "Shorts" tab selected; the like column isn't always present.
  - YouTube long-form: `watch_player`; the like row is horizontal; channel comes from a "Subscribe to <name>." description.
  - Instagram reel viewer: `clips_viewer…`, visible only while a reel is on screen (hidden but present on other tabs).
  - Instagram bottom tabs: `tab_bar` with selected `feed_tab` / `search_tab` / `clips_tab` / `direct_tab`. A stray off-screen "Reels" element is always selected; ignore off-screen bounds.
  - Instagram DM: thread IDs `direct_thread_header`, `thread_view_root`, `message_thread_container`, `reel_share_item_view`. A reel opened from a DM is a full-screen viewer with no tab bar and a "Reply to …" box; swiping on removes the box (suggested reels).
  - realme launcher `com.android.launcher` shows its own uninstall dialog and removes apps itself (`uninstallSource = com.android.launcher`).
- **Tools:**
  - Engine logs: `adb logcat -s Arcify`.
  - Replay recordings: set `ARCIFY_RECORDINGS=<folder>` and run `gradlew :blocker:testDebugUnitTest --tests *RecordedScreensTest* -i`.
  - Pull recordings: `adb exec-out run-as app.arcify.debug tar c files/snapshots`.
- **Round 3 (2026-11-05), confirmed working by the user:**
  - Foreground = owner of the active application window, re-checked before acting. Blinkit/Amazon were kicked back to focus because their screens were never recognised.
  - Instagram DMs set to Allow permit only the reel opened from the chat; swiping on gives `DmScroll`.
  - DM reply hint narrowed to "reply to".
  - Content blocks show an accessibility-overlay banner (`BlockBanner`) instead of the full-screen page, so there's no flash and no feed refresh.
  - Reels tab is left by tapping `feed_tab`.
  - `BlockScreenLauncher.showContentBlock` was removed. `BlockOverlayActivity`'s content reasons still exist but are unused.
- **Known gaps (not fixed yet):**
  - Changing the system clock can end time-based locks early.
  - Safe mode / adb can always remove the app.
  - OEM battery managers can kill the accessibility service (no heartbeat/re-enable nudge yet).
  - Insights text rendering ("smudged") is left for the UI pass.

## 3. Decisions log
| Date | Decision | Why |
|---|---|---|
| 2026-11-05 | Target = `ai.regainapp` (EpowerX Labs) | Top "Regain" result, 50L+ installs, matches user intent |
| 2026-11-05 | Name **Arcify**; no Regain name/logo/"Rega"/assets/strings | Trademark & copyright — clean-room rebuild (see docs/adr/0001) |
| 2026-11-05 | Kotlin + Jetpack Compose, native Android | Features depend on AccessibilityService, UsageStats, overlays, DeviceAdmin |
| 2026-11-05 | Offline-first; multiplayer/leaderboard deferred to M8 | User wants a local app first |
| 2026-11-05 | License GPL-3.0 | Keeps forks open source (user may switch to MIT) |
| 2026-11-05 | UI/visual design deferred; placeholder Material 3 with theme tokens | User will decide UI later |
| 2026-11-05 | Extra features beyond original: IG per-surface reel toggles (DM/feed/search-explore/Reels tab), reel+shorts counters (IG, YT, Chrome, Brave), block-after-N, default editable lo-fi YouTube study list, every list customizable (Room + reset + JSON import/export) | User request |
| 2026-11-05 | APK supplied by user as a file; RE artifacts live outside repo in `C:\Users\Admin\Projects\arcify-re\` | Never commit third-party binaries/decompiled code |
| 2026-11-05 | Timer uses wall-clock timestamps, not tick counting; running session in memory only (kept alive by the FGS) | Simple and drift-free; persisting across process death is a later hardening step |
| 2026-11-05 | Strict: no pause, no stop during focus; Pomodoro breaks can be stopped; stopwatch never strict | Spec F01/F02 |
| 2026-11-05 | Debug build uses `applicationIdSuffix .debug` | Lets a debug and a future release build coexist on one phone |
| 2026-11-05 | Strict focus in M3 uses the accessibility path (Home → back to the focus screen); the proxy launcher moves to M5 | A wrong launcher setup can strand the user without a home screen; it belongs with the protection work |
| 2026-11-05 | Only strict sessions block apps, and only during focus phases (breaks are free) | Matches the listing's wording; non-strict sessions stay a plain timer |
| 2026-11-05 | `:blocker` depends on `:core` (api); UI hooks go through the `BlockScreenLauncher` interface bound in `:app` | Engine stays free of screens; detectors stay testable |
| 2026-11-05 | `QUERY_ALL_PACKAGES` instead of `<queries>` | Needed to tell real app screens from dialogs for any app; sideloaded APK, so no Play policy issue |
| 2026-11-05 | M4 built without a phone: rules and browser-URL detection are final; in-app detectors are own heuristics, marked unverified, with a debug recorder for calibration | User chose to start M4 before connecting a phone |
| 2026-11-05 | Study modes apply during focus phases of any session (not only strict) and keep YouTube/browsers usable in strict focus | Matches the "during focus" wording in the listing |
| 2026-11-05 | No rule = allowed but counted; YouTube channel detection fails open | Counting gives awareness by default; a detector miss must not lock users out of YouTube |
| 2026-11-05 | Detector defaults and seed lists live in Kotlin (`ContentDefaults`, `DetectorDefaults`); JSON import/export later with Settings | Keeps M4 small and unit-testable; CLAUDE.md rule 7 export still owed |
| 2026-11-05 | Protection default = "While committed"; a strict session commits for its whole length (breaks included) | Breaks mustn't become an escape window; "Always" stays opt-in |
| 2026-11-05 | Proxy home activity ships `enabled=false`, toggled with `setComponentEnabledSetting` | Declaring HOME would otherwise make Android ask "which home app?" for every user |
| 2026-11-05 | Split-screen/floating checks apply whenever an app would be blocked, not only with protection on | Otherwise split screen would bypass limits and strict focus |
| 2026-11-05 | Protection responds with Home (not Back) | Back from a Settings page often lands on another guarded page |
| 2026-11-05 | Emergency-exit phrase: "I am choosing to end my focus session early" | Long enough to make breaking a session deliberate; own wording |
| 2026-11-05 | M6 split: insights/schedule/goal/streaks/nudges/widget first; F13, F14 and the adult list as M6b | Keeps each PR reviewable |
| 2026-11-05 | Streaks computed from stored history instead of a `StreakDay` table | No duplicated state; always consistent with the data |
| 2026-11-05 | Charts with plain Compose layouts instead of Vico; WorkManager + Glance added | Fewer dependencies; WorkManager is in the agreed stack |
| 2026-11-05 | Adult list is checked before website rules and study mode; exceptions can't be added while locked, but can always be removed | Removing an exception only makes it stricter |
| 2026-11-05 | Batching never holds calls, alarms, reminders, navigation, transport, ongoing or Arcify's own notifications; switching batching off delivers what's held | Batching must never hide something urgent or lose a message |
| 2026-11-05 | Batches are delivered on fixed slots from local midnight (every 30 min / 1 / 2 / 3 h) with an inexact, idle-allowed alarm | Predictable times; no exact-alarm need for a non-urgent summary |
| 2026-11-05 | Wake alarm: only the earliest alarm is armed (setAlarmClock); off only via focus, limited snooze, or three two-digit sums; Back disabled; stops after 10 min | One armed alarm keeps re-arming simple; the 10-min cap avoids ringing forever if the phone is away |
| 2026-11-05 | Receivers, worker and widget get dependencies through a Hilt `@EntryPoint` | `@AndroidEntryPoint` receivers need `super.onReceive` on an abstract method; entry points avoid that |

## 4. Environment
- Repo: `C:\Users\Admin\Projects\arcify` · RE workspace: `C:\Users\Admin\Projects\arcify-re\` (apk/, evidence/, ledger.md)
- JDK for Gradle: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` (global JAVA_HOME points to a JRE 11 — override for builds; with `cmd /c` use `set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot&& …`, with no space before `&&`)
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools 23.0.0 in `cmdline-tools\latest`). The new CLI uses **slash** package names: `sdkmanager platforms/android-36 build-tools/36.0.0` (semicolon names silently do nothing). Run it through `cmd /c` from PowerShell.
- Machine: 7.7 GB RAM, 8 cores. Keep JVM heaps ≤3 GB (JADX with `-Xmx6g` on the whole APK stalled at 32%). Decompile per DEX instead.
- REA's Android tools need `REA_JADX_MCP_JAR` (jadx-headless-mcp 0.7.1) and apktool; neither is configured, so use the JADX CLI.
- adb: winget Google.PlatformTools (on PATH)
- JADX: `C:\Users\Admin\Tools\jadx-1.5.6\bin`
- REA MCP server available (Android tools: inspect_android_package, decode_android_resources, search_android_classes, trace_android_references, …)
- **Build** (PowerShell; `cmd` doesn't inherit the PowerShell location, so use absolute paths):
  `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot'; cmd /c "cd /d C:\Users\Admin\Projects\arcify && gradlew.bat :core:testDebugUnitTest :blocker:testDebugUnitTest :app:assembleDebug --console=plain"`
  The first full build took about 11 min (downloads); incremental builds take about 25 s.
- **Gradle wrapper quirk on this machine:** Java gets `SSLException: Unsupported or unrecognized SSL message` when following the services.gradle.org redirect (Maven downloads work; PowerShell downloads work).
  - Workaround: the wrapper cache was seeded by copying `gradle-8.14.3-bin.zip` into `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\`.
  - The wrapper was generated with `--no-validate-url` and `networkTimeout=60000`.
  - A standalone Gradle is also installed at `C:\Users\Admin\Tools\gradle-8.14.3`.
  - CI isn't affected.
- `local.properties` (git-ignored) holds `sdk.dir`.
- Hilt gotcha: every module that applies the Hilt Gradle plugin needs its own direct `implementation(libs.hilt.android)`. Getting it through `api` from `:core` doesn't count.
- Windows gotcha: "Couldn't delete …\R.jar" means a stale Gradle daemon holds a lock. Run `gradlew --stop`, then rebuild.

## 5. RE status
- Spec files done: all (`docs/spec/`). Each fact is tagged `[listing]`/`[manifest]`/`[static]`/`[inferred]`/`[arcify]`.
- Resolved:
  - Stack: native Kotlin.
  - Website blocking: accessibility URL reading (no VPN).
  - Home hiding: proxy launcher.
  - Uninstall friction: device admin + accessibility guards on installer, Play Store and system UI.
  - Shorts/Reels: structural detection of the interaction-control column + screen position.
  - c68: wake alarm.
- Open (needs a device, M4): Instagram surface signatures (DM/feed/explore/Reels tab) and Chrome/Brave address-bar view IDs.
- Candidates for user decision: F13 notification batching, F14 study wake alarm.
- Ledger: `C:\Users\Admin\Projects\arcify-re\ledger.md`

## 6. Session log
- 2026-11-05 — Session 1: identified target, planned milestones, installed gh, scaffolded repo + memory file, created GitHub repo, installed cmdline-tools, received APKS v73.6.2089. Commits c29f323, b7a277d.
- 2026-11-05 — Session 1 (cont.): user OK'd SDK license; installed platform 36 + build-tools 36. Started M1 on branch `m1/re-spec`: signing, stack and manifest analysed; first whole-APK JADX run stalled (RAM), restarted per DEX.
- 2026-11-05 — Session 1 (cont.): per-DEX decompile done; traced the blocking mechanisms; wrote the full spec set (17 files); M1 complete, PR opened.
- 2026-11-05 — Session 1 (cont.): PR #1 merged (f9b9914). M2 on `m2/skeleton-timer`: Gradle project, timer engine + 19 tests passing, foreground timer service, Compose UI; debug APK builds. Hit and worked around the wrapper SSL issue. Not yet tried on a device.
- 2026-11-05 — Session 1 (cont.): PR #2 merged (925ae15). M3 on `m3/blocking-engine`: limit/block/usage rules, Room v2, accessibility service, block screen, Block tab; 38 tests passing; APK builds. Specs F02/F03 updated. Not yet tried on a device.
- 2026-11-05 — Session 1 (cont.): PR #3 merged (27e1912). M4 on `m4/content-blocking`: short-form/website/study rules, Room v3, detectors + ContentGuard + recorder, Block hub with 3 new screens; 74 tests passing; APK builds. In-app detectors unverified (no phone yet).
- 2026-11-05 — Session 1 (cont.): PR #4 merged (d8eb4b2). M5 on `m5/protection`: protection levels + commitments, protection detector, device admin, window checks, proxy home + chooser, emergency exit, Protection screen; 88 tests passing; APK builds. Not yet tried on a device.
- 2026-11-05 — Session 1 (cont.): PR #5 merged (5d6e47b). The user chose F13 + F14 + the StevenBlack list (→ M6b). M6 on `m6/insights-schedule`: usage history, insights tab, streaks, schedule + alarms, daily goal, nudges, Today widget; 100 tests passing; APK builds.
- 2026-11-05 — Session 1 (cont.): first phone tests on realme RMX3840 / Android 16. Three fix rounds on `fix/device-round1` (687dfd9, 9964477, 92621e1) driven by logcat, `uiautomator` dumps and recordings; 115 tests passing; the user confirmed everything tested works. Merged PR #6 and the fix PR into `main`. Next: M6b.
- 2026-11-05 — Session 1 (cont.): M6b on `m6b/batching-alarm-adult`: adult-site filter (StevenBlack list bundled), F14 wake-up alarm, F13 notification batching, Room v5; 131 tests passing; APK builds. Not yet on the phone.
