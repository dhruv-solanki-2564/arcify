# Arcify — Agent Memory

> **Read this first in every session. Update it before ending a session and commit it.**
> This file is the single source of truth for "where did we leave off".

## 1. Now
- **Current milestone:** M0 almost done → M1 next
- **Next step:** (a) once the user OKs it, accept the Android SDK licenses and install `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`. (b) Start M1: extract `base.apk` from the APKS in `arcify-re\apk\` and run REA (`inspect_apk_signature` → `inspect_android_package` → `decode_android_resources` → stack check → per-feature traces).
- **Blockers:** the SDK license acceptance needs the user's explicit OK.
- **GitHub:** https://github.com/dhruv-solanki-2564/arcify (public, `main`)
- **APK under study:** `Regain-Study-Timer-for-Focus_73.6.2089_apkcube.apks` (v73.6.2089). It's a split bundle: `base.apk` (67.7 MB) + `split_config.arm64_v8a` + density splits. Copied to `arcify-re\apk\`.

## 2. Milestone checklist
- [ ] **M0 Bootstrap**
  - [x] Identify target: `ai.regainapp` — "Regain: Study Timer for Focus", EpowerX Labs
  - [x] Install GitHub CLI (winget, v2.103.3)
  - [x] Android SDK cmdline-tools 16111833 (`%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`)
  - [ ] SDK licenses + platform/build-tools (awaiting user OK)
  - [ ] JADX 1.5.6 (`C:\Users\Admin\Tools\jadx-1.5.6`); download was in flight at the end of session 1, so verify `bin\jadx.bat` exists
  - [x] Local repo scaffold (`C:\Users\Admin\Projects\arcify`), GPL-3.0 LICENSE
  - [x] git identity: Dhruv Solanki <dhruv2004solanki@gmail.com> (repo-local)
  - [x] GitHub repo created and pushed: dhruv-solanki-2564/arcify (gh logged in as dhruv-solanki-2564)
- [ ] **M1 RE → spec** (F1–F12 spec files, screen map, data model, permissions, ledger)
- [ ] **M2 Skeleton + Focus timer** (F1, F12)
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

## 4. Environment
- Repo: `C:\Users\Admin\Projects\arcify` · RE workspace: `C:\Users\Admin\Projects\arcify-re\` (apk/, evidence/, ledger.md)
- JDK for Gradle: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` (global JAVA_HOME points to a JRE 11 — override for builds)
- Android SDK: `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools in `cmdline-tools\latest`)
- adb: winget Google.PlatformTools (on PATH)
- JADX: `C:\Users\Admin\Tools\jadx-1.5.6\bin`
- REA MCP server available (Android tools: inspect_android_package, decode_android_resources, search_android_classes, trace_android_references, …)
- Build: `./gradlew assembleDebug` (once M2 exists)

## 5. RE status
- Spec files done: none yet (only `docs/spec/00-overview.md` stub from Play listing)
- Open unknowns: original stack (native/Flutter/RN?), website-block mechanism (accessibility vs VPN), Shorts/Reels detection method, uninstall-protection method, strict-mode home-screen hiding method
- Ledger: `C:\Users\Admin\Projects\arcify-re\ledger.md` (create in M1)

## 6. Session log
- 2026-11-05 — Session 1: identified target, planned milestones, installed gh, scaffolded repo + memory file, created GitHub repo, installed cmdline-tools, received APKS v73.6.2089. Commits c29f323 + memory update.
