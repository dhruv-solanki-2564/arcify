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
