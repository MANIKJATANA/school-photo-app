---
name: AI context scaffolding expected up front
description: User wants CLAUDE.md, decision log, and custom subagent definitions created early in projects so future Claude Code sessions inherit rich context
type: feedback
originSessionId: ee4cc3cf-6129-4f5f-8979-b79608fa1b77
---
When setting up a new project (or planning the foundations of one), proactively include:

- A `CLAUDE.md` at the project root with stack, conventions, domain glossary, and key invariants
- A `.claude/decision-log.md` (or `docs/decisions/`) capturing architectural decisions with date, decision, alternatives considered, and rationale
- Custom subagent definitions under `.claude/agents/` for the roles that map to the project's work — for this project the user named: backend-developer, architect-designer, senior-software-developer
- Any other context files that would help future sessions pick up cold (glossary, conventions, runbook)

**Why:** The user explicitly asked for this scaffolding while planning the school photo app. They want every future Claude Code session on the project to land with enough shared context to act as a competent contributor without re-deriving the design.

**How to apply:** When planning or bootstrapping a new project, add a "Phase 0" item that creates these files alongside the code skeleton. Treat them as first-class deliverables, not optional extras. Suggest additional helpful subagents proactively (e.g., db-expert, security-reviewer, test-engineer, devops-engineer) — the user welcomed this.
