#include "LuaDiagnostics.hpp"
#include <sle/scripting/EditorLuaHost.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include <sle/scripting/ScriptResource.hpp>

#include <algorithm>
#include <array>
#include <string_view>

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

// Only the candidate may run during staging. Primitive metatables are shared
// across the VM and can dispatch into live code (including string methods).
class ReloadStagingScope
{
public:
    explicit ReloadStagingScope(lua_State* lua)
        : state(lua), gcRunning(lua_gc(lua, LUA_GCISRUNNING) != 0), hook(lua_gethook(lua)),
          hookMask(lua_gethookmask(lua)), hookCount(lua_gethookcount(lua))
    {
        lua_gc(state, LUA_GCSTOP);
        lua_sethook(state, nullptr, 0, 0);
        for (std::size_t i = 0; i < values.size(); ++i)
        {
            values[i](state);
            if (lua_getmetatable(state, -1))
            {
                metatables[i] = luaL_ref(state, LUA_REGISTRYINDEX);
                lua_pushnil(state);
                lua_setmetatable(state, -2);
            }
            lua_pop(state, 1);
        }
    }

    ~ReloadStagingScope()
    {
        for (std::size_t i = 0; i < values.size(); ++i)
        {
            if (metatables[i] == LUA_NOREF) continue;
            values[i](state);
            lua_rawgeti(state, LUA_REGISTRYINDEX, metatables[i]);
            lua_setmetatable(state, -2);
            lua_pop(state, 1);
            luaL_unref(state, LUA_REGISTRYINDEX, metatables[i]);
        }
        lua_sethook(state, hook, hookMask, hookCount);
        if (gcRunning) lua_gc(state, LUA_GCRESTART);
    }

private:
    using PushValue = void (*)(lua_State*);
    static constexpr std::array<PushValue, 5> values{
        lua_pushnil,
        [](lua_State* lua) { lua_pushboolean(lua, false); },
        [](lua_State* lua) { lua_pushinteger(lua, 0); },
        [](lua_State* lua) { lua_pushliteral(lua, ""); },
        [](lua_State* lua) { lua_pushcfunction(lua, [](lua_State*) { return 0; }); }
    };

    lua_State* state;
    bool gcRunning;
    lua_Hook hook;
    int hookMask;
    int hookCount;
    std::array<int, values.size()> metatables{LUA_NOREF, LUA_NOREF, LUA_NOREF, LUA_NOREF, LUA_NOREF};
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
    const auto stillConfigured = [&] {
        const auto configured = configurations.find(entity);
        return configured == configurations.end() ||
            std::any_of(configured->second.begin(), configured->second.end(), [&](const auto& spec) {
                return spec.key == key && spec.scriptAsset == asset;
            });
    };
    if (!stillConfigured()) return false;
    if (const auto saved = reloadSources.find(asset); saved != reloadSources.end())
        return restartBehaviourFromSource(entity, key, asset, saved->second.bytes, stillConfigured, saved->second.path, false) ==
            BehaviourReloadResult::Restarted;
    auto resource = sle::core::Resources::get<ScriptResource>(asset);
    if (!resource) resource = sle::core::Resources::create<ScriptResource>(asset, asset);
    if (!resource)
    {
        core::Log::luaError(fmt::format("Failed to resolve behaviour script: {}", asset),
            {.entity = entity, .behaviour = key});
        return false;
    }

    ScriptInstance instance;
    instance.assetPath = asset;
    instance.generation = ++nextGeneration;
    const auto generation = instance.generation;
    BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
        executingBehaviourGeneration, entity, key, generation);
    const auto& source = resource->getSource();
    if (luaL_loadbuffer(L, source.c_str(), source.size(), ("@" + resource->getPath()).c_str()) != LUA_OK)
    {
        detail::logLuaError(L, fmt::format("Lua load error [{}]", asset), executingEntity, executingBehaviourKey, resource->getPath());
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
    if (callLua(0, 1, ProfileCall::Chunk) != LUA_OK)
    {
        detail::logLuaError(L, fmt::format("Lua runtime error [{}]", asset), executingEntity, executingBehaviourKey);
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
    captureLifecycle(instance);
    return initializeInstance(entity, key, instance);
}

void ScriptEngine::captureLifecycle(ScriptInstance& instance)
{
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
        lua_rawgeti(L, LUA_REGISTRYINDEX, instance.envRef);
        instance.initRef = extractFunctionRef(-1, "init");
        instance.updateRef = extractFunctionRef(-1, "update");
        instance.destroyRef = extractFunctionRef(-1, "destroy");
        lua_pop(L, 1);
    }
    lua_pop(L, 1);
}

bool ScriptEngine::initializeInstance(uint32_t entity, const std::string& key, const ScriptInstance& instance)
{
    const auto generation = instance.generation;
    instances[entity][key] = instance;
    if (instance.initRef < 0) return true;
    std::optional<nlohmann::json> parameters;
    if (!key.empty() && instance.tableRef >= 0)
    {
        lua_rawgeti(L, LUA_REGISTRYINDEX, instance.tableRef);
        auto schema = readBehaviourSchema(L, -1);
        lua_pop(L, 1);
        if (!schema.error.empty())
            core::Log::warn("Invalid properties on behaviour {}: {}", key, schema.error);
        if (!schema.defaults.empty())
        {
            parameters = std::move(schema.defaults);
            if (const auto configured = configurations.find(entity); configured != configurations.end())
            {
                const auto spec = std::find_if(configured->second.begin(), configured->second.end(), [&](const auto& candidate) {
                    return candidate.key == key && candidate.scriptAsset == instance.assetPath;
                });
                if (spec != configured->second.end())
                    for (const auto& [name, value] : spec->parameters)
                    {
                        const auto field = parameters->find(name);
                        if (field == parameters->end()) continue;
                        std::visit([&](const auto& scalar) {
                            const nlohmann::json overrideValue = scalar;
                            if (field->type() == overrideValue.type()) *field = overrideValue;
                        }, value);
                    }
            }
        }
    }
    if (!callFunction(instance.initRef, entity, -1.0f, ProfileCall::Init,
            parameters ? &*parameters : nullptr))
    {
        if (getBehaviourGeneration(entity, key) == generation) removeBehaviour(entity, key);
        return false;
    }
    return getBehaviourGeneration(entity, key) == generation;
}

void ScriptEngine::initializeReloadEnvironment()
{
    // Capture the original functions before any script can replace them.
    lua_newtable(L);
    for (const auto* name : {"assert", "error", "ipairs", "next", "pairs", "rawequal",
             "rawget", "rawlen", "rawset", "select", "tonumber", "tostring", "type"})
    {
        lua_getglobal(L, name);
        lua_setfield(L, -2, name);
    }
    reloadGlobalsRef = luaL_ref(L, LUA_REGISTRYINDEX);
}

BehaviourReloadResult ScriptEngine::reloadBehaviour(uint32_t entity, const std::string& key,
    const std::string& asset, const std::string& source, const std::function<bool()>& stillAttached, const std::string& sourcePath)
{
    if (executingEntityScript) return BehaviourReloadResult::AttachmentChanged;
    return restartBehaviourFromSource(entity, key, asset, source, stillAttached, sourcePath, true);
}

BehaviourReloadResult ScriptEngine::restartBehaviourFromSource(uint32_t entity, const std::string& key,
    const std::string& asset, const std::string& source, const std::function<bool()>& stillAttached,
    const std::string& sourcePath, bool rememberSource)
{
    using Result = BehaviourReloadResult;
    if (!L || !stillAttached()) return Result::AttachmentChanged;

    ScriptInstance instance;
    instance.assetPath = asset;
    instance.generation = ++nextGeneration;
    instance.privateReloadEnvironment = true;
    {
        const ReloadStagingScope staging(L);
        const auto& file = sourcePath.empty() ? asset : sourcePath;
        if (luaL_loadbufferx(L, source.data(), source.size(), ("@" + file).c_str(), "t") != LUA_OK)
        {
            detail::logLuaError(L, "Lua reload compilation failed", entity, key, file);
            lua_pop(L, 1);
            return Result::StageFailed;
        }

        lua_newtable(L);
        lua_newtable(L);
        lua_rawgeti(L, LUA_REGISTRYINDEX, reloadGlobalsRef);
        lua_pushcclosure(L, [](lua_State* state) {
            lua_pushvalue(state, 2);
            lua_rawget(state, lua_upvalueindex(1));
            if (!lua_isnil(state, -1)) return 1;
            const char* name = lua_tostring(state, 2);
            return luaL_error(state, "Global '%s' is unavailable during reload staging. Access it from a lifecycle callback.",
                name ? name : "?");
        }, 1);
        lua_setfield(L, -2, "__index");
        lua_pushboolean(L, false);
        lua_setfield(L, -2, "__metatable");
        lua_setmetatable(L, -2);
        lua_pushvalue(L, -1);
        instance.envRef = luaL_ref(L, LUA_REGISTRYINDEX);
        lua_setupvalue(L, -2, 1);

        const int status = [&] {
            BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
                executingBehaviourGeneration, entity, key, instance.generation);
            return callLua(0, 1, ProfileCall::Chunk);
        }();
        if (status != LUA_OK)
        {
            detail::logLuaError(L, "Lua reload top-level execution failed", entity, key, file);
            lua_pop(L, 1);
            releaseInstance(instance);
            return Result::StageFailed;
        }
        captureLifecycle(instance);

        lua_rawgeti(L, LUA_REGISTRYINDEX, instance.envRef);
        lua_pushglobaltable(L);
        lua_pushliteral(L, "_G");
        lua_pushvalue(L, -2);
        lua_rawset(L, -4);
        lua_newtable(L);
        lua_pushvalue(L, -2);
        lua_setfield(L, -2, "__index");
        lua_setmetatable(L, -3);
        lua_pop(L, 2);
    }
    removeBehaviour(entity, key);
    if (!stillAttached() || hasBehaviour(entity, key))
    {
        releaseInstance(instance);
        return Result::AttachmentChanged;
    }

    if (rememberSource) reloadSources.insert_or_assign(asset, ReloadSource{source, sourcePath});

    BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
        executingBehaviourGeneration, entity, key, instance.generation);
    return initializeInstance(entity, key, instance) ? Result::Restarted : Result::InitFailed;
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
    if (instance.updateRef >= 0) (void)callFunction(instance.updateRef, entity, dt, ProfileCall::Update);
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
        if (callLua(1, boolean ? 1 : 0) != LUA_OK)
        {
            detail::logLuaError(L, fmt::format("Lua callback error [{}]", name), executingEntity, executingBehaviourKey);
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
    const std::string* retiredSource = nullptr;
    if (const auto configured = configurations.find(entity); configured != configurations.end())
    {
        if (std::none_of(configured->second.begin(), configured->second.end(),
            [](const auto& spec) { return spec.enabled; })) return false;
        const auto primary = std::find_if(configured->second.begin(), configured->second.end(),
            [](const auto& spec) { return spec.key.empty(); });
        if (primary != configured->second.end())
        {
            if (!primary->enabled) return false;
            if (reloadSources.contains(primary->scriptAsset)) retiredSource = &primary->scriptAsset;
        }
    }
    // Preserve scene-global guards while suppressing callbacks left in _G
    // by the retired legacy primary attachment.
    if (!boolean && !hasScript(entity)) return false;
    const auto primaryGeneration = getBehaviourGeneration(entity, {});
    if (primaryGeneration)
    {
        const auto& primary = instances.at(entity).at("");
        retiredSource = primary.privateReloadEnvironment ? &primary.assetPath : nullptr;
    }
    if (retiredSource)
    {
        lua_pushglobaltable(L);
        lua_pushlstring(L, name.data(), name.size());
        const bool isFunction = lua_rawget(L, -2) == LUA_TFUNCTION;
        lua_Debug info{};
        const bool fromRetiredPrimary = isFunction && lua_getinfo(L, ">S", &info) &&
            info.source && info.source[0] == '@' && std::string_view(info.source + 1) == *retiredSource;
        if (!isFunction) lua_pop(L, 1);
        lua_pop(L, 1);
        if (fromRetiredPrimary) return false;
    }
    if (primaryGeneration && behaviourEnabled(entity, {}))
    {
        BehaviourScope scope(executingEntity, executingEntityScript, executingBehaviourKey,
            executingBehaviourGeneration, entity, {}, primaryGeneration);
        return boolean ? callGlobalBoolFunction(name, entity) : callGlobalFunction(name, entity);
    }
    return boolean ? callGlobalBoolFunction(name, entity) : callGlobalFunction(name, entity);
}

} // namespace sle::scripting
