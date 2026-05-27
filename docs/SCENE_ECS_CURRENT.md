# Scene and ECS Current State

This document describes how the Scene module and ECS currently work in the codebase.

## Responsibilities

The Scene module owns:
- entity lifetime
- parent/child hierarchy
- the component registry
- the event bus

It does not know about rendering or Lua.

## Core Types

### Entity

Entities are lightweight IDs. They are created and destroyed by `Scene` and tracked in the registry.

### Registry

The registry stores component pools and supports:
- `createEntity()`
- `destroyEntity(entity)`
- `hasEntity(entity)`
- `addComponent<T>(entity, ...)`
- `getComponent<T>(entity)`
- `removeComponent<T>(entity)`
- `view<T>(...)`
- `view<T1, T2>(...)`

### Scene

`Scene` is the structural owner of the ECS world.

It currently exposes:
- `createEntity()`
- `destroyEntity(entity)`
- `setParent(child, parent)`
- `getParent(entity)`
- `getChildren(entity)`
- `getRoots()`
- `getRegistry()`
- `getEventBus()`

## Current Components

### TransformComponent

Local transform data for an entity.

Current behavior:
- private fields
- getters and setters
- dirty flag
- parent link

This component is mutated through setters so dirty propagation stays correct.

### WorldTransformComponent

Computed world-space transform written by the transform system each frame.

### SpriteRenderer

Render data for visible sprites.

It stores:
- color tint
- texture region
- render layer

### ScriptComponent

Script binding for an entity.

It stores:
- script asset path
- enabled flag
- internal Lua callback refs

## Hierarchy Model

The hierarchy is stored in the Scene layer, not in the renderer or scripting layer.

Important rules:
- roots are tracked separately
- child lists are stored in the scene
- destroying a parent destroys descendants
- reparenting updates parent and child maps

## Transform Flow

The transform pipeline is:

1. Local data lives in `TransformComponent`
2. `TransformSystem` walks the hierarchy
3. World data is written into `WorldTransformComponent`
4. Rendering and other systems read world transforms later in the frame

The current implementation uses iterative DFS traversal and dirty-aware recompute.

## How This Fits the Recent Work

This module underpins several recent changes:
- script-created entities now receive a transform immediately
- scripts can reparent entities
- scripts can query child counts and destroy child groups
- transform traversal was optimized for large hierarchies

## Practical Mental Model

Think of Scene as the engine's data tree:
- entities are nodes
- components are attached data
- hierarchy defines parent/child relationships
- systems read the tree and derive runtime behavior
