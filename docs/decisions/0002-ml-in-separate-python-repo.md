# ADR 0002: ML face-matching lives in a separate Python repo

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Project owner

## Context

Face matching for student tagging is the system's central ML workload. Three properties matter:

1. The model ecosystem is Python-first (PyTorch, InsightFace, ArcFace, FaceNet). DJL exists for Java but the broader iteration / experimentation tooling sits in Python.
2. Models retrain and roll forward independently of the API. Coupling release cadence forces the API to redeploy on every model bump.
3. Inference is GPU-friendly. The Spring Boot API is CPU-cheap. Co-locating wastes GPU-node budget and complicates autoscaling.

## Decision

ML face matching lives in a **separate Python repo and service**. The Java side talks to it over REST with an HMAC-signed webhook callback for results.

Contract (owned by `ml-integration-specialist`):

- Outbound: `POST <ml-base>/runs` with `{ml_run_id, event_id, photo_ids, model_version_hint?}`. Returns 202.
- Inbound webhook: `POST /api/v1/webhooks/ml/{ml_run_id}` with `{matches:[…], status, model_version, error?}` and `X-ML-Signature: HMAC-SHA256(body)`.
- Idempotent on `ml_run_id`. Replay is a no-op via `INSERT … ON CONFLICT DO NOTHING` plus the `student_event` increment guarded by the same conflict path.

## Consequences

### Positive

- Independent release cadence. Model retraining doesn't touch the API.
- GPU infra isolation. ML pods get GPU nodes; API pods stay cheap.
- Failure isolation. An OOM in matching doesn't kill API request handlers.
- Python ecosystem available end-to-end for ML work.

### Negative

- One cross-service contract to maintain. Versioning the contract becomes a real concern as the system evolves.
- Two deploy pipelines, two ops surfaces, two on-call rotations (or one shared one with two skill sets).
- Webhook security to manage (HMAC secret rotation).

### Neutral

- The Java side stubs `MlClient` for tests. Integration tests run against a stub that returns deterministic matches.

## Alternatives considered

- **Embed ML in Java via DJL** — simpler ops, no contract drift. Rejected because the Python ecosystem is too far ahead for the iteration speed we need.
- **Async via SQS/RabbitMQ instead of REST+webhook** — better backpressure and retry semantics. Rejected for v1 — adds infra. Migrate when the outbox-as-queue stops being enough.
- **Sync ML inline** — lowest infra count. Rejected because it ties API request latency to model latency and makes scaling impossible.

## References

- Plan file, "ML integration" section.
- `.claude/agents/ml-integration-specialist.md`
