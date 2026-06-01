# SLE Documentation Index

This is the first stop for project context. Prefer the small docs listed here before opening older long-form references.

## Start Here

1. `CURRENT_TASK.md` - what is happening now and handoff notes.
2. `PROJECT_MEMORY.md` - durable project context and known debt.
3. `CONSTRAINTS.md` - architecture, ECS, scripting, rendering, testing, and doc rules.
4. `DOCS_MAINTENANCE.md` - what to update before finishing a task.

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
| Scripting and Lua | `topics/Scripting.md` |
| Rendering | `topics/Rendering.md` |
| Events | `topics/Events.md` |
| Physics | `topics/Physics.md` |
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

## Agent Workflow

Before coding:

1. Read `CURRENT_TASK.md`.
2. Read `CONSTRAINTS.md`.
3. Open the affected module and topic docs.

Before finishing:

1. Update `CURRENT_TASK.md` with final status and validation.
2. Update affected module/topic docs.
3. Update `PROJECT_MEMORY.md` for durable context.
4. Update `CONSTRAINTS.md` if rules changed.
5. Mention docs changed in the final response.
