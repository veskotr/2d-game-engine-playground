---
name: sle-2d-engine
description: "Instructions for contributing to SLE (Simple Little Engine), a modular 2D game engine with strict architecture, ECS, and Lua scripting."
---

# SLE 2D Engine - AI & Developer Guidelines

**Version**: Current (May 2026)  
**Last Verified Against Code**: Yes - documentation reconciled with actual implementations

## Quick Reference

- **Architecture**: Strict one-way module dependency chain (Core → Platform → Renderer → Resources → Scene → Scripting → Systems → Runtime → Sandbox)
- **Design Pattern**: Pure ECS + command-based rendering
- **Scripting**: Single Lua VM per engine instance
- **Build**: CMake, C++17, OpenGL 4.3

---

## Core Architectural Principles

### 1. Strict Module Layering (NO EXCEPTIONS)

The engine follows a rigid one-way dependency chain. **Lower layers never depend on higher layers.**

```
Core (utilities, logging, timers)
  ↓
Platform (window, input, GLFW)
  ↓
Renderer (OpenGL, shaders, commands)
  ↓
Resources (asset caching, loading)
  ↓
Scene (ECS, entities, components, hierarchy)
  ↓
Scripting (Lua VM, bindings, script lifecycle)
  ↓
Systems (orchestration, runtime loop)
  ↓
Runtime (main engine coordinator)
  ↓
Sandbox (game/application code)
```

**Violation Examples (❌ NEVER DO):**
- Renderer accessing Scene components
- Scene knowing about Lua/scripting
- Platform using Resources
- Any layer knowing about Sandbox

**Correct Pattern:** Dependency injection via Context struct passed through systems.

### 2. ECS Architecture

- **Entities**: Lightweight IDs, created/destroyed by Scene registry
- **Components**: Pure data structures, NO methods (no logic in data)
- **Systems**: Pure functions that read components and issue commands
- **Registry**: Central lookup, supports `view<T>()` and `view<T1, T2>()`

**Component Design Rules:**
- Data-only (no methods on components)
- All public members (struct, not class)
- Serializable to/from Lua and JSON
- Default-constructible
- Prefer POD types (glm::vec2, float, uint32_t, bool, std::string)

**Existing Components:**
- `TransformComponent`: Local position/rotation/scale + dirty flag, private fields with setters
- `WorldTransformComponent`: Computed world-space (read-only, updated each frame)
- `SpriteRenderer`: Color tint, texture, layer, shader
- `ScriptComponent`: Script asset path, lifecycle flags, internal Lua callback refs

### 3. Single Lua VM Strategy

**One VM per engine instance** — standard industry approach (Godot, LÖVE, etc.)

- Simpler than per-entity VMs
- Allows scripts to call shared helper functions
- Easier debugging
- Per-entity script state maintained in Lua userdata tables

**Script Lifecycle:**
```
Entity created → Load script asset → Call init(entity) → Each frame: update(entity, dt) → Destroy: call destroy(entity)
```

**EngineAPI Boundary:** All entity/scene access goes through `ScriptApi` interface (abstract, implemented by Runtime).

### 4. Command-Based Rendering

**Two-layer rendering:**

1. **RenderSystem** (Scene layer): Walks entities with `WorldTransformComponent` + `SpriteRenderer`, emits `QuadCommand` objects
2. **Renderer** (Render layer): Batches commands by layer→shader→texture, streams to GPU via ping-pong VBOs

**Key Optimization:** Early frustum culling before command submission; late sorting before GPU upload.

### 5. Transform Pipeline

```
Frame Start
  ↓
TransformSystem walks hierarchy (iterative DFS)
  → computes WorldTransformComponent for each entity
  → respects dirty flag (only recompute changed subtrees)
  ↓
Later Systems (Render, Physics, Scripts) read WorldTransformComponent
```

**Important:** Transform fields are private; mutations go through setters to maintain dirty flag invariant.

---

## Module Breakdown

### Core Module
- **Exports**: `Log`, `Result`, `Timer`, `EngineConfig`, `EventBus`
- **Dependencies**: Standard library only
- **Responsibility**: Foundational utilities
- **Use cases**: Any module can use Core

### Platform Module
- **Exports**: `Window`, `Input`, `Camera2D`
- **Dependencies**: Core, GLFW, OpenGL headers
- **Responsibility**: Window lifecycle, input polling, camera state
- **Key Pattern**: Input state cleared BEFORE polling (not after) to maintain edge-triggered semantics
- **Use cases**: Runtime needs Platform for window/input, Renderer uses Camera2D

### Renderer Module
- **Exports**: `Renderer`, `Shader`, `Texture`, `QuadCommand`, `BatchKey`
- **Dependencies**: Core, Platform, OpenGL
- **Responsibility**: GPU state, shader compilation, texture loading, batch submission
- **Key Pattern**: Stateless command acceptance, deferred batching
- **Non-Exports**: Does NOT know about Scene, Lua, or entity concepts
- **Use cases**: Runtime/RenderSystem submit QuadCommands

### Resources Module
- **Exports**: `Resources`, `ResourcePool<T>`
- **Dependencies**: Core, Renderer (for Shader/Texture loading)
- **Responsibility**: Asset caching, deduplication, loading
- **Key Pattern**: Type-indexed, ID-based lookup; prevents duplicate asset loads
- **Use cases**: Scripts and systems load textures/shaders by path

### Scene Module
- **Exports**: `Scene`, `Registry`, `Entity`, `Components`
- **Dependencies**: Core, Resources (only for internal asset refs)
- **Responsibility**: Entity lifetime, hierarchy, component storage
- **Non-Exports**: Does NOT know about Renderer, Lua, or Input
- **Key Pattern**: Pure data layer; hierarchy mutations trigger dirty flags on affected entities
- **Use cases**: Runtime creates/destroys entities, systems query components

### Scripting Module
- **Exports**: `ScriptEngine`, `ScriptApi` (abstract interface)
- **Dependencies**: Core, Scene, Resources, Lua library
- **Responsibility**: Lua VM lifecycle, binding registration, script loading, per-entity script state
- **Key Pattern**: Concrete `ScriptApiImpl` bridges Lua → Runtime services via abstract `ScriptApi`
- **Boundary**: Scripts access everything through EngineAPI; never direct C++ object access
- **Use cases**: Runtime manages ScriptEngine; ScriptSystem calls Lua update callbacks

### Systems Module
- **Exports**: `TransformSystem`, `ScriptSystem`, `PhysicsSystem`, `RenderSystem`, `Runtime`
- **Dependencies**: All layers (Core, Platform, Renderer, Resources, Scene, Scripting)
- **Responsibility**: Game loop orchestration, per-frame updates
- **Key Pattern**: Systems operate on Scene via Registry, issue commands (Render, Input, etc.)
- **Use cases**: Runtime::run() calls systems in sequence each frame

### Runtime/Systems Loop
```
Per Frame:
  1. Process pending scene switches
  2. Poll input (Input::update clears pressed/released state FIRST)
  3. Tick timer
  4. Build Context struct (window, camera, scene, renderer, etc.)
  5. TransformSystem::update(ctx)      → recomputes all world transforms
  6. ScriptSystem::update(ctx)         → runs Lua update(entity, dt) callbacks
  7. PhysicsSystem::update(ctx)        → steps physics (Box2D placeholder)
  8. renderer.beginFrame()
  9. RenderSystem::update(ctx)         → submits QuadCommands
 10. renderer.endFrame()               → batches and uploads to GPU
 11. window.swapBuffers()
 12. Log profiling stats (ms per phase, averaged)
```

---

## Key Design Decisions & Rationale

### Why Strict Layering?
- **Clear ownership**: Each module has one job, no ambiguity
- **Testability**: Lower layers can be tested independently
- **Reusability**: Lower layers can be used in other projects
- **Extensibility**: New systems added to top without touching lower layers

### Why Pure ECS?
- **Data locality**: Component pools improve cache performance
- **Composability**: Entities mix any combination of components
- **Parallelization**: Systems easily parallelize over component views
- **Serialization**: Component data easily saved/loaded

### Why One Lua VM?
- **Industry standard**: Godot, LÖVE, etc. use single VMs
- **Simplicity**: Easier to debug, fewer edge cases
- **Shared state**: Scripts can call shared helper functions
- **No per-entity overhead**: Scales to thousands of scripted entities

### Why Command-Based Rendering?
- **Decoupling**: Scene doesn't know rendering details
- **Batching**: Late sorting enables GPU optimization
- **Extensibility**: New render types = new command types
- **Debugging**: Commands are inspectable, replayable

---

## Common Workflows

### Adding a New Component
1. Define struct in `Scene/include/sle/scene/components/`
   - Data-only, public members
   - Default-constructible
2. Implement serialization (Lua table ↔ C++, JSON ↔ C++)
3. Register in `Scene/src/` or ComponentRegistry
4. Export from Scene public API
5. Add to any systems that need it (via Registry view)
6. Update `COMPONENT_SYSTEM_GUIDE.md`

### Adding a Lua API Function
1. Add abstract method to `ScriptApi` (Scripting/include/)
2. Implement in `ScriptApiImpl` (Systems/src/)
3. Register binding in `LuaBindings.cpp` via `lua_register()`
4. Document in script template and `SCRIPTING_CURRENT.md`
5. Test in Sandbox with a simple script

### Creating a New System
1. Define in `Systems/include/sle/systems/`
2. Only depend on abstract interfaces (Scene, ScriptApi, etc.)
3. Take `Context` struct with pointers to all needed services
4. Operate via Registry views, not direct pointers
5. Call into Runtime/ScriptApi only for high-level operations
6. Add to Runtime::run() at appropriate point in frame
7. Update `IMPLEMENTATION_OVERVIEW.md` frame loop

### Extending Renderer
1. New command type? Add to `QuadCommand` or create sibling
2. New batch key? Update `BatchKey` struct
3. New GPU upload strategy? Modify upload loop in `Renderer::endFrame()`
4. **KEY**: Do not add Scene dependencies
5. Update `RENDERING_CURRENT.md`

---

## Documentation Standards

Each major `.md` file serves a purpose:

- **ARCHITECTURE.md**: Module design, responsibilities, rationale
- **IMPLEMENTATION_OVERVIEW.md**: Current-state, what's actually coded, frame loop
- **COMPONENT_SYSTEM_GUIDE.md**: How to add components, serialization patterns
- **SCRIPTING_CURRENT.md**: Lua integration, EngineAPI, script lifecycle
- **RENDERING_CURRENT.md**: Render pipeline, batching, GPU strategy
- **SCENE_ECS_CURRENT.md**: Entity model, hierarchy, component storage
- **LUA_IMPLEMENTATION_QUICKSTART.md**: Step-by-step Lua integration guide
- **OPTIMIZATIONS_CURRENT.md**: Performance work, bottlenecks addressed

**When you modify code, update corresponding .md file immediately.** Documentation lag is a killer for AI + team understanding.

---

## Code Style & Conventions

### Namespacing
```cpp
namespace sle::core { }      // Core module
namespace sle::platform { }  // Platform module
namespace sle::renderer { }  // Renderer module
namespace sle::resources { } // Resources module
namespace sle::scene { }     // Scene module
namespace sle::systems { }   // Systems module
namespace sle::scripting { } // Scripting module
namespace sle::components { } // Within sle::scene for clarity
```

### Naming
- Classes/Structs: `PascalCase` (e.g., `TransformComponent`, `QuadCommand`)
- Functions/Methods: `camelCase` (e.g., `beginFrame()`, `getComponent()`)
- Member vars: `camelCase` (e.g., `modelMatrix`, `spriteLayer`)
- Constants: `UPPER_SNAKE_CASE` or `CONSTANT_NAME` if compile-time
- Private members: prefix with underscore or use private sections

### Structure
- `.hpp` headers in `include/sle/module/`
- `.cpp` implementations in `src/`
- CMakeLists.txt specifies include paths
- Keep headers minimal; complex logic in .cpp

### Error Handling
- Use `Result<T, E>` pattern from Core for fallible operations
- Return `Result<Entity>` not `Entity*` (NULL is ambiguous)
- Log errors with `LOG_ERROR()`, don't silently fail
- Lua errors should propagate via ScriptApi, not crash engine

---

## Testing & Verification

### When Code Changes
1. **Compile without warnings**: `cmake --build build/debug`
2. **Run Sandbox**: Verify visual behavior
3. **Update docs**: Sync with actual implementation
4. **Check dependencies**: Verify no backward dependencies introduced
5. **Test scripts**: Run simple Lua scripts if scripting affected

### Before AI Review Requests
- [ ] Code compiles cleanly
- [ ] Docs updated
- [ ] No architecture violations
- [ ] Module responsibilities clear
- [ ] No circular dependencies

---

## AI Guardrails

When using AI to extend SLE:

1. **Preserve layering**: Never ask AI to break dependency chain
2. **ECS purity**: Components are data, systems are pure functions
3. **Boundary compliance**: Scene doesn't talk to Renderer; Renderer doesn't know Lua
4. **API consistency**: New Lua functions go through ScriptApi interface
5. **Doc sync**: Always ask AI to update .md files alongside code changes

Example good request:
> "Add a ColorComponent with serialize/deserialize. Update code AND COMPONENT_SYSTEM_GUIDE.md"

Example bad request:
> "Make the Renderer aware of the Scene for optimization" (breaks layering)

---

## Resources & Files

**Architecture Files:**
- Root: `ARCHITECTURE.md`, `IMPLEMENTATION_OVERVIEW.md`, `COMPONENT_SYSTEM_GUIDE.md`
- Scripting: `SCRIPTING_CURRENT.md`, `LUA_IMPLEMENTATION_QUICKSTART.md`
- Rendering: `RENDERING_CURRENT.md`
- Scene: `SCENE_ECS_CURRENT.md`
- Performance: `OPTIMIZATIONS_CURRENT.md`

**Key Directories:**
- `EngineModules/Core`, `Platform`, `Renderer`, `Resources`, `Scene`, `Scripting`, `Systems`
- `Sandbox/`: Example game code
- `External/`: Third-party headers (miniaudio, stb)
- `assets/`: Scripts, shaders, textures

**Build:**
- `CMakeLists.txt`: Root build config
- `CMakePresets.json`: Build presets
- `build/debug` and `build/release`: Output directories

---

## FAQ for AI & Developers

**Q: Can I add a dependency upward?**  
A: No. The dependency chain is absolute. Use dependency injection instead.

**Q: How do I pass data between modules?**  
A: Via public structs/interfaces or Context parameters. Never via static globals or back-pointers.

**Q: What if I need to reload scripts without restarting?**  
A: ScriptEngine supports hot-reload. Verify it works in Sandbox before shipping.

**Q: Should I serialize components to Lua tables or JSON?**  
A: Both. See COMPONENT_SYSTEM_GUIDE.md for the pattern.

**Q: Can entities be destroyed mid-frame?**  
A: Yes. Scene::destroyEntity() handles it. Systems read from Registry views which skip dead entities.

---

**Last Updated**: May 2026  
**Verified Against**: All documented .md files + source code exploration  
**Maintainer**: Architecture Council (documented in ARCHITECTURE.md)
