#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

#include <glm/vec2.hpp>
#include <cstdint>

namespace sle::scripting {

namespace {

int l_getDeltaTime(lua_State* L)
{
    lua_pushnumber(L, detail::getApi(L)->getDeltaTime());
    return 1;
}

int l_getWindowSize(lua_State* L)
{
    const auto size = detail::getApi(L)->getWindowSize();
    detail::pushVec2(L, size.x, size.y);
    return 1;
}

int l_createEntity(lua_State* L)
{
    const auto entity = detail::getApi(L)->createEntity();
    lua_pushinteger(L, entity.id);
    return 1;
}

int l_isEntityAlive(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->isEntityAlive({id}));
    return 1;
}

int l_destroyEntity(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    detail::getApi(L)->destroyEntity({id});
    return 0;
}

int l_getChildCount(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushinteger(L, detail::getApi(L)->getChildCount({id}));
    return 1;
}

int l_destroyChildren(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushinteger(L, detail::getApi(L)->destroyChildren({id}));
    return 1;
}

int l_setParent(lua_State* L)
{
    const uint32_t childId = static_cast<uint32_t>(luaL_checkinteger(L, 1));

    uint32_t parentId = 0;
    if (!lua_isnoneornil(L, 2))
        parentId = static_cast<uint32_t>(luaL_checkinteger(L, 2));

    lua_pushboolean(L, detail::getApi(L)->setParent({childId}, {parentId}));
    return 1;
}

int l_getParent(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const auto parent = detail::getApi(L)->getParent({id});

    if (parent.id == 0)
    {
        lua_pushnil(L);
        return 1;
    }

    lua_pushinteger(L, parent.id);
    return 1;
}

int l_getTransformPosition(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    glm::vec2 position(0.0f);
    if (!detail::getApi(L)->getTransformPosition({id}, position))
    {
        lua_pushnil(L);
        return 1;
    }

    detail::pushVec2(L, position.x, position.y);
    return 1;
}

int l_setTransformPosition(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float x = static_cast<float>(luaL_checknumber(L, 2));
    const float y = static_cast<float>(luaL_checknumber(L, 3));
    lua_pushboolean(L, detail::getApi(L)->setTransformPosition({id}, {x, y}));
    return 1;
}

int l_getTransformScale(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    glm::vec2 scale(1.0f);
    if (!detail::getApi(L)->getTransformScale({id}, scale))
    {
        lua_pushnil(L);
        return 1;
    }

    detail::pushVec2(L, scale.x, scale.y);
    return 1;
}

int l_loadTexture(lua_State* L)
{
    const char* assetPath = luaL_checkstring(L, 1);
    lua_pushinteger(L, detail::getApi(L)->loadTexture(assetPath));
    return 1;
}

int l_setSpriteTexture(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* assetPath = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->setSpriteTexture({id}, assetPath));
    return 1;
}

int l_hasScene(lua_State* L)
{
    const char* sceneName = luaL_checkstring(L, 1);
    lua_pushboolean(L, detail::getApi(L)->hasScene(sceneName));
    return 1;
}

int l_switchScene(lua_State* L)
{
    const char* sceneName = luaL_checkstring(L, 1);
    lua_pushboolean(L, detail::getApi(L)->switchScene(sceneName));
    return 1;
}

int l_getCurrentSceneName(lua_State* L)
{
    const auto name = detail::getApi(L)->getCurrentSceneName();
    lua_pushstring(L, name.c_str());
    return 1;
}

int l_setStateMachineBool(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* key = luaL_checkstring(L, 2);
    const bool value = lua_toboolean(L, 3) != 0;
    lua_pushboolean(L, detail::getApi(L)->setStateMachineBool({id}, key, value));
    return 1;
}

int l_setStateMachineTrigger(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* key = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->setStateMachineTrigger({id}, key));
    return 1;
}

int l_getStateMachineState(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    std::string state;
    if (!detail::getApi(L)->getStateMachineCurrentState({id}, state))
    {
        lua_pushnil(L);
        return 1;
    }

    lua_pushstring(L, state.c_str());
    return 1;
}

int l_forceStateMachineState(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* stateName = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->forceStateMachineState({id}, stateName));
    return 1;
}

int l_isStateMachineInState(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* stateName = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->isStateMachineInState({id}, stateName));
    return 1;
}

int l_sendStateMachineEvent(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* eventName = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->sendStateMachineEvent({id}, eventName));
    return 1;
}

// ============================================================
// Animator
// ============================================================

int l_playAnimation(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* clipAsset = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->playAnimation({id}, clipAsset));
    return 1;
}

int l_stopAnimation(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->stopAnimation({id}));
    return 1;
}

int l_pauseAnimation(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->pauseAnimation({id}));
    return 1;
}

int l_resumeAnimation(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->resumeAnimation({id}));
    return 1;
}

int l_setAnimationSpeed(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float speed = static_cast<float>(luaL_checknumber(L, 2));
    lua_pushboolean(L, detail::getApi(L)->setAnimationSpeed({id}, speed));
    return 1;
}

int l_setAnimationTime(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float t = static_cast<float>(luaL_checknumber(L, 2));
    lua_pushboolean(L, detail::getApi(L)->setAnimationTime({id}, t));
    return 1;
}

int l_isAnimationPlaying(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->isAnimationPlaying({id}));
    return 1;
}

int l_getAnimationTime(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushnumber(L, detail::getApi(L)->getAnimationTime({id}));
    return 1;
}

int l_setAnimationTarget(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* targetName = luaL_checkstring(L, 2);
    // targetEntity may be nil/0 to clear the binding
    const uint32_t targetId = lua_isnoneornil(L, 3)
        ? 0
        : static_cast<uint32_t>(luaL_checkinteger(L, 3));
    lua_pushboolean(L, detail::getApi(L)->setAnimationTarget({id}, targetName, {targetId}));
    return 1;
}

int l_setAnimatorFloat(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* name = luaL_checkstring(L, 2);
    const float value = static_cast<float>(luaL_checknumber(L, 3));
    lua_pushboolean(L, detail::getApi(L)->setAnimatorFloat({id}, name, value));
    return 1;
}

int l_getAnimatorFloat(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* name = luaL_checkstring(L, 2);
    float value = 0.0f;
    if (!detail::getApi(L)->getAnimatorFloat({id}, name, value))
    {
        lua_pushnil(L);
        return 1;
    }

    lua_pushnumber(L, value);
    return 1;
}

int l_log(lua_State* L)
{
    const char* msg = luaL_checkstring(L, 1);
    detail::getApi(L)->log(msg);
    return 0;
}

int l_warn(lua_State* L)
{
    const char* msg = luaL_checkstring(L, 1);
    detail::getApi(L)->warn(msg);
    return 0;
}

int l_error(lua_State* L)
{
    const char* msg = luaL_checkstring(L, 1);
    detail::getApi(L)->error(msg);
    return 0;
}

int l_setUIBinding(lua_State* L)
{
    const char* key = luaL_checkstring(L, 1);
    const char* value = luaL_checkstring(L, 2);
    lua_pushboolean(L, detail::getApi(L)->setUIBinding(key, value));
    return 1;
}

} // namespace

void registerEngineFunctions(lua_State* L, int engineTable, ScriptApi* api)
{
    detail::setEngineFunction(L, engineTable, api, "getDeltaTime", l_getDeltaTime);
    detail::setEngineFunction(L, engineTable, api, "getWindowSize", l_getWindowSize);
    detail::setEngineFunction(L, engineTable, api, "createEntity", l_createEntity);
    detail::setEngineFunction(L, engineTable, api, "isEntityAlive", l_isEntityAlive);
    detail::setEngineFunction(L, engineTable, api, "destroyEntity", l_destroyEntity);
    detail::setEngineFunction(L, engineTable, api, "getChildCount", l_getChildCount);
    detail::setEngineFunction(L, engineTable, api, "destroyChildren", l_destroyChildren);
    detail::setEngineFunction(L, engineTable, api, "setParent", l_setParent);
    detail::setEngineFunction(L, engineTable, api, "getParent", l_getParent);
    detail::setEngineFunction(L, engineTable, api, "getTransformPosition", l_getTransformPosition);
    detail::setEngineFunction(L, engineTable, api, "setTransformPosition", l_setTransformPosition);
    detail::setEngineFunction(L, engineTable, api, "getTransformScale", l_getTransformScale);
    detail::setEngineFunction(L, engineTable, api, "loadTexture", l_loadTexture);
    detail::setEngineFunction(L, engineTable, api, "setSpriteTexture", l_setSpriteTexture);
    detail::setEngineFunction(L, engineTable, api, "hasScene", l_hasScene);
    detail::setEngineFunction(L, engineTable, api, "switchScene", l_switchScene);
    detail::setEngineFunction(L, engineTable, api, "getCurrentSceneName", l_getCurrentSceneName);
    detail::setEngineFunction(L, engineTable, api, "setStateMachineBool", l_setStateMachineBool);
    detail::setEngineFunction(L, engineTable, api, "setStateMachineTrigger", l_setStateMachineTrigger);
    detail::setEngineFunction(L, engineTable, api, "getStateMachineState", l_getStateMachineState);
    detail::setEngineFunction(L, engineTable, api, "setState", l_forceStateMachineState);
    detail::setEngineFunction(L, engineTable, api, "getState", l_getStateMachineState);
    detail::setEngineFunction(L, engineTable, api, "isState", l_isStateMachineInState);
    detail::setEngineFunction(L, engineTable, api, "sendStateEvent", l_sendStateMachineEvent);
    detail::setEngineFunction(L, engineTable, api, "log", l_log);
    detail::setEngineFunction(L, engineTable, api, "warn", l_warn);
    detail::setEngineFunction(L, engineTable, api, "error", l_error);
    detail::setEngineFunction(L, engineTable, api, "setUIBinding", l_setUIBinding);

    // Animator
    detail::setEngineFunction(L, engineTable, api, "playAnimator",      l_playAnimation);
    detail::setEngineFunction(L, engineTable, api, "stopAnimator",      l_stopAnimation);
    detail::setEngineFunction(L, engineTable, api, "pauseAnimator",     l_pauseAnimation);
    detail::setEngineFunction(L, engineTable, api, "resumeAnimator",    l_resumeAnimation);
    detail::setEngineFunction(L, engineTable, api, "setAnimatorSpeed",  l_setAnimationSpeed);
    detail::setEngineFunction(L, engineTable, api, "setAnimatorTime",   l_setAnimationTime);
    detail::setEngineFunction(L, engineTable, api, "isAnimatorPlaying", l_isAnimationPlaying);
    detail::setEngineFunction(L, engineTable, api, "getAnimatorTime",   l_getAnimationTime);
    detail::setEngineFunction(L, engineTable, api, "setAnimatorTarget", l_setAnimationTarget);
    detail::setEngineFunction(L, engineTable, api, "setAnimatorFloat",   l_setAnimatorFloat);
    detail::setEngineFunction(L, engineTable, api, "getAnimatorFloat",   l_getAnimatorFloat);
}

} // namespace sle::scripting
