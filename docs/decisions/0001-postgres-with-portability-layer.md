# ADR 0001: PostgreSQL with a portability layer

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Project owner

## Context

We need a relational database for the school photo system. Three things matter for storage:

1. The dominant query is "given (student_id, event_id) → list photos." It runs on every event-page load.
2. We will store millions of `photo_student` rows from ML matching, with hot reads and bulk writes per ML batch.
3. The project owner wants the option to swap databases later (cost, ops team familiarity, vendor preference shifts).

PostgreSQL gives us declarative HASH partitioning on `photo` by `event_id`, partial unique indexes (e.g., one default event per school), JSONB for ML bbox payloads, and `FOR UPDATE SKIP LOCKED` for the outbox worker. None of these have direct equivalents across the major alternatives without re-shaping the schema or pushing logic into the application.

But these PG-specific features can leak into the application code — JPA entities that reference PG types, service-layer queries with PG functions, repository methods that bake in `SKIP LOCKED`. If they leak, the "swap DB" option becomes untrue in practice even though we say it's possible.

## Decision

Use PostgreSQL for v1 **and** confine every PG-specific feature to either Flyway DDL or a repository-interface implementation. The service layer, JPA entities, and domain code remain DB-agnostic.

Concretely:

- HASH partitioning of `photo` is defined only in `V1__baseline.sql`. App code never references partitions.
- Outbox claim is `OutboxStore.claimBatch(int)` interface; the PG impl uses `SELECT … FOR UPDATE SKIP LOCKED`.
- Partition-aware / hot-path queries live behind interfaces like `PhotoQueryRepository`; PG impls use `JdbcClient` with parameterised SQL.
- Partial unique indexes are PG-only at the DDL level. An alt-DB port enforces the same invariant in service code.
- `bbox` JSONB stays an opaque payload; service code never calls `jsonb_*` operators.
- No PG types in JPA entities (no `citext`, no PG-specific arrays).

## Consequences

### Positive

- Real PG features are available in v1 without architectural fudges.
- A future swap (Aurora, Cloud SQL Postgres) is trivial — same dialect.
- A larger swap (MySQL, CockroachDB) is bounded: rewrite Flyway migrations, re-implement the handful of repository PG impls, leave service/domain untouched.

### Negative

- More indirection up front. Developers must understand the rule "PG-isms behind interfaces."
- Reviewer overhead — the loop's reviewer must check this rule on every diff that touches data.

### Neutral

- The portability layer doesn't make the system multi-DB at runtime. Only one DB is active per deploy.

## Alternatives considered

- **MySQL** — would force re-shape: no PG-style hash partitioning, no JSONB (use JSON), no partial-unique indexes. Doable but degrades the partition strategy and visibility predicate composition. Rejected for v1.
- **PostgreSQL with no portability discipline** — simpler now, but the project owner explicitly asked for swap-readiness. Rejected on user requirement.
- **Multi-DB at runtime via `Database` strategy beans** — over-engineered for current needs. The portability layer gives us swap-readiness without runtime multi-DB cost.

## References

- Plan file: `/Users/manikjatana/.claude/plans/school-student-table-tidy-cake.md`, "DB-portability layer" section.
