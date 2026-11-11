# SLE Documentation Index

This is the first stop for project context. Prefer the small docs listed here before opening older long-form references.

## Start Here

1. [AGENTS.md](../AGENTS.md) - repository instructions and map.
2. [CONTRIBUTING.md](../CONTRIBUTING.md) - setup and validation.
3. [CONSTRAINTS.md](CONSTRAINTS.md) - architecture and subsystem invariants.
4. [DOCS_MAINTENANCE.md](DOCS_MAINTENANCE.md) - documentation ownership.
5. [PROJECT_MEMORY.md](PROJECT_MEMORY.md) - durable context and known debt.

`CURRENT_TASK.md` contains a historical chunked-world handoff. Confirm its status
against the current request and code before using it. Plans describe intent,
not proof of completion.

## Module Docs

| Module | File |
|---|---|
| Core | `modules/Core.md` |
| Events | `modules/Events.md` |
| Platform | `modules/Platform.md` |
| Renderer | `modules/Renderer.md` |
| Resources | `modules/Resources.md` |
| Scene | `modules/Scene.md` |
| Physics | `modules/Physics.md` |
| Scripting | `modules/Scripting.md` |
| UI | `modules/UI.md` |
| Systems | `modules/Systems.md` |

## Topic Docs

| Topic | File |
|---|---|
| Architecture | `topics/Architecture.md` |
| Runtime frame loop | `topics/Runtime.md` |
| ECS and components | `topics/ECS.md` |
| Scene files and stable references | [Scenes](topics/Scenes.md) |
| Multiple scripts and scene behaviours | [Behaviours](topics/Behaviours.md) |
| Prefab subtrees and overrides | [Prefabs](topics/Prefabs.md) |
| Scripting and Lua | `topics/Scripting.md` |
| Rendering | `topics/Rendering.md` |
| Events | `topics/Events.md` |
| Physics | `topics/Physics.md` |
| Chunk activity | [Chunks](topics/Chunks.md) |
| UI | `topics/UI.md` |
| Testing | `topics/Testing.md` |
| Roadmap | `topics/Roadmap.md` |

## Legacy And Deep References

These files are still useful for detailed background, but they may overlap with the smaller docs:

- `ARCHITECTURE_VERIFIED.md`
- `ARCHITECTURE.md`
- `IMPLEMENTATION_OVERVIEW.md`
- `ENGINE_MASTER_PLAN.md`
- `COMPONENT_SYSTEM_GUIDE.md`
- `SCRIPTING_CURRENT.md`
- `SCENE_ECS_CURRENT.md`
- `RENDERING_CURRENT.md`
- `UI_PROGRESS.md`
- `UI_IMPLEMENTATION_PLAN.md`
- `BOX2D_PHYSICS_INTEGRATION_PLAN.md`
- `EVENT_SYSTEM_IMPLEMENTATION_PLAN.md`
- `OPTIMIZATIONS_CURRENT.md`
- `KNOWN_ISSUES.md`

If a legacy doc conflicts with the small docs, verify against code and update both the relevant small doc and the legacy doc or mark the legacy doc stale.

## Agent workflow

Follow [AGENTS.md](../AGENTS.md), read affected module/topic references on demand,
and update documents whose facts changed. For substantial work, follow
[PLANS.md](../PLANS.md). Do not update every context file for routine tasks.
