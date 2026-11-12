#include <sle/platform/Input.hpp>
#include <cmath>

namespace sle::input {

std::array<KeyState, 1024> Input::keys{};
MouseState Input::mouse;
GLFWwindow* Input::hostWindow = nullptr;
glm::dvec2 Input::hostMousePos{};
glm::vec4 Input::hostViewport{};
glm::vec2 Input::hostLogicalSize{};
bool Input::mapHostCursor = false;
bool Input::lastCursorWasHost = false;

glm::dvec2 Input::lastMousePos = {0.0, 0.0};

void Input::init(GLFWwindow *window)
{
    hostWindow = window;
    mapHostCursor = false;
    lastCursorWasHost = false;
    keys = {};
    mouse = {};
    lastMousePos = {0.0, 0.0};
    if (!window) return;
    glfwGetCursorPos(window, &lastMousePos.x, &lastMousePos.y);
    mouse.position = lastMousePos;
    hostMousePos = lastMousePos;
    lastCursorWasHost = true;
    glfwSetKeyCallback(window, keyCallback);
    glfwSetCursorPosCallback(window, cursorCallback);
    glfwSetMouseButtonCallback(window, mouseButtonCallback);
}

void Input::setKeyState(int key, bool down)
{
    keyCallback(nullptr, key, 0, down ? GLFW_PRESS : GLFW_RELEASE, 0);
}

void Input::setMouseButtonState(int button, bool down)
{
    mouseButtonCallback(nullptr, button, down ? GLFW_PRESS : GLFW_RELEASE, 0);
}

void Input::setCursorPosition(double x, double y)
{
    cursorCallback(nullptr, x, y);
}

void Input::setHostCursorViewport(glm::vec4 rectangle, glm::vec2 logicalSize)
{
    if (!std::isfinite(rectangle.x) || !std::isfinite(rectangle.y) ||
        !std::isfinite(rectangle.z) || !std::isfinite(rectangle.w) ||
        !std::isfinite(logicalSize.x) || !std::isfinite(logicalSize.y) ||
        rectangle.z<=0 || rectangle.w<=0 || logicalSize.x<=0 || logicalSize.y<=0) {
        clearHostCursorViewport();
        return;
    }
    hostViewport=rectangle;
    hostLogicalSize=logicalSize;
    mapHostCursor=true;
    if (hostWindow && lastCursorWasHost) cursorCallback(hostWindow,hostMousePos.x,hostMousePos.y);
}

void Input::clearHostCursorViewport()
{
    mapHostCursor=false;
    if (hostWindow && lastCursorWasHost) cursorCallback(hostWindow,hostMousePos.x,hostMousePos.y);
}

void Input::releaseAll()
{
    for (int key = 0; key < static_cast<int>(keys.size()); ++key)
        if (keys[key].down) setKeyState(key, false);
    if (mouse.left) setMouseButtonState(GLFW_MOUSE_BUTTON_LEFT, false);
    if (mouse.right) setMouseButtonState(GLFW_MOUSE_BUTTON_RIGHT, false);
    if (mouse.middle) setMouseButtonState(GLFW_MOUSE_BUTTON_MIDDLE, false);
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
    mouse.middlePressed = false;
    mouse.middleReleased = false;
}

bool Input::isKeyDown(int key)
{
    return key >= 0 && key < static_cast<int>(keys.size()) && keys[key].down;
}

bool Input::isKeyPressed(int key)
{
    return key >= 0 && key < static_cast<int>(keys.size()) && keys[key].pressed;
}

bool Input::isKeyReleased(int key)
{
    return key >= 0 && key < static_cast<int>(keys.size()) && keys[key].released;
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

void Input::mouseButtonCallback(GLFWwindow* window, int button, int action, int)
{
    if (window && mapHostCursor && !mouse.inViewport && action==GLFW_PRESS) return;
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

    if (button == GLFW_MOUSE_BUTTON_MIDDLE)
    {
        if (action == GLFW_PRESS)
        {
            mouse.middlePressed = !mouse.middle;
            mouse.middle = true;
        }
        else if (action == GLFW_RELEASE)
        {
            mouse.middle = false;
            mouse.middleReleased = true;
        }
    }
}

void Input::cursorCallback(GLFWwindow* window, double xpos, double ypos)
{
    glm::dvec2 current = {xpos, ypos};
    mouse.inViewport = true;
    lastCursorWasHost = window != nullptr;
    if (window) {
        hostMousePos=current;
        if (mapHostCursor) {
            int windowWidth=0,windowHeight=0,frameWidth=0,frameHeight=0;
            glfwGetWindowSize(window,&windowWidth,&windowHeight);
            glfwGetFramebufferSize(window,&frameWidth,&frameHeight);
            if (windowWidth<=0 || windowHeight<=0 || frameWidth<=0 || frameHeight<=0) {
                mouse.inViewport=false;
                return;
            }
            const glm::dvec2 framebuffer{xpos*frameWidth/windowWidth,ypos*frameHeight/windowHeight};
            const double top=frameHeight-hostViewport.y-hostViewport.w;
            current={(framebuffer.x-hostViewport.x)*hostLogicalSize.x/hostViewport.z,
                (framebuffer.y-top)*hostLogicalSize.y/hostViewport.w};
            mouse.inViewport=current.x>=0 && current.y>=0 &&
                current.x<hostLogicalSize.x && current.y<hostLogicalSize.y;
        }
    }

    mouse.delta += current - lastMousePos;
    mouse.position = current;

    lastMousePos = current;
}

} // namespace sle::input
