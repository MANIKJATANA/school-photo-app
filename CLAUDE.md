# photo-app — project context for Claude Code

This file is loaded into every Claude Code session on this repo. Read it before doing anything.

## What this is

A school photo-management system. Schools have students, teachers, classes, and events. Photos are uploaded against events. An ML face-matching service tags students in photos. Students/parents view their tagged photos; admins and teachers can share photos to a student / class / school-wide. All photo bytes flow through S3 presigned URLs; the API never proxies image data.

The full implementation plan lives at `/Users/manikjatana/.claude/plans/school-student-table-tidy-cake.md` and is the source of truth for scope and phasing.

## Stack

- **Spring Boot 4.0.6**, **Java 21**.
- **PostgreSQL 16+** (Flyway migrations under `src/main/resources/db/migration/`).
- **Spring Data JPA + Hibernate** for boring CRUD.
- **Spring's `JdbcClient`** (since Spring 6.1) for partition-aware / hot-path queries.
- **Spring Security 6** with **JWT** (JJWT lib).
- **AWS SDK v2** — `software.amazon.awssdk:s3` and `:s3-presigner`.
- **MapStruct** for DTO ↔ entity mapping.
- **com.github.f4b6a3:uuid-creator** for UUIDv7 generation.
- **Lombok** (sparingly: `@RequiredArgsConstructor`, `@Slf4j` only).
- **Testcontainers** with `postgres:16-alpine`, **LocalStack** for S3 in tests, **WireMock** for ML stubs.

## Module / package layout

Under `com.example.photoapp`:

```
config/         SecurityConfig, JpaConfig, S3Config, AsyncConfig
common/         error/, pagination/ (cursor codec), school/ (SchoolContext), audit/
domain/         entities only — NO Spring imports
                  school/, user/, student/, teacher/, klass/, event/,
                  photo/, tagging/ (PhotoStudent, StudentEvent), ml/, share/, notification/
repository/     Spring Data JPA interfaces + JdbcClient-backed query repos
                  (PG-specific impls live behind interfaces here)
service/        orchestration: PhotoUploadService, TaggingService,
                  StudentEventRefresher, MlOrchestrator, ShareService,
                  NotificationService, OutboxRelay
security/       jwt/, PrincipalResolver, AccessPolicy
storage/s3/     S3Presigner wrapper, S3KeyStrategy (BlobStore impl)
ml/             client/ (HTTP to Python), webhook/ (HMAC-validated callback), dto/
notification/   relay/ (OutboxPoller), channel/ (InAppChannel only in v1)
web/            controller/, dto/ (records), mapper/ (MapStruct), auth/
```

`domain/` deliberately holds plain JPA entities and value objects with no Spring imports. `service/` is the orchestration layer. `repository/` mixes Spring Data interfaces (boring CRUD) with hand-written `JdbcClient` impls for partition-aware hot paths.

## Domain glossary

- **school** — the top-level entity that every domain row scopes to via `school_id`.
- **student / teacher** — domain rows; each has an `app_user` for login.
- **klass** — class table (Java keyword dodge).
- **app_user** — single login row per human. Roles: `ADMIN | TEACHER | STUDENT`. Parents share the student's login (no PARENT role — see ADR 0006).
- **event** — what photos are grouped by. Each school has exactly one **default event** (`is_default = TRUE`) for "uncategorised" uploads. Photos always belong to *some* event.
- **photo** — partitioned by hash of `event_id` into 16 partitions. PK `(event_id, id)`.
- **photo_student** — ML output. Many-to-many tagging.
- **student_event** — precompute. "Which events does this student appear in?" Refreshed in the same tx as `photo_student`.
- **ml_run** — provenance + idempotency for ML batches. Callbacks are idempotent on `ml_run_id`.
- **share** — access grants. Scope = `STUDENT | CLASS | ALL`. Layered on top of role-based visibility.
- **outbox** — transactional outbox for reliable side-effects (notifications, ML enqueue).
- **notification** — in-app inbox row.

## Key invariants (the reviewer enforces these on every diff)

1. Every photo has an `event_id` — the per-school default event is the sentinel when no specific event applies.
2. `student_class` has at most one active row per student (`UNIQUE WHERE valid_to IS NULL`).
3. `student_event` is refreshed **in the same transaction** as `photo_student` upserts. Never a separate tx.
4. All photo bytes flow through presigned URLs. The API never proxies image data.
5. Every read path applies `school_id` from `SchoolContext` (request-scoped, populated by the JWT filter). Never trust client-supplied `school_id`.
6. UUIDv7 PKs everywhere. Generated app-side via `IdGenerator`. Exception: `outbox.id` is `BIGSERIAL`.
7. Soft delete via `deleted_at`. Hard deletes are forbidden except for the outbox prune.

## DB-portability rule (ADR 0001)

PostgreSQL is the chosen DB, but a swap must remain feasible. Therefore:

- PG-specific features (HASH partitioning, `SKIP LOCKED`, partial unique indexes, JSONB operators, `CITEXT`) live **only** in Flyway DDL or behind a repository-interface implementation.
- JPA entities use portable types (`varchar`, not `citext`). Case-insensitive email is `lower(email)` + an indexed expression.
- Service-layer code never calls PG-specific functions or uses PG-specific syntax.
- Examples of correctly-encapsulated PG-isms:
  - `OutboxStore.claimBatch(int n)` interface; PG impl uses `SELECT … FOR UPDATE SKIP LOCKED`.
  - `PhotoQueryRepository` interface; PG impl uses `JdbcClient` with parameterised SQL that exploits partition pruning.
  - `bbox` JSONB column; service code treats it as an opaque payload (`JsonNode`).

## Authorization cheatsheet

Roles on `app_user`: `ADMIN | TEACHER | STUDENT`.

| Action | ADMIN (school) | TEACHER | STUDENT |
|---|---|---|---|
| Create event | yes | no | no |
| Upload photo | yes | own classes only | no |
| Tag students | yes | own classes' students | no |
| Confirm/reject ML match | yes | own classes' students | self |
| Share to STUDENT/CLASS/ALL | all 3 | STUDENT/CLASS within own class | no |
| View photo | any in school | photos containing own-class students, or shared | photos of self, or shared |

`AccessPolicy` is the single source of truth for visibility. Controllers route filtered reads through it. Admin-only routes have explicit guards.

Sharing extends visibility additively — a `share` row grants access regardless of role-derived visibility.

## Run / test / migrate

```bash
# Bring up dev infra (Postgres + LocalStack S3)
docker compose up -d

# Run migrations (Flyway runs automatically on app start, but to run manually:)
./mvnw flyway:migrate

# Run the app
./mvnw spring-boot:run

# Tests
./mvnw test                  # unit + slice tests
./mvnw verify                # all tests including integration (Testcontainers)

# Build
./mvnw clean package
```

Default config in `application.properties` matches the dev compose file. Override with env vars in any other env.

## Project memory

Project-level Claude Code memory lives at `.claude/memory/`. The index is `.claude/memory/MEMORY.md`. Read the index at session start; load individual memory files when relevant.

Currently saved memories (preferences for *how to work on this project*):

- AI scaffolding (CLAUDE.md, ADRs, custom subagents) created up front, not retroactively.
- Plan→implement→review three-agent loop is the default execution model for non-trivial slices.
- Single-file errors (`common/error/Errors.java`) and DRY discipline for shared utilities.
- Personal GitHub (MANIKJATANA) only — never the work account.
- No "tenant" wording — scope is `school_id` directly.
- **No auto-commit / push.** Never run `git commit` / `push` / `amend` / `force-push` / `rebase` / `reset` without an explicit user request in the same turn. Slices end with files staged or in the working tree.

## Workflow rules

- **Never commit or push autonomously.** Even after an APPROVED reviewer verdict, leave the changes uncommitted and hand the slice back to the user for review. The user reviews the working tree (or `git diff --staged`) and explicitly says "commit it" / "push it" before any git write happens. See `.claude/memory/feedback_no_auto_commit.md`.
- When the user does ask for a commit, first summarise *what* will be committed (file list + scope) and *to where* (branch / remote) before running the command.
- The same hold applies to `--amend`, `--force`, `--force-with-lease`, `git rebase`, `git reset --hard`, `git stash`, and any `gh pr` write operation.

## Custom subagents

Defined in `.claude/agents/`. Use them via the `Agent` tool when work matches the agent's description.

**Loop trio (default execution model):**
- `planner` — designs slices, mandates abstractions, produces structured specs.
- `implementer` — executes specs exactly, no scope creep.
- `reviewer` — audits diffs against spec + checklist, routes issues to PLANNER or IMPLEMENTER.

**Specialists (called on demand):**
- `architect-designer` — system design, ADR drafting.
- `senior-software-developer` — second-opinion reviewer.
- `database-expert` — Postgres specialist, owns migrations and indexes.
- `security-reviewer` — auth / OWASP / HMAC / TTL.
- `test-engineer` — Testcontainers, authz-matrix harness, keystone-query test.
- `devops-engineer` — Dockerfile, docker-compose, CI.
- `ml-integration-specialist` — Java↔Python ML contract.

## Where decisions live

`docs/decisions/` — Architecture Decision Records. Read these before re-litigating any major design choice.

- `0001-postgres-with-portability-layer.md`
- `0002-ml-in-separate-python-repo.md`
- `0003-photo-hash-partitioning.md`
- `0004-precompute-student-event.md`
- `0005-presigned-urls-only.md`
- `0006-no-parent-role.md`
- `0007-in-app-notifications-v1.md`
- `0008-soft-delete-and-uuidv7.md`

New significant decisions get a new ADR. Use `docs/decisions/_template.md`.

## Coding conventions

- Records for DTOs. JPA entities are classes with `@Entity`, `@Getter`, `@Setter` — no Lombok `@Data` (it generates equals/hashCode that misbehave on entities).
- No comments unless the WHY is non-obvious. Don't restate the code. Don't reference current task / fix / callers.
- No `// removed` markers, no kept-for-back-compat re-exports. If something is unused, delete it.
- Don't add error handling, fallbacks, or validation for scenarios that can't happen. Validate at boundaries (controllers, external APIs) only.
- Cursor pagination, not offset/limit, on any endpoint that lists rows. Cursors are opaque base64 of `(sort_key, id)`.
- Migrations are forward-only. Destructive changes need an ADR.
- New config keys are documented in this file under the relevant section.
