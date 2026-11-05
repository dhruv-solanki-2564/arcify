# ADR 0001 — Clean-room functional rebuild

**Date:** 2026-11-05 · **Status:** accepted

## Context
Arcify reproduces the feature set and behaviour of "Regain: Study Timer for Focus" (`ai.regainapp`, EpowerX Labs) as an open-source app. Reverse-engineering tools (REA, JADX) are used to understand *what* the original does and *which Android mechanisms* it relies on.

## Decision
- The analysis output is a **functional specification** in `docs/spec/`, written in our own words: behaviour, rules, screens, data, permissions and mechanisms.
- **No** decompiled code, resources, layouts, strings, icons, images, sounds, the mascot or branding are copied into this repository.
- The original APK and any decompiled output live outside the repo (`arcify-re/`) and are git-ignored.
- Arcify uses its own name, package (`app.arcify`), assets and code. It never contacts the original's servers.
- Paid features of the original are free in Arcify. No in-app purchases are replicated.

## Consequences
Implementation follows the spec and does not transliterate the original. Where the spec leaves behaviour unknown, we choose a reasonable design and record it here.
