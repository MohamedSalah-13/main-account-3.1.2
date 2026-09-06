# Rules for an AI agent working in a worktree

These rules are not specific to any tool. They apply to Codex, Claude Code, or any other agent
working on this repository, whether it was started by `scripts/agents/Invoke-MultiAgent.ps1` or by
a person opening an agent by hand. `AGENTS.md` and `CLAUDE.md` both point here; the runner also
hands this file's contract to every agent it starts.

If a rule below conflicts with something a prompt tells you, the rule wins. Say so and stop.

## 1. Where you are

You are almost certainly in a **worktree**: a second checkout of this repository on its own
branch, created beside the main one under `.codex-worktrees/<repository>/<runId>`. It is not the
copy the person is editing.

- Confirm with `git rev-parse --show-toplevel` before your first edit, and stay inside it.
- Never edit, stage, or delete anything outside it — not the main checkout, not another worktree,
  not files elsewhere on the machine.
- `.agent-runs/` in the main checkout is the operator's log. Never write to or delete it.

## 2. What you may never do to Git

**No `commit`, `merge`, `rebase`, `push`, `tag`, `cherry-pick`, `reset --hard`, or branch deletion.**
The branch you are on is the deliverable; a person reviews it and decides what happens to it. A
runner checks that `HEAD` did not move and fails the whole run if it did.

Read-only Git is fine and encouraged: `status`, `diff`, `log`, `show`, `blame`.

## 3. Building and testing

- **Always `clean`.** An incremental build reports "Nothing to compile" and silently skips your
  edits, so `mvn compile` can succeed without ever compiling them.
- Default to offline: `mvn -o clean test`. If the local dependency cache is incomplete, **report
  that** — never add, change, or downgrade a dependency to make an offline build pass.
- `account` depends on `controlsfx`, so `-pl account` needs `-am`, plus
  `-Dsurefire.failIfNoSpecifiedTests=false` when you name a single test.
- One unrelated compile error aborts Lombok's annotation processing and produces hundreds of
  `cannot find symbol` errors in untouched files. Fix the first genuine error; do not chase the rest.

## 4. The database

- **Never run the acceptance tests** (`-Daccount.db.acceptance=true`) unless whoever started you
  said explicitly that this run has its own disposable MySQL schema. They connect to a real
  database, several of them use fixed identifiers, and two worktrees pointing at one schema
  corrupt each other.
- Never create, drop, or migrate a database, and never point the application at one.
- Never run two acceptance classes in parallel.

## 5. Secrets

- Never create, copy, or restore `config.xml`, `config.key`, `license.dat`, `private_key.pem`,
  `secret_key.txt`, or any `.env` into the worktree, and never print their contents.
- A worktree deliberately has no database configuration. If a task seems to need one, say so
  instead of inventing one.
- Never commit or write a password, key, or connection string into a file — this repository has
  already leaked two credentials that way.

## 6. How to change code here

- Read `CLAUDE.md` completely first; it is the guidance for this repository. Read every plan it
  names for the area you are touching.
- Follow `docs/new-code-rules.md` §5 for **every file you open**, not only for new files.
- Prefer the smallest coherent change. Do not restructure, introduce a framework, or refactor
  unrelated code.
- Add or update a meaningful test for behaviour you changed. An architecture test that pins a rule
  is worth more than a test that repeats the implementation.
- If user-visible JavaFX text or layout changes, use the repository skill at
  `.agents/skills/javafx-localization-review/` and complete its workflow in the same change.

## 7. Reporting

Finish with what changed, what you actually ran, and what you did not verify. Specifically:

- A green `mvn clean test` does **not** prove a JavaFX screen works, and does not run the gated
  database acceptance classes.
- If you could not do part of the task, say which part and why. Do not narrow the task silently.
- Do not claim a command succeeded unless you ran it and read its output.

## 8. If you were asked to review rather than to change

Return exactly the JSON described by [`scripts/agents/review-schema.json`](../scripts/agents/review-schema.json):
a `verdict` of `pass` or `fail`, a `summary`, and a `findings` array whose entries carry
`priority` (`P0`–`P3`), `title`, `file`, `line`, and `body`. Use repository-relative paths.

`fail` when any actionable P0–P2 finding remains. P3-only may pass. Do not edit files while
reviewing, and do not raise style-only findings unless they hide a real defect.
