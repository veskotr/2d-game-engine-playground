# Documentation Maintenance

Use this file when deciding which docs to update.

## Canonical Files

- `docs/CURRENT_TASK.md` - active task and handoff.
- `docs/PROJECT_MEMORY.md` - durable project memory.
- `docs/CONSTRAINTS.md` - rules and invariants.
- `docs/README_DOCUMENTATION.md` - documentation map.
- `docs/modules/*.md` - per-module current state.
- `docs/topics/*.md` - cross-cutting topics and workflows.

## Update Rules

Update `CURRENT_TASK.md` for every non-trivial task:

- current status
- files changed
- validation run
- unresolved follow-ups

Update `PROJECT_MEMORY.md` when the project truth changes:

- architecture decisions
- ownership decisions
- long-lived known debt
- roadmap state that future agents need

Update `CONSTRAINTS.md` when rules change:

- dependency boundaries
- testing gates
- style rules
- subsystem invariants

Update module docs when code in that module changes:

- public APIs
- responsibilities
- dependencies
- important files
- known issues

Update topic docs when cross-module behavior changes:

- frame order
- scene/component model
- scripting API
- render pipeline
- event/physics/UI behavior
- testing commands or labels

## Legacy Docs

Older long-form docs are still useful as deep references, but they are not the preferred first-read path. If a legacy doc conflicts with the small docs, verify against code and then update the small docs plus the legacy doc or mark the legacy doc stale.

## Completion Checklist

- `CURRENT_TASK.md` reflects the final status.
- All changed behavior has matching module/topic doc updates.
- `PROJECT_MEMORY.md` is updated for durable decisions.
- `CONSTRAINTS.md` is updated for new or changed rules.
- Final response lists docs changed and validation run.
