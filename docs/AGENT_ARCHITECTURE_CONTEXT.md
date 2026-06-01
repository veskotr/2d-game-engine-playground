# Agent Architecture Context

This file remains as a compatibility entry point for agents that were told to open it first. The preferred starting point is now `docs/README_DOCUMENTATION.md`.

## Fast Reads

1. `CURRENT_TASK.md`
2. `PROJECT_MEMORY.md`
3. `CONSTRAINTS.md`
4. `topics/Architecture.md`
5. the affected `modules/*.md` and `topics/*.md` files

## Architecture Summary

Target module order:

`Core -> Events -> Platform -> Renderer -> Resources -> Scene -> Physics -> Scripting -> UI -> Systems -> examples`

Core invariants:

- Lower modules do not depend on higher modules.
- `Systems` coordinates runtime behavior.
- Components are data-centric; systems own behavior.
- Renderer consumes commands.
- Lua-facing engine access goes through bindings and runtime bridge APIs.

## Documentation Rule

When code changes, update the affected small docs in the same task. Use `DOCS_MAINTENANCE.md` as the checklist.
