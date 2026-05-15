#include <sle/scripting/LuaBindings.hpp>
#include <sle/scripting/ScriptApi.hpp>
#include <sle/platform/Input.hpp>

#include <cstdint>

extern "C" {
#include <lua.h>
#include <lauxlib.h>
}

namespace sle::scripting {

namespace {

ScriptApi* getApi(lua_State* L)
{
    return static_cast<ScriptApi*>(lua_touserdata(L, lua_upvalueindex(1)));
}

void pushVec2(lua_State* L, float x, float y)
{
    lua_newtable(L);
    lua_pushnumber(L, x);
    lua_setfield(L, -2, "x");
    lua_pushnumber(L, y);
    lua_setfield(L, -2, "y");
}

void setEngineFunction(lua_State* L, int engineTable, ScriptApi* api, const char* name, lua_CFunction fn)
{
    lua_pushlightuserdata(L, api);
    lua_pushcclosure(L, fn, 1);
    lua_setfield(L, engineTable, name);
}

void setTableFunction(lua_State* L, int tableIndex, ScriptApi* api, const char* name, lua_CFunction fn)
{
    lua_pushlightuserdata(L, api);
    lua_pushcclosure(L, fn, 1);
    lua_setfield(L, tableIndex, name);
}

void setTableInt(lua_State* L, int tableIndex, const char* name, int value)
{
    lua_pushinteger(L, value);
    lua_setfield(L, tableIndex, name);
}

int l_getDeltaTime(lua_State* L)
{
    lua_pushnumber(L, getApi(L)->getDeltaTime());
    return 1;
}

int l_getWindowSize(lua_State* L)
{
    const auto size = getApi(L)->getWindowSize();
    pushVec2(L, size.x, size.y);
    return 1;
}

int l_createEntity(lua_State* L)
{
    const auto entity = getApi(L)->createEntity();
    lua_pushinteger(L, entity.id);
    return 1;
}

int l_isEntityAlive(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, getApi(L)->isEntityAlive({id}));
    return 1;
}

int l_destroyEntity(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    getApi(L)->destroyEntity({id});
    return 0;
}

int l_getChildCount(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushinteger(L, getApi(L)->getChildCount({id}));
    return 1;
}

int l_destroyChildren(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    lua_pushinteger(L, getApi(L)->destroyChildren({id}));
    return 1;
}

int l_setParent(lua_State* L)
{
    const uint32_t childId = static_cast<uint32_t>(luaL_checkinteger(L, 1));

    uint32_t parentId = 0;
    if (!lua_isnoneornil(L, 2))
        parentId = static_cast<uint32_t>(luaL_checkinteger(L, 2));

    lua_pushboolean(L, getApi(L)->setParent({childId}, {parentId}));
    return 1;
}

int l_getParent(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const auto parent = getApi(L)->getParent({id});

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
    if (!getApi(L)->getTransformPosition({id}, position))
    {
        lua_pushnil(L);
        return 1;
    }

    pushVec2(L, position.x, position.y);
    return 1;
}

int l_setTransformPosition(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const float x = static_cast<float>(luaL_checknumber(L, 2));
    const float y = static_cast<float>(luaL_checknumber(L, 3));
    lua_pushboolean(L, getApi(L)->setTransformPosition({id}, {x, y}));
    return 1;
}

int l_getTransformScale(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    glm::vec2 scale(1.0f);
    if (!getApi(L)->getTransformScale({id}, scale))
    {
        lua_pushnil(L);
        return 1;
    }

    pushVec2(L, scale.x, scale.y);
    return 1;
}

int l_loadTexture(lua_State* L)
{
    const char* assetPath = luaL_checkstring(L, 1);
    lua_pushinteger(L, getApi(L)->loadTexture(assetPath));
    return 1;
}

int l_setSpriteTexture(lua_State* L)
{
    const uint32_t id = static_cast<uint32_t>(luaL_checkinteger(L, 1));
    const char* assetPath = luaL_checkstring(L, 2);
    lua_pushboolean(L, getApi(L)->setSpriteTexture({id}, assetPath));
    return 1;
}

int l_hasScene(lua_State* L)
{
    const char* sceneName = luaL_checkstring(L, 1);
    lua_pushboolean(L, getApi(L)->hasScene(sceneName));
    return 1;
}

int l_switchScene(lua_State* L)
{
    const char* sceneName = luaL_checkstring(L, 1);
    lua_pushboolean(L, getApi(L)->switchScene(sceneName));
    return 1;
}

int l_getCurrentSceneName(lua_State* L)
{
    const auto name = getApi(L)->getCurrentSceneName();
    lua_pushstring(L, name.c_str());
    return 1;
}

int l_log(lua_State* L)
{
    const char* msg = luaL_checkstring(L, 1);
    getApi(L)->log(msg);
    return 0;
}

int l_warn(lua_State* L)
{
    const char* msg = luaL_checkstring(L, 1);
    getApi(L)->warn(msg);
    return 0;
}

int l_error(lua_State* L)
{
    const char* msg = luaL_checkstring(L, 1);
    getApi(L)->error(msg);
    return 0;
}

int l_isKeyDown(lua_State* L)
{
    const int key = static_cast<int>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, getApi(L)->isKeyDown(key));
    return 1;
}

int l_isKeyPressed(lua_State* L)
{
    const int key = static_cast<int>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, getApi(L)->isKeyPressed(key));
    return 1;
}

int l_isKeyReleased(lua_State* L)
{
    const int key = static_cast<int>(luaL_checkinteger(L, 1));
    lua_pushboolean(L, getApi(L)->isKeyReleased(key));
    return 1;
}

int l_getMousePosition(lua_State* L)
{
    const auto pos = getApi(L)->getMousePosition();
    pushVec2(L, static_cast<float>(pos.x), static_cast<float>(pos.y));
    return 1;
}

int l_getCameraPosition(lua_State* L)
{
    const auto pos = getApi(L)->getCameraPosition();
    pushVec2(L, pos.x, pos.y);
    return 1;
}

int l_setCameraPosition(lua_State* L)
{
    const float x = static_cast<float>(luaL_checknumber(L, 1));
    const float y = static_cast<float>(luaL_checknumber(L, 2));
    getApi(L)->setCameraPosition({x, y});
    return 0;
}

int l_moveCamera(lua_State* L)
{
    const float dx = static_cast<float>(luaL_checknumber(L, 1));
    const float dy = static_cast<float>(luaL_checknumber(L, 2));
    getApi(L)->moveCamera({dx, dy});
    return 0;
}

int l_getCameraZoom(lua_State* L)
{
    lua_pushnumber(L, getApi(L)->getCameraZoom());
    return 1;
}

int l_setCameraZoom(lua_State* L)
{
    const float zoom = static_cast<float>(luaL_checknumber(L, 1));
    getApi(L)->setCameraZoom(zoom);
    return 0;
}

} // namespace

void registerLuaBindings(lua_State* L, ScriptApi* api)
{
    lua_newtable(L);
    const int engineTable = lua_gettop(L);

    setEngineFunction(L, engineTable, api, "getDeltaTime", l_getDeltaTime);
    setEngineFunction(L, engineTable, api, "getWindowSize", l_getWindowSize);
    setEngineFunction(L, engineTable, api, "createEntity", l_createEntity);
    setEngineFunction(L, engineTable, api, "isEntityAlive", l_isEntityAlive);
    setEngineFunction(L, engineTable, api, "destroyEntity", l_destroyEntity);
    setEngineFunction(L, engineTable, api, "getChildCount", l_getChildCount);
    setEngineFunction(L, engineTable, api, "destroyChildren", l_destroyChildren);
    setEngineFunction(L, engineTable, api, "setParent", l_setParent);
    setEngineFunction(L, engineTable, api, "getParent", l_getParent);
    setEngineFunction(L, engineTable, api, "getTransformPosition", l_getTransformPosition);
    setEngineFunction(L, engineTable, api, "setTransformPosition", l_setTransformPosition);
    setEngineFunction(L, engineTable, api, "getTransformScale", l_getTransformScale);
    setEngineFunction(L, engineTable, api, "loadTexture", l_loadTexture);
    setEngineFunction(L, engineTable, api, "setSpriteTexture", l_setSpriteTexture);
    setEngineFunction(L, engineTable, api, "hasScene", l_hasScene);
    setEngineFunction(L, engineTable, api, "switchScene", l_switchScene);
    setEngineFunction(L, engineTable, api, "getCurrentSceneName", l_getCurrentSceneName);
    setEngineFunction(L, engineTable, api, "log", l_log);
    setEngineFunction(L, engineTable, api, "warn", l_warn);
    setEngineFunction(L, engineTable, api, "error", l_error);

    lua_newtable(L);
    const int inputTable = lua_gettop(L);
    setTableFunction(L, inputTable, api, "isKeyDown", l_isKeyDown);
    setTableFunction(L, inputTable, api, "isKeyPressed", l_isKeyPressed);
    setTableFunction(L, inputTable, api, "isKeyReleased", l_isKeyReleased);
    setTableFunction(L, inputTable, api, "getMousePosition", l_getMousePosition);
    lua_setfield(L, engineTable, "Input");

    lua_newtable(L);
    const int cameraTable = lua_gettop(L);
    setTableFunction(L, cameraTable, api, "getPosition", l_getCameraPosition);
    setTableFunction(L, cameraTable, api, "setPosition", l_setCameraPosition);
    setTableFunction(L, cameraTable, api, "move", l_moveCamera);
    setTableFunction(L, cameraTable, api, "getZoom", l_getCameraZoom);
    setTableFunction(L, cameraTable, api, "setZoom", l_setCameraZoom);
    lua_setfield(L, engineTable, "Camera");

    lua_newtable(L);
    const int keysTable = lua_gettop(L);
    setTableInt(L, keysTable, "A", static_cast<int>(sle::input::Input::Key::A));
    setTableInt(L, keysTable, "C", static_cast<int>(sle::input::Input::Key::C));
    setTableInt(L, keysTable, "D", static_cast<int>(sle::input::Input::Key::D));
    setTableInt(L, keysTable, "S", static_cast<int>(sle::input::Input::Key::S));
    setTableInt(L, keysTable, "W", static_cast<int>(sle::input::Input::Key::W));
    setTableInt(L, keysTable, "Q", static_cast<int>(sle::input::Input::Key::Q));
    setTableInt(L, keysTable, "E", static_cast<int>(sle::input::Input::Key::E));
    setTableInt(L, keysTable, "R", static_cast<int>(sle::input::Input::Key::R));
    setTableInt(L, keysTable, "F", static_cast<int>(sle::input::Input::Key::F));
    setTableInt(L, keysTable, "UP", static_cast<int>(sle::input::Input::Key::Up));
    setTableInt(L, keysTable, "DOWN", static_cast<int>(sle::input::Input::Key::Down));
    setTableInt(L, keysTable, "LEFT", static_cast<int>(sle::input::Input::Key::Left));
    setTableInt(L, keysTable, "RIGHT", static_cast<int>(sle::input::Input::Key::Right));
    setTableInt(L, keysTable, "SPACE", static_cast<int>(sle::input::Input::Key::Space));
    setTableInt(L, keysTable, "ENTER", static_cast<int>(sle::input::Input::Key::Enter));
    setTableInt(L, keysTable, "TAB", static_cast<int>(sle::input::Input::Key::Tab));
    setTableInt(L, keysTable, "ESCAPE", static_cast<int>(sle::input::Input::Key::Escape));
    setTableInt(L, keysTable, "LEFT_SHIFT", static_cast<int>(sle::input::Input::Key::LeftShift));
    setTableInt(L, keysTable, "RIGHT_SHIFT", static_cast<int>(sle::input::Input::Key::RightShift));
    setTableInt(L, keysTable, "LEFT_CONTROL", static_cast<int>(sle::input::Input::Key::LeftControl));
    setTableInt(L, keysTable, "RIGHT_CONTROL", static_cast<int>(sle::input::Input::Key::RightControl));
    setTableInt(L, keysTable, "ZERO", static_cast<int>(sle::input::Input::Key::Zero));
    setTableInt(L, keysTable, "ONE", static_cast<int>(sle::input::Input::Key::One));
    setTableInt(L, keysTable, "TWO", static_cast<int>(sle::input::Input::Key::Two));
    setTableInt(L, keysTable, "THREE", static_cast<int>(sle::input::Input::Key::Three));
    setTableInt(L, keysTable, "FOUR", static_cast<int>(sle::input::Input::Key::Four));
    setTableInt(L, keysTable, "FIVE", static_cast<int>(sle::input::Input::Key::Five));
    setTableInt(L, keysTable, "SIX", static_cast<int>(sle::input::Input::Key::Six));
    setTableInt(L, keysTable, "SEVEN", static_cast<int>(sle::input::Input::Key::Seven));
    setTableInt(L, keysTable, "EIGHT", static_cast<int>(sle::input::Input::Key::Eight));
    setTableInt(L, keysTable, "NINE", static_cast<int>(sle::input::Input::Key::Nine));
    lua_setfield(L, engineTable, "Keys");

    lua_newtable(L);
    const int mouseButtonsTable = lua_gettop(L);
    setTableInt(L, mouseButtonsTable, "LEFT", static_cast<int>(sle::input::Input::MouseButton::Left));
    setTableInt(L, mouseButtonsTable, "RIGHT", static_cast<int>(sle::input::Input::MouseButton::Right));
    setTableInt(L, mouseButtonsTable, "MIDDLE", static_cast<int>(sle::input::Input::MouseButton::Middle));
    lua_setfield(L, engineTable, "MouseButtons");

    lua_setglobal(L, "Engine");
}

} // namespace sle::scripting
