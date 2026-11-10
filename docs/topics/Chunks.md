# Chunk activity and gameplay state

`ChunkSceneRuntimeState` updates camera-driven activity before simulation.
`ChunkActivationController` writes only the runtime `suspendedSubsystems` mask on
`ChunkActivationComponent`. It never changes gameplay `enabled` or `visible` flags.
Consumers combine gameplay intent with `isChunkActive(registry, entity, subsystem)`.
An entity without chunk metadata is active by default. The mask is derived runtime
state and is not authored scene data.

## Policies

Global Disabled/EventsOnly modes impose no suspension. With global AutoToggle,
per-entity EventsOnly and AlwaysActive opt out; Inherit uses the global mode.
`affectsActivation=false` opts out. Each policy update resets and recomputes masks,
so switching to an opt-out releases old suspension. Refreshing scene runtime state
also clears masks, including when no enabled map is found.

The affect flags independently control scripts, state machines, animation, physics,
collision, zones, UI, and sprites. `keepScriptsWhenInactive` preserves script updates;
`keepPhysicsWhenInactive` preserves physics/collision/zones; `keepRenderingWhenInactive`
preserves sprite/UI presentation. These options never force gameplay-disabled
components to become enabled or visible.

## Consumers and lifetime

- Scripts pause per-entity updates without destroying existing Lua instances. Initial
  script loading waits for activity. Entity state-machine callbacks respect the
  script gate. Explicitly gameplay-disabled/removed scripts retain their prior
  lifecycle behavior.
- Animation and state-machine clocks stop while their subsystem is suspended.
- Rendering and UI stop submitting geometry while suspended. Cached UI documents
  remain cached; initial entity UI resource/behavior loading waits for activity.
  Presentation gates do not modify gameplay visibility or reload cached UI scripts.
- Physics disables existing Box2D bodies before stepping. Their bodies, fixtures,
  and velocities remain allocated, with simulation/contacts suspended. On resume,
  the latest local pose is synchronized even if rendering already cleared its dirty
  flag while the body was inactive.
- Active bodies reconcile collider and zone fixtures with each component's gameplay
  enabled flag and subsystem gate. Disabled fixtures are removed and enabled ones
  are recreated as needed. Disabling a whole body suppresses all its contacts,
  regardless of independent collision/zone policy. Zone metadata stays available
  during synchronous Box2D exit callbacks.

Entity-owned Lua event subscriptions now respect chunk script suspension while
retaining callback references for resume. Subscriptions created by top-level global
assets remain scene-global. Audio, transform hierarchy traversal, Lua global
state-machine guards, and global UI callback execution keep their own policies.
See [Events](Events.md) for ownership and cleanup boundaries.

## Validation

`integration_chunk_gameplay_state_path` exercises gameplay intent, Lua state,
animation/state-machine pause/resume, actual Box2D motion, local edits while paused,
fixture-only suspension, policy opt-out, and named zone exit events. It needs no GUI.

`integration_chunk_render_visibility_path` checks framebuffer pixels through the
real RenderSystem and UISystem for active, suspended, resumed, and gameplay-hidden
content. It is labeled `windowed` and skips with code 125 without GLFW windowing.
The existing controller/runtime/tile tests check effective activity rather than
expecting chunk logic to overwrite component flags.
