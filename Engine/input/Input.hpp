#pragma once
#include <GLFW/glfw3.h>
#include <glm/glm.hpp>
#include <array>

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