# Lua Scripting Implementation Quick Start

This guide provides a step-by-step checklist to implement Lua scripting in SLE.

---

## Quick Reference

**Three Key Files:**
1. `Scripting/include/sle/scripting/ScriptApi.hpp` - Abstract interface
2. `Engine/src/ScriptApiImpl.cpp` - Implementation  
3. `Scripting/src/LuaBindings.cpp` - Lua↔C++ bridge

**Dependency Flow:**
```
Runtime (Engine) 
  ↓ implements
ScriptApi (interface)
  ← bindings registered in LuaBindings.cpp
Lua VM
  ← controls
ECS System
```

---

## Phase 2a: Core Infrastructure (Week 1)

### Task 1: Lua VM Initialization

**Files to modify:**
- `Scripting/CMakeLists.txt` - add `lua::lua` dependency
- `Scripting/include/sle/scripting/ScriptEngine.hpp` - add Lua state management
- `Scripting/src/ScriptEngine.cpp` - implement init/shutdown

**Checklist:**
```cpp
// ScriptEngine.hpp
class ScriptEngine
{
    bool init();                    // Create lua_State
    void shutdown();                // Close lua_State
    
private:
    lua_State* L = nullptr;
    ScriptApi* api = nullptr;
};

// ScriptEngine.cpp
bool ScriptEngine::init()
{
    L = luaL_newstate();
    luaL_openlibs(L);
    lua_atpanic(L, panicHandler);
    
    registerLuaBindings(L, api);  // Register Engine API
    
    return true;
}
```

**Test:**
```bash
cd c:\projects\engine
cmake -B build/debug
cmake --build build/debug
# Should compile without Lua errors
```

### Task 2: Create ScriptApi Interface

**File:** `Scripting/include/sle/scripting/ScriptApi.hpp`

```cpp
namespace sle::scripting {

class ScriptApi
{
    // Entity management
    virtual EntityRef createEntity() = 0;
    virtual void destroyEntity(EntityRef entity) = 0;
    virtual bool isEntityAlive(EntityRef entity) const = 0;
    
    // Components
    virtual LuaTable getComponent(EntityRef e, const std::string& name) = 0;
    virtual void setComponent(EntityRef e, const std::string& name, 
                             const LuaTable& data) = 0;
    
    // Engine state
    virtual float getDeltaTime() const = 0;
    virtual glm::vec2 getWindowSize() const = 0;
    
    // Input
    virtual bool isKeyDown(int key) const = 0;
};

} // namespace sle::scripting
```

**Test:** Compiles, abstract interface (no implementation).

### Task 3: Implement ScriptApiImpl

**File:** `Engine/src/ScriptApiImpl.cpp`

```cpp
namespace sle {

EntityRef ScriptApiImpl::createEntity()
{
    auto* obj = engine->scene.createObject();
    auto e = obj->getEntity();
    return EntityRef{e.getID()};
}

LuaTable ScriptApiImpl::getComponent(EntityRef entity, const std::string& name)
{
    LuaTable result;
    
    if (name == "Transform")
    {
        auto* t = engine->scene.getRegistry()
            .getComponent<sle::components::Transform>(Entity(entity.id, nullptr));
        if (!t) return result;
        
        result.data["x"] = std::to_string(t->position.x);
        result.data["y"] = std::to_string(t->position.y);
    }
    
    return result;
}

// ... other methods ...

} // namespace sle
```

**Test:**
```bash
cmake --build build/debug
# Should have no linker errors
```

---

## Phase 2b: Lua Bindings (Week 1-2)

### Task 4: Create Lua Binding Functions

**File:** `Scripting/src/LuaBindings.cpp`

```cpp
void registerLuaBindings(lua_State* L, ScriptApi* api)
{
    // Create Engine global table
    lua_newtable(L);
    int engineTable = lua_gettop(L);
    
    // Store api pointer
    lua_pushlightuserdata(L, api);
    int apiIndex = lua_gettop(L);
    
    // Engine.createEntity()
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        EntityRef ref = api->createEntity();
        lua_pushinteger(L, ref.id);
        return 1;
    }, 1);
    lua_setfield(L, engineTable, "createEntity");
    
    // Engine.getComponent(entity, name)
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        EntityRef entity{(uint32_t)luaL_checkinteger(L, 1)};
        const char* name = luaL_checkstring(L, 2);
        
        LuaTable data = api->getComponent(entity, name);
        nativeTableToLua(L, data);
        return 1;
    }, 1);
    lua_setfield(L, engineTable, "getComponent");
    
    // ... more bindings ...
    
    lua_setglobal(L, "Engine");
}
```

**Test:** Manual test with Lua snippet
```lua
-- Test binding from Lua
local entity = Engine.createEntity()
Engine.log("Created entity: " .. entity)
```

### Task 5: Script Loading

**File:** `Scripting/src/ScriptEngine.cpp`

Add to `ScriptEngine`:
```cpp
bool ScriptEngine::loadScriptFile(const std::string& scriptAsset)
{
    std::ifstream file(scriptAsset);
    if (!file.is_open())
        return false;
    
    std::stringstream buffer;
    buffer << file.rdbuf();
    std::string code = buffer.str();
    
    if (luaL_dostring(L, code.c_str()) != LUA_OK)
    {
        Log::error("Lua error: {}", lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }
    
    return true;
}

bool ScriptEngine::extractFunctions(const std::string& asset, 
                                   int& refInit, int& refUpdate, int& refDestroy)
{
    lua_getglobal(L, "init");
    refInit = luaL_ref(L, LUA_REGISTRYINDEX);
    
    lua_getglobal(L, "update");
    refUpdate = luaL_ref(L, LUA_REGISTRYINDEX);
    
    lua_getglobal(L, "destroy");
    refDestroy = luaL_ref(L, LUA_REGISTRYINDEX);
    
    return true;
}
```

---

## Phase 2c: Script Lifecycle (Week 2)

### Task 6: Initialize ScriptComponent

**File:** `Scripting/src/ScriptEngine.cpp`

```cpp
bool ScriptEngine::initializeScript(sle::entity::Entity entity,
                                    sle::components::ScriptComponent& script)
{
    if (script.initialized)
        return true;
    
    if (!loadScriptFile(script.scriptAsset))
        return false;
    
    int refInit, refUpdate, refDestroy;
    if (!extractFunctions(script.scriptAsset, refInit, refUpdate, refDestroy))
        return false;
    
    script.luaRefInit = refInit;
    script.luaRefUpdate = refUpdate;
    script.luaRefDestroy = refDestroy;
    
    // Call init()
    if (!callLuaFunction(refInit, entity.getID()))
        return false;
    
    script.initialized = true;
    return true;
}

bool ScriptEngine::callLuaFunction(int funcRef, uint32_t entityId, float dt)
{
    if (funcRef == LUA_NOREF)
        return true;  // Function doesn't exist, that's ok
    
    lua_rawgeti(L, LUA_REGISTRYINDEX, funcRef);
    lua_pushinteger(L, entityId);
    if (dt > 0)
        lua_pushnumber(L, dt);
    
    int args = dt > 0 ? 2 : 1;
    if (lua_pcall(L, args, 0, 0) != LUA_OK)
    {
        Log::error("Lua error: {}", lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }
    
    return true;
}
```

### Task 7: Update Loop Integration

**File:** `Engine/src/Engine.cpp`

In `Engine::tick()`:

```cpp
void Engine::tick(float dt)
{
    // Input
    Input::update();
    window.pollEvents();
    
    // Script detection & init
    scene.view<sle::components::ScriptComponent>(
        [this](sle::entity::Entity entity, sle::components::ScriptComponent& script)
        {
            if (!script.initialized && script.enabled)
            {
                scriptEngine.initializeScript(entity, script);
            }
        });
    
    // Script update
    scriptEngine.updateScripts(dt);
    
    // Render
    renderer.beginFrame();
    scene.view<sle::components::Transform, sle::components::SpriteRenderer>(
        [this](sle::entity::Entity e, auto& t, auto& s) {
            renderer.submit({t.position, s.size, s.color, s.textureId});
        });
    renderer.endFrame();
}
```

---

## Phase 2d: Test Lua Scripts (Week 2-3)

### Task 8: Create Test Script

**File:** `assets/scripts/test.lua`

```lua
function init(entity)
    Engine.log("===== TEST SCRIPT INIT =====")
    Engine.log("Entity ID: " .. entity)
    
    local transform = Engine.getComponent(entity, "Transform")
    if transform then
        Engine.log("Position: " .. transform.x .. ", " .. transform.y)
    end
    
    Engine.log("===== INIT COMPLETE =====")
end

function update(entity, dt)
    -- Move up on W key
    if Engine.isKeyDown(GLFW_KEY_W) then
        Engine.log("W pressed!")
    end
end

function destroy(entity)
    Engine.log("Test script destroyed")
end
```

### Task 9: Create Test Scene

**File:** `assets/scenes/test.json`

```json
{
  "entities": [
    {
      "name": "test_entity",
      "components": {
        "Transform": {
          "position": [100, 100],
          "rotation": 0,
          "scale": [1, 1]
        },
        "SpriteRenderer": {
          "color": [1, 1, 1, 1],
          "size": [32, 32],
          "textureId": 0
        },
        "ScriptComponent": {
          "scriptAsset": "assets/scripts/test.lua",
          "enabled": true
        }
      }
    }
  ]
}
```

### Task 10: Test Execution

**In Sandbox/main.cpp:**

```cpp
int main()
{
    EngineConfig config;
    config.width = 800;
    config.height = 600;
    config.title = "SLE Scripting Test";
    
    Engine engine(config);
    
    if (!engine.init())
    {
        Log::error("Failed to init engine");
        return -1;
    }
    
    // Create test entity with script
    auto entity = engine.getScene().createObject();
    auto* transform = entity->getEntity().getComponent<Transform>();
    auto* sprite = entity->getEntity().getComponent<SpriteRenderer>();
    auto* script = entity->getEntity().addComponent<ScriptComponent>();
    
    if (script)
    {
        script->scriptAsset = "assets/scripts/test.lua";
        script->enabled = true;
    }
    
    engine.run();
}
```

**Expected Output:**
```
[INFO] ===== TEST SCRIPT INIT =====
[INFO] Entity ID: 1
[INFO] Position: 0, 0
[INFO] ===== INIT COMPLETE =====
[INFO] W pressed!  (when holding W)
```

---

## Phase 2e: Extend Component Access (Week 3)

### Task 11: Complete getComponent() for All Components

In `ScriptApiImpl::getComponent()`:

```cpp
LuaTable ScriptApiImpl::getComponent(EntityRef entity, const std::string& name)
{
    LuaTable result;
    sle::entity::Entity e(entity.id, nullptr);
    auto& registry = engine->scene.getRegistry();
    
    if (name == "Transform")
    {
        auto* t = registry.getComponent<sle::components::Transform>(e);
        if (!t) return result;
        
        result.data["x"] = std::to_string(t->position.x);
        result.data["y"] = std::to_string(t->position.y);
        result.data["rotation"] = std::to_string(t->rotation);
        result.data["scaleX"] = std::to_string(t->scale.x);
        result.data["scaleY"] = std::to_string(t->scale.y);
    }
    else if (name == "SpriteRenderer")
    {
        auto* s = registry.getComponent<sle::components::SpriteRenderer>(e);
        if (!s) return result;
        
        result.data["r"] = std::to_string(s->color.r);
        result.data["g"] = std::to_string(s->color.g);
        result.data["b"] = std::to_string(s->color.b);
        result.data["a"] = std::to_string(s->color.a);
        result.data["sizeX"] = std::to_string(s->size.x);
        result.data["sizeY"] = std::to_string(s->size.y);
        result.data["textureId"] = std::to_string(s->textureId);
    }
    
    return result;
}
```

### Task 12: Complete setComponent() for All Components

In `ScriptApiImpl::setComponent()`:

```cpp
void ScriptApiImpl::setComponent(EntityRef entity, const std::string& name,
                               const LuaTable& data)
{
    sle::entity::Entity e(entity.id, nullptr);
    auto& registry = engine->scene.getRegistry();
    
    if (name == "Transform")
    {
        auto* t = registry.getComponent<sle::components::Transform>(e);
        if (!t) return;
        
        if (data.data.count("x")) t->position.x = std::stof(data.data.at("x"));
        if (data.data.count("y")) t->position.y = std::stof(data.data.at("y"));
        if (data.data.count("rotation")) t->rotation = std::stof(data.data.at("rotation"));
    }
    else if (name == "SpriteRenderer")
    {
        auto* s = registry.getComponent<sle::components::SpriteRenderer>(e);
        if (!s) return;
        
        if (data.data.count("r")) s->color.r = std::stof(data.data.at("r"));
        if (data.data.count("g")) s->color.g = std::stof(data.data.at("g"));
        if (data.data.count("b")) s->color.b = std::stof(data.data.at("b"));
        if (data.data.count("a")) s->color.a = std::stof(data.data.at("a"));
    }
}
```

### Task 13: Test Component Modification

**Script test:**
```lua
function update(entity, dt)
    if Engine.isKeyDown(GLFW_KEY_UP) then
        local t = Engine.getComponent(entity, "Transform")
        t.y = t.y - 100 * dt
        Engine.setComponent(entity, "Transform", t)
    end
end
```

---

## Phase 2f: Input & Engine State Bindings (Week 3-4)

### Task 14: Bind Input Functions

In `LuaBindings.cpp`:

```cpp
// Engine.isKeyDown(key)
lua_pushvalue(L, apiIndex);
lua_pushcclosure(L, [](lua_State* L) -> int {
    ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
    int key = luaL_checkinteger(L, 1);
    lua_pushboolean(L, api->isKeyDown(key));
    return 1;
}, 1);
lua_setfield(L, engineTable, "isKeyDown");

// Engine.isKeyPressed(key)
// Engine.isKeyReleased(key)
// Engine.getDeltaTime()
// Engine.getWindowSize()
// ... etc ...
```

### Task 15: Test Input

**Script:**
```lua
function update(entity, dt)
    if Engine.isKeyDown(GLFW_KEY_W) then
        local t = Engine.getComponent(entity, "Transform")
        t.y = t.y + 200 * dt
        Engine.setComponent(entity, "Transform", t)
    end
    
    if Engine.isKeyPressed(GLFW_KEY_SPACE) then
        Engine.log("Space pressed!")
    end
end
```

---

## Build & Test Checklist

**Per phase:**

```bash
# After each task:
cd c:\projects\engine

# 1. Reconfigure CMake
cmake -B build/debug -G "Visual Studio 17 2022"

# 2. Clean build
cmake --build build/debug --config Debug --clean-first

# 3. Run
.\build\debug\Sandbox\Debug\sandbox.exe

# 4. Check output in terminal for Lua messages
# [INFO] ===== TEST SCRIPT INIT =====
# [INFO] Entity ID: 1
```

---

## Common Issues & Fixes

### Issue: "Undefined reference to lua_..."
**Fix:** Check that lua::lua is linked in CMakeLists.txt
```cmake
target_link_libraries(sle_scripting PUBLIC lua::lua)
```

### Issue: "Lua error: attempt to index a nil value"
**Fix:** Check that Engine API is registered before loading scripts
```cpp
registerLuaBindings(L, api);  // Do this first!
luaL_dostring(L, code);        // Then load script
```

### Issue: Component changes don't appear on screen
**Fix:** Ensure you're calling `Engine.setComponent()` to persist changes
```lua
-- GOOD
local t = Engine.getComponent(entity, "Transform")
t.y = t.y + 100
Engine.setComponent(entity, "Transform", t)  -- Commit changes!

-- BAD (changes are local, not persisted)
local t = Engine.getComponent(entity, "Transform")
t.y = t.y + 100
```

### Issue: Script runs once then crashes
**Fix:** Check Lua refs are properly stored and cleaned up
```cpp
// Make sure script.initialized is set
// Make sure luaRefs are valid before calling
```

---

## Success Criteria

By end of Phase 2e, you should be able to:

- ✅ Create entity from C++ with ScriptComponent
- ✅ Load Lua script asset
- ✅ Call init(entity) when script loads
- ✅ Call update(entity, dt) every frame
- ✅ Access Transform via `Engine.getComponent(entity, "Transform")`
- ✅ Modify Transform via `Engine.setComponent(entity, "Transform", data)`
- ✅ Check keyboard input via `Engine.isKeyDown(GLFW_KEY_W)`
- ✅ See entity move on screen when script modifies position

---

## Next Phases (Optional Enhancements)

- **Phase 3:** Scene loading from JSON
- **Phase 4:** Resources loading from Lua (textures, shaders)
- **Phase 5:** Physics integration
- **Phase 6:** Audio integration
- **Phase 7:** Animation system

