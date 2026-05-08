# Loop log

Audit trail for the plan→implement→review loop. Each row is one stage of one iteration of one slice. The agents append here automatically.

## Format

```
<ISO-8601 timestamp> <slice-id> <stage:plan|impl|review> iter <n> <verdict> <details>
```

Where:

- `slice-id` — short kebab-case identifier (e.g., `phase1-school-crud`).
- `stage` — which agent produced the entry: `plan`, `impl`, or `review`.
- `iter` — iteration number for this slice, starting at 1. Resets per slice.
- `verdict` — for `plan`: `spec-written` or `spec-revised`. For `impl`: `diff-produced` or `kicked-back-to-planner`. For `review`: `APPROVED` or `CHANGES_REQUESTED`.
- `details` — for review: `<blocker_count>:<major_count>:<nit_count>`. Otherwise free-text under 80 chars.

## Entries

<!-- agents append here -->
