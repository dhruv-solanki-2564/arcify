# F01 — Focus timer

## Behaviour
- Three modes `[listing]`:
  - **Timer**: countdown from a chosen length.
  - **Stopwatch**: counts up until stopped.
  - **Pomodoro**: alternating focus and break periods, with short breaks and a long break after a number of cycles. Durations are customizable.
- Pause and resume are allowed. A notification fires when each Pomodoro period ends. `[listing]`
- The running timer lives in a **foreground notification service**. `[manifest]` A **floating timer bubble** can be shown over other apps. `[manifest]`
- **Ambient sound** plays through a separate foreground media service. `[manifest]` The original describes "science-backed music". Arcify ships its own CC0 sounds.
- **Themes** for the timer screen `[listing]`. Premium-only in the original, free in Arcify.
- **Tags.** Sessions are labelled (e.g. a subject). Stats show tag distribution. `[listing]` A tag overlay can appear after or around sessions. `[manifest]`
- **Session summary.** Weekly comparison and stats heartbeat data exist in the domain layer. `[static]`
- During a session, chosen apps are blocked or allowed (see F02). A browser can be explicitly allowed for study (F06). `[static]`
- Focus can optionally **block notifications** during the session. A remotely toggled feature exists. `[static]`

## Rules `[arcify]`
- A session state machine: `Idle → Running(mode, phase, startedAt, plannedMs) ↔ Paused → Completed | Abandoned`.
- Pomodoro defaults: 25 min focus / 5 min short break / 15 min long break, with a long break every 4 cycles. All editable.
- Elapsed time is computed from monotonic timestamps, never from a counter, so it survives process death.
- A session shorter than 1 minute is not recorded.
- Daily cap: sessions past 16 h/day are flagged, and they don't count toward streaks. This addresses a reviewer concern about inflated hours.
- Persist sessions as `FocusSession(id, mode, tagId, start, end, focusedMs, pausedMs, completed, strict)`.

## Unknowns
- Exact pause limits in strict mode (we choose: pause is disabled in strict mode).
