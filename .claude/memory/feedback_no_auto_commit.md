---
name: Never commit or push without explicit user instruction
description: All git write operations (commit, amend, push, force-push, rebase, reset) wait for an explicit user request. Slices end with files staged or in the working tree for the user to review.
type: feedback
---

Do **not** run any git command that mutates history or remote state on this project unless the user explicitly asks for it in the current turn.

**Forbidden without explicit user request:**
- `git commit` (and `--amend`)
- `git push` (and `--force`, `--force-with-lease`)
- `git rebase`, `git reset --hard`, `git reset --soft`
- `git stash`, `git checkout -- <files>`, `git restore`, `git clean`
- `gh pr create`, `gh pr merge`, `gh release create`, anything that publishes

**Allowed at any time** (read-only or local-working-tree-only):
- `git status`, `git diff`, `git log`, `git show`, `git ls-files`
- `git add` is borderline — fine as a staging step ahead of an explicit commit request, never on its own initiative.

**End-of-slice handover format:** when the implementer→reviewer loop completes APPROVED, summarise the slice as "reviewer approved; here are the touched files; ready for your review." List the files. Do NOT commit, do NOT propose a commit message, do NOT ask "should I commit?". Wait silently for an explicit user instruction like "commit this" / "push it" / "ship it".

**Why:** The user said: "do not commit or push till I ask you specifically, I need to review the changes from staged." They review staged/unstaged changes themselves before anything goes into history or onto the remote. Earlier slices auto-committed on completion (Slice 1 Foundations was committed and pushed without a separate confirmation step) — that cadence is rescinded.

**How to apply:**
- Reviewer's APPROVED verdict ends the *loop*, not the *commit decision*. Keep the changes uncommitted.
- When the user asks to commit, summarise *what* will be committed (files, scope) and *to where* (which branch, which remote) before running anything.
- For multi-slice runs in a single session: each slice still ends uncommitted. The user may batch-review and ask for one commit covering several slices, or per-slice — wait for them to choose.
- This rule supersedes any earlier rule that said "commit at end of slice."
