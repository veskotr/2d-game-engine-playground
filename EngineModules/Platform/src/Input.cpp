#include <sle/platform/Input.hpp>

namespace sle::input {

std::array<KeyState, 1024> Input::keys{};
MouseState Input::mouse;

glm::dvec2 Input::lastMousePos = {0.0, 0.0};

void Input::init(GLFWwindow *window)
{
    glfwSetKeyCallback(window, keyCallback);
    glfwSetCursorPosCallback(window, cursorCallback);
    glfwSetMouseButtonCallback(window, mouseButtonCallback);
}

void Input::update()
{
    for (auto &key : keys)
    {
        key.pressed = false;
        key.released = false;
    }

    mouse.delta = {0.0, 0.0};
    mouse.leftPressed = false;
    mouse.leftReleased = false;
    mouse.rightPressed = false;
    mouse.rightReleased = false;
}

bool Input::isKeyDown(int key)
{
    return keys[key].down;
}

bool Input::isKeyPressed(int key)
{
    return keys[key].pressed;
}

bool Input::isKeyReleased(int key)
{
    return keys[key].released;
}

const MouseState &Input::getMouse()
{
    return mouse;
}

void Input::keyCallback(GLFWwindow*, int key, int, int action, int)
{
    if (key < 0 || key >= 1024) return;

    auto& state = keys[key];

    if (action == GLFW_PRESS)
    {
        if (!state.down)
            state.pressed = true;

        state.down = true;
    }
    else if (action == GLFW_RELEASE)
    {
        state.down = false;
        state.released = true;
    }
}

void Input::mouseButtonCallback(GLFWwindow*, int button, int action, int)
{
    if (button == GLFW_MOUSE_BUTTON_LEFT)
    {
        if (action == GLFW_PRESS)
        {
            mouse.leftPressed = !mouse.left;
            mouse.left = true;
        }
        else if (action == GLFW_RELEASE)
        {
            mouse.left = false;
            mouse.leftReleased = true;
        }
    }

    if (button == GLFW_MOUSE_BUTTON_RIGHT)
    {
        if (action == GLFW_PRESS)
        {
            mouse.rightPressed = !mouse.right;
            mouse.right = true;
        }
        else if (action == GLFW_RELEASE)
        {
            mouse.right = false;
            mouse.rightReleased = true;
        }
    }
}

void Input::cursorCallback(GLFWwindow*, double xpos, double ypos)
{
    glm::dvec2 current = {xpos, ypos};

    mouse.delta = current - lastMousePos;
    mouse.position = current;

    lastMousePos = current;
}

} // namespace sle::input
