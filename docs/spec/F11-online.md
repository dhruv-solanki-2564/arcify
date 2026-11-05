# F11 — Online features (deferred to M8)

## What the original does
- **Focus groups.** Join or search groups, suggested groups, member lists, group preferences, group ranks. `[listing]` `[static]`
- **Leagues** with tiers and weekly ranks. `[static]`
- **Reactions** from other users, and wake-up toggles (friends nudging each other). `[static]`
- Global leaderboards and live focus rooms. `[listing]`
- Sign-in, cloud backup/restore (a restore-backup foreground service), and push via Firebase messaging. `[manifest]`

## Arcify plan `[arcify]`
- v1 has no network. The `SyncRepository` interface lives in `:core` with a `NoopSyncRepository`.
- M8: a self-hostable backend, implementation TBD (e.g. Supabase). It covers groups, live presence, weekly leagues and reactions.
- **Anti-cheat** (reviewers' main complaint about the original): server-side caps on daily focus hours, a heartbeat requirement while a session runs, and outlier sessions excluded from ranks.
- Local backup/export (JSON file) is available from v1, with no account needed.
