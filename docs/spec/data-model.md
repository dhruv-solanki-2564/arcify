# Data model (Room) `[arcify]`

Designed for Arcify from the feature specs. The original's internal schema isn't reproduced.

| Entity | Key fields | Feature |
|---|---|---|
| `Tag` | id, name, colorToken | F1, F8 |
| `FocusSession` | id, mode, tagId, start, end, focusedMs, pausedMs, completed, strict, flaggedOutlier | F1 |
| `PomodoroConfig` | focusMin, shortBreakMin, longBreakMin, cyclesBeforeLong | F1 (DataStore) |
| `AllowedApp` | sessionProfileId, package | F2 |
| `AppLimit` | package, dailyLimitMs, reminderPct, strictUntil, enabled | F3 |
| `UsageHour` | date, hour, package, foregroundMs, launches | F3, F8 |
| `UnlockCount` | date, count | F8 |
| `AppCategory` | package, category | F8 |
| `ShortFormRule` | source, surface?, mode, limitN?, strictUntil | F4 |
| `ShortFormEvent` | date, source, surface?, itemHash, at | F4, F8 |
| `MonitoredApp` | package, kind (YOUTUBE/INSTAGRAM/…), enabled | F4 |
| `MonitoredBrowser` | package, urlBarIds (JSON), enabled | F4, F6 |
| `StudyChannel` | id, handle, channelId?, displayName, isDefault, position | F5 |
| `WebsiteRule` | pattern, kind, category, source, strictUntil | F6 |
| `ProtectionSettings` | level, deviceAdminEnabled | F7 (DataStore) |
| `ScheduledSession` | id, title, tagId, startTime, durationMin, mode, strict, repeat, date | F9 |
| `DailyGoal` | minutes | F9 (DataStore) |
| `StreakDay` | date, kind | F3, F9, F10 |
| `HeldNotification` | id, packageName, title, text, postedAt (deleted once delivered) | F13 (Room `held_notification`) |
| `WakeAlarm` | id, minuteOfDay, daysMask, enabled, snoozesAllowed, snoozeMinutes, focusMinutes | F14 (Room `wake_alarm`) |

Every list-type entity has a bundled default set (`assets/defaults/*.json`). It supports **reset to defaults** and **export/import JSON** (`ListBackup(version, entities…)`).
