#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

#include <vector>
#include <cstdint>

namespace sle::scripting {

namespace {

int l_addForce(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float forceX = static_cast<float>(luaL_checknumber(L, 2));
    const float forceY = static_cast<float>(luaL_checknumber(L, 3));
    lua_pushboolean(L, detail::getApi(L)->addForce({id}, forceX, forceY));
    return 1;
}

int l_addImpulse(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float impulseX = static_cast<float>(luaL_checknumber(L, 2));
    const float impulseY = static_cast<float>(luaL_checknumber(L, 3));
    lua_pushboolean(L, detail::getApi(L)->addImpulse({id}, impulseX, impulseY));
    return 1;
}

int l_setVelocity(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float velocityX = static_cast<float>(luaL_checknumber(L, 2));
    const float velocityY = static_cast<float>(luaL_checknumber(L, 3));
    lua_pushboolean(L, detail::getApi(L)->setVelocity({id}, velocityX, velocityY));
    return 1;
}

int l_getVelocity(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    glm::vec2 velocity(0.0f);
    if (!detail::getApi(L)->getVelocity({id}, velocity))
    {
        lua_pushnil(L);
        return 1;
    }
    detail::pushVec2(L, velocity.x, velocity.y);
    return 1;
}

int l_setAngularVelocity(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float angularVelocity = static_cast<float>(luaL_checknumber(L, 2));
    lua_pushboolean(L, detail::getApi(L)->setAngularVelocity({id}, angularVelocity));
    return 1;
}

int l_getAngularVelocity(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushnumber(L, detail::getApi(L)->getAngularVelocity({id}));
    return 1;
}

int l_setGravityScale(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float gravityScale = static_cast<float>(luaL_checknumber(L, 2));
    lua_pushboolean(L, detail::getApi(L)->setGravityScale({id}, gravityScale));
    return 1;
}

int l_isTouching(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, detail::getApi(L)->isTouching({id}));
    return 1;
}

int l_raycastFirst(lua_State* L)
{
    const float startX = static_cast<float>(luaL_checknumber(L, 1));
    const float startY = static_cast<float>(luaL_checknumber(L, 2));
    const float endX = static_cast<float>(luaL_checknumber(L, 3));
    const float endY = static_cast<float>(luaL_checknumber(L, 4));

    PhysicsRaycastHit hit;
    if (!detail::getApi(L)->raycastFirst({startX, startY}, {endX, endY}, hit))
    {
        lua_pushnil(L);
        return 1;
    }

    detail::pushRaycastHit(L, hit);
    return 1;
}

int l_raycastAll(lua_State* L)
{
    const float startX = static_cast<float>(luaL_checknumber(L, 1));
    const float startY = static_cast<float>(luaL_checknumber(L, 2));
    const float endX = static_cast<float>(luaL_checknumber(L, 3));
    const float endY = static_cast<float>(luaL_checknumber(L, 4));

    std::vector<PhysicsRaycastHit> hits;
    detail::getApi(L)->raycastAll({startX, startY}, {endX, endY}, hits);

    lua_newtable(L);
    int index = 1;
    for (const auto& hit : hits)
    {
        detail::pushRaycastHit(L, hit);
        lua_rawseti(L, -2, index++);
    }

    return 1;
}

int l_setPhysicsDebugEnabled(lua_State* L)
{
    const bool enabled = lua_toboolean(L, 1) != 0;
    detail::getApi(L)->setPhysicsDebugEnabled(enabled);
    return 0;
}

int l_isPhysicsDebugEnabled(lua_State* L)
{
    lua_pushboolean(L, detail::getApi(L)->isPhysicsDebugEnabled());
    return 1;
}

} // namespace

void registerPhysicsTable(lua_State* L, int engineTable, ScriptApi* api)
{
    lua_newtable(L);
    const int physicsTable = lua_gettop(L);
    detail::setTableFunction(L, physicsTable, api, "addForce", l_addForce);
    detail::setTableFunction(L, physicsTable, api, "addImpulse", l_addImpulse);
    detail::setTableFunction(L, physicsTable, api, "setVelocity", l_setVelocity);
    detail::setTableFunction(L, physicsTable, api, "getVelocity", l_getVelocity);
    detail::setTableFunction(L, physicsTable, api, "setAngularVelocity", l_setAngularVelocity);
    detail::setTableFunction(L, physicsTable, api, "getAngularVelocity", l_getAngularVelocity);
    detail::setTableFunction(L, physicsTable, api, "setGravityScale", l_setGravityScale);
    detail::setTableFunction(L, physicsTable, api, "isTouching", l_isTouching);
    detail::setTableFunction(L, physicsTable, api, "raycastFirst", l_raycastFirst);
    detail::setTableFunction(L, physicsTable, api, "raycastAll", l_raycastAll);
    detail::setTableFunction(L, physicsTable, api, "setDebugEnabled", l_setPhysicsDebugEnabled);
    detail::setTableFunction(L, physicsTable, api, "isDebugEnabled", l_isPhysicsDebugEnabled);
    lua_setfield(L, engineTable, "Physics");
}

} // namespace sle::scripting
