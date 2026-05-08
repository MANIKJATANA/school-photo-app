# ADR 0003: Hash-partition `photo` by `event_id` into 16 partitions

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Project owner, database-expert

## Context

The dominant photo query is "given (student_id, event_id) → list photos." Every event-page load runs it. Photos at maturity reach the millions per school district. Without partitioning, this query scans a single very large index; with the right partitioning we can prune 15/16 of the data on every read.

Three partitioning strategies were considered:

- HASH on `event_id` — every read pins one event_id, prunes to one partition.
- LIST on `event_id` — one partition per event. Per-event isolation but thousands of partitions; planner overhead, catalog bloat.
- RANGE on `created_at` — temporal grouping good for archival, but the hot query reads any partition; doesn't help.

## Decision

`photo` is `PARTITION BY HASH (event_id)` into **16 partitions**. PK is `(event_id, id)` because the partition key must be in the PK. FK from `photo.event_id` to `event(id)` is supported on partitioned tables since PG 12.

`photo_student` is **not** partitioned in v1. With the keystone index `(student_id, event_id, photo_id)`, index-only scans stay sub-10ms to ~100M rows. Revisit at ~500M rows; HASH on `student_id` would be the candidate.

## Consequences

### Positive

- Hot query prunes to one partition automatically.
- 16-way IO parallelism for `pg_dump`, `VACUUM`, sequential scans.
- No hotspot per recent event (unlike LIST or RANGE-on-created_at).

### Negative

- Cross-event scans hit all 16 partitions. Acceptable: cross-event reads are rare and admin-only.
- Can't `DROP PARTITION` to archive an event wholesale. Archival is a `DELETE` instead. Acceptable cost for the partition strategy fit.
- 16 is a one-shot decision — repartitioning later is `INSERT … SELECT` and painful. We pick a number that lasts ~3 years for a typical school district.

### Neutral

- Postgres handles thousands of partitions before planner cost matters; 16 is well below that threshold.

## Alternatives considered

- **LIST(event_id)** — rejected: too many partitions at school-year scale.
- **RANGE(created_at)** — rejected: hot partition gets all writes, doesn't help the dominant query.
- **No partitioning, big indexes only** — rejected: at multi-million-row scale, partition pruning + smaller per-partition indexes outperforms one huge index.
- **Partition `photo_student` too** — rejected for v1; revisit at 500M rows. Adding partitioning later for that table is feasible because no cross-table FKs depend on its partition key.

## References

- Plan file, "Partitioning" section.
- PostgreSQL declarative partitioning docs.
