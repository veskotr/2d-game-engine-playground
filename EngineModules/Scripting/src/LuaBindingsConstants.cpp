#include "LuaBindingsInternal.hpp"
#include "LuaBindingsCommon.hpp"

#include <sle/platform/Input.hpp>

namespace sle::scripting {

void registerConstantsTables(lua_State* L, int engineTable)
{
    lua_newtable(L);
    const int keysTable = lua_gettop(L);
    detail::setTableInt(L, keysTable, "A", static_cast<int>(sle::input::Input::Key::A));
    detail::setTableInt(L, keysTable, "C", static_cast<int>(sle::input::Input::Key::C));
    detail::setTableInt(L, keysTable, "D", static_cast<int>(sle::input::Input::Key::D));
    detail::setTableInt(L, keysTable, "S", static_cast<int>(sle::input::Input::Key::S));
    detail::setTableInt(L, keysTable, "W", static_cast<int>(sle::input::Input::Key::W));
    detail::setTableInt(L, keysTable, "Q", static_cast<int>(sle::input::Input::Key::Q));
    detail::setTableInt(L, keysTable, "E", static_cast<int>(sle::input::Input::Key::E));
    detail::setTableInt(L, keysTable, "R", static_cast<int>(sle::input::Input::Key::R));
    detail::setTableInt(L, keysTable, "F", static_cast<int>(sle::input::Input::Key::F));
    detail::setTableInt(L, keysTable, "F3", static_cast<int>(sle::input::Input::Key::F3));
    detail::setTableInt(L, keysTable, "UP", static_cast<int>(sle::input::Input::Key::Up));
    detail::setTableInt(L, keysTable, "DOWN", static_cast<int>(sle::input::Input::Key::Down));
    detail::setTableInt(L, keysTable, "LEFT", static_cast<int>(sle::input::Input::Key::Left));
    detail::setTableInt(L, keysTable, "RIGHT", static_cast<int>(sle::input::Input::Key::Right));
    detail::setTableInt(L, keysTable, "SPACE", static_cast<int>(sle::input::Input::Key::Space));
    detail::setTableInt(L, keysTable, "ENTER", static_cast<int>(sle::input::Input::Key::Enter));
    detail::setTableInt(L, keysTable, "TAB", static_cast<int>(sle::input::Input::Key::Tab));
    detail::setTableInt(L, keysTable, "ESCAPE", static_cast<int>(sle::input::Input::Key::Escape));
    detail::setTableInt(L, keysTable, "LEFT_SHIFT", static_cast<int>(sle::input::Input::Key::LeftShift));
    detail::setTableInt(L, keysTable, "RIGHT_SHIFT", static_cast<int>(sle::input::Input::Key::RightShift));
    detail::setTableInt(L, keysTable, "LEFT_CONTROL", static_cast<int>(sle::input::Input::Key::LeftControl));
    detail::setTableInt(L, keysTable, "RIGHT_CONTROL", static_cast<int>(sle::input::Input::Key::RightControl));
    detail::setTableInt(L, keysTable, "ZERO", static_cast<int>(sle::input::Input::Key::Zero));
    detail::setTableInt(L, keysTable, "ONE", static_cast<int>(sle::input::Input::Key::One));
    detail::setTableInt(L, keysTable, "TWO", static_cast<int>(sle::input::Input::Key::Two));
    detail::setTableInt(L, keysTable, "THREE", static_cast<int>(sle::input::Input::Key::Three));
    detail::setTableInt(L, keysTable, "FOUR", static_cast<int>(sle::input::Input::Key::Four));
    detail::setTableInt(L, keysTable, "FIVE", static_cast<int>(sle::input::Input::Key::Five));
    detail::setTableInt(L, keysTable, "SIX", static_cast<int>(sle::input::Input::Key::Six));
    detail::setTableInt(L, keysTable, "SEVEN", static_cast<int>(sle::input::Input::Key::Seven));
    detail::setTableInt(L, keysTable, "EIGHT", static_cast<int>(sle::input::Input::Key::Eight));
    detail::setTableInt(L, keysTable, "NINE", static_cast<int>(sle::input::Input::Key::Nine));
    lua_setfield(L, engineTable, "Keys");

    lua_newtable(L);
    const int mouseButtonsTable = lua_gettop(L);
    detail::setTableInt(L, mouseButtonsTable, "LEFT", static_cast<int>(sle::input::Input::MouseButton::Left));
    detail::setTableInt(L, mouseButtonsTable, "RIGHT", static_cast<int>(sle::input::Input::MouseButton::Right));
    detail::setTableInt(L, mouseButtonsTable, "MIDDLE", static_cast<int>(sle::input::Input::MouseButton::Middle));
    lua_setfield(L, engineTable, "MouseButtons");
}

} // namespace sle::scripting
