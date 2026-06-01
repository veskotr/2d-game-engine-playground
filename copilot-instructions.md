---
name: sle-2d-engine
description: "Agent instructions for SLE, a modular C++ 2D engine."
---

# SLE Agent Instructions

Read this file first, then keep the repo documentation fresh as you work.

## First Reads

1. `docs/CURRENT_TASK.md` - active work, decisions, and handoff notes.
2. `docs/PROJECT_MEMORY.md` - durable project context.
3. `docs/CONSTRAINTS.md` - architecture, testing, and style constraints.
4. `docs/README_DOCUMENTATION.md` - map to module and topic docs.

Open the specific module/topic files for the code you are changing. Do not rely on chat history as the source of truth.

## Non-Negotiable Rules

- Keep module dependencies one-way. Lower modules must not depend on higher modules.
- Put data in components and behavior in systems.
- Keep Renderer command-driven; it must not query Scene or Lua directly.
- Route Lua-facing engine access through `ScriptApi` / runtime-owned bridges.
- Preserve transform dirty propagation by using the existing setters and hierarchy flow.
- Treat docs as part of the change: update the task, memory, constraints, and affected module/topic docs when behavior changes.

## Current Module Order

`Core -> Events -> Platform -> Renderer -> Resources -> Scene -> Physics -> Scripting -> UI -> Systems -> examples`

Known code reality: `Scene` currently links `Renderer` because `SpriteRenderer` includes `TextureRegion`. Track this as a known architecture issue unless you are actively fixing it.

## Documentation Update Gate

Before marking work complete:

1. Update `docs/CURRENT_TASK.md` with status, files touched, validation, and next steps.
2. Update `docs/PROJECT_MEMORY.md` for durable decisions or changed project truth.
3. Update `docs/CONSTRAINTS.md` when rules, boundaries, build/test gates, or invariants change.
4. Update affected files in `docs/modules/` and `docs/topics/`.
5. If docs do not need changes, state why in the final response.

## Build And Test

Typical commands from the repository root:

```powershell
cmake --preset debug
cmake --build build/debug --config Debug
ctest --test-dir build/debug -C Debug --output-on-failure
```

Use narrower CTest labels when appropriate:

```powershell
ctest --test-dir build/debug -C Debug -L integration --output-on-failure
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
```

## Working Style

- Prefer existing patterns over new abstractions.
- Keep changes scoped to the requested task.
- Do not overwrite unrelated user changes in a dirty worktree.
- When changing public behavior, add or update focused tests.
- Final summaries should list code changes, doc changes, and validation run.
