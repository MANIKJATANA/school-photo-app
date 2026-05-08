---
name: Plan-implement-review three-agent loop is the default execution model
description: User wants every implementation slice driven by a three-agent loop — planner produces an abstraction-heavy design, implementer executes it, reviewer audits — iterating until the reviewer approves
type: feedback
originSessionId: ee4cc3cf-6129-4f5f-8979-b79608fa1b77
---
For implementation work on this project, default to a three-agent loop, not a single straight-through implementation:

1. **Planner agent** receives the slice intent and produces a design spec. It must consider multiple perspectives (correctness, performance, security, testability, DB-portability, maintainability) and proactively introduce abstractions (interfaces, ports, strategy patterns, repository abstractions) wherever there is even a hint of swappable or evolving behaviour. Read-only — its output is the slice spec the implementer follows.
2. **Implementer agent** executes the planner's spec exactly. No scope creep, no re-design. If the spec is unclear or wrong, it kicks back to the planner rather than guessing.
3. **Reviewer agent** audits the diff against the spec + a fixed checklist. Outputs a structured verdict (APPROVED | CHANGES_REQUESTED with itemised issues). Read-only.

Loop routing:
- If reviewer flags an **implementation** issue → back to implementer.
- If reviewer flags a **design** issue (missing abstraction, wrong layering, leaky concrete in the wrong place) → back to planner for a spec revision, then implementer reworks against the new spec.
- Iteration cap (3 by default); on cap, escalate to user instead of looping silently.

**Why:** The user explicitly asked for three agents — "planner thinks of solution in all perspectives and uses abstraction everywhere possible, then 2 are implementation and reviewer." They want design rigour and abstraction-first thinking baked into every slice, not added retroactively in code review.

**How to apply:**
- Treat this as the default execution model for non-trivial slices on this project (and propose it for other projects).
- Define all three roles in `.claude/agents/` so they persist across sessions.
- The planner's spec should explicitly call out which abstractions are introduced and why — this is the artefact the reviewer uses to judge whether the implementation honoured the design.
- Skip the loop only for trivial mechanical edits (typo fixes, single-line config tweaks); say so explicitly when skipping.
