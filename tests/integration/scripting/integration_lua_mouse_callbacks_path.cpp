#include <sle/engine/Runtime.hpp>
#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/platform/Input.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <GLFW/glfw3.h>
#include <iostream>

int main()
{
    if (!glfwInit())
        return 125;
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
    GLFWwindow* window = glfwCreateWindow(64, 64, "Lua mouse callbacks", nullptr, nullptr);
    if (!window) { glfwTerminate(); return 125; }
    bool passed = false;
    {
        sle::core::EngineConfig config;
        sle::Runtime runtime(config);
        sle::ScriptApiImpl api(runtime);
        sle::scripting::ScriptEngine engine;
        sle::input::Input::init(window);
        const auto buttonCallback = glfwSetMouseButtonCallback(window, nullptr);
        const auto cursorCallback = glfwSetCursorPosCallback(window, nullptr);
        glfwSetMouseButtonCallback(window, buttonCallback);
        glfwSetCursorPosCallback(window, cursorCallback);
        if (buttonCallback && cursorCallback && engine.init(&api) &&
            engine.executeScriptAsset("tests/data/scripts/lua_mouse_callbacks.lua"))
        {
            sle::input::Input::update();
            // Establish a known baseline without requiring real pointer events.
            cursorCallback(window, 10, 20);
            sle::input::Input::update();
            cursorCallback(window, 13, 24);
            cursorCallback(window, 18, 30);
            buttonCallback(window, GLFW_MOUSE_BUTTON_MIDDLE, GLFW_PRESS, 0);
            passed = engine.callGlobalFunction("mousePressed");
            sle::input::Input::update();
            passed = engine.callGlobalFunction("mouseHeld") && passed;
            buttonCallback(window, GLFW_MOUSE_BUTTON_MIDDLE, GLFW_RELEASE, 0);
            passed = engine.callGlobalFunction("mouseReleased") && passed;
            sle::input::Input::update();
            passed = engine.callGlobalFunction("mouseIdle") && passed;
        }
        engine.shutdown();
    }
    glfwDestroyWindow(window);
    glfwTerminate();
    if (!passed)
        std::cerr << "Lua mouse callback state contract failed\n";
    return passed ? 0 : 1;
}
