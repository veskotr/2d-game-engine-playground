# Project Memory

This file stores durable project context that future agents should not have to recover from chat.

## Project Identity

SLE is a modular C++ 2D engine with ECS scene data, Lua scripting, command-based rendering, Box2D physics, an XML-driven UI system, and CTest coverage.

## Current Architecture Model

Module intent:

`Core -> Events -> Platform -> Renderer -> Resources -> Scene -> Physics -> Scripting -> UI -> Systems -> examples`

`Systems` is the orchestration layer. It may depend on lower engine modules and owns the runtime loop.

## Durable Decisions

- Components should stay data-centric and serializable.
- Systems own behavior and should query `Registry` views.
- Rendering flows through `RenderSystem` into renderer commands.
- Lua scripts use the `Engine` table backed by C++ bindings.
- Scene loading and runtime orchestration live in `Systems`, not in low-level modules.
- UI documents are attached through `UIComponent`; the UI module owns parsed document state.
- Tests are split into smoke, integration, windowed, and subsystem labels.

## Known Architecture Debt

- `Scene` currently depends on `Renderer` through `SpriteRenderer` and `TextureRegion`.
- Some namespaces do not match module ownership.
- Some systems still call `ScriptEngine` directly where a narrower script runtime boundary is desired.
- Legacy large docs remain in `docs/`; new small docs under `docs/modules/` and `docs/topics/` are the preferred first read.

## Current Worktree Note

As of the documentation cleanup, there were existing user/code changes in engine files. Documentation work should not revert or overwrite them.

## How To Update This File

Update this file only for context that should survive across multiple tasks:

- architecture decisions
- module ownership changes
- known debt that affects future work
- major roadmap status changes
- project-wide workflow changes
