# Working on SLE

SLE (Simple Little Engine) is a C++20 2D engine using CMake, ECS scene data,
Lua 5.4, OpenGL, Box2D, and XML UI. Systems owns runtime orchestration.

## Start here

- Read [README.md](README.md) for setup and [CONTRIBUTING.md](CONTRIBUTING.md) for validation.
- Open only the relevant [module and topic docs](docs/README_DOCUMENTATION.md).
- Read [constraints](docs/CONSTRAINTS.md) before changing engine behavior.
- Treat code and CMake targets as evidence of current behavior. Plans and old
  “verified” documents can describe historical or unfinished work.
- The user's current request defines the task. Do not resume work merely because
  `docs/CURRENT_TASK.md` or a roadmap says it is active.

## Repository map

- `EngineModules/*/include/sle/`: public module headers; `src/`: implementations.
- `EngineModules/Systems/`: runtime, scene loading, system orchestration, ScriptApiImpl.
- `engine_app/`: config-driven executable using `engine.json`.
- `examples/`: sandbox and event demonstrations.
- `tests/`: smoke tests, integration executables, shared harness, and fixtures.
- `assets/`: shared scenes, Lua, shaders, UI, sounds, and generated API stub.
- `cmake/Dependencies.cmake`: FetchContent dependencies.

## Architecture and coding

- Keep module dependencies acyclic. Inspect affected `target_link_libraries`
  declarations rather than assuming the modules form a single linear chain.
- Lower modules must not acquire orchestration dependencies on Systems.
- Existing Scene-to-Renderer coupling through `SpriteRenderer`/`TextureRegion`
  is known debt; changing it is a separate task.
- Components hold data; systems own behavior. Existing invariant-preserving
  accessors are intentional: use Transform setters to preserve dirty propagation.
- Use Registry views and existing entity/component access patterns. Respect entity
  lifetime; do not retain component references across structural mutations.
- Renderer consumes render commands; keep scene queries in RenderSystem.
- Lua access goes through registered bindings and ScriptApi/runtime bridges.
  Keep raw ownership out of Lua and contain script errors.
- Match nearby naming, formatting, logging, error handling, and ownership patterns.
  Avoid unrelated rewrites, dependency upgrades, or bulk line-ending changes.

## Workflow

- Check `git status` and preserve all pre-existing edits. Never reset the worktree
  or overwrite unrelated changes to simplify a task.
- For substantial multi-module work, use [PLANS.md](PLANS.md). Simple edits need no plan file.
- Add focused regression coverage for behavior changes. Follow [test guidance](tests/AGENTS.md).
- Update only docs affected by the change; use [documentation maintenance](docs/DOCS_MAINTENANCE.md).
- Report the result, commands actually run, and remaining limitations. Never label
  a historical test result or an unexecuted command as current validation.

## Review guidance

Prioritize reproducible correctness problems: lifetime and teardown, invalid
entity access, transform propagation, event dispatch ordering, Lua boundary
errors, and changes to scene/config compatibility. Explain the trigger and impact
and cite the affected code. Separate confirmed bugs from suggestions.
