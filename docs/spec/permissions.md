# Permission map

Most of these were observed in the original's manifest. The "Arcify" column is our decision.

| Permission / capability | Original | Arcify | Used for |
|---|---|---|---|
| Accessibility service | yes | **yes** | F2–F7 detection and blocking |
| `PACKAGE_USAGE_STATS` | yes | **yes** | F3 limits, F8 insights |
| `SYSTEM_ALERT_WINDOW` | yes | **yes** | Floating timer, counters, overlays |
| `FOREGROUND_SERVICE` (+ special use, media playback) | yes | **yes** | Monitor, timer, ambient sound |
| `POST_NOTIFICATIONS` | yes | **yes** | Timer, nudges, schedules |
| `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT` | yes | **yes** | F9 schedules, F14 alarm |
| `RECEIVE_BOOT_COMPLETED` | yes | **yes** | Re-arm alarms and services |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | yes | **yes** | Keep services alive |
| `QUERY_ALL_PACKAGES` | yes | **yes** | App pickers (sideloaded APK, so no Play policy issue) |
| `VIBRATE`, `WAKE_LOCK` | yes | **yes** | Timer end, alarms |
| Device admin (force-lock) | yes | **optional** | F7 uninstall friction |
| HOME launcher activity | yes | **optional** | F2 home hiding |
| Notification listener | yes | **optional** | F13 batching |
| `INTERNET`, `ACCESS_NETWORK_STATE` | yes | **no** (until M8) | — |
| `CAMERA`, storage read | yes | no | Profile photos (not needed) |
| Billing, ad IDs, ad services, install referrer, FCM | yes | **no** | Monetisation/analytics |
| `READ_BASIC_PHONE_STATE` | yes | no | — |
| VPN service | **no** | no | — |
