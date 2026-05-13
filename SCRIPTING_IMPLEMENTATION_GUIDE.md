# SLE Scripting Implementation Guide

## Overview

This document provides concrete patterns for implementing the Lua scripting layer, including:
- ScriptApi interface implementation
- Lua binding layer patterns  
- Script lifecycle management
- Error handling and debugging
- Common pitfalls and solutions

---

## 1. ScriptApi Implementation Pattern

### 1.1 Abstract Interface (in Scripting module)

```cpp
// Scripting/include/sle/scripting/ScriptApi.hpp
#pragma once

#include <glm/glm.hpp>
#include <string>
#include <vector>
#include <map>
#include <cstdint>

namespace sle::scripting {

// Opaque types for Lua
struct EntityRef { uint32_t id; };
struct LuaTable 
{
    std::map<std::string, std::string> data;  // For now, serialize to strings
    // Later: variant types for proper Lua types
};

class ScriptApi
{
public:
    virtual ~ScriptApi() = default;
    
    // ====== ENTITY MANAGEMENT ======
    virtual EntityRef createEntity() = 0;
    virtual void destroyEntity(EntityRef entity) = 0;
    virtual bool isEntityAlive(EntityRef entity) const = 0;
    
    // ====== COMPONENT ACCESS ======
    virtual LuaTable getComponent(EntityRef entity, const std::string& componentName) = 0;
    virtual void setComponent(EntityRef entity, const std::string& componentName, 
                             const LuaTable& data) = 0;
    virtual bool hasComponent(EntityRef entity, const std::string& componentName) const = 0;
    
    // ====== ENGINE STATE ======
    virtual float getDeltaTime() const = 0;
    virtual float getElapsedTime() const = 0;
    virtual glm::vec2 getWindowSize() const = 0;
    
    // ====== CAMERA ======
    virtual void setCameraPosition(glm::vec2 pos) = 0;
    virtual void setCameraZoom(float zoom) = 0;
    
    // ====== INPUT ======
    virtual bool isKeyDown(int key) const = 0;
    virtual bool isKeyPressed(int key) const = 0;
    virtual bool isKeyReleased(int key) const = 0;
    virtual glm::dvec2 getMousePosition() const = 0;
    virtual bool isMouseButtonDown(int button) const = 0;
    
    // ====== RESOURCES ======
    virtual uint32_t loadTexture(const std::string& assetPath) = 0;
    virtual uint32_t loadShader(const std::string& vertPath, 
                               const std::string& fragPath) = 0;
    
    // ====== RENDERING ======
    virtual void drawQuad(glm::vec2 pos, glm::vec2 size, glm::vec4 color, 
                         uint32_t textureId = 0) = 0;
    
    // ====== LOGGING ======
    virtual void log(const std::string& message) = 0;
    virtual void warn(const std::string& message) = 0;
    virtual void error(const std::string& message) = 0;
};

} // namespace sle::scripting
```

### 1.2 Runtime Implementation (in Engine module)

```cpp
// Engine/include/sle/engine/ScriptApiImpl.hpp
#pragma once

#include <sle/scripting/ScriptApi.hpp>

namespace sle {

class Engine;  // Forward declare

class ScriptApiImpl : public sle::scripting::ScriptApi
{
public:
    ScriptApiImpl(Engine* engine) : engine(engine) {}
    
    // Implement all virtual methods
    EntityRef createEntity() override;
    void destroyEntity(EntityRef entity) override;
    bool isEntityAlive(EntityRef entity) const override;
    
    LuaTable getComponent(EntityRef entity, const std::string& componentName) override;
    void setComponent(EntityRef entity, const std::string& componentName, 
                     const LuaTable& data) override;
    bool hasComponent(EntityRef entity, const std::string& componentName) const override;
    
    float getDeltaTime() const override;
    float getElapsedTime() const override;
    glm::vec2 getWindowSize() const override;
    
    void setCameraPosition(glm::vec2 pos) override;
    void setCameraZoom(float zoom) override;
    
    bool isKeyDown(int key) const override;
    bool isKeyPressed(int key) const override;
    bool isKeyReleased(int key) const override;
    glm::dvec2 getMousePosition() const override;
    bool isMouseButtonDown(int button) const override;
    
    uint32_t loadTexture(const std::string& assetPath) override;
    uint32_t loadShader(const std::string& vertPath, const std::string& fragPath) override;
    
    void drawQuad(glm::vec2 pos, glm::vec2 size, glm::vec4 color, 
                 uint32_t textureId = 0) override;
    
    void log(const std::string& message) override;
    void warn(const std::string& message) override;
    void error(const std::string& message) override;
    
private:
    Engine* engine;  // Non-owning pointer to runtime
};

} // namespace sle
```

### 1.3 Implementation Examples

```cpp
// Engine/src/ScriptApiImpl.cpp

namespace sle {

using namespace sle::scripting;

EntityRef ScriptApiImpl::createEntity()
{
    auto entity = engine->getScene().createObject();
    return EntityRef{entity->getEntity().getID()};
}

void ScriptApiImpl::destroyEntity(EntityRef entity)
{
    Scene& scene = engine->getScene();
    // Find and destroy the engine object with this entity ID
    // (Need scene query capability)
    Log::info("Lua destroyed entity {}", entity.id);
}

bool ScriptApiImpl::isEntityAlive(EntityRef entity) const
{
    // Check if entity still exists in registry
    return engine->getScene().getRegistry().hasEntity(Entity(entity.id, nullptr));
}

LuaTable ScriptApiImpl::getComponent(EntityRef entity, const std::string& componentName)
{
    LuaTable result;
    
    if (componentName == "Transform")
    {
        // Get from scene
        auto* transform = engine->getScene().getRegistry()
            .getComponent<sle::components::Transform>(Entity(entity.id, nullptr));
        
        if (!transform)
            return result;  // nil
        
        result.data["x"] = std::to_string(transform->position.x);
        result.data["y"] = std::to_string(transform->position.y);
        result.data["rotation"] = std::to_string(transform->rotation);
        result.data["scaleX"] = std::to_string(transform->scale.x);
        result.data["scaleY"] = std::to_string(transform->scale.y);
    }
    else if (componentName == "SpriteRenderer")
    {
        auto* sprite = engine->getScene().getRegistry()
            .getComponent<sle::components::SpriteRenderer>(Entity(entity.id, nullptr));
        
        if (!sprite)
            return result;
        
        result.data["colorR"] = std::to_string(sprite->color.r);
        result.data["colorG"] = std::to_string(sprite->color.g);
        result.data["colorB"] = std::to_string(sprite->color.b);
        result.data["colorA"] = std::to_string(sprite->color.a);
        result.data["sizeX"] = std::to_string(sprite->size.x);
        result.data["sizeY"] = std::to_string(sprite->size.y);
        result.data["textureId"] = std::to_string(sprite->textureId);
    }
    
    return result;
}

void ScriptApiImpl::setComponent(EntityRef entity, const std::string& componentName, 
                               const LuaTable& data)
{
    if (componentName == "Transform")
    {
        auto* transform = engine->getScene().getRegistry()
            .getComponent<sle::components::Transform>(Entity(entity.id, nullptr));
        
        if (!transform)
            return;
        
        if (data.data.count("x")) transform->position.x = std::stof(data.data.at("x"));
        if (data.data.count("y")) transform->position.y = std::stof(data.data.at("y"));
        if (data.data.count("rotation")) transform->rotation = std::stof(data.data.at("rotation"));
        if (data.data.count("scaleX")) transform->scale.x = std::stof(data.data.at("scaleX"));
        if (data.data.count("scaleY")) transform->scale.y = std::stof(data.data.at("scaleY"));
    }
}

float ScriptApiImpl::getDeltaTime() const
{
    return engine->getTimer().getDeltaTime();
}

bool ScriptApiImpl::isKeyDown(int key) const
{
    return sle::input::Input::isKeyDown(key);
}

// ... implement remaining methods ...

} // namespace sle
```

---

## 2. Lua Bindings Layer

### 2.1 Binding Helpers

```cpp
// Scripting/include/sle/scripting/LuaBindings.hpp
#pragma once

#include <lua.hpp>
#include <functional>
#include <string>
#include <glm/glm.hpp>

namespace sle::scripting {

// Helper to convert Lua table → LuaTable
LuaTable luaTableToNative(lua_State* L, int index);

// Helper to push LuaTable to Lua stack
void nativeTableToLua(lua_State* L, const LuaTable& table);

// Helper to safely get number from Lua table
float getLuaTableFloat(const LuaTable& table, const std::string& key, float def = 0.0f);

// Safe string conversion
std::string getLuaTableString(const LuaTable& table, const std::string& key, 
                             const std::string& def = "");

// Bind C++ functions to Lua
void registerLuaBindings(lua_State* L, ScriptApi* api);

} // namespace sle::scripting
```

### 2.2 Binding Implementation

```cpp
// Scripting/src/LuaBindings.cpp
#include <sle/scripting/LuaBindings.hpp>
#include <sle/core/Log.hpp>

namespace sle::scripting {

using sle::core::Log;

LuaTable luaTableToNative(lua_State* L, int index)
{
    LuaTable result;
    
    if (!lua_istable(L, index))
        return result;
    
    lua_pushnil(L);  // nil key
    while (lua_next(L, index) != 0)
    {
        const char* key = lua_tostring(L, -2);
        const char* value = lua_tostring(L, -1);
        
        if (key && value)
            result.data[key] = value;
        
        lua_pop(L, 1);  // Remove value, keep key
    }
    
    return result;
}

void nativeTableToLua(lua_State* L, const LuaTable& table)
{
    lua_newtable(L);
    
    for (const auto& [key, value] : table.data)
    {
        lua_pushstring(L, value.c_str());
        lua_setfield(L, -2, key.c_str());
    }
}

float getLuaTableFloat(const LuaTable& table, const std::string& key, float def)
{
    auto it = table.data.find(key);
    if (it == table.data.end())
        return def;
    
    try
    {
        return std::stof(it->second);
    }
    catch (...)
    {
        Log::warn("Failed to parse {} as float: {}", key, it->second);
        return def;
    }
}

std::string getLuaTableString(const LuaTable& table, const std::string& key, 
                             const std::string& def)
{
    auto it = table.data.find(key);
    return it != table.data.end() ? it->second : def;
}

void registerLuaBindings(lua_State* L, ScriptApi* api)
{
    // Create global Engine table
    lua_newtable(L);
    int engineTable = lua_gettop(L);
    
    // Store API pointer as upvalue
    lua_pushlightuserdata(L, api);
    int apiIndex = lua_gettop(L);
    
    // ====== Entity Management ======
    
    // Engine.createEntity()
    lua_pushvalue(L, apiIndex);  // Push api as upvalue
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        EntityRef ref = api->createEntity();
        lua_pushinteger(L, ref.id);
        return 1;
    }, 1);
    lua_setfield(L, engineTable, "createEntity");
    
    // Engine.destroyEntity(entity)
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        uint32_t id = luaL_checkinteger(L, 1);
        api->destroyEntity(EntityRef{id});
        return 0;
    }, 1);
    lua_setfield(L, engineTable, "destroyEntity");
    
    // Engine.isEntityAlive(entity)
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        uint32_t id = luaL_checkinteger(L, 1);
        bool alive = api->isEntityAlive(EntityRef{id});
        lua_pushboolean(L, alive);
        return 1;
    }, 1);
    lua_setfield(L, engineTable, "isEntityAlive");
    
    // ====== Component Access ======
    
    // Engine.getComponent(entity, name)
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        
        if (!lua_isinteger(L, 1))
        {
            return luaL_error(L, "getComponent: first argument must be entity ID (integer)");
        }
        
        const char* componentName = luaL_checkstring(L, 2);
        EntityRef entity{(uint32_t)lua_tointeger(L, 1)};
        
        if (!api->isEntityAlive(entity))
        {
            return luaL_error(L, "getComponent: entity %u is not alive", entity.id);
        }
        
        LuaTable data = api->getComponent(entity, componentName);
        nativeTableToLua(L, data);
        return 1;
    }, 1);
    lua_setfield(L, engineTable, "getComponent");
    
    // Engine.setComponent(entity, name, data)
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        
        EntityRef entity{(uint32_t)luaL_checkinteger(L, 1)};
        const char* name = luaL_checkstring(L, 2);
        
        if (!lua_istable(L, 3))
            return luaL_error(L, "setComponent: third argument must be a table");
        
        if (!api->isEntityAlive(entity))
            return luaL_error(L, "setComponent: entity %u is not alive", entity.id);
        
        LuaTable data = luaTableToNative(L, 3);
        api->setComponent(entity, name, data);
        return 0;
    }, 1);
    lua_setfield(L, engineTable, "setComponent");
    
    // ====== Input ======
    
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
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        int key = luaL_checkinteger(L, 1);
        lua_pushboolean(L, api->isKeyPressed(key));
        return 1;
    }, 1);
    lua_setfield(L, engineTable, "isKeyPressed");
    
    // Engine.getDeltaTime()
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        lua_pushnumber(L, api->getDeltaTime());
        return 1;
    }, 1);
    lua_setfield(L, engineTable, "getDeltaTime");
    
    // ====== Logging ======
    
    // Engine.log(message)
    lua_pushvalue(L, apiIndex);
    lua_pushcclosure(L, [](lua_State* L) -> int {
        ScriptApi* api = (ScriptApi*)lua_touserdata(L, lua_upvalueindex(1));
        const char* msg = luaL_checkstring(L, 1);
        api->log(msg);
        return 0;
    }, 1);
    lua_setfield(L, engineTable, "log");
    
    // Remove api pointer from stack
    lua_remove(L, apiIndex);
    
    // Set Engine as global
    lua_setglobal(L, "Engine");
}

} // namespace sle::scripting
```

---

## 3. Script Lifecycle Management

### 3.1 Script Loading

```cpp
// Scripting/include/sle/scripting/ScriptEngine.hpp
#pragma once

#include <lua.hpp>
#include <unordered_map>
#include <memory>
#include <sle/entity/Entity.hpp>
#include <sle/components/ScriptComponent.hpp>
#include <sle/scripting/ScriptApi.hpp>

namespace sle::scripting {

struct ScriptInstance
{
    uint32_t entityId;
    std::string scriptAsset;
    
    // Lua registry references
    int refInit = LUA_NOREF;
    int refUpdate = LUA_NOREF;
    int refDestroy = LUA_NOREF;
    
    bool initialized = false;
    bool loadedSuccessfully = false;
};

class ScriptEngine
{
public:
    ScriptEngine(ScriptApi* api);
    ~ScriptEngine();
    
    bool init();
    void shutdown();
    
    // Load and initialize a script asset for an entity
    bool initializeScript(sle::entity::Entity entity, sle::components::ScriptComponent& script);
    
    // Update all active scripts
    void updateScripts(float dt);
    
    // Cleanup script when component removed or entity destroyed
    void cleanupScript(uint32_t entityId, sle::components::ScriptComponent& script);
    
    // Query loaded scripts
    bool isScriptLoaded(const std::string& scriptAsset) const;
    
private:
    lua_State* L = nullptr;
    ScriptApi* api = nullptr;
    
    // Maps entity ID → script instance
    std::unordered_map<uint32_t, ScriptInstance> instances;
    
    // Cache of loaded script assets (asset path → (init, update, destroy refs))
    std::unordered_map<std::string, std::tuple<int, int, int>> scriptCache;
    
    // Load .lua file into Lua state
    bool loadScriptFile(const std::string& scriptAsset);
    
    // Extract function references from loaded script
    bool extractFunctions(const std::string& scriptAsset, int& refInit, 
                         int& refUpdate, int& refDestroy);
    
    // Call a Lua function with error handling
    bool callLuaFunction(int funcRef, uint32_t entityId, float dt = 0.0f);
    
    // Error callback for Lua
    static int luaPanicHandler(lua_State* L);
};

} // namespace sle::scripting
```

### 3.2 Implementation

```cpp
// Scripting/src/ScriptEngine.cpp
#include <sle/scripting/ScriptEngine.hpp>
#include <sle/core/Log.hpp>
#include <fstream>
#include <sstream>

namespace sle::scripting {

using sle::core::Log;

ScriptEngine::ScriptEngine(ScriptApi* api) : api(api) {}

ScriptEngine::~ScriptEngine()
{
    shutdown();
}

bool ScriptEngine::init()
{
    L = luaL_newstate();
    if (!L)
    {
        Log::error("Failed to create Lua state");
        return false;
    }
    
    // Open standard libraries
    luaL_openlibs(L);
    
    // Register panic handler
    lua_atpanic(L, luaPanicHandler);
    
    // Register Engine API
    registerLuaBindings(L, api);
    
    Log::info("Lua VM initialized");
    return true;
}

void ScriptEngine::shutdown()
{
    // Clean up all scripts
    for (auto& [entityId, instance] : instances)
    {
        cleanupScript(entityId, {});
    }
    instances.clear();
    
    // Clean up cached scripts
    for (auto& [asset, refs] : scriptCache)
    {
        luaL_unref(L, LUA_REGISTRYINDEX, std::get<0>(refs));
        luaL_unref(L, LUA_REGISTRYINDEX, std::get<1>(refs));
        luaL_unref(L, LUA_REGISTRYINDEX, std::get<2>(refs));
    }
    scriptCache.clear();
    
    // Destroy Lua state
    if (L)
    {
        lua_close(L);
        L = nullptr;
    }
    
    Log::info("Lua VM shutdown");
}

bool ScriptEngine::initializeScript(sle::entity::Entity entity, 
                                    sle::components::ScriptComponent& script)
{
    if (script.initialized)
        return true;  // Already initialized
    
    if (!loadScriptFile(script.scriptAsset))
    {
        Log::error("Failed to load script: {}", script.scriptAsset);
        return false;
    }
    
    // Get or extract functions
    int refInit, refUpdate, refDestroy;
    if (!extractFunctions(script.scriptAsset, refInit, refUpdate, refDestroy))
    {
        Log::error("Failed to extract functions from script: {}", script.scriptAsset);
        return false;
    }
    
    // Store in component
    script.luaRefInit = refInit;
    script.luaRefUpdate = refUpdate;
    script.luaRefDestroy = refDestroy;
    
    // Create instance
    ScriptInstance& instance = instances[entity.getID()];
    instance.entityId = entity.getID();
    instance.scriptAsset = script.scriptAsset;
    instance.refInit = refInit;
    instance.refUpdate = refUpdate;
    instance.refDestroy = refDestroy;
    
    // Call init callback
    if (!callLuaFunction(refInit, entity.getID()))
    {
        Log::error("Failed to call init() in script: {}", script.scriptAsset);
        cleanupScript(entity.getID(), script);
        return false;
    }
    
    script.initialized = true;
    instance.initialized = true;
    
    Log::info("Script initialized: {} (entity: {})", script.scriptAsset, entity.getID());
    return true;
}

void ScriptEngine::updateScripts(float dt)
{
    for (auto& [entityId, instance] : instances)
    {
        if (!instance.initialized)
            continue;
        
        callLuaFunction(instance.refUpdate, entityId, dt);
    }
}

void ScriptEngine::cleanupScript(uint32_t entityId, sle::components::ScriptComponent& script)
{
    auto it = instances.find(entityId);
    if (it == instances.end())
        return;
    
    ScriptInstance& instance = it->second;
    
    // Call destroy callback
    if (instance.initialized && instance.refDestroy != LUA_NOREF)
    {
        callLuaFunction(instance.refDestroy, entityId);
    }
    
    instances.erase(it);
    
    Log::info("Script cleaned up (entity: {})", entityId);
}

bool ScriptEngine::loadScriptFile(const std::string& scriptAsset)
{
    // Check cache
    if (scriptCache.count(scriptAsset))
        return true;
    
    // Read file
    std::ifstream file(scriptAsset);
    if (!file.is_open())
    {
        Log::error("Cannot open script file: {}", scriptAsset);
        return false;
    }
    
    std::stringstream buffer;
    buffer << file.rdbuf();
    std::string code = buffer.str();
    
    // Execute in Lua
    if (luaL_dostring(L, code.c_str()) != LUA_OK)
    {
        Log::error("Lua compilation error in {}: {}", scriptAsset, lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }
    
    Log::info("Loaded script: {}", scriptAsset);
    return true;
}

bool ScriptEngine::extractFunctions(const std::string& scriptAsset, int& refInit, 
                                   int& refUpdate, int& refDestroy)
{
    // Check if already extracted
    if (scriptCache.count(scriptAsset))
    {
        auto [init, update, destroy] = scriptCache[scriptAsset];
        refInit = init;
        refUpdate = update;
        refDestroy = destroy;
        return true;
    }
    
    // Get global table for this script's functions
    // Note: assumes functions are defined at global scope in the script
    
    // Get init function
    lua_getglobal(L, "init");
    refInit = luaL_ref(L, LUA_REGISTRYINDEX);  // Pops from stack, stores in registry
    
    if (refInit == LUA_NOREF)
    {
        Log::warn("Script {} has no init() function", scriptAsset);
        refInit = LUA_NOREF;
    }
    
    // Get update function
    lua_getglobal(L, "update");
    refUpdate = luaL_ref(L, LUA_REGISTRYINDEX);
    
    if (refUpdate == LUA_NOREF)
    {
        Log::warn("Script {} has no update() function", scriptAsset);
    }
    
    // Get destroy function
    lua_getglobal(L, "destroy");
    refDestroy = luaL_ref(L, LUA_REGISTRYINDEX);
    
    if (refDestroy == LUA_NOREF)
    {
        Log::warn("Script {} has no destroy() function", scriptAsset);
    }
    
    // Cache for future use
    scriptCache[scriptAsset] = std::make_tuple(refInit, refUpdate, refDestroy);
    
    return true;
}

bool ScriptEngine::callLuaFunction(int funcRef, uint32_t entityId, float dt)
{
    if (funcRef == LUA_NOREF)
        return false;
    
    // Push function onto stack
    lua_rawgeti(L, LUA_REGISTRYINDEX, funcRef);
    
    if (!lua_isfunction(L, -1))
    {
        Log::error("Lua ref is not a function!");
        lua_pop(L, 1);
        return false;
    }
    
    // Push arguments
    lua_pushinteger(L, entityId);  // entity ID
    if (dt > 0)
        lua_pushnumber(L, dt);     // delta time (for update)
    
    // Call function
    int args = dt > 0 ? 2 : 1;
    if (lua_pcall(L, args, 0, 0) != LUA_OK)
    {
        const char* error = lua_tostring(L, -1);
        Log::error("Lua error: {}", error);
        lua_pop(L, 1);
        return false;
    }
    
    return true;
}

int ScriptEngine::luaPanicHandler(lua_State* L)
{
    const char* msg = lua_tostring(L, -1);
    Log::error("Lua panic: {}", msg ? msg : "unknown error");
    return 0;
}

} // namespace sle::scripting
```

---

## 4. Integration with Runtime

### 4.1 Engine Update Loop

```cpp
// Engine/src/Engine.cpp

void Engine::tick(float dt)
{
    // ====== INPUT PHASE ======
    sle::input::Input::update();
    window.pollEvents();
    
    // ====== SCRIPT DETECTION & INITIALIZATION ======
    // Check for new ScriptComponents that need initialization
    scene.view<sle::components::ScriptComponent>(
        [this](sle::entity::Entity entity, sle::components::ScriptComponent& script)
        {
            if (!script.initialized && script.enabled)
            {
                // First time seeing this component - initialize it
                scriptEngine.initializeScript(entity, script);
            }
        });
    
    // ====== SCRIPT UPDATE PHASE ======
    // All scripts update here (Lua runs)
    scriptEngine.updateScripts(dt);
    
    // ====== ECS LOGIC PHASE ======
    // Future: physics, animation, etc.
    
    // ====== RENDER PHASE ======
    renderer.beginFrame();
    
    // Submit quads from ECS
    scene.view<sle::components::Transform, sle::components::SpriteRenderer>(
        [this](sle::entity::Entity entity, 
               sle::components::Transform& t, 
               sle::components::SpriteRenderer& sprite)
        {
            sle::renderer::QuadCommand cmd;
            cmd.position = t.position;
            cmd.size = sprite.size * t.scale;
            cmd.color = sprite.color;
            cmd.texture = sprite.textureId;
            renderer.submit(cmd);
        });
    
    renderer.endFrame();
    window.swapBuffers();
}
```

### 4.2 ScriptApiImpl Implementation

```cpp
// Engine/src/ScriptApiImpl.cpp

namespace sle {

EntityRef ScriptApiImpl::createEntity()
{
    auto* obj = engine->scene.createObject();
    sle::entity::Entity e = obj->getEntity();
    
    // Add default Transform
    auto& registry = engine->scene.getRegistry();
    registry.addComponent<sle::components::Transform>(e);
    
    return EntityRef{e.getID()};
}

LuaTable ScriptApiImpl::getComponent(EntityRef entity, const std::string& componentName)
{
    LuaTable result;
    sle::entity::Entity e(entity.id, nullptr);
    auto& registry = engine->scene.getRegistry();
    
    if (componentName == "Transform")
    {
        auto* t = registry.getComponent<sle::components::Transform>(e);
        if (!t) return result;
        
        result.data["x"] = std::to_string(t->position.x);
        result.data["y"] = std::to_string(t->position.y);
        result.data["rotation"] = std::to_string(t->rotation);
        result.data["scaleX"] = std::to_string(t->scale.x);
        result.data["scaleY"] = std::to_string(t->scale.y);
    }
    
    return result;
}

// ... implement remaining methods ...

} // namespace sle
```

---

## 5. Error Handling Best Practices

### 5.1 Defensive Lua Calls

```cpp
// Always check entity validity before Lua calls
bool ScriptEngine::callLuaFunction(int funcRef, uint32_t entityId, float dt)
{
    // Validate entity exists
    if (!api->isEntityAlive(EntityRef{entityId}))
    {
        Log::warn("Attempting to call Lua function on dead entity {}", entityId);
        return false;
    }
    
    // Protected call with error handler
    lua_rawgeti(L, LUA_REGISTRYINDEX, funcRef);
    lua_pushinteger(L, entityId);
    if (dt > 0)
        lua_pushnumber(L, dt);
    
    if (lua_pcall(L, dt > 0 ? 2 : 1, 0, 0) != LUA_OK)
    {
        Log::error("Lua error (entity {}): {}", entityId, lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }
    
    return true;
}
```

### 5.2 Memory Safety Patterns

```cpp
// Don't store raw Lua pointers
// Instead use Lua registry references with luaL_ref/luaL_unref

class ScriptComponent
{
    // GOOD: Registry references (indices)
    int luaRefInit = LUA_NOREF;
    int luaRefUpdate = LUA_NOREF;
    
    // BAD: Raw pointers (unsafe!)
    // lua_Object init;
};

// When script is destroyed, always unref
void cleanup(ScriptComponent& script)
{
    luaL_unref(L, LUA_REGISTRYINDEX, script.luaRefInit);
    luaL_unref(L, LUA_REGISTRYINDEX, script.luaRefUpdate);
    script.luaRefInit = LUA_NOREF;
    script.luaRefUpdate = LUA_NOREF;
}
```

---

## 6. Debugging Tips

### 6.1 Lua Stack Traces

```cpp
// Print Lua stack for debugging
void printLuaStack(lua_State* L)
{
    int top = lua_gettop(L);
    printf("--- Lua Stack (top=%d) ---\n", top);
    
    for (int i = 1; i <= top; i++)
    {
        int t = lua_type(L, i);
        printf("[%d] %s", i, lua_typename(L, t));
        
        if (t == LUA_TSTRING)
            printf(" = %s", lua_tostring(L, i));
        else if (t == LUA_TNUMBER)
            printf(" = %g", lua_tonumber(L, i));
        else if (t == LUA_TBOOLEAN)
            printf(" = %s", lua_toboolean(L, i) ? "true" : "false");
        
        printf("\n");
    }
}
```

### 6.2 Script Debugging Output

```lua
-- In assets/scripts/player.lua

function init(entity)
    Engine.log("===== PLAYER INIT =====")
    Engine.log("Entity ID: " .. entity)
    
    local t = Engine.getComponent(entity, "Transform")
    if t then
        Engine.log("Position: " .. t.x .. ", " .. t.y)
    else
        Engine.error("NO TRANSFORM COMPONENT!")
    end
    
    Engine.log("===== INIT COMPLETE =====")
end
```

---

## 7. Migration Checklist

### Phase 2a: Basic Infrastructure
- [ ] Add `ScriptApi` abstract interface to Scripting module
- [ ] Implement `ScriptApiImpl` in Engine module
- [ ] Create `LuaBindings.cpp` with `registerLuaBindings()`
- [ ] Implement `ScriptEngine` (Lua VM management)
- [ ] Add test Lua script (`assets/scripts/test.lua`)

### Phase 2b: Script Lifecycle
- [ ] Update `ScriptComponent` to store Lua refs
- [ ] Implement `initializeScript()`, `updateScripts()`, `cleanupScript()`
- [ ] Integrate script detection in Engine::tick()
- [ ] Test init/update/destroy lifecycle

### Phase 2c: Component Access
- [ ] Implement `getComponent()` / `setComponent()` bindings
- [ ] Serialize `Transform` component to Lua tables
- [ ] Serialize `SpriteRenderer` component to Lua tables
- [ ] Test Lua modifying Transform position

### Phase 2d: Input & Engine State
- [ ] Bind `isKeyDown()`, `isKeyPressed()`, `isKeyReleased()`
- [ ] Bind `getDeltaTime()`, `getElapsedTime()`
- [ ] Bind `getWindowSize()`
- [ ] Test keyboard input from Lua

### Phase 2e: Asset Loading
- [ ] Bind `loadTexture()`, `loadShader()`
- [ ] Create assets directory structure
- [ ] Test loading in Lua

