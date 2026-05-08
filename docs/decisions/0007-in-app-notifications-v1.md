# ADR 0007: In-app notifications only in v1, behind an outbox + channel-strategy abstraction

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: Project owner

## Context

Notifications fire when ML produces matches ("new photos of you have been tagged") and when shares are received. Channels considered:

- In-app — write to a `notification` table; FE polls or subscribes.
- Email — SES / SendGrid / similar.
- SMS — Twilio.
- Push — FCM/APNS, requires a mobile app.

For v1, the project owner chose in-app only. Email and SMS introduce vendor cost, deliverability concerns, and template management. Push requires a mobile app that doesn't exist yet.

The risk of v1-only is "we'll add email later as a quick patch" — and end up with notification-channel logic scattered across services. That's prevented by introducing the channel abstraction now even though only one channel exists.

## Decision

V1 ships **in-app notifications only**. Architecture is:

- ML callback / share creation writes a `notification` row in the same transaction as the underlying state change.
- Actually — to support multi-channel later — the trigger writes an `outbox` row (transactional outbox pattern) in the same tx.
- An `OutboxPoller` (`@Scheduled`, claims via `OutboxStore.claimBatch` interface — PG impl uses `SKIP LOCKED`) dispatches outbox events to a `NotificationDispatcher`.
- The dispatcher fans out across `NotificationChannel` strategy beans. In v1 only `InAppChannel` is wired (writes a `notification` row).
- Adding email/SMS/push later is one new `NotificationChannel` impl + a config flag.

## Consequences

### Positive

- Zero external provider cost for v1.
- Outbox pattern gives exactly-once-ish delivery across crashes.
- New channels plug in without touching service or controller code.

### Negative

- One polling worker to operate. Default poll interval (configurable) trades latency for DB load.
- Outbox table grows. Needs a periodic prune of `processed_at IS NOT NULL AND processed_at < now() - 30 days`.

### Neutral

- FE polls `GET /notifications?unread_only=true`. SSE/websocket can replace polling later without API changes.

## Alternatives considered

- **Direct push from ML callback** — rejected: ties notification reliability to ML-callback success. A failed notification rolls back the match write, or worse, is silently lost.
- **Email-first** — rejected by user for v1.
- **No abstraction, just write `notification` rows** — rejected: adding a second channel later would touch every site that creates a notification.

## References

- Plan file, "Notifications" section.
