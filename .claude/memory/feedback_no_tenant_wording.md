---
name: No "tenant" / "multi-tenant" wording — scope by school_id directly
description: This project does not have an abstract "tenant" concept; the only scoping axis is school_id. Avoid "tenant", "tenancy", "multi-tenant", "TenantContext" everywhere — code, comments, ADRs, agent prompts, plan docs.
type: feedback
---

There is **no abstract tenant concept** on this project. The only request-scoping axis is `school_id`. Do not introduce "tenant" terminology — it implies an abstraction layer the project does not have and should not carry.

**Wording mapping (use the right column):**

| Avoid | Use instead |
|---|---|
| `tenant` (as a noun for school) | `school` |
| `multi-tenancy`, `multi-tenant` | `school scope` / "scoped to `school_id`" |
| `tenant isolation` | `school scope` / "school-scoped queries" |
| `cross-tenant` | `cross-school` |
| `Tenant ID` | `school_id` |
| `tenant-related` | `school-scope-related` |
| `the top tenant` (referring to the `school` row) | rephrase — drop "tenant" |
| `TenantContext` (class) | `SchoolContext` |
| `common/tenant/` (package) | `common/school/` |

**Why:** The user explicitly redirected: "tenant restriction is not part of this, here the restriction is for school id". A multi-tenant abstraction would suggest the system might host non-school tenants, or that the tenant boundary might live somewhere other than `school_id`. Neither is true. Calling it what it is — school scope — keeps the model honest and the code grep-friendly.

**How to apply:**
- When writing new code, comments, ADRs, agent prompts, or plan/design docs: never type "tenant" / "multi-tenant" / "tenancy". Use `school_id`, "school scope", or `school` directly.
- The request-scoped holder bean (when it lands in Phase 1) is named `SchoolContext`, lives at `com.example.photoapp.common.school.SchoolContext`, and is populated by the JWT filter from the JWT's `school_id` claim.
- If a reviewer / planner / implementer agent slips into "tenant" wording, treat it as a routing-back issue.
- Verification check at any time: `grep -rin -e tenant -e tenancy -e 'multi-tenant' .` (excluding `.git/` and this memory file itself) must return zero hits.
