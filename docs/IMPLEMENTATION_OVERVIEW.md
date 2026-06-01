# SLE Implementation Overview

This document is the current-state map of the engine. It is meant to answer two questions:

1. What does the engine do at a high level?
2. How do the recent implementations fit together?

Some older docs in this repo describe intended design or partially outdated target-state APIs. This file reflects the code that is currently implemented.

**Last updated: May 2026 — Phases 0–5 complete.**

## 1. Big Picture

SLE is a modular 2D engine built around a simple frame loop:

1. Poll input and window events.
2. Resolve transforms.
3. Run scripts.
4. Run physics.
5. Build render commands.
6. Submit and flush rendering.
7. Swap buffers.

The main application entry point is the Sandbox project. It configures a `Runtime`, registers one or more scenes, loads the startup scene, then calls `Runtime::run()`.

## 2. Module Layout

The engine is split into layered modules with one-way dependencies:

- **Core**: logging, timers, result/error helpers, config, EngineConfig
- **Events**: EventBus, event structs (CollisionEvents, ZoneEvents, ScriptEvents, StateMachineEvents, etc.), ScopedSubscription
- **Platform**: window creation, input handling, GLFW integration
- **Renderer**: OpenGL shaders, textures, Camera2D, commands, batch submission
- **Resources**: asset loading and cached resource pools
- **Scene**: entities, registry, components, hierarchy, scene lifetime
- **Physics**: Box2D integration, body/fixture lifecycle, contact events, zone sensors
- **Scripting**: Lua VM, Lua bindings, script resource handling, script lifecycle
- **UI**: document model, layout parser, binding context, rendering, font atlas
- **Systems**: orchestration systems that drive transforms, scripts, physics, animation, audio, UI, state machines, runtime flow
- **Sandbox**: example game/application using the engine

The important rule is that lower layers do not depend on higher layers. For example, Renderer does not know about Lua, and Scene does not know about rendering.

**Verified module order in CMake**: `Core → Platform → Renderer → Resources → Scene → Events → Physics → Scripting → UI → Systems → Sandbox`

## 3. Runtime Flow

`Runtime` is the top-level orchestrator.

It owns:
- the window
- the camera
- the renderer
- the active scene and scene manager
- the script engine
- the physics world
- all per-frame systems

Per frame, `Runtime::run()` currently does this:

1. `SceneManager::processPendingSwitch()`
2. `Input::update()`
3. `Timer::tick()`
4. Handle resize and escape key
5. `AnimationSystem::update(scene, dt)`
6. `TransformSystem::update(ctx)`
7. `ScriptSystem::update(ctx)`
8. `StateMachineSystem::update(scene, dt, &scriptEngine)`
9. `PhysicsSystem::update(ctx)`
10. `AudioSystem::update(scene)`
11. `renderer.beginFrame()`
12. `RenderSystem::update(ctx)`
13. `UISystem::update(ctx)`
14. `renderer.endFrame()`
15. `window.swapBuffers()`

A profiling log in `Runtime` also prints averaged per-phase timings once per second.

## 4. Scene and ECS

The Scene module is a pure data and hierarchy layer.

It owns:
- entity lifetime
- parent/child relationships
- the component registry
- event bus

Important components currently used by the engine:

| Component | Purpose |
|-----------|---------|
| `TransformComponent` | Local position, rotation, scale, dirty flags, parent link |
| `WorldTransformComponent` | Computed world-space transform (written by TransformSystem) |
| `SpriteRenderer` | Texture, UV rect, color, layer |
| `ScriptComponent` | Script asset path and runtime script state |
| `StateMachineComponent` | Active state machine definition asset, current state, runtime cache |
| `AnimatorComponent` | Active clip, playback time, speed, loop mode, target entity map |
| `RigidBodyComponent` | Box2D body type, mass, damping, gravity scale |
| `BoxColliderComponent` / `CircleColliderComponent` | Fixture shape and physics material |
| `BoxZoneComponent` / `CircleZoneComponent` | Sensor zones that emit zone.enter/zone.exit events |
| `AudioComponent` | Asset path, loop, volume, pitch, play/stop request flags, state |
| `UIComponent` | Layout XML asset, font asset, behavior script, space mode, layer |

## 5. Transform Pipeline

Transform handling is split into two parts:

- `TransformComponent` stores local position, rotation, scale, parent, and a dirty flag
- `TransformSystem` computes `WorldTransformComponent` each frame using iterative depth-first traversal

The important implementation detail is that transform fields are private and mutated through setters so dirty propagation stays correct.

## 6. Scripting Architecture

Scripting is built around a single global Lua VM owned by `ScriptEngine`.

Current script flow:

1. `Runtime` creates and initializes `ScriptEngine`.
2. `ScriptEngine` registers Lua bindings.
3. `ScriptSystem` finds active `ScriptComponent`s each frame.
4. For each entity, `ScriptEngine::ensureScript()` loads the Lua file through the `Resources` system.
5. Lua `init`, `update`, and `destroy` callbacks are stored and called through registry references.
6. When an entity disappears, `ScriptEngine::syncEntities()` removes stale script instances.

### Current Lua-facing API (Engine.* table)

**Core / Entity**
- `Engine.getDeltaTime()`, `Engine.getWindowSize()`
- `Engine.createEntity()`, `Engine.destroyEntity(e)`, `Engine.isEntityAlive(e)`
- `Engine.setParent(child, parent)`, `Engine.getParent(e)`, `Engine.getChildCount(e)`, `Engine.destroyChildren(e)`

**Transform**
- `Engine.getTransformPosition(e)`, `Engine.setTransformPosition(e, x, y)`
- `Engine.getTransformScale(e)`

**Sprite**
- `Engine.loadTexture(path)`, `Engine.setSpriteTexture(e, path)`

**Scene**
- `Engine.hasScene(name)`, `Engine.switchScene(name)`, `Engine.getCurrentSceneName()`

**State Machine**
- `Engine.setState(e, state)`, `Engine.getState(e)`, `Engine.isState(e, state)`, `Engine.sendStateEvent(e, event)`

**Animator (Phase 3)**
- `Engine.AnimatorPlay(e, clip)`, `Engine.AnimatorStop(e)`, `Engine.AnimatorPause(e)`, `Engine.AnimatorResume(e)`
- `Engine.setAnimatorSpeed(e, speed)`, `Engine.setAnimatorTime(e, t)`, `Engine.isAnimatorPlaying(e)`, `Engine.getAnimatorTime(e)`
- `Engine.setAnimationTarget(e, name, targetEntity)`

**Audio (Phase 4)**
- `Engine.playSound(e, path, loop)`, `Engine.stopSound(e)`
- `Engine.setSoundVolume(e, vol)`, `Engine.setSoundPitch(e, pitch)`
- `Engine.isSoundPlaying(e)`

**UI**
- `Engine.setUIBinding(key, value)`

**Physics**
- `Engine.Physics.addForce(e, fx, fy)`, `.addImpulse(e, ix, iy)`, `.setVelocity(e, vx, vy)`, `.getVelocity(e)`
- `Engine.Physics.setAngularVelocity(e, av)`, `.getAngularVelocity(e)`, `.setGravityScale(e, gs)`, `.isTouching(e)`
- `Engine.Physics.raycastFirst(sx,sy,ex,ey)`, `.raycastAll(sx,sy,ex,ey)`
- `Engine.Physics.setDebugEnabled(bool)`, `.isDebugEnabled()`

**Events**
- `Engine.Events.subscribe(name, fn)`, `.unsubscribe(handle)`, `.emit(name, sourceEntity, payload)`

**Input**
- `Engine.Input.isKeyDown(key)`, `.isKeyPressed(key)`, `.isKeyReleased(key)`, `.getMousePosition()`
- `Engine.Keys.*`, `Engine.MouseButtons.*`

**Camera**
- `Engine.Camera.getPosition()`, `.setPosition(x,y)`, `.move(dx,dy)`, `.getZoom()`, `.setZoom(z)`

**Logging**
- `Engine.log(msg)`, `Engine.warn(msg)`, `Engine.error(msg)`

## 7. State Machine

The state machine is a generic, reusable system driven by JSON asset definitions.

- `StateMachineComponent` holds the active definition, current state, and runtime flag cache.
- `StateMachineDefinition` JSON describes states, transitions (boolean, event, timer, Lua guard), and enter/exit/update callbacks.
- `StateMachineSystem` evaluates transitions and dispatches Lua callbacks each frame.

## 8. Animation

The animation system is a generic property-animation engine driven by JSON clip assets.

- `AnimatorComponent` holds the active clip, playback state, speed, loop mode, and a target entity map.
- Clip JSON defines typed tracks (`float`, `vec2`, `vec4`, `bool`, `int`) with time-based keyframes and interpolation modes (`step`, `linear`, `ease_in`, `ease_out`, `smoothstep`, `exp`, `log`).
- `AnimationSystem` evaluates each active clip per frame, resolves binding paths (e.g. `self.Transform.position.x`, `entity:npc.SpriteRenderer.color.a`), and writes values to the target components.
- Invalid binding paths log a warning and skip that track without crashing the frame loop.

## 9. Audio

Audio playback is managed by `AudioSystem` using miniaudio as the backend.

- `AudioComponent` is a pure data component: `assetPath`, `loop`, `volume`, `pitch`, `playRequested`, `stopRequested`, `state`, `runtimeHandle`.
- **WAV, MP3, FLAC** are decoded by miniaudio directly via `ma_sound_init_from_file`.
- **OGG/Vorbis** files are decoded to raw PCM by stb_vorbis at load time, then fed into an `ma_audio_buffer` as `ma_format_s16`.
- AudioSystem is headless-safe: if `ma_engine_init` fails (e.g. no audio device in CI), the system logs a warning and no-ops for the rest of the session.
- Lua sets `assetPath` on the component or can leave it empty to reuse a pre-configured path.

## 10. UI System

The UI system renders XML-described documents attached to entities as screen-space or world-space overlays.

- `UIComponent` on an entity specifies the layout XML, font asset, optional behavior script, space mode, and layer.
- `UISystem` owns parsed document trees and binding contexts; entity documents are synced automatically from `UIComponent` state each frame.
- **Supported element types**: `Panel`, `Label`, `Button`
- **Layout attributes**: `x`, `y`, `width`, `height`, `color`, `fontSize`, `onClick`
- **Phase 5 additions**: `textAlign="left|center|right"`, `wrap="true"`, `anchor="topleft|topcenter|topright|middleleft|center|middleright|bottomleft|bottomcenter|bottomright"`
- **Reactive bindings**: `{{key}}` syntax in text or attribute values; Lua updates them via `Engine.setUIBinding(key, value)`.
- Auto-bindings are applied each frame from entity components: `entity.transform.*`, `entity.world.*`, `entity.sprite.*`, `entity.target.<name>.distance`.
- Click handlers emit `UIClickEvent` to the event bus and call the named Lua global.

## 11. Rendering Architecture

Rendering is split into two stages:

1. `RenderSystem` walks entities with `WorldTransformComponent` + `SpriteRenderer` and creates `QuadCommand`s.
2. `Renderer` batches those commands and flushes them to OpenGL using streaming instance uploads with ping-pong VBOs.

Batch ordering: sorted by layer, shader, then texture. Culling happens in `RenderSystem` before submit.

## 12. Physics

Physics is driven by Box2D through a thin integration layer.

- `PhysicsSystem` steps Box2D, syncs body positions back to `TransformComponent`, and drains the deferred contact event queue.
- `ContactListener` defers all contact begin/end callbacks to the post-step drain to avoid re-entrant world mutations.
- Zone components (`BoxZoneComponent`, `CircleZoneComponent`) emit `zone.enter` and `zone.exit` events with the zone ID and the entering entity.
- Physics debug rendering is controlled at runtime via `Engine.Physics.setDebugEnabled(true)`.

## 13. Test Architecture

Tests live under `tests/` and run through CTest with the following labels:

| Label | Purpose |
|-------|---------|
| `integration` | Deterministic feature tests, no window required |
| `smoke` | Runtime boot/shutdown path, headless-safe |
| `windowed` | Tests that require an OpenGL window; excluded in CI with `-LE windowed` |
| `ui`, `events`, `physics`, `scripting`, `renderer`, `systems` | Subsystem labels for filtered runs |

Current test counts (May 2026): 29+ integration tests, 2 smoke tests.

Fast gate: `ctest -L integration --output-on-failure`

## 14. Where the Code Lives

| Concern | File |
|---------|------|
| Runtime and frame loop | `EngineModules/Systems/src/Runtime.cpp` |
| Transform hierarchy | `EngineModules/Systems/src/TransformSystem.cpp` |
| State machine | `EngineModules/Systems/src/StateMachineSystem.cpp` |
| Animation | `EngineModules/Systems/src/AnimationSystem.cpp` |
| Audio | `EngineModules/Systems/src/AudioSystem.cpp` |
| UI system | `EngineModules/UI/src/UISystem.cpp` |
| Script API | `EngineModules/Systems/src/ScriptApiImpl.cpp` |
| Lua bindings | `EngineModules/Scripting/src/LuaBindings*.cpp` |
| Renderer batching | `EngineModules/Renderer/src/Renderer.cpp` |
| Sandbox demo | `examples/sandbox/main.cpp` + `assets/scripts/npc_zone_demo.lua` |

## 15. Practical Mental Model

