# Arcify — Agent Memory

> **Read this first in every session. Update it before ending a session and commit it.**
> This file is the single source of truth for "where did we leave off".

## 1. Now
- **Current milestone:** M2 built and unit-tested (PR from `m2/skeleton-timer`) → **M3 next**: blocking engine + app limits + strict focus
- **Next step:** after the M2 PR merges, branch `m3/blocking-engine`. Add `ArcifyAccessibilityService` + detectors in `:blocker` (see `docs/spec/blocking-engine.md`), the usage monitor, the block overlay activity, and `AppLimit` rules + tests (F03). Then strict focus (F02: proxy launcher + allowed apps).
- **Not yet verified on a device:** the M2 APK hasn't been installed anywhere (no phone connected). First thing when a device or emulator is available: install `app-debug.apk`, then start, pause and stop a Pomodoro and check the notification actions.
- **Blockers:** none. Decide F13 (notification batching) / F14 (wake alarm) scope with the user before M6.
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
- [ ] **M2 Skeleton + Focus timer** (F1, F12) — code done, awaiting PR merge + on-device check
  - [x] Gradle 8.14.3 wrapper, AGP 8.12.0, Kotlin 2.2.10, Compose BOM 2025.08.00, Hilt 2.57, Room 2.7.2
  - [x] Modules `:app` (app.arcify), `:core`, `:blocker`; minSdk 26, compile/target 36
  - [x] `FocusTimer` pure state machine + `SessionRules` + `TimerController`; 17 unit tests passing
  - [x] Room DB (tag, focus_session; schema in `core/schemas/`), DataStore Pomodoro settings
  - [x] Foreground `FocusTimerService` + notification actions; Compose bottom nav + Focus screen; placeholders for other tabs
  - [x] `:blocker` `NodeSnapshot` model + 2 tests
  - [x] `assembleDebug` OK → `app/build/outputs/apk/debug/app-debug.apk` (12.4 MB, id `app.arcify.debug`)
  - [ ] Installed and checked on a device
- [ ] **M3 Blocking engine + App limits + Strict focus** (F2, F3)
- [ ] **M4 Content blocking** (F4 incl. Instagram per-surface toggles + reel/shorts counters + block-after-N, F5 default lo-fi study list, F6)
- [ ] **M5 Block protection** (F7)
- [ ] **M6 Insights + Schedule + Nudges** (F8, F9, F10)
- [ ] **M7 APK delivery** (signed release, GitHub Actions, Releases)
- [ ] **M8 (later) Online features** (F11, self-hostable backend behind `SyncRepository`)

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

## 4. Environment
- Repo: `C:\Users\Admin\Projects\arcify` · RE workspace: `C:\Users\Admin\Projects\arcify-re\` (apk/, evidence/, ledger.md)
- JDK for Gradle: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` (global JAVA_HOME points to a JRE 11 — override for builds)
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
