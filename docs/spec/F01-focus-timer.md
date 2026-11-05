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
- Elapsed time comes from recorded wall-clock timestamps (start, pause, resume), never from counting ticks. A late or missed tick can't drift the clock, and the phases the phone slept through are caught up on the next tick. *(M2: the running session lives in memory, kept alive by the foreground service. Persisting it across process death is a later hardening step.)*
- Strict sessions: no pausing, and no stopping during a focus phase. A Pomodoro break may be stopped. The stopwatch can't be strict, since it has no natural end.
- Stopping counts as **completed** for a stopwatch, or for a Pomodoro with ≥1 finished focus cycle. Stopping a countdown early counts as not completed.
- Optional `targetCycles` makes a Pomodoro finish on its own after N focus cycles.
- A session shorter than 1 minute is not recorded.
- Daily cap: sessions past 16 h/day are flagged, and they don't count toward streaks. This addresses a reviewer concern about inflated hours.
- Persist sessions as `FocusSession(id, mode, tagId, start, end, focusedMs, pausedMs, completed, strict)`.

## Implementation (M2)
- Engine: `core/.../timer/FocusTimer.kt` (pure) + `TimerController` (app-wide owner, saves finished sessions).
- Recording rules: `SessionRules` (<1 min not saved; past 16 h/day flagged as an outlier).
- Service: `app/.../timer/FocusTimerService.kt` (special-use foreground service, 1 s tick, notification actions).
- Tests: `core/src/test/.../FocusTimerTest.kt`, `SessionRulesTest.kt`.

## Still open
- Tag picker UI and the tag overlay after sessions (tags are already stored).
- Ambient sounds and timer themes (later; UI decisions pending).
