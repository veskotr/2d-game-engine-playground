# Scripting Current State

This document describes the Lua scripting system as it exists now in the codebase.

## Responsibilities

The scripting layer owns:
- the single Lua VM
- Lua binding registration
- script resource loading
- per-entity script lifecycle
- calling init/update/destroy callbacks

It does not own scene data. It only accesses the scene through `ScriptApi`.

## Main Pieces

### ScriptEngine

`ScriptEngine` owns the Lua state.

Current responsibilities:
- create and destroy the Lua VM
- register Lua bindings
- load script resources through `Resources`
- extract `init`, `update`, and `destroy` callbacks
- run update callbacks each frame
- clean up stale script instances when entities disappear

### ScriptApi

`ScriptApi` is the abstract Lua-facing service interface.

It currently exposes:
- engine state queries
- entity lifetime helpers
- transform access
- input access
- camera access
- resource loading
- scene switching
- state machine controls (`setState`, `getState`, `isState`, `sendStateEvent`)
- logging

### ScriptApiImpl

`ScriptApiImpl` is the concrete bridge from Lua to the runtime.

It currently implements:
- entity creation and destruction
- parent/child hierarchy operations
- transform position and scale access
- input queries
- camera queries and updates
- texture loading and sprite attachment
- scene queries and scene switching
- logging helpers

## Current Lua API

Lua sees a global `Engine` table.

Exposed groups include:
- `Engine.Input.*`
- `Engine.Camera.*`
- `Engine.Keys.*`
- `Engine.MouseButtons.*`

Current core functions include:
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

`ScriptEngine` now supports two global callback call modes:
- `callGlobalFunction(...)` for side-effect callbacks
- `callGlobalBoolFunction(...)` for boolean guard evaluation (used by `StateMachineSystem` Lua guard transitions)

## Lua Resource Model

Scripts are treated as resources.

Current flow:
1. `ScriptEngine` resolves the script asset through `Resources`
2. the script source is loaded from a `ScriptResource`
3. callbacks are extracted and stored by entity
4. updates run through registry references, not by re-reading files each frame

## Current Demo Script Pattern

The active sandbox script currently:
- moves the player with WASD
- spawns child entities in a stress pattern
- attaches tile2 sprites to spawned children
- supports cleanup through one-key removal
- uses throttled aggregate logging

## Why This Matters

The scripting architecture is intentionally narrow:
- Lua cannot directly mutate engine internals
- engine services are exposed as functions
- transform mutations go through setters so dirty flags stay correct
- resource loading is cached and shared
- entity hierarchy is still owned by Scene, not Lua
