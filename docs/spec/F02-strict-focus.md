# F02 — Strict focus

## Behaviour
- In strict mode, the user **cannot exit the session early** and the **home screen is hidden** during deep focus. `[listing]`
- **Mechanism for hiding home:** the original declares an invisible activity in the *home/launcher* category. `[manifest]` If the user makes it the default launcher, pressing Home during focus routes through it, so the app decides whether to show the real launcher or return to the focus screen. `[inferred]`
- Apps outside the allowed set are intercepted by the accessibility service and covered by the block overlay. `[manifest]` `[static]`
- Uninstall/settings escapes are covered by F07 while strict focus is active.

## Arcify design `[arcify]`
- `ProxyHomeActivity` (HOME category, no UI). When no strict session is active, it forwards immediately to the user's previously chosen launcher (stored package). During a strict session it returns to the focus screen.
- Setup flow explains the launcher choice. If the user declines, strict mode falls back to the accessibility path: when the launcher package comes to the foreground, show the overlay.
- Allowed-apps list per session (default: phone, messages, clock, Arcify). Editable.
- Emergency exit: an optional "hard exit" that requires typing a long confirmation phrase. It is **off by default** in strict mode.

## Implementation (M3)
- **Lock window**: only strict sessions, and only during focus phases. Pomodoro breaks are free (`BlockPolicy.focusLock`). Non-strict sessions don't block apps.
- **Accessibility path**:
  - When an app not on the allowed list comes to the front, the block screen covers it, with "Back to focus" and the time left.
  - When a home-screen app comes to the front, Arcify's focus screen comes back instead.
- **Always allowed**: Arcify, system UI, the default dialer and SMS apps, telephony/emergency packages, enabled keyboards (`AppCatalog.alwaysAllowedPackages`).
- **Allowed list**: user-editable in the Block tab, with reset to defaults (`strict_allowed_app` table). JSON export comes with the Settings work.
- **Deferred to M5**: the optional proxy launcher (`ProxyHomeActivity`) and the hard-exit phrase. They ship together with block protection, because they change how the user gets out of the app.
