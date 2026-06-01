# Agent Architecture Context Pack

Use this file as the first read for any architecture-sensitive task.

## 1. One-Page System Model

Dependency chain:

`Core -> Events -> Platform -> Renderer -> Resources -> Scene -> Physics -> Scripting -> UI -> Systems -> Sandbox`

Non-negotiable rule:

- lower layers do not depend on higher layers
- cross-layer access happens via public interfaces and Context injection

## 2. Ownership Boundaries

- Core: logging, timing, result/error types, EngineConfig
- Events: EventBus, event structs (CollisionEvents, ZoneEvents, ScriptEvents, etc.), ScopedSubscription
- Platform: window, input
- Renderer: GPU resources, batching, draw submission, Camera2D
- Resources: asset loading + cache ownership
- Scene: ECS entities, components, hierarchy, scene event bus holder
- Physics: Box2D world, body/fixture lifecycle, contact events
- Scripting: Lua VM lifecycle, bindings, script callback execution via ScriptApi
- UI: XML layout documents, binding context, UI rendering
- Systems: Runtime, frame orchestration systems (transform/script/physics/render/audio/animation/UI/state-machine)
- Sandbox/examples: game-side usage and demos

## 3. Frame Pipeline (Current)

1. process pending scene switches
2. update input
3. tick timer
4. window poll events + resize handling
5. build Context
6. flush deferred event queues (scene + global buses)
7. animation update
8. audio update (play/stop request processing)
9. transform update
10. script update
11. state machine update
12. physics update
13. renderer beginFrame
14. render-system submit commands (sprites + physics debug)
15. UISystem update
16. renderer endFrame
17. swap buffers

## 4. Critical Architecture Invariants

- Scene is data and structure; no rendering or Lua ownership in Scene types.
- Renderer consumes commands; it does not query ECS directly.
- Script access to engine services goes through ScriptApi.
- Transform mutations must go through setters to maintain dirty propagation.
- New components are data-centric and serializable; methods are allowed only to enforce invariants (e.g. dirty flags).

## 5. First Files To Open By Task Type

Architecture changes:

- `docs/ARCHITECTURE_VERIFIED.md`
- `docs/IMPLEMENTATION_OVERVIEW.md`
- `docs/ENGINE_MASTER_PLAN.md`

ECS/components:

- `docs/SCENE_ECS_CURRENT.md`
- `docs/COMPONENT_SYSTEM_GUIDE.md`

Scripting/API:

- `docs/SCRIPTING_CURRENT.md`
- `docs/LUA_IMPLEMENTATION_QUICKSTART.md`

Rendering/perf:

- `docs/RENDERING_CURRENT.md`
- `docs/OPTIMIZATIONS_CURRENT.md`

Physics/events:

- `docs/BOX2D_PHYSICS_INTEGRATION_PLAN.md`
- `docs/EVENT_SYSTEM_IMPLEMENTATION_PLAN.md`

## 6. Change-Safety Checklist

Before coding:

- confirm affected modules do not violate dependency direction
- confirm whether docs reflect current implementation or future plan
- identify one validation path in sandbox/example

After coding:

- update `docs/IMPLEMENTATION_OVERVIEW.md` if runtime behavior changed
- update subsystem doc for changed area
- update `docs/ENGINE_MASTER_PLAN.md` if roadmap priorities changed

## 7. Task-Finish Protocol (Mandatory)

Before declaring any task done, execute all checks:

1. Architecture truth check:

- if module boundaries, ownership, frame order, or core API changed, update `docs/ARCHITECTURE_VERIFIED.md`

2. Runtime truth check:

- if behavior in the frame loop or system flow changed, update `docs/IMPLEMENTATION_OVERVIEW.md`

3. Subsystem coverage check:

- update the subsystem doc touched by the change (Scene, Scripting, Rendering, Physics, UI, Components)

4. Plan alignment check:

- if priority or sequencing changed, update `docs/ENGINE_MASTER_PLAN.md`

5. Agent retrieval check:

- if first-read guidance or invariants changed, update this file

6. Final-report contract:

- list all updated docs in the completion summary
- if no docs were changed, include a one-line reason

## 8. Canonical Planning Source

- Roadmap and sequencing: `docs/ENGINE_MASTER_PLAN.md`
- Detailed implementation notes: subsystem plan docs
- Historical design context: `docs/ARCHITECTURE.md`
