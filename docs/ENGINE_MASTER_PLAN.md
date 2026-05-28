# SLE Engine Master Plan

This document is the single planning hub for near-term engine work.

Use it to decide what to build next, how to break the work into vibecodable slices, and which older docs are now reference material instead of decision-making material.

## 1. What This Plan Replaces

This file combines the practical roadmap material that used to live across:

- `ARCHITECTURE.md`
- `IMPLEMENTATION_OVERVIEW.md`
- `SCENE_ECS_CURRENT.md`
- `SCRIPTING_CURRENT.md`
- `RENDERING_CURRENT.md`
- `UI_PROGRESS.md`
- `COMPONENT_SYSTEM_GUIDE.md`
- `LUA_IMPLEMENTATION_QUICKSTART.md`
- `EVENT_SYSTEM_IMPLEMENTATION_PLAN.md`
- `BOX2D_PHYSICS_INTEGRATION_PLAN.md`

The older documents still matter, but only as source notes, subsystem references, or implementation detail archives.

## 2. Canonical Reading Order

If you only want the shortest path to the truth, read in this order:

1. `ARCHITECTURE_VERIFIED.md` for the verified module architecture and boundaries.
2. `IMPLEMENTATION_OVERVIEW.md` for the current frame loop and runtime behavior.
3. `ENGINE_MASTER_PLAN.md` for the implementation order and feature roadmap.
4. `UI_PROGRESS.md` only for the UI-specific checklist and current status.

## 3. Planning Principles

- Build foundation first, then feature layers.
- Prefer reusable engine systems over feature-specific hacks.
- Keep Scene data-only and let Systems perform behavior.
- Keep Lua and runtime services behind narrow APIs.
- Make every feature small enough to finish in one or two focused implementation passes.
- Use data-driven assets whenever a feature needs designer-facing configuration.

## 4. Test Architecture (Build This Before New Features)

Goal: define one exact path for writing tests so every new feature lands with predictable coverage.

### 4.1 Testing Model

- Primary confidence comes from integration tests and smoke tests.
- Unit tests are targeted to pure logic and critical edge cases only.
- Every feature must add at least one deterministic terminal-verifiable test path.

Recommended ratio:

- ~70% integration tests
- ~20% smoke tests
- ~10% focused unit tests

### 4.2 Test Folder and Naming Convention

Create this structure once and reuse it for all phases:

- `tests/CMakeLists.txt`
- `tests/harness/`
- `tests/integration/`
- `tests/smoke/`
- `tests/unit/`
- `tests/data/` for deterministic test scenes/assets/scripts

Naming convention:

- integration: `integration_<subsystem>_<scenario>.cpp`
- smoke: `smoke_<feature_or_example>.cpp`
- unit: `unit_<module>_<behavior>.cpp`

### 4.3 CMake and Execution Path

Use one standard path so contributors and AI runs are consistent:

1. Enable CTest in root CMake and add `add_subdirectory(tests)`.
2. Build tests through the same preset as engine code.
3. Register tests with labels: `unit`, `integration`, `smoke`, and subsystem labels like `physics`, `scripting`, `events`, `ui`, `renderer`.

Terminal execution contract:

- fast gate: `ctest -L integration --output-on-failure`
- smoke gate: `ctest -L smoke --output-on-failure`
- full gate: `ctest --output-on-failure`

### 4.4 Standard Test Case Template

Every new test should follow the same layout:

1. Arrange: create scene/world/script/data fixture.
2. Act: run a fixed frame count or deterministic operation.
3. Assert: verify component state, event delivery, API result, or emitted log marker.
4. Teardown: clear scene/subscriptions/resources explicitly.

### 4.5 Definition of Done for Any Feature PR

- one happy-path integration test added
- one edge/failure-path integration test added
- one smoke path updated or added if runtime-visible behavior changed
- terminal output remains deterministic enough for CI/automation

### 4.6 Module-by-Module Unit Test Strategy

Use unit tests where logic is deterministic and isolated. Prefer integration tests where behavior depends on orchestration, external libraries, or runtime state.

Core:

- unit test now: math helpers, time utilities, result/error helpers, event queue ordering semantics
- integration first: cross-module logging flows

Platform:

- unit test now: input state transitions, edge-trigger detection, camera math helpers
- integration first: window lifecycle, GLFW event pump behavior

Renderer:

- unit test now: sort keys/layer ordering logic, batch grouping decisions, UV/quad helper math
- integration first: OpenGL state correctness and GPU driver interactions

Resources:

- unit test now: cache key resolution, path normalization, lookup/fallback rules
- integration first: actual file IO and graphics resource upload

Scene:

- unit test now: registry invariants, parent-child hierarchy rules, destroy/reparent edge cases
- integration first: scene + systems interaction over frame updates

Events:

- unit test now: subscribe/unsubscribe lifetime, deferred queue drain order, duplicate/unsubscribe edge cases
- integration first: physics-contact-to-event flow and scene teardown behavior

Physics:

- unit test now: conversion helpers, category/mask filtering helpers, deterministic utility calculations
- integration first: Box2D stepping, contacts, sensor/zone enter-exit behavior

Scripting:

- unit test now: data marshalling helpers, Lua argument validation, error translation boundaries
- integration first: end-to-end Lua lifecycle (`init/update/destroy`) with real scene entities

UI:

- unit test now: layout solver rules, focus traversal algorithm, binding expression parsing/helpers
- integration first: full document load, runtime bindings, and interactive callbacks

Systems/Runtime:

- unit test now: small stateless scheduling/order helpers only
- integration first: full frame loop behavior and subsystem ordering guarantees

### 4.7 When to Write Unit Tests: Now vs Later

Write unit tests now when:

1. logic is pure and small (math, state transitions, ordering, parsing)
2. a bug was fixed in deterministic logic and needs regression protection
3. failure in this code would silently corrupt state

Defer unit tests until later when:

1. behavior is mostly orchestration already covered by integration tests
2. assertions would be tightly coupled to unstable implementation details
3. the same confidence is achieved cheaper with one deterministic integration scenario

Practical default for this engine:

- do not wait for a giant unit-test phase at the end
- add targeted unit tests continuously for high-value logic while building features
- keep unit tests focused and small; avoid broad mock-heavy tests with low signal

## 5. Recommended Build Order

### Phase 0: Documentation Cleanup

Goal: remove duplication and make one obvious place to plan work.

Tasks:

1. Keep this file as the primary roadmap.
2. Turn the older plan docs into references or short summaries.
3. Update the doc index so this file is the first planning stop.
4. Mark any superseded architecture docs as historical if they conflict with verified behavior.

Done when:

- New contributors can find the current plan in one click.
- No subsystem doc is needed to understand the build order.
- Each older doc has a clearly defined purpose or pointer.

### Phase 0.5: Testing Foundation

Goal: make tests first-class so later feature work has a fixed, repeatable path.

Tasks:

1. Add `tests/` tree and base CMake wiring.
2. Add a reusable harness for scene bootstrap, fixed-step frame execution, and deterministic logging capture.
3. Add CTest labels for `unit`, `integration`, `smoke`, plus subsystem labels.
4. Add one sentinel smoke test that launches a minimal scene and verifies boot/update/shutdown markers.

Done when:

- one command runs integration tests and returns non-zero on failures
- one command runs smoke tests and surfaces readable failure output
- new contributors can add a test by copying one existing file and changing fixture/asserts

Step-by-step rollout:

1. Step 1: scaffold `tests/` + CMake/CTest wiring only.
2. Step 2: add shared harness for fixed-step frame driving and log capture.
3. Step 3: add one sentinel smoke test (engine boot/update/shutdown path).
4. Step 4: add one integration test each for events, physics, scripting, UI, renderer (minimal happy paths).
5. Step 5: add one edge/failure integration test each for events, physics, scripting, UI, renderer.

### Phase 0.5, Step 1 (Start Here Now)

Goal: get the test execution pipeline alive before adding real test logic.

Deliverables:

1. root CMake enables CTest and includes `add_subdirectory(tests)`.
2. `tests/CMakeLists.txt` exists and registers at least one placeholder test target.
3. placeholder test is labeled `smoke` and can run under CTest.
4. README or docs include exact commands for build + test run.

Exit criteria:

- `ctest -L smoke --output-on-failure` executes and reports pass/fail
- failure in placeholder test returns non-zero exit code
- this step lands with no functional engine behavior changes

### Phase 1: Event Foundation

Goal: make the event layer safe enough to support physics, state changes, quests, triggers, and UI callbacks.

Why this comes first:

- physics contacts need deferred dispatch
- scene switches need clean subscription lifetime handling
- gameplay systems will depend on events immediately

Work items:

1. Fix the event bus lifetime and frame flow.
2. Replace any per-frame subscription clearing with explicit subscription management.
3. Queue events from physics callbacks instead of emitting synchronously inside Box2D contact code.
4. Make scene teardown clear subscriptions intentionally.
5. Fix any stale event-bus injection across scene or world swaps.

Suggested file targets:

- `EngineModules/Core/include/sle/core/EventBus.hpp`
- `EngineModules/Systems/src/Runtime.cpp`
- `EngineModules/Systems/src/PhysicsSystem.cpp`
- `EngineModules/Physics/src/ContactListener.cpp`
- `EngineModules/Scene/src/Scene.cpp`

Done when:

- event dispatch is safe during physics callbacks
- scene switches do not lose listeners unexpectedly
- subscriptions survive a normal frame unless explicitly removed
- a simple listener can observe collision or zone events across multiple frames
- event integration tests cover deferred dispatch, subscription lifetime, and scene teardown behavior

### Phase 2: Generic Scriptable State Machine

Goal: add one reusable state machine system that can drive animation, behavior, quests, UI modes, and scripted gameplay flow.

This is the feature that gives you the most leverage for future work.

Recommended shape:

- a data-only state machine component on entities
- a runtime system that evaluates transitions each frame
- optional event-driven transitions
- optional timed transitions
- optional Lua callbacks for enter, update, exit, and transition guards
- serializable state definitions so content can live in assets

Suggested runtime model:

- `StateMachineComponent` stores the active definition asset, current state, enabled flag, and runtime cache.
- `StateMachineDefinition` describes states, transitions, conditions, and action hooks.
- `StateMachineSystem` resolves transitions and dispatches lifecycle callbacks.
- Lua can start, stop, force, or inspect the current state.

What it should support:

- animation states like idle, run, jump, hit, death
- behavior states like patrol, chase, attack, flee
- quest states like not started, active, objective complete, reward ready
- UI states like hidden, focused, pressed, disabled
- scripted scene flow like intro, gameplay, dialogue, victory

Implementation slices:

1. Define the component and the serialized asset format.
2. Add a minimal runtime evaluator with enter and exit callbacks.
3. Add transition rules for events, booleans, timers, and Lua guards.
4. Expose a minimal Lua API.
5. Add one example entity that proves the system works.

Suggested file targets:

- `EngineModules/Scene/include/sle/scene/components/StateMachineComponent.hpp`
- `EngineModules/Scene/include/sle/scene/components/StateMachineDefinition.hpp`
- `EngineModules/Systems/include/sle/engine/StateMachineSystem.hpp`
- `EngineModules/Systems/src/StateMachineSystem.cpp`
- `EngineModules/Scripting/src/ScriptApiImpl.cpp`
- `EngineModules/Scripting/src/LuaBindings.cpp`

Minimum Lua surface:

- `Engine.getState(entity)`
- `Engine.setState(entity, stateName)`
- `Engine.isState(entity, stateName)`
- `Engine.sendStateEvent(entity, eventName)`

Done when:

- one entity can change state based on input or events
- one state machine can drive both gameplay behavior and animation selection
- one transition can be triggered from Lua or from a native event
- state definitions can be edited without recompiling the engine
- scripting integration tests validate native-event-to-Lua transitions and Lua-triggered transitions

Current status snapshot (May 2026):

- Implemented: component + definition + runtime evaluator + transition events
- Implemented: trigger/bool/timer transitions and serialized JSON state machine definitions
- Implemented: Lua state API surface (`setState`, `getState`, `isState`, `sendStateEvent`)
- Implemented: Lua guard transitions via boolean guard callbacks
- Implemented: sandbox/example and integration coverage for callbacks, assets, and Lua guard transitions
- Remaining before fully closing Phase 2: broaden native-event-driven scenarios as needed by gameplay content

### Phase 3: Animation Built on the State Machine

Goal: make animation a consumer of state instead of a separate ad-hoc feature.

Recommended strategy:

- use the state machine to choose animation clips or animation groups
- keep animation playback data separate from state logic
- support a simple first version before adding blending or graphs

Suggested component model:

- `AnimatorComponent` stores the current animation, playback time, speed, loop mode, and asset reference.
- optional animation clips are loaded from data assets.
- the state machine decides which clip should play.

Implementation slices:

1. Add animation asset loading and a simple clip representation.
2. Add the animator component and system.
3. Connect state names to animation names.
4. Expose play/stop/query helpers to Lua.
5. Add a demo character with idle and run states.

Suggested file targets:

- `EngineModules/Scene/include/sle/scene/components/AnimatorComponent.hpp`
- `EngineModules/Systems/include/sle/engine/AnimationSystem.hpp`
- `EngineModules/Systems/src/AnimationSystem.cpp`
- `EngineModules/Resources/` for animation asset loading if needed

Done when:

- a character can switch between at least two animations
- animation changes are driven by state or script
- update order does not cause one-frame desync between state and playback

### Phase 4: Audio

Goal: add basic sound effects and music playback with a small, stable API.

Recommended scope for the first version:

- one-shot sound effects
- looping music
- volume and pause/stop controls
- entity-bound audio for positional or contextual playback later

Suggested component model:

- `AudioComponent` stores asset path, loop flag, volume, pitch, and runtime handle.
- `AudioSystem` manages playback and synchronization.

Implementation slices:

1. Integrate the audio backend.
2. Add the audio component and playback system.
3. Add simple Lua helpers.
4. Make one demo action play a sound.
5. Add one looping music example.

Suggested file targets:

- `EngineModules/Scene/include/sle/scene/components/AudioComponent.hpp`
- `EngineModules/Systems/include/sle/engine/AudioSystem.hpp`
- `EngineModules/Systems/src/AudioSystem.cpp`
- `EngineModules/Scripting/src/ScriptApiImpl.cpp`

Done when:

- a sound effect can be triggered from Lua or gameplay code
- music can loop without special-case code in the sandbox
- audio playback does not block the frame loop

### Phase 5: UI Polish

Goal: finish the UI layer so it feels usable instead of merely functional.

Current next steps already identified in `UI_PROGRESS.md`:

- expand layout rules beyond absolute positioning
- add a small example scene and layout asset
- expose Lua-facing binding mutation helpers
- add keyboard focus and navigation
- add text wrapping, alignment, and fallback font chains

Suggested order:

1. Layout rules and sizing behavior.
2. Focus and navigation.
3. Text wrapping and alignment.
4. Font fallback and richer text handling.
5. Example scene polish.

Done when:

- UI can be used in a real sample without hardcoded positioning everywhere
- keyboard navigation works for at least buttons and fields
- labels do not break when text gets longer than expected
- UI integration tests validate focus navigation, binding updates, and layout behavior at multiple resolutions

### Phase 6: Gameplay Systems That Fit the State Machine

Goal: use the new reusable logic layer for behavior and quest flow instead of adding one-off scripts.

Good candidates:

- enemy AI state graphs
- quest progression states
- dialogue flow
- cutscene flow
- interactable objects

Implementation pattern:

1. Put the shared rules in the state machine.
2. Keep per-feature data in assets.
3. Use Lua or event hooks for specific game logic.
4. Keep the component small and serializable.

Done when:

- a quest or behavior can be expressed as a state definition plus a few hooks
- the engine does not need a new subsystem for every gameplay pattern

### Parallel Workstream: Integration Test Plan by Subsystem

Run this workstream continuously as each feature phase lands.

Events:

1. event queued during physics callback dispatches on the expected frame boundary
2. listeners survive normal frames and are removed only via explicit unsubscribe or scene teardown
3. scene switch does not leak old listeners or drop new listeners unexpectedly

Physics:

1. dynamic body falls and settles deterministically over fixed steps
2. sensor/zone enter-exit events fire once per transition
3. body/fixture lifecycle survives entity create-destroy cycles without stale handles

Scripting:

1. `init/update/destroy` Lua lifecycle order is correct for entity spawn and teardown
2. Lua can read/write component data via `ScriptApi` with type-safe behavior
3. script errors are reported without crashing the runtime loop

UI:

1. document loads and binds expected data values
2. keyboard focus traversal order is deterministic
3. interaction callbacks bridge to Lua/native handlers correctly

Renderer:

1. sprite command submission order honors layer and stable ordering rules
2. missing texture/shader fallback path is stable and non-crashing
3. physics debug overlay commands appear when debug mode is enabled

## 6. Feature-By-Feature Vibecoding Slices

Use this as the smallest useful task unit for an AI coding pass.

### Event System Slice

- fix one bug
- run the minimal build
- prove one event still works after the fix
- add or update one integration test for the fixed behavior

### State Machine Slice

- add the data struct
- add the system loop
- add one transition rule
- add one Lua helper
- prove one entity can change state
- add one transition integration test and one failure-path transition test

### Animation Slice

- add the animator component
- wire the system to the current state machine output
- prove a sprite changes animation on state switch
- add one integration assertion for state-to-animation mapping

### Audio Slice

- integrate the backend
- add a playback component
- expose one Lua helper
- prove one sound can play
- add one smoke check for non-blocking playback path

### UI Slice

- improve one layout rule
- add one keyboard navigation path
- validate one sample document
- add one integration test for focus or binding behavior

## 7. Definition of Done For New Work

Before a feature is considered done, it should satisfy all of these:

- the new code fits the existing module boundaries
- the feature has one minimal demo path in the sandbox
- the feature is serializable or asset-driven if it is content-facing
- the Lua API is small and explicit if scripts need it
- the old docs do not need to be read to use the feature
- required tests are present and pass under `ctest` labels

## 8. Documentation Cleanup Plan

This is the doc consolidation plan I recommend after the master plan exists.

Keep as canonical references:

- `ARCHITECTURE_VERIFIED.md`
- `IMPLEMENTATION_OVERVIEW.md`
- `ENGINE_MASTER_PLAN.md`

Keep as specialized references:

- `UI_PROGRESS.md`
- `COMPONENT_SYSTEM_GUIDE.md`
- `LUA_IMPLEMENTATION_QUICKSTART.md`

Treat as working-plan source material or historical detail:

- `ARCHITECTURE.md`
- `EVENT_SYSTEM_IMPLEMENTATION_PLAN.md`
- `BOX2D_PHYSICS_INTEGRATION_PLAN.md`

If you want the repository to feel cleaner, the next cleanup pass should shorten the older plan docs so they point here instead of repeating the same roadmap in full.
