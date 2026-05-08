# ADR 0004: Precompute `student_event` mapping; refresh in same tx as `photo_student`

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Project owner, architect-designer

## Context

Two reads dominate the student home screen: "which events does this student appear in?" and "for this event, which photos contain this student?". The second is solved by the keystone index on `photo_student`. The first is solvable two ways:

- **On the fly**: `SELECT DISTINCT event_id FROM photo_student WHERE student_id = ?`. Needs a covering index, runs a hash aggregate. Always live.
- **Precomputed**: maintain a `student_event(student_id, event_id, photo_count, …)` table. One row per (student, event). Single index lookup at read time.

At realistic scale (~100k photo_student rows per active student over a year), the on-the-fly query is 10–50ms. The precomputed query is sub-1ms.

## Decision

Maintain a precomputed `student_event` table. It is **refreshed in the same transaction** that upserts `photo_student` rows — typically inside `MlCallbackController` after HMAC validation, or inside the manual-tagging service. Refresh is `INSERT … ON CONFLICT (student_id, event_id) DO UPDATE SET photo_count = photo_count + EXCLUDED.delta, last_updated_at = now()`.

The same-transaction invariant is non-negotiable. If `photo_student` writes commit and `student_event` doesn't (or vice versa), the home-screen read is wrong, silently.

## Consequences

### Positive

- Home-screen latency dominated by the precompute lookup (~1ms).
- Refresh is cheap — bounded to dozens of rows per ML batch.
- Storage is bounded by `students × events_per_student`, not photos.

### Negative

- Two writes per match instead of one. Acceptable: ML batches are bursty, not steady-state.
- A bug that breaks the same-tx invariant produces silently-wrong reads. We mitigate by:
  - A test that runs `photo_student` writes outside a transaction and asserts the home-screen read goes wrong (so the invariant is testable).
  - The reviewer checklist explicitly calls out this invariant.

### Neutral

- The precompute is derivable from `photo_student` + `photo`. We could rebuild it offline if it ever drifts.

## Alternatives considered

- **No precompute** — rejected: adds 10–50ms to every home-screen load. At scale, a noticeable regression.
- **Materialized view refreshed periodically** — rejected: staleness window. The home screen claiming "no events" right after an upload is bad UX. Synchronous refresh in-tx wins.
- **Precompute via DB trigger** — possible, but pushes critical logic into PG-only code. Conflicts with ADR-0001's portability discipline. Service-layer refresh wins.

## References

- Plan file, "Schema" and "DB-portability layer" sections.
