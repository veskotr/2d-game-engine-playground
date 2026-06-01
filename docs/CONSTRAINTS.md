# Project Constraints

This file is the compact rulebook for code and documentation work.

## Architecture

- Lower modules must not depend on higher modules.
- Cross-module access should go through public interfaces, value types, handles, or runtime `Context`.
- `Systems` owns orchestration and may coordinate multiple modules.
- Avoid global mutable state; prefer ownership through runtime objects.
- Do not introduce circular module dependencies.

## ECS

- Entities are lightweight IDs.
- Components are data-centric and default-constructible when practical.
- Behavior belongs in systems.
- Systems should query through `Registry::view` and component access helpers.
- Transform mutation must preserve dirty propagation.

## Scripting

- One Lua VM is owned by `ScriptEngine`.
- Lua-facing access goes through registered bindings and runtime bridge APIs.
- Scripts should not hold raw C++ ownership.
- Lua errors should be logged and contained instead of crashing the engine.

## Rendering

- Scene data is converted to render commands before it reaches the renderer.
- Renderer owns GPU objects, batching, sorting, and upload strategy.
- Renderer should not know about Lua or entity lifecycle.

## Testing

- Prefer focused integration tests for subsystem behavior.
- Use smoke tests for runtime boot/shutdown coverage.
- Mark OpenGL-window tests with `windowed`.
- Run the narrowest meaningful CTest label during development, and broader tests before handoff when feasible.

## Documentation

- Update docs in the same task as code behavior changes.
- Keep new docs small and topic-specific.
- Put temporary handoff details in `docs/CURRENT_TASK.md`.
- Put durable decisions in `docs/PROJECT_MEMORY.md`.
- Put rules and invariants here.
- Update affected `docs/modules/*.md` and `docs/topics/*.md`.

## Style

- C++ standard: C++20 per current CMake configuration.
- Prefer existing naming and folder conventions.
- Keep headers minimal and implementations in `.cpp` files where possible.
- Avoid broad refactors unless they are required for the task.
