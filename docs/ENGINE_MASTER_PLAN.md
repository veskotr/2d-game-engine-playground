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

## 4. Recommended Build Order

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

## 5. Feature-By-Feature Vibecoding Slices

Use this as the smallest useful task unit for an AI coding pass.

### Event System Slice

- fix one bug
- run the minimal build
- prove one event still works after the fix

### State Machine Slice

- add the data struct
- add the system loop
- add one transition rule
- add one Lua helper
- prove one entity can change state

### Animation Slice

- add the animator component
- wire the system to the current state machine output
- prove a sprite changes animation on state switch

### Audio Slice

- integrate the backend
- add a playback component
- expose one Lua helper
- prove one sound can play

### UI Slice

- improve one layout rule
- add one keyboard navigation path
- validate one sample document

## 6. Definition of Done For New Work

Before a feature is considered done, it should satisfy all of these:

- the new code fits the existing module boundaries
- the feature has one minimal demo path in the sandbox
- the feature is serializable or asset-driven if it is content-facing
- the Lua API is small and explicit if scripts need it
- the old docs do not need to be read to use the feature

## 7. Documentation Cleanup Plan

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
