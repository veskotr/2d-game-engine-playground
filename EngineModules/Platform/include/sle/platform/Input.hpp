#pragma once
#include <GLFW/glfw3.h>
#include <glm/glm.hpp>
#include <array>

namespace sle::input {

struct MouseState
{
    glm::dvec2 position = {0.0, 0.0};
    glm::dvec2 delta = {0.0, 0.0};

    bool left = false;
    bool right = false;
};

struct KeyState
{
    bool down = false;
    bool pressed = false;
    bool released = false;
};

class Input
{
public:
    enum class Key : int
    {
        A = GLFW_KEY_A,
        C = GLFW_KEY_C,
        D = GLFW_KEY_D,
        S = GLFW_KEY_S,
        W = GLFW_KEY_W,
        Q = GLFW_KEY_Q,
        E = GLFW_KEY_E,
        R = GLFW_KEY_R,
        F = GLFW_KEY_F,
        F3 = GLFW_KEY_F3,
        Up = GLFW_KEY_UP,
        Down = GLFW_KEY_DOWN,
        Left = GLFW_KEY_LEFT,
        Right = GLFW_KEY_RIGHT,
        Space = GLFW_KEY_SPACE,
        Enter = GLFW_KEY_ENTER,
        Tab = GLFW_KEY_TAB,
        Escape = GLFW_KEY_ESCAPE,
        LeftShift = GLFW_KEY_LEFT_SHIFT,
        RightShift = GLFW_KEY_RIGHT_SHIFT,
        LeftControl = GLFW_KEY_LEFT_CONTROL,
        RightControl = GLFW_KEY_RIGHT_CONTROL,
        Zero = GLFW_KEY_0,
        One = GLFW_KEY_1,
        Two = GLFW_KEY_2,
        Three = GLFW_KEY_3,
        Four = GLFW_KEY_4,
        Five = GLFW_KEY_5,
        Six = GLFW_KEY_6,
        Seven = GLFW_KEY_7,
        Eight = GLFW_KEY_8,
        Nine = GLFW_KEY_9
    };

    enum class MouseButton : int
    {
        Left = GLFW_MOUSE_BUTTON_LEFT,
        Right = GLFW_MOUSE_BUTTON_RIGHT,
        Middle = GLFW_MOUSE_BUTTON_MIDDLE
    };

    static void init(GLFWwindow* window);
    static void update();

    static bool isKeyDown(int key);
    static bool isKeyPressed(int key);
    static bool isKeyReleased(int key);

    static const MouseState& getMouse();

private:
    static void keyCallback(GLFWwindow*, int key, int scancode, int action, int mods);
    static void mouseButtonCallback(GLFWwindow*, int button, int action, int mods);
    static void cursorCallback(GLFWwindow*, double xpos, double ypos);

private:
    static std::array<KeyState, 1024> keys;
    static MouseState mouse;

    static glm::dvec2 lastMousePos;
};

} // namespace sle::input
