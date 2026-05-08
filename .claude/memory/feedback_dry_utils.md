---
name: Factor shared logic into utils / common helpers — never duplicate
description: Common operations (id generation, cursor encode/decode, time, validation, mapping primitives, scope filters) live in named utility classes under common/, not duplicated across services
type: feedback
---

Anything that repeats across more than one call site goes into a named utility under `common/` (or its sub-packages). Service classes compose utilities; they don't re-implement them.

**Mandatory utilities the project relies on (introduce as needed; reuse strictly afterwards):**

- `common/id/Ids.java` — `Ids.newId()` returns a UUIDv7 from `com.github.f4b6a3:uuid-creator`. Wraps the only call site of the third-party API; everything else calls `Ids.newId()`.
- `common/time/Clocks.java` — exposes a `Clock` bean (default = `Clock.systemUTC()`) that all services and tests inject. No `Instant.now()` / `LocalDateTime.now()` direct calls in services.
- `common/pagination/Cursors.java` — `Cursors.encode(sortKey, id)` / `Cursors.decode(token)` for the infinite-scroll cursor pagination scheme. Single source of truth for the encoding (base64 + length-prefixed components or similar — pick one format and document it).
- `common/error/Errors.java` — see the single-error-file memory. All exception types + global handler.
- `common/scope/SchoolScopes.java` (or `common/school/`) — predicates / Specification fragments that apply the `school_id` filter. Every read path that filters by school goes through one of these helpers, never inline.
- `common/audit/Auditable.java` — `@MappedSuperclass` providing `id`, `createdAt`, `updatedAt`, `deletedAt`. Every entity extends it.
- `web/dto/Mappers.java` (or per-domain mappers via MapStruct) — DTO ↔ entity translation. Controllers don't hand-map.

**Why:** The user said "use proper use to utils, common functions and everything" — the project should not have inlined repeats of UUID generation, time access, cursor encoding, error throwing, or school filtering. Past projects that skipped this drift toward inconsistency: two cursor formats, three places that generate IDs differently, four different ways to throw 404.

**How to apply:**
- Before writing inline logic, search `common/` for an existing helper. If one exists, use it. If one *should* exist but doesn't, **create the helper in this slice** (the planner adds it to `ABSTRACTIONS INTRODUCED`) and use it.
- One helper, one purpose. Don't bundle unrelated utilities into a god-class.
- Helper classes are `final` with a private constructor (or `sealed` if a small type hierarchy is appropriate). No state.
- Reviewer flags any inline `UUID.randomUUID()`, `Instant.now()`, or hand-rolled cursor as a `route: PLANNER` issue (the spec should have used the utility).
