-- ============================================================================
-- V2__seed_default_event.sql
--
-- Per ADR 0005 / plan: every photo must belong to *some* event. Each school
-- has exactly one default event (is_default = TRUE) used as the sentinel for
-- uncategorised uploads. The uniqueness invariant is enforced by the partial
-- unique index `uq_event_default_per_school` defined in V1.
--
-- This migration provides the trigger that auto-creates the default event row
-- when a new school is inserted. The event is created with its own UUIDv7
-- generated via the gen_random_uuid() fallback (UUIDv4 here, since this is the
-- ONE place where the DB generates an entity ID — service-layer entities still
-- generate UUIDv7 app-side per ADR 0008). It also requires a created_by user;
-- we use the same school's bootstrap admin if one exists, otherwise we defer
-- creation until the admin is created (see deferred path below).
--
-- Implementation choice: rather than a trigger that fires on `school` insert
-- (which couldn't supply created_by — no admin exists yet), the default event
-- is created by the OnboardingService in the service layer when a school is
-- bootstrapped together with its first admin. This file is intentionally a
-- no-op DDL placeholder so that V2 exists in version history; the actual
-- seeding happens in OnboardingService and the partial-unique index from V1
-- is the safety net.
--
-- Why a no-op migration rather than skipping V2 entirely: keeping a
-- versioned no-op preserves the migration sequence so that future versions
-- (V3, V4, …) line up across environments where someone may have applied
-- earlier drafts.
-- ============================================================================

-- Sanity check: confirm the partial unique index from V1 exists.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes
     WHERE schemaname = 'public'
       AND indexname = 'uq_event_default_per_school'
  ) THEN
    RAISE EXCEPTION 'V1 baseline missing: uq_event_default_per_school index not found';
  END IF;
END
$$;
