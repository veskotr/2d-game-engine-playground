#pragma once
#include "core/EngineConfig.hpp"
#include <GLFW/glfw3.h>
#include "Result.hpp"

class Window
{
public:
    Result<bool> create(const EngineConfig &config);
    void pollEvents();
    void swapBuffers();
    bool shouldClose();

    GLFWwindow *getNative(); // for Input system

private:
    GLFWwindow *window = nullptr;
};