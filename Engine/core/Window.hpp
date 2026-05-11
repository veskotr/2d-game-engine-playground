#pragma once
#include "core/EngineConfig.hpp"
#include <GLFW/glfw3.h>
#include "Result.hpp"

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