# Instructions for AI agents working on Arcify

1. **Start of every session:** read `AGENT_MEMORY.md` fully. Resume from its "Now → Next step".
2. **End of every session (or after each milestone step):** update `AGENT_MEMORY.md`
   (Now, checklist, decisions, session log with commit SHA) and commit it.
3. **Clean-room rule:** Arcify is an independent re-implementation of the *features* of
   "Regain: Study Timer for Focus" (`ai.regainapp`). Never copy decompiled code, resources,
   strings, icons, sounds, the "Rega" mascot or branding into this repo. Specs in `docs/spec/`
   are written in our own words. Third-party APKs and decompiled output stay in
   `C:\Users\Admin\Projects\arcify-re\` and are never committed.
4. Never call the original app's servers.
5. Stack: Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, WorkManager.
   Modules: `:app`, `:core`, `:blocker`. Business rules live in plain Kotlin and are unit-tested.
6. UI is placeholder until the user decides on design — keep visuals behind theme tokens.
7. All user-facing lists (blocked apps, limits, websites, YouTube channels, browsers,
   Instagram surface toggles) must be editable, resettable to defaults, and exportable as JSON.
8. Commit messages: imperative, scoped (`blocker: add reel counter`). Work on branches; PR into `main`.
