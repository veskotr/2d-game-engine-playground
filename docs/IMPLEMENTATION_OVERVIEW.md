# SLE Implementation Overview

This document is the current-state map of the engine. It is meant to answer two questions:

1. What does the engine do at a high level?
2. How do the recent implementations fit together?

Some older docs in this repo describe intended design or partially outdated target-state APIs. This file reflects the code that is currently implemented.

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

- Core: logging, timers, result/error helpers, config
- Platform: window creation, input handling, camera state, GLFW integration
- Renderer: OpenGL shaders, textures, commands, batch submission
- Resources: asset loading and cached resource pools
- Scene: entities, registry, components, hierarchy, scene lifetime
- Scripting: Lua VM, Lua bindings, script resource handling, script lifecycle
- Systems: orchestration systems that drive transforms, scripts, rendering, runtime flow
- Sandbox: example game/application using the engine

The important rule is that lower layers do not depend on higher layers. For example, Renderer does not know about Lua, and Scene does not know about rendering.

## 3. Runtime Flow

`Runtime` is the top-level orchestrator.

It owns:
- the window
- the camera
- the renderer
- the active scene
- the scene manager
- the script engine
- the systems used each frame

Per frame, `Runtime::run()` currently does this:

1. `SceneManager::processPendingSwitch()`
2. `Input::update()`
3. `Timer::tick()`
4. handle resize and escape key
5. build a `Context`
6. `TransformSystem::update(ctx)`
7. `ScriptSystem::update(ctx)`
8. `StateMachineSystem::update(scene, dt, &scriptEngine)`
9. `PhysicsSystem::update(ctx)`
10. `renderer.beginFrame()`
11. `RenderSystem::update(ctx)`
12. `renderer.endFrame()`
13. `window.swapBuffers()`

A profiling log in `Runtime` also prints averaged per-phase timings once per second.

## 4. Scene and ECS

The Scene module is a pure data and hierarchy layer.

It owns:
- entity lifetime
- parent/child relationships
- the component registry
- event bus

Important components currently used by the engine:
- `TransformComponent`: local transform, private fields, dirty flags, parent link
- `WorldTransformComponent`: computed world-space transform
- `SpriteRenderer`: render data for visible sprites
- `ScriptComponent`: script asset and runtime script state

The current hierarchy is important:
- root entities live in `Scene::getRoots()`
- `Scene::setParent()` maintains parent and children maps
- `Scene::destroyEntity()` recursively destroys descendants

## 5. Transform Pipeline

Transform handling is split into two parts:

- `TransformComponent` stores local position, rotation, scale, parent, and a dirty flag
- `TransformSystem` computes `WorldTransformComponent` each frame

The important implementation detail is that transform fields are private and mutated through setters so dirty propagation stays correct.

The transform optimization implemented recently uses iterative depth-first traversal instead of recursive descent. That keeps the hierarchy walk correct while reducing call overhead for large trees.

## 6. Scripting Architecture

Scripting is built around a single global Lua VM owned by `ScriptEngine`.

Current script flow:

1. `Runtime` creates and initializes `ScriptEngine`.
2. `ScriptEngine` registers Lua bindings.
3. `ScriptSystem` finds active `ScriptComponent`s each frame.
4. For each entity, `ScriptEngine::ensureScript()` loads the Lua file through the `Resources` system.
5. Lua `init`, `update`, and `destroy` callbacks are stored and called through registry references.
6. When an entity disappears, `ScriptEngine::syncEntities()` removes stale script instances.

### Current Lua-facing API

Lua sees a global `Engine` table with service-style functions, not direct engine state.

It currently exposes:
- `Engine.getDeltaTime()`
- `Engine.getWindowSize()`
- `Engine.createEntity()`
- `Engine.destroyEntity(entity)`
- `Engine.isEntityAlive(entity)`
- `Engine.setParent(child, parent)`
- `Engine.getParent(entity)`
- `Engine.getChildCount(parent)`
- `Engine.destroyChildren(parent)`
- `Engine.getTransformPosition(entity)`
- `Engine.setTransformPosition(entity, x, y)`
- `Engine.getTransformScale(entity)`
- `Engine.loadTexture(path)`
- `Engine.setSpriteTexture(entity, path)`
- `Engine.hasScene(name)`
- `Engine.switchScene(name)`
- `Engine.getCurrentSceneName()`
- `Engine.setState(entity, stateName)`
- `Engine.getState(entity)`
- `Engine.isState(entity, stateName)`
- `Engine.sendStateEvent(entity, eventName)`
- `Engine.log()`, `Engine.warn()`, `Engine.error()`

It also exposes service groups:
- `Engine.Input.*`
- `Engine.Camera.*`
- `Engine.Keys.*`
- `Engine.MouseButtons.*`

## 7. Rendering Architecture

Rendering is split into two stages:

1. `RenderSystem` walks entities with `WorldTransformComponent` + `SpriteRenderer` and creates `QuadCommand`s.
2. `Renderer` batches those commands and flushes them to OpenGL.

Important current behavior:
- culling happens in `RenderSystem` before submit
- batch ordering is sorted by layer, shader, then texture
- instance data is staged in reusable buffers
- the renderer uses streaming instance uploads with ping-pong VBOs

This means the system is still CPU-driven, but it is much more scalable than a naive per-entity draw path.

## 8. Resources

The `Resources` module caches loaded assets by type and ID.

It is currently used for:
- textures
- shaders
- script resources

Lua scripts are treated as resources by the scripting engine. The script asset path is the resource key, and the loaded script source is cached in a `ScriptResource`.

## 9. Current Implementations and Why They Exist

### Render culling

Purpose:
- Skip off-screen sprites before they become render commands.

Effect:
- Reduces render submission work when many sprites are not visible.

### Spawn and death controls

Purpose:
- Keep stress tests bounded and reproducible.

Effect:
- Spawn budget per second
- active child cap
- one-key clear
- script-side count queries

### Logging reduction

Purpose:
- Remove IO bottlenecks from stress runs.

Effect:
- aggregated status logging once per second
- configurable log level in the script

### Transform optimization

Purpose:
- Reduce transform hierarchy traversal cost.

Effect:
- iterative DFS
- dirty-aware recompute
- clean leaf fast path

### Render submit CPU optimization

Purpose:
- Reduce CPU overhead while building draw batches.

Effect:
- direct 2D matrix construction
- persistent staging buffers
- better batch ordering
- reduced per-frame allocations

### GPU-focused streaming improvements

Purpose:
- Reduce sync stalls and upload bottlenecks.

Effect:
- ping-pong instance VBOs
- dynamic instance buffer growth
- orphan + stream upload path

## 10. What a Typical Demo Scene Looks Like

The current sandbox demo typically does the following:

- registers a scene named `main`
- preloads `tile2` texture and the player movement Lua script
- creates one visible player entity with:
  - `TransformComponent`
  - `SpriteRenderer`
  - `ScriptComponent`
- the Lua script moves the entity with WASD
- the Lua script can spawn and clear child entities for stress testing

## 11. Reading Order If You Are Catching Up

If you want the fastest path to understanding the current codebase, read in this order:

1. `IMPLEMENTATION_OVERVIEW.md`
2. `ARCHITECTURE.md`
3. `SCRIPTING_IMPLEMENTATION_GUIDE.md`
4. `COMPONENT_SYSTEM_GUIDE.md`
5. `LUA_IMPLEMENTATION_QUICKSTART.md`

## 12. Where the Recent Work Lives

If you want to inspect the concrete implementation behind the recent changes:

- runtime and frame loop: `EngineModules/Systems/src/Runtime.cpp`
- transform hierarchy: `EngineModules/Systems/src/TransformSystem.cpp`
- rendering submit: `EngineModules/Systems/src/RenderSystem.cpp`
- renderer batching and upload: `EngineModules/Renderer/src/Renderer.cpp`
- script API implementation: `EngineModules/Systems/src/ScriptApiImpl.cpp`
- Lua bindings: `EngineModules/Scripting/src/LuaBindings.cpp`
- stress script: `assets/scripts/player_move.lua`

## 13. Practical Mental Model

A simple way to think about the engine is:

- Scene owns the data.
- Systems derive runtime behavior from that data.
- Scripting asks the engine to do things through a controlled API.
- Renderer turns visible world state into GPU work.
- Runtime glues everything together.

That is the architecture in one sentence.
