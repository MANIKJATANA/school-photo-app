---
name: Use personal GitHub (MANIKJATANA), never the work account
description: This project's git/GitHub work goes through the personal MANIKJATANA account; the work account (manik-jatana-irame / @irame.ai) must never be used here
type: feedback
---

For all git and GitHub work on this project — commits, remotes, PRs, gh CLI commands — use the **personal** GitHub account `MANIKJATANA`, never the work account.

Concretely on this machine:
- SSH default `Host github.com` → `~/.ssh/id_ed25519_personal` → personal account `MANIKJATANA`. Use this.
- SSH alias `Host github-irame` → `~/.ssh/id_ed25519` → work account `manik-jatana-irame`. **Do not use** for this project.
- `gh` CLI is authenticated as `MANIKJATANA` (active account, SSH protocol). Don't switch accounts.
- Repo-local git config (`.git/config` inside this project): `user.name=MANIKJATANA`, `user.email=MANIKJATANA@users.noreply.github.com`. The global `~/.gitconfig` has the work email — explicitly override per-repo so commits attribute to personal.

**Why:** The user explicitly stated "use my personal github to do everything not office account" while bootstrapping this project's GitHub repo. The machine has both accounts wired up; default-routing through the global gitconfig would attribute commits to the work email even when pushing to the personal repo.

**How to apply:**
- Always set repo-local `user.email`/`user.name` (never rely on global) when initialising or cloning anything for this project.
- When suggesting `gh` commands, never include `--hostname` for an alternate org/account.
- When pushing remotes, use `git@github.com:MANIKJATANA/<repo>.git` (the default host that routes to personal), not `github-irame:`.
- If a future change asks to mirror or fork to a work org, treat it as a deliberate exception and confirm with the user first.
