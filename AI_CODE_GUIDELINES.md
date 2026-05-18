---
Title: SLE 2D Engine - AI Code Generation Guidelines
Version: 1.0 (May 2026)
Audience: AI Assistants and Automated Code Tools
Status: Mandatory Compliance
---

# AI Code Generation Guidelines for SLE Engine

This document provides strict rules for AI-assisted code generation in SLE. Follow these rules to maintain architectural integrity.

---

## Rule 1: Respect Module Dependency Layering

**MANDATORY**: Do not introduce backward dependencies.

### The Chain (Absolute Order)
```
Core → Platform → Renderer → Resources → Scene → Scripting → Systems → Runtime → Sandbox
```

### ❌ FORBIDDEN Patterns
```cpp
// ❌ Renderer accessing Scene - FORBIDDEN
class Renderer {
    void render(Scene& scene);  // NO!
};

// ❌ Scene using Renderer - FORBIDDEN
class Scene {
    Renderer renderer;  // NO!
};

// ❌ Platform depending on Resources - FORBIDDEN
class Window {
    Resources resources;  // NO!
};
```

### ✅ CORRECT Patterns
```cpp
// ✅ Dependency injection via Context
class RenderSystem {
    void update(const Context& ctx) {
        ctx.renderer->submitQuad(cmd);  // Renderer through Context
        ctx.scene->getRegistry().view<SpriteRenderer>();  // Scene through Context
    }
};

// ✅ Using Abstract Interface
class ScriptSystem {
    void update(const Context& ctx) {
        ctx.scriptEngine->runUpdateCallbacks(ctx);  // Abstract, not direct access
    }
};
```

### Verification
- CMakeLists.txt target_link_libraries must follow chain order
- No `#include` of higher-layer modules in lower-layer code
- Compile errors if violated (linker fails)

---

## Rule 2: ECS Components Are Pure Data

**MANDATORY**: Components contain ONLY data, NEVER methods or logic.

### ❌ FORBIDDEN
```cpp
// ❌ Component with methods - FORBIDDEN
struct TransformComponent {
    glm::vec2 position;
    
    void move(glm::vec2 delta) {  // NO METHODS!
        position += delta;
    }
    
    void rotate(float angle) {    // NO LOGIC!
        // ...
    }
};

// ❌ Virtual methods - FORBIDDEN
struct BaseComponent {
    virtual void update() = 0;  // NO!
};
```

### ✅ CORRECT
```cpp
// ✅ Data only, methods via systems
struct TransformComponent {
    glm::vec2 position{0.0f};
    float rotation = 0.0f;
    glm::vec2 scale{1.0f, 1.0f};
    // No methods!
};

// ✅ Logic lives in systems
void TransformSystem::update(const Context& ctx) {
    auto view = ctx.scene->getRegistry().view<TransformComponent>();
    for (auto entity : view) {
        auto& transform = view.get<TransformComponent>(entity);
        transform.position += deltaMovement;  // System handles mutation
    }
}
```

### Component Checklist
- [ ] All members public
- [ ] No methods (except getters/setters for private fields if needed)
- [ ] Default-constructible: `Component c;` works
- [ ] Serializable to Lua table
- [ ] Serializable to JSON
- [ ] Use POD types: glm::vec2, float, uint32_t, std::string, bool

---

## Rule 3: Systems Query Via Registry Views

**MANDATORY**: Use Registry::view<T>() or view<T1, T2>(), not direct entity lookups.

### ❌ FORBIDDEN
```cpp
// ❌ Iterating entities manually - FORBIDDEN
for (auto entity : scene.getAllEntities()) {
    if (scene.hasComponent<TransformComponent>(entity) &&
        scene.hasComponent<SpriteRenderer>(entity)) {
        auto& t = scene.getComponent<TransformComponent>(entity);
        auto& s = scene.getComponent<SpriteRenderer>(entity);
        // Process...
    }
}

// ❌ Caching entity pointers - FORBIDDEN
std::vector<Entity*> cached_entities;  // NO!
```

### ✅ CORRECT
```cpp
// ✅ Use Registry views (efficient, cacheable)
auto view = ctx.scene->getRegistry().view<TransformComponent, SpriteRenderer>();
for (auto entity : view) {
    auto& transform = view.get<TransformComponent>(entity);
    auto& sprite = view.get<SpriteRenderer>(entity);
    // Process...
}

// ✅ Single component view
auto view = ctx.scene->getRegistry().view<TransformComponent>();
for (auto entity : view) {
    // ...
}
```

### Why
- Views are memory-efficient (sparse iteration)
- Views are the standard Registry pattern
- Views can be parallelized in future

---

## Rule 4: Lua Scripts Access Everything Through ScriptApi

**MANDATORY**: Lua cannot directly call C++ object methods; must go through ScriptApi interface.

### ❌ FORBIDDEN
```lua
-- ❌ FORBIDDEN - Direct C++ object access
local scene_ptr = Engine.getScenePtr()  -- NO!
scene_ptr:createEntity()

-- ❌ FORBIDDEN - Undocumented internal access
Engine.Internal.getRegistry()  -- NO!
```

### ✅ CORRECT
```lua
-- ✅ Public EngineAPI only
local entity = Engine.createEntity()
Engine.setTransformPosition(entity, 100, 200)
Engine.attachTexture(entity, "assets/textures/player.png")
if Engine.Input.isKeyPressed(Engine.Keys.W) then
    local x, y = Engine.getTransformPosition(entity)
    Engine.setTransformPosition(entity, x, y - 100 * dt)
end
```

### How to Add New Lua Functions
1. Add abstract method to `ScriptApi` interface
2. Implement in `ScriptApiImpl` (Systems module)
3. Bind in `LuaBindings.cpp` via `lua_register()`
4. Document in `SCRIPTING_CURRENT.md`
5. Test in Sandbox

**NEVER** expose internal Scene pointers, Registry, or object addresses to Lua.

---

## Rule 5: Rendering Via Commands, Not Direct Calls

**MANDATORY**: RenderSystem emits QuadCommand objects; Renderer batches and submits.

### ❌ FORBIDDEN
```cpp
// ❌ Direct renderer calls from game code - FORBIDDEN
void GameLogic::render() {
    renderer.drawSprite(texture, position, scale);  // NO!
}

// ❌ Renderer accessing Scene - FORBIDDEN
void Renderer::render(Scene& scene) {  // NO!
    for (auto entity : scene.getEntities()) {
        // Direct rendering
    }
}
```

### ✅ CORRECT
```cpp
// ✅ RenderSystem generates commands
void RenderSystem::update(const Context& ctx) {
    auto view = ctx.scene->getRegistry().view<SpriteRenderer, WorldTransformComponent>();
    for (auto entity : view) {
        QuadCommand cmd;
        cmd.modelMatrix = worldTransform;
        cmd.color = spriteRenderer.color;
        cmd.uvRect = spriteRenderer.uvRect;
        cmd.textureHandle = getTextureHandle(spriteRenderer.textureAsset);
        ctx.renderer->submitQuad(cmd);
    }
}

// ✅ Renderer batches commands
void Renderer::endFrame() {
    // Batch by layer, shader, texture
    // Upload to GPU in order
    // Draw in batch order
}
```

### Why
- Decoupling: Renderer doesn't know about Scene/Lua
- Optimization: Late sorting before GPU submission
- Extensibility: New render types = new command structs

---

## Rule 6: Transform System Hierarchy

**MANDATORY**: Transforms computed once per frame by TransformSystem; read-only elsewhere.

### ❌ FORBIDDEN
```cpp
// ❌ Directly setting WorldTransformComponent - FORBIDDEN
entity.getComponent<WorldTransformComponent>().matrix = glm::mat4(1.0f);  // NO!

// ❌ Mutating transforms outside TransformSystem - FORBIDDEN
void PhysicsSystem::update() {
    auto& transform = scene.getComponent<TransformComponent>(entity);
    transform.setPosition(newPos);  // Should be done in PhysicsSystem BEFORE TransformSystem
}

// ❌ Recursive hierarchy walk - FORBIDDEN (use iterative)
void walkHierarchy(Entity e) {
    for (auto child : scene.getChildren(e)) {
        walkHierarchy(child);  // NO - iterative only
    }
}
```

### ✅ CORRECT
```cpp
// ✅ TransformSystem computes each frame
void TransformSystem::update(const Context& ctx) {
    // Iterative DFS walk
    std::stack<Entity> stack;
    for (auto root : ctx.scene->getRoots()) {
        stack.push(root);
    }
    while (!stack.empty()) {
        auto entity = stack.top(); stack.pop();
        // Compute world transform
        auto& world = ctx.scene->getComponent<WorldTransformComponent>(entity);
        world.matrix = computeWorldMatrix(entity);
        // Push children
        for (auto child : ctx.scene->getChildren(entity)) {
            stack.push(child);
        }
    }
}

// ✅ Other systems read WorldTransformComponent
void RenderSystem::update(const Context& ctx) {
    auto& world = ctx.scene->getComponent<WorldTransformComponent>(entity);
    cmd.modelMatrix = world.matrix;  // Read-only
}
```

### Why
- Single point of truth: WorldTransformComponent is source of truth
- Deterministic: Always computed same way each frame
- Parallelizable: Can eventually parallelize transform walk

---

## Rule 7: No Global State

**MANDATORY**: All state passed via Context struct or Module ownership.

### ❌ FORBIDDEN
```cpp
// ❌ Global renderer - FORBIDDEN
Renderer* g_renderer = nullptr;  // NO!

// ❌ Static scene instance - FORBIDDEN
static Scene g_scene;  // NO!

// ❌ Singleton pattern - FORBIDDEN
class Engine {
    static Engine& getInstance();  // NO!
};

// ❌ Global input polling - FORBIDDEN
bool g_isKeyPressed(int key);  // NO!
```

### ✅ CORRECT
```cpp
// ✅ State owned by Runtime
class Runtime {
    Window window;
    Renderer renderer;
    Scene scene;
    Input input;
    // All state here, passed via Context
};

// ✅ Systems receive Context
void TransformSystem::update(const Context& ctx) {
    ctx.renderer->submitQuad(...);  // Get renderer from context
}

// ✅ Module ownership
class ScriptEngine {
    lua_State* L;  // Owned, not global
    ScriptApi* api;
};
```

### Why
- Testability: Mock Context for testing
- Thread-safety: No global state races
- Extensibility: Can have multiple engine instances

---

## Rule 8: Documentation Must Stay In Sync

**MANDATORY**: Update .md files when changing code. Out-of-date docs break AI understanding.

### ❌ FORBIDDEN
```cpp
// ❌ Add new system without updating docs
class NewSystem { };  // NO - update IMPLEMENTATION_OVERVIEW.md!

// ❌ Change frame loop order without updating docs
void Runtime::run() {
    // ... new order ...
}  // NO - update IMPLEMENTATION_OVERVIEW.md!

// ❌ Add component without updating guide
struct NewComponent { };  // NO - update COMPONENT_SYSTEM_GUIDE.md!
```

### ✅ CORRECT
```cpp
// ✅ Add system AND update docs
class AnimationSystem { };  // Added

// In IMPLEMENTATION_OVERVIEW.md, update frame loop:
// 7. AnimationSystem::update(ctx)  // NEW - runs after transforms, before physics

// ✅ Add component AND update guide
struct HealthComponent {
    float currentHealth;
    float maxHealth;
};

// In COMPONENT_SYSTEM_GUIDE.md:
// ## HealthComponent
// Stores entity health state.
// Serialization: {health = 100, maxHealth = 100}
```

### Documentation Files (Keep Updated)
- **ARCHITECTURE.md**: Module structure, responsibilities
- **IMPLEMENTATION_OVERVIEW.md**: Current code, frame loop
- **COMPONENT_SYSTEM_GUIDE.md**: How to add components
- **SCRIPTING_CURRENT.md**: Lua API, script lifecycle
- **RENDERING_CURRENT.md**: Render pipeline, batching
- **SCENE_ECS_CURRENT.md**: Entity model, hierarchy
- **OPTIMIZATIONS_CURRENT.md**: Perf work, bottlenecks
- **copilot-instructions.md**: AI guidelines (this package)

---

## Rule 9: Error Handling Via Result<T, E>

**MANDATORY**: Use Result pattern for fallible operations, not nullptr or exceptions.

### ❌ FORBIDDEN
```cpp
// ❌ Returning nullptr - FORBIDDEN
Entity* Scene::createEntity() {  // NO - use Result!
    return entity_ptr;
}

// ❌ Exceptions in engine code - FORBIDDEN (except Lua errors)
void Renderer::init() {
    throw std::runtime_error("Init failed");  // NO!
}

// ❌ Silent failures - FORBIDDEN
bool Resources::load(const std::string& path) {
    if (!fileExists(path)) return false;  // NO - log error!
}
```

### ✅ CORRECT
```cpp
// ✅ Result pattern
Result<Entity> Scene::createEntity() {
    if (isFull()) return Result<Entity>::error("Entity pool full");
    return Result<Entity>::success(entity);
}

// ✅ Lua errors propagate cleanly
int lua_createEntity(lua_State* L) {
    auto res = scene->createEntity();
    if (!res) {
        lua_pushstring(L, res.error().c_str());
        lua_error(L);
    }
    lua_pushnumber(L, res.value());
    return 1;
}

// ✅ Log all errors
bool Resources::load(const std::string& path) {
    if (!fileExists(path)) {
        LOG_ERROR("Asset not found: " + path);
        return false;
    }
    return true;
}
```

---

## Rule 10: Input State Management

**MANDATORY**: Clear input state BEFORE polling, not after.

### ❌ FORBIDDEN
```cpp
// ❌ Clearing AFTER polling - FORBIDDEN
void InputSystem::update() {
    // Game code sees input
    if (input.isKeyPressed(W)) { move(); }
    
    // Then clear (too late! next frame sees nothing)
    input.clearPressed();  // NO!
}
```

### ✅ CORRECT
```cpp
// ✅ Clear BEFORE polling
void Runtime::run() {
    while (isRunning) {
        Input::update();  // Clear pressed/released FIRST
        // THEN poll new events
        
        // THEN game code sees current frame input
        if (input.isKeyPressed(W)) { move(); }
    }
}
```

### Why
- Edge-triggered input: pressing means "changed to pressed this frame"
- If you clear after polling, next frame's query sees nothing
- Must maintain state across frames

---

## Rule 11: Script Lifecycle

**MANDATORY**: Scripts must follow init → update → destroy pattern.

### ❌ FORBIDDEN
```lua
-- ❌ No init function - FORBIDDEN
function update(entity, dt)
    -- Never initialized
end

-- ❌ Stateful initialization - FORBIDDEN
local health = 100  -- Global state, shared across all entities
function init(entity)
    -- All entities share same health!
end

-- ❌ Cleanup in update - FORBIDDEN
function update(entity, dt)
    if isDead then
        Engine.destroyEntity(entity)  -- Avoid mid-frame destruction
    end
end
```

### ✅ CORRECT
```lua
-- ✅ Full lifecycle
local state = { health = 100 }

function init(entity)
    -- Called once at entity creation
    Engine.attachTexture(entity, "assets/player.png")
    Engine.setTransformScale(entity, 2.0, 2.0)
end

function update(entity, dt)
    -- Called every frame
    if Engine.Input.isKeyPressed(Engine.Keys.W) then
        local x, y = Engine.getTransformPosition(entity)
        Engine.setTransformPosition(entity, x, y - 100 * dt)
    end
    
    -- Queue destruction (not immediate)
    state.health -= damage
    if state.health <= 0 then
        Engine.destroyEntity(entity)  -- Safe to queue
    end
end

function destroy(entity)
    -- Called when entity destroyed
    LOG("Entity destroyed")
end
```

---

## Rule 12: Naming Conventions

**MANDATORY**: Follow naming conventions for consistency.

### ✅ CORRECT
```cpp
// Classes/Structs: PascalCase
class TransformComponent { };
struct QuadCommand { };

// Functions/Methods: camelCase
void beginFrame();
void submitQuad();
glm::vec2 getPosition();

// Member variables: camelCase
float modelMatrix;
uint32_t renderLayer;
glm::vec2 velocity;

// Namespaces: snake_case
namespace sle::scene { }
namespace sle::systems { }

// Constants: UPPER_SNAKE_CASE
const uint32_t MAX_ENTITIES = 10000;
const float GRAVITY = 9.81f;

// Private members: _underscore or private section
class Component {
private:
    float _internalValue;
    glm::vec2 _state;
};
```

---

## Rule 13: CMakeLists.txt Dependency Order

**MANDATORY**: Link targets must follow module chain.

### ✅ CORRECT
```cmake
# Core has no dependencies
add_library(SLE_Core ...)

# Platform depends on Core
add_library(SLE_Platform ...)
target_link_libraries(SLE_Platform SLE_Core)

# Renderer depends on Core, Platform
add_library(SLE_Renderer ...)
target_link_libraries(SLE_Renderer SLE_Core SLE_Platform)

# Scene depends on Core, Resources
add_library(SLE_Scene ...)
target_link_libraries(SLE_Scene SLE_Core SLE_Resources)

# Systems depends on everything
add_library(SLE_Systems ...)
target_link_libraries(SLE_Systems SLE_Core SLE_Platform SLE_Renderer SLE_Resources SLE_Scene SLE_Scripting)
```

### Verification Command
```bash
# Build with clean dependencies
cd c:\projects\engine
cmake -B build/debug
cmake --build build/debug
# If linker errors: dependency order violated
```

---

## Compliance Checklist for AI

Before generating code, verify:

- [ ] **Dependency chain respected** — No upward dependencies
- [ ] **Components data-only** — No methods on components
- [ ] **Systems use Registry views** — `registry.view<T>()`
- [ ] **Lua through ScriptApi** — Extend interface, implement in ScriptApiImpl
- [ ] **Rendering via commands** — RenderSystem emits QuadCommand
- [ ] **No global state** — All state in Runtime or module ownership
- [ ] **Result pattern** — Fallible ops return Result<T, E>
- [ ] **Documentation updated** — .md files match code changes
- [ ] **Input cleared first** — Before polling, not after
- [ ] **Naming conventions** — PascalCase/camelCase correct
- [ ] **CMakeLists.txt updated** — Dependency order maintained

---

## Example: Correct AI Task Request

**GOOD REQUEST:**
```
Add a HealthComponent that stores current/max health.
Include:
1. Component struct (data-only, public members)
2. Lua serialization functions
3. HealthComponent damage(entity, amount) in ScriptApiImpl
4. Engine.damageEntity(entity, amount) Lua binding
5. Update COMPONENT_SYSTEM_GUIDE.md with serialization pattern
6. Update SCRIPTING_CURRENT.md with new Lua function

Follow copilot-instructions.md rules strictly.
```

**BAD REQUEST:**
```
Make the Renderer aware of Scene components for optimization.
```
(Violates Rule 1: breaks layering)

---

## Exception Process

If you must violate a rule:

1. **Document the violation** in a comment with `// EXCEPTION:` prefix
2. **Explain rationale** — why the normal pattern doesn't work
3. **Get approval** — from architecture authority (documented in ARCHITECTURE.md)
4. **Update ARCHITECTURE.md** — document the exception as a known deviation
5. **Add to FAQ** — explain to future developers

**Example Exception Comment**:
```cpp
// EXCEPTION: Renderer accessing Scene for frustum culling optimization
// Rationale: Early-stage optimization critical for 2000+ sprite performance
// Approved: Architecture council (2026-05-18)
// See: ARCHITECTURE.md "Known Exceptions"
void Renderer::cullAgainstFrustum(const Scene& scene) {
    // ...
}
```

---

## Resources

- **copilot-instructions.md**: Main architectural guide
- **ARCHITECTURE_VERIFIED.md**: Comprehensive verified architecture
- **ARCHITECTURE.md**: Design decisions, module responsibilities
- **IMPLEMENTATION_OVERVIEW.md**: Current-state, frame loop
- **COMPONENT_SYSTEM_GUIDE.md**: Adding components pattern
- **SCRIPTING_CURRENT.md**: Lua API, script lifecycle
- **RENDERING_CURRENT.md**: Render pipeline details
- **SCENE_ECS_CURRENT.md**: Entity model, hierarchy
- **OPTIMIZATIONS_CURRENT.md**: Performance optimizations

---

**Version**: 1.0 (May 2026)  
**Status**: Mandatory for all AI-generated code  
**Enforcement**: Compile-time (dependency order), code review (rules 2-10), documentation check (Rule 8)

**Last Updated**: May 2026  
**Reviewed By**: Code Architecture Review (verified against codebase)
