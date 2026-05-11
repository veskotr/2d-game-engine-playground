#include <glad/gl.h>
#include "Window.hpp"
#include <iostream>
#include "Log.hpp"

static void glfwErrorCallback(int error, const char* description);

Result<bool> Window::create(const EngineConfig &config)
{
    glfwInit();
    glfwSetErrorCallback(glfwErrorCallback);

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    GLFWmonitor *monitor = nullptr;

    if (config.screenMode == ScreenMode::Fullscreen)
    {
        monitor = glfwGetPrimaryMonitor();
    }

    if (config.screenMode == ScreenMode::BorderlessFullscreen)
    {
        monitor = glfwGetPrimaryMonitor();

        const GLFWvidmode *mode = glfwGetVideoMode(monitor);
        glfwWindowHint(GLFW_RED_BITS, mode->redBits);
        glfwWindowHint(GLFW_GREEN_BITS, mode->greenBits);
        glfwWindowHint(GLFW_BLUE_BITS, mode->blueBits);
        glfwWindowHint(GLFW_REFRESH_RATE, mode->refreshRate);

        const_cast<EngineConfig &>(config).width = mode->width;
        const_cast<EngineConfig &>(config).height = mode->height;
    }

    window = glfwCreateWindow(
        config.width,
        config.height,
        config.title.c_str(),
        monitor,
        nullptr);

    if (!window)
        return Result<bool>::error("Failed to create GLFW window");

    glfwMakeContextCurrent(window);

    if (!gladLoadGL(glfwGetProcAddress))
    {
        return Result<bool>::error("Failed to initialize GLAD");
    }

    if (config.vsync)
        glfwSwapInterval(1);
    else
        glfwSwapInterval(0);

     return Result<bool>::success(true);
}

void Window::pollEvents()
{
    glfwPollEvents();
}

void Window::swapBuffers()
{
    glfwSwapBuffers(window);
}

bool Window::shouldClose()
{
    return glfwWindowShouldClose(window);
}

GLFWwindow *Window::getNative()
{
    return window;
}

static void glfwErrorCallback(int error, const char* description)
{
    Log::error("GLFW Error {}: {}", error, description);
}