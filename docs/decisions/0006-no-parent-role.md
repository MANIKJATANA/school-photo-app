# ADR 0006: No PARENT role; parents share the student's login credentials

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Project owner

## Context

In K-8 schools, photos are typically viewed by parents, not by students directly. Two ways to model this:

- **PARENT role**: a separate `app_user` per parent, linked to one or more `student` rows. Each viewer (student or parent) has their own login.
- **Shared credentials**: the student's `app_user` is used by both the student and their parent. There's one login per student, regardless of who sits in front of it.

The project owner explicitly chose shared credentials.

## Decision

There is **no PARENT role**. The `app_user.role` enum is `ADMIN | TEACHER | STUDENT`. A student is represented by exactly one `app_user` row. Parents log in using the student's credentials.

`student.user_id` references the single login; there is no separate `parent_user_id`.

## Consequences

### Positive

- Auth is dramatically simpler: no parent-student linkage table, no per-parent permissions, no invitation/approval flow.
- One login per student, regardless of household composition (single parent, two parents, guardians, etc.).
- Parent-targeted notifications can simply target the student's `app_user`.

### Negative

- No audit distinction between "student viewed" and "parent viewed." If we later need that distinction (e.g., for compliance), we have to introduce per-viewer identity, which is a real migration.
- Account recovery (forgotten password) involves the student. For young students, that becomes the school admin's problem.
- Cannot personalise UI for "parent" vs "student" experiences without additional signals.

### Neutral

- Sharing still works: shares can target a student, and either the student or parent (sharing the credentials) can see them.

## Alternatives considered

- **PARENT role with student linkage** — rejected by user. Would simplify some future features but adds significant initial complexity.
- **Optional parent role** (configurable per school) — rejected: configurability of identity is a foot-gun. Pick one model and stick with it for v1.

## References

- Plan file, "Locked decisions" #3.
