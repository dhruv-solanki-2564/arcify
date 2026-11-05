# F07 — Block protection

## What the original does
- **Device admin.** A device-admin receiver requesting only the force-lock policy. `[manifest]` While admin is active, Android won't uninstall the app until admin is turned off. `[inferred]`
- **Accessibility guards.** The accessibility service watches the system package installer (both AOSP and Google variants), the system UI and the Play Store. `[static]` These are the places where an uninstall or a force-stop would happen.
- **Floating-window and split-screen blocking.** Split-screen layout is detected from window positions. There's an OEM-specific canvas package for one vendor's floating windows. `[listing]` `[static]`
- A dedicated block-protection screen and settings. `[manifest]`
- Remote config hints at an "uninstall" support path (phone number). Arcify ignores this. `[static]`

## Arcify design `[arcify]`
- Protection levels: **Off**, **During strict sessions/limits only** (default), **Always**.
- When protection is active:
  - The uninstall confirmation dialog for Arcify → back + overlay.
  - Arcify's page in the Play Store → overlay on the uninstall button area. Implemented as back navigation.
  - System settings for *Arcify's* accessibility toggle, device-admin page, app info → overlay.
  - Split-screen or a floating window containing a blocked app → overlay over that window.
- Device admin is optional and explained in onboarding. Arcify needs only force-lock.
- Escape hatch: protection can always be turned off when no strict lock is active.

## Implementation (M5)
- **Rules** (pure, tested; `core/.../protection/Protection.kt`):
  - `ProtectionLevel` OFF / DURING_COMMITMENTS (default) / ALWAYS.
  - `Commitments`: a strict session for its whole length, breaks included, plus the count of locked limits, short-form rules and website rules.
  - Protection can be raised any time, but lowered only with no commitments. The same goes for anything that weakens it: emergency exit on, home hiding off, device admin off.
- **Live state**: `CommitmentTracker` combines the timer and all locked rules, re-checking every 30 s for lock expiry. `ProtectionRepository` (DataStore) refuses changes that would weaken protection.
- **Detector** (`blocker/.../protection/ProtectionDetector.kt`, tested on hand-built screens):
  - Installer packages: any screen naming Arcify (the uninstall dialog).
  - Settings packages: Arcify's accessibility-service label, or "Arcify" (whole word) together with uninstall / force stop / deactivate / disable / clear storage words.
  - Play Store: "Arcify" plus uninstall.
  - Plain app lists that include Arcify are left alone.
  - Response: Home, then the "Protection is on" screen.
  - **⚠️ Unverified** on real phones. OEM settings packages (MIUI, ColorOS) and English-only button words are guesses.
- **Split screen / floating windows**: on window changes, every visible app window goes through the same app-level decision. A blocked app in a second window triggers Home and its block screen. This applies whenever the app would be blocked, not only while protection is on, because otherwise split screen would bypass limits. **⚠️ Unverified.**
- **Device admin**: `ArcifyDeviceAdminReceiver` (force-lock policy only, never used). Turned on and off from Block → Protection. Turning it off is refused during commitments, and the system device-admin page is guarded.
- **UI**: Block → Protection.
  - Status, level chips.
  - Device admin on/off.
  - Home-screen hiding (F02).
  - Emergency exit (F02).
