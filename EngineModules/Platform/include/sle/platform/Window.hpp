#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Result.hpp>
#include <GLFW/glfw3.h>

namespace sle::core {

class Window
{
public:
    ~Window();

    Result<bool> create(const EngineConfig &config);
    void destroy();
    void pollEvents();
    void swapBuffers();
    bool shouldClose();

    int getWidth() const;
    int getHeight() const;
    bool consumeResize();

    GLFWwindow *getNative(); // for Input system

private:
    friend void glfwFramebufferSizeCallback(GLFWwindow* window, int width, int height);

    GLFWwindow *window = nullptr;
    int width = 0;
    int height = 0;
    bool resized = false;
    bool glfwInitialized = false;

    void onFramebufferResize(int width, int height);
};

} // namespace sle::core
