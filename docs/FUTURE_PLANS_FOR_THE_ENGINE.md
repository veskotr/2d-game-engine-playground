# SLE Vision: Game Authoring, Data-Driven Design and Future Architecture

## Purpose

The long-term goal of SLE is not simply to be a rendering engine or ECS framework.

The goal is to provide a complete pipeline that allows a developer to transform an idea into a playable game with as little friction as possible.

SLE should allow users to start with simple configuration files and Lua scripts while still providing enough depth for advanced users to extend the engine and create complex systems.

The engine should prioritize:

* Fast iteration
* Simple workflows
* Data-driven development
* AI-assisted development
* Progressive complexity
* Stable runtime architecture

---

# Core Philosophy

A game should primarily consist of data.

The engine should provide a set of primitives that allow users to describe their game through configuration and scripting rather than requiring engine modifications.

The desired flow is:

```text
Idea
  ↓
JSON/XML/Lua
  ↓
Build Pipeline
  ↓
Optimized Runtime Package
  ↓
game.exe
```

The engine should act as a pipe through which users serialize their ideas into a finished game.

---

# Data Driven Development

The primary authoring formats are:

```text
JSON
XML
Lua
```

Responsibilities:

```text
JSON
  - scenes
  - entities
  - prefabs
  - components
  - engine configuration

XML
  - UI definitions
  - layouts

Lua
  - gameplay logic
  - automation
  - custom behavior
```

The runtime should be able to load these files directly.

Future build steps may preprocess them into optimized runtime formats.

---

# ECS Philosophy

The ECS model should remain simple.

```text
Entities = identifiers
Components = data
Systems = behavior
```

Rules:

* Components should remain data-focused.
* Behavior belongs in systems.
* Systems coordinate engine functionality.
* Entity relationships should be represented through hierarchy where appropriate.

---

# Entity Hierarchies

Entities should be able to own child entities.

This allows complex game objects to be represented as trees.

Example:

```text
Player
 ├── Weapon
 ├── Hitbox
 ├── UIAnchor
 └── ParticleEmitter
```

Benefits:

* Logical grouping
* Transform inheritance
* Scene organization
* Prefab composition

Container entities are acceptable when they improve organization.

The engine should not aggressively fight this pattern.

---

# Multiple Scripts Per Entity

Entities should support multiple scripts.

Example:

```json
{
  "scripts": [
    "health.lua",
    "interactable.lua",
    "merchant.lua"
  ]
}
```

This encourages composition over monolithic entity scripts.

Scripts should remain small and focused.

---

# Events As A Primitive

Events are intended to be a core engine primitive.

Events should allow systems, scripts, UI and gameplay code to communicate without tight coupling.

Example uses:

* Input events
* Gameplay events
* UI events
* Animation events
* Script communication

The event system should remain one of the primary building blocks available to users.

---

# State Machines As A Primitive

State machines are intended to become a first-class engine feature.

They should provide structured behavior without requiring users to write large custom systems.

State machines should define:

* States
* Transitions
* Enter callbacks
* Exit callbacks
* Update callbacks
* Event reactions

Example:

```text
Idle
  ↓
Talking
  ↓
Walking
  ↓
Idle
```

State machines should provide structure.

Lua should provide custom logic.

State machines should not become a replacement for scripting.

---

# Scripting Philosophy

Lua should be used as glue code.

Responsibilities:

```text
Lua
  - custom game logic
  - gameplay rules
  - quests
  - NPC behavior
  - editor automation
```

Responsibilities that should remain in C++:

```text
Rendering
Physics
Resource management
Engine systems
Core runtime
```

The engine should expose stable APIs to Lua rather than exposing internal engine structures directly.

---

# Editor Vision

The editor should not become the source of truth.

The source of truth should always be project files.

```text
JSON
XML
Lua
Assets
```

The editor should act as a visual representation of those files.

Benefits:

* Easy version control
* Easy manual editing
* AI-friendly workflows
* Tool interoperability
* No proprietary project format

Users should always be able to open and edit project files manually.

---

# Incremental Editor Development

The editor should evolve gradually.

Proposed evolution:

```text
1. Manual file editing
2. Small utility tools
3. Specialized editors
4. Unified editor
5. Full game authoring environment
```

The editor should always remain functional while growing.

Avoid large rewrites.

---

# Runtime Reflection And Tool Generation

Engine data should eventually become self-describing.

Example:

```json
{
  "component": "Transform",
  "fields": [
    {
      "name": "position",
      "type": "Vec2"
    }
  ]
}
```

Potential benefits:

* Automatic property inspectors
* Tool generation
* Editor integration
* Documentation generation
* AI integration

This could eventually allow editor functionality to be generated automatically from engine metadata.

---

# Asset Safety

The editor should never risk corrupting project files.

Desired workflow:

```text
Edit
  ↓
In-memory model
  ↓
Validation
  ↓
Temporary file
  ↓
Atomic replace
```

Requirements:

* Detect external file changes
* Support live reloading
* Avoid long-term file locks
* Preserve manual editing workflows
* Support conflict resolution

---

# Build Pipeline

A future build pipeline should optimize projects before distribution.

Potential responsibilities:

```text
Validate project
Resolve references
Precompile Lua
Pack assets
Generate manifests
Convert data to runtime formats
```

Development files should remain human-readable.

Runtime packages may become optimized binary formats.

---

# Lua Editor Automation

Lua should eventually be usable inside the editor.

Potential uses:

* Asset generation
* Batch operations
* Scene modifications
* Data migration
* Content pipelines

Example:

```lua
for entity in scene:entities_with("SpriteRenderer") do
    entity:add_component("Interactable")
end
```

This allows users to automate repetitive content creation tasks.

---

# Asynchronous Runtime Vision

The engine should eventually support asynchronous execution.

However, simplicity and safety should remain priorities.

---

## Engine Parallelism

Potential background tasks:

```text
Resource loading
Asset streaming
Pathfinding
Animation processing
Audio streaming
Build tasks
Import tasks
```

Preferred rule:

```text
Worker threads produce results.
Main thread applies results.
```

Background threads should not directly mutate ECS state.

---

## Lua Async Model

The preferred first implementation is coroutine-based.

Example:

```lua
function npc_behavior(entity)
    while true do
        move_to(entity, target)
        wait_seconds(2)
        say(entity, "Hello")
    end
end
```

This should feel asynchronous to users while remaining safe and deterministic.

---

## Background Tasks

A future task system may allow data processing on worker threads.

Example:

```lua
task.run_background(function()
    return expensive_calculation()
end)
```

Restrictions:

* No direct ECS access
* No rendering access
* No physics access
* No world mutation

Results should return to the main thread before affecting gameplay.

---

# Long Term Goal

SLE should become a complete game creation environment where:

* Engine features are data-driven.
* Games are primarily described through configuration.
* Lua provides flexible behavior.
* The editor accelerates content creation.
* AI can easily understand and generate project data.
* Users can remain productive at every stage of development.

The ultimate objective is to make it easy for a developer to transform an idea into a working game without unnecessary complexity while still allowing advanced users to extend and customize every layer of the engine.
