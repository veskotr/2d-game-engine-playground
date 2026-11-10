# Documentation maintenance

[AGENTS.md](../AGENTS.md) is the canonical agent entry point.
[CONTRIBUTING.md](../CONTRIBUTING.md) owns setup and validation instructions.

Update documentation when the facts it describes change:

- `modules/` for module responsibilities, public APIs, dependencies, and known issues.
- `topics/` for cross-module behavior, frame order, workflows, and test conventions.
- `CONSTRAINTS.md` for architecture and subsystem invariants.
- `PROJECT_MEMORY.md` for durable decisions and significant known debt.
- A task-specific plan following [PLANS.md](../PLANS.md) for substantial work.

Do not require updates to every context file for each task. Keep temporary progress
in the relevant plan. `CURRENT_TASK.md` is a historical handoff unless explicitly
updated for a current request; its presence does not authorize continuing old work.

Older long-form references and implementation plans may describe past states or
future intent. Verify relevant claims against code. Correct or mark stale sections
when a task touches them; do not claim the entire document was reverified.

Before finishing, check changed links and report actual validation. For a code
change with no documentation impact, a brief explanation in the PR is sufficient.
