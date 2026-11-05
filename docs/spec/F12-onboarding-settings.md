# F12 — Onboarding, permissions, settings

## What the original does
- A guided permission flow, including transparent overlay "steps" shown on top of system settings to guide the user. `[manifest]`
- A dedicated accessibility-permission explainer screen. `[manifest]`
- Asks for battery-optimisation exemption. `[manifest]`
- Subscription/paywall screens, ratings and surveys. `[manifest]` `[static]` Arcify drops all of these.

## Arcify design `[arcify]`
- Onboarding order:
  1. Welcome
  2. Goals (study, reduce scrolling)
  3. Notifications
  4. Usage access
  5. Accessibility (with a clear statement that data never leaves the device)
  6. Draw over apps
  7. Battery-optimisation exemption
  8. Optional: device admin, default launcher, notification access
- Every permission screen shows what breaks without it. The app works in a degraded mode with any subset.
- A small guide overlay while the user is inside the system settings page ("Find Arcify → turn on").
- Settings: theme tokens, Pomodoro defaults, protection level, monitored apps and browsers, all editable lists, backup/export/import JSON, reset to defaults.
