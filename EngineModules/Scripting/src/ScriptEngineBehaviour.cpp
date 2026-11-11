#include <sle/scripting/ScriptEngine.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptResource.hpp>

#include <algorithm>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
}

namespace sle::scripting {
namespace {
constexpr int kLuaNoRef = -2;

// This scope also covers chunk execution, before lifecycle functions exist.
class BehaviourScope
{
public:
    BehaviourScope(uint32_t& entity, bool& script, std::string& key, uint64_t& generation,
                   uint32_t nextEntity, const std::string& nextKey, uint64_t nextGeneration)
        : entity(entity), script(script), key(key), generation(generation),
          oldEntity(entity), oldScript(script), oldKey(key), oldGeneration(generation)
    { entity = nextEntity; script = true; key = nextKey; generation = nextGeneration; }
    ~BehaviourScope()
    { entity = oldEntity; script = oldScript; key = oldKey; generation = oldGeneration; }
private:
    uint32_t& entity;
    bool& script;
    std::string& key;
    uint64_t& generation;
    uint32_t oldEntity;
    bool oldScript;
    std::string oldKey;
    uint64_t oldGeneration;
};
}

bool ScriptEngine::hasBehaviour(uint32_t entity, const std::string& key) const
{
    const auto it = instances.find(entity);
    return it != instances.end() && it->second.contains(key);
}

uint64_t ScriptEngine::getBehaviourGeneration(uint32_t entity, const std::string& key) const
{
    const auto it = instances.find(entity);
    if (it == instances.end()) return 0;
    const auto found = it->second.find(key);
    return found == it->second.end() ? 0 : found->second.generation;
}

bool ScriptEngine::behaviourEnabled(uint32_t entity, const std::string& key) const
{
    const auto it = configurations.find(entity);
    if (it == configurations.end()) return true;
    const auto spec = std::find_if(it->second.begin(), it->second.end(),
        [&](const auto& value) { return value.key == key; });
    return spec != it->second.end() && spec->enabled;
}

std::vector<std::pair<std::string, uint64_t>> ScriptEngine::orderedBehaviours(uint32_t entity, bool includeDisabled) const
{
    std::vector<std::pair<std::string, uint64_t>> result;
    const auto configured = configurations.find(entity);
    if (configured != configurations.end())
    {
        auto specs = configured->second;
        std::stable_sort(specs.begin(), specs.end(), [](const auto& a, const auto& b) { return a.order < b.order; });
        for (const auto& spec : specs)
            if (includeDisabled || spec.enabled)
                if (const auto generation = getBehaviourGeneration(entity, spec.key))
                    result.emplace_back(spec.key, generation);
    }
    else if (const auto it = instances.find(entity); it != instances.end())
    {
        for (const auto& [key, instance] : it->second)
            result.emplace_back(key, instance.generation);
        std::sort(result.begin(), result.end());
    }
    return result;
}

void ScriptEngine::configureBehaviours(uint32_t entity, const std::vector<ScriptBehaviourSpec>& specs)
{
    configurations[entity] = specs;
    std::vector<std::pair<std::string, uint64_t>> stale;
    if (const auto it = instances.find(entity); it != instances.end())
        for (const auto& [key, instance] : it->second)
        {
            const auto spec = std::find_if(specs.begin(), specs.end(), [&](const auto& value) {
                return value.key == key && value.scriptAsset == instance.assetPath;
            });
            if (spec == specs.end()) stale.emplace_back(key, instance.generation);
        }
    for (const auto& [key, generation] : stale)
    {
        // A destroy callback can recursively configure this entity.
        const auto current = configurations.find(entity);
        if (current == configurations.end() || getBehaviourGeneration(entity, key) != generation) continue;
        const auto owner = instances.find(entity);
        const std::string asset = owner->second.at(key).assetPath;
        if (std::none_of(current->second.begin(), current->second.end(), [&](const auto& value) {
            return value.key == key && value.scriptAsset == asset;
        })) removeBehaviour(entity, key);
    }
}

void ScriptEngine::releaseInstance(const ScriptInstance& instance)
{
    if (!L) return;
    for (const int ref : {instance.initRef, instance.updateRef, instance.destroyRef, instance.tableRef, instance.envRef})
        if (ref >= 0) luaL_unref(L, LUA_REGISTRYINDEX, ref);
}

void ScriptEngine::removeBehaviour(uint32_t entity, const std::string& key)
{
    if (!L) return;
    auto owner = instances.find(entity);
    if (owner == instances.end()) return;
    auto found = owner->second.find(key);
    if (found == owner->second.end()) return;
    const ScriptInstance instance = found->second;
    owner->second.erase(found);
    if (owner->second.empty()) instances.erase(owner);
    {
        BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
            executingBehaviourGeneration, entity, key, instance.generation);
        if (instance.destroyRef >= 0) (void)callFunction(instance.destroyRef, entity);
    }
    if (api) api->clearBehaviourEventSubscriptions(entity, key, instance.generation);
    releaseInstance(instance);
}

bool ScriptEngine::ensureBehaviour(uint32_t entity, const std::string& key, const std::string& asset)
{
    if (!L || asset.empty()) return false;
    if (const auto owner = instances.find(entity); owner != instances.end())
        if (const auto found = owner->second.find(key); found != owner->second.end())
        {
            if (found->second.assetPath == asset) return true;
            removeBehaviour(entity, key);
            // A destroy callback may have replaced this attachment itself.
            if (hasBehaviour(entity, key)) return false;
        }
    if (const auto configured = configurations.find(entity); configured != configurations.end())
        if (std::none_of(configured->second.begin(), configured->second.end(), [&](const auto& spec) {
            return spec.key == key && spec.scriptAsset == asset;
        })) return false;
    auto resource = sle::core::Resources::get<ScriptResource>(asset);
    if (!resource) resource = sle::core::Resources::create<ScriptResource>(asset, asset);
    if (!resource) return false;

    ScriptInstance instance;
    instance.assetPath = asset;
    instance.generation = ++nextGeneration;
    const auto generation = instance.generation;
    BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
        executingBehaviourGeneration, entity, key, generation);
    const auto& source = resource->getSource();
    if (luaL_loadbuffer(L, source.c_str(), source.size(), asset.c_str()) != LUA_OK)
    {
        sle::core::Log::error("Lua load error [{}]: {}", asset, lua_tostring(L, -1));
        lua_pop(L, 1);
        if (api) api->clearBehaviourEventSubscriptions(entity, key, generation);
        return false;
    }
    if (!key.empty())
    {
        lua_newtable(L);
        lua_pushglobaltable(L);
        lua_setfield(L, -2, "_G");
        lua_newtable(L);
        lua_pushglobaltable(L);
        lua_setfield(L, -2, "__index");
        lua_setmetatable(L, -2);
        lua_pushvalue(L, -1);
        instance.envRef = luaL_ref(L, LUA_REGISTRYINDEX);
        lua_setupvalue(L, -2, 1); // Chunk's _ENV consumes the environment table.
    }
    else
    {
        lua_pushglobaltable(L);
        instance.envRef = luaL_ref(L, LUA_REGISTRYINDEX);
    }
    instances[entity][key] = instance;
    if (lua_pcall(L, 0, 1, 0) != LUA_OK)
    {
        sle::core::Log::error("Lua runtime error [{}]: {}", asset, lua_tostring(L, -1));
        lua_pop(L, 1);
        if (getBehaviourGeneration(entity, key) == generation) removeBehaviour(entity, key);
        else if (api) api->clearBehaviourEventSubscriptions(entity, key, generation);
        return false;
    }
    if (getBehaviourGeneration(entity, key) != generation)
    {
        lua_pop(L, 1);
        if (api) api->clearBehaviourEventSubscriptions(entity, key, generation);
        return false;
    }
    if (lua_istable(L, -1))
    {
        lua_pushvalue(L, -1);
        instance.tableRef = luaL_ref(L, LUA_REGISTRYINDEX);
        instance.initRef = extractFunctionRef(-1, "init");
        instance.updateRef = extractFunctionRef(-1, "update");
        instance.destroyRef = extractFunctionRef(-1, "destroy");
    }
    else
    {
        // Raw environment lookup prevents another script's globals from becoming lifecycle callbacks.
        if (instance.envRef >= 0) lua_rawgeti(L, LUA_REGISTRYINDEX, instance.envRef);
        else lua_pushglobaltable(L);
        auto lifecycle = [&](const char* name) {
            lua_pushstring(L, name);
            lua_rawget(L, -2);
            if (lua_isfunction(L, -1)) return luaL_ref(L, LUA_REGISTRYINDEX);
            lua_pop(L, 1);
            return kLuaNoRef;
        };
        instance.initRef = lifecycle("init");
        instance.updateRef = lifecycle("update");
        instance.destroyRef = lifecycle("destroy");
        lua_pop(L, 1);
    }
    lua_pop(L, 1);
    instances[entity][key] = instance;
    if (instance.initRef >= 0 && !callFunction(instance.initRef, entity))
    {
        if (getBehaviourGeneration(entity, key) == generation) removeBehaviour(entity, key);
        return false;
    }
    return getBehaviourGeneration(entity, key) == generation;
}

void ScriptEngine::runBehaviourUpdate(uint32_t entity, const std::string& key, float dt)
{
    if (!behaviourEnabled(entity, key)) return;
    const auto owner = instances.find(entity);
    if (owner == instances.end()) return;
    const auto found = owner->second.find(key);
    if (found == owner->second.end()) return;
    const ScriptInstance instance = found->second;
    BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
        executingBehaviourGeneration, entity, key, instance.generation);
    if (instance.updateRef >= 0) (void)callFunction(instance.updateRef, entity, dt);
}

int ScriptEngine::behaviourFunctionRef(const ScriptInstance& instance, const std::string& name)
{
    for (const int ref : {instance.tableRef, instance.envRef})
    {
        if (ref < 0) continue;
        lua_rawgeti(L, LUA_REGISTRYINDEX, ref);
        lua_pushstring(L, name.c_str());
        lua_rawget(L, -2);
        if (lua_isfunction(L, -1))
        {
            const int result = luaL_ref(L, LUA_REGISTRYINDEX);
            lua_pop(L, 1);
            return result;
        }
        lua_pop(L, 2);
    }
    return kLuaNoRef;
}

bool ScriptEngine::callEntityCallbacks(uint32_t entity, const std::string& name, bool boolean)
{
    if (!L || name.empty()) return false;
    const auto ordered = orderedBehaviours(entity);
    bool matched = false;
    bool result = true;
    for (const auto& [key, generation] : ordered)
    {
        if (!behaviourEnabled(entity, key) || getBehaviourGeneration(entity, key) != generation) continue;
        const ScriptInstance instance = instances.at(entity).at(key);
        const int ref = behaviourFunctionRef(instance, name);
        if (ref < 0) continue;
        matched = true;
        BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
            executingBehaviourGeneration, entity, key, generation);
        lua_rawgeti(L, LUA_REGISTRYINDEX, ref);
        luaL_unref(L, LUA_REGISTRYINDEX, ref);
        lua_pushinteger(L, entity);
        if (lua_pcall(L, 1, boolean ? 1 : 0, 0) != LUA_OK)
        {
            sle::core::Log::error("Lua callback error [{}]: {}", name, lua_tostring(L, -1));
            lua_pop(L, 1);
            result = false;
        }
        else if (boolean)
        {
            result = (lua_isboolean(L, -1) && lua_toboolean(L, -1)) && result;
            lua_pop(L, 1);
        }
    }
    if (matched) return result;
    if (const auto configured = configurations.find(entity); configured != configurations.end())
    {
        if (std::none_of(configured->second.begin(), configured->second.end(),
            [](const auto& spec) { return spec.enabled; })) return false;
        const auto primary = std::find_if(configured->second.begin(), configured->second.end(),
            [](const auto& spec) { return spec.key.empty(); });
        if (primary != configured->second.end() && !primary->enabled) return false;
    }
    // Preserve global guard and legacy global-format callback support.
    if (!boolean && !hasScript(entity)) return false;
    const auto primaryGeneration = getBehaviourGeneration(entity, {});
    if (primaryGeneration && behaviourEnabled(entity, {}))
    {
        BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
            executingBehaviourGeneration, entity, {}, primaryGeneration);
        return boolean ? callGlobalBoolFunction(name, entity) : callGlobalFunction(name, entity);
    }
    return boolean ? callGlobalBoolFunction(name, entity) : callGlobalFunction(name, entity);
}

} // namespace sle::scripting
