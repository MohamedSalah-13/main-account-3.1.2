# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Autonomous Working Mode

Use this workflow by default:

**Inspect → Understand → Plan internally → Implement → Test → Fix → Verify → Report**

- Inspect the relevant project files first and understand the existing architecture, conventions,
  dependencies, and implementation before making changes.
- Form the implementation plan internally, then execute it. Do not stop after presenting a plan, and do
  not ask for approval between ordinary implementation steps.
- Make reasonable technical decisions independently by following the project's current architecture and
  established patterns. Prefer inspecting and reusing the repository's existing solutions over asking the
  user to choose between equivalent technical approaches.
- Continue until the requested work is complete or a genuine blocker exists. Run the relevant clean build,
  compilation, and tests when possible; investigate and fix build or test failures caused by the changes,
  then verify again before reporting.
- Keep changes within the requested scope. Do not impose a dependency-injection framework, replace the
  existing architecture, or perform unrelated restructuring.
- Finish with a concise report of what changed, what was verified, and any genuine remaining limitations.

Ask the user only when:

- A fundamental ambiguity cannot be resolved from the repository and the possible interpretations would
  materially change business or user-facing behaviour.
- An operation is destructive, risks irreversible data loss, or requires a destructive database migration.
- Completing the request requires removing major existing functionality.
- Required credentials, secrets, API keys, licences, or unavailable external resources are missing.
- A business decision cannot reasonably be inferred from the requirements or the existing project.

Do not ask questions whose answers can be determined safely by inspecting the repository.

## Everything else is in CLAUDE.md

**[`CLAUDE.md`](CLAUDE.md) is the guidance for this repository. Read it before changing
anything.** What this file used to carry below this line - the build commands, the
architecture, the schema and migration conventions, licensing, localization - lives there,
in full and kept current. The working mode above is the only part specific to this file.

Two more documents govern work here, and `CLAUDE.md` says when each applies:
[`docs/new-code-rules.md`](docs/new-code-rules.md), whose §5 binds any file you open for
any reason, and [`docs/erp-roadmap.md`](docs/erp-roadmap.md).

### Why this is a pointer now

Because the copy drifted, and a wrong guidance file is worse than a short one. On
2026-08-31 this file still said:

- **"Test coverage is almost nothing"**, naming four test classes as the whole suite. There
  are 1,014 tests across 111 classes, and thirteen more acceptance classes gated behind
  `-Daccount.db.acceptance=true` that a change to a view or a balance is expected to run.
  An agent acting on the old sentence would have drawn exactly the wrong conclusion from a
  green build.
- **Startup wiring happens in a constructor.** It has not since startup became a background
  task behind a loading screen. The same sentence was in `CLAUDE.md`, was caught by the
  audit in `docs/audit-2026-08-31.html`, and was corrected there the same day - while this
  copy kept saying it.
- **"Services add little; the real logic is in controllers and DAOs."** That is the shape
  the project is deliberately moving away from; new behaviour goes in `features/`.
- **"Expect heavily generic signatures"** on the invoice seam, after the work that removed
  T1 and T2 from every consumer.

It also never mentioned `AuthorizationGuard`, `DocumentTableSpec`, `PartyTableSpec`,
`document_profit`, `treasury_current_balance`, `stock_count`, `DefaultStock`, or
`features/invoice` - each of which is load bearing, and several of which exist to stop an
agent breaking a rule it would not otherwise know about.

None of that was neglect. It is what two files of the same guidance do: one gets updated
with the change that prompted it, the other is remembered later, or not. One file and a
pointer cannot drift.

**So: do not grow this file.** If something belongs in the guidance, it belongs in
`CLAUDE.md`. Keep here only what is true of Codex and of no other reader.
