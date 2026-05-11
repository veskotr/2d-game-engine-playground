#include <glad/gl.h>
#include "Window.hpp"
#include "Log.hpp"

static void glfwErrorCallback(int error, const char* description);
static void glfwFramebufferSizeCallback(GLFWwindow* window, int width, int height);

Window::~Window()
{
    destroy();
}

Result<bool> Window::create(const EngineConfig &config)
{
    if (!glfwInit())
    {
        return Result<bool>::error("Failed to initialize GLFW");
    }

    glfwInitialized = true;
    glfwSetErrorCallback(glfwErrorCallback);

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    GLFWmonitor *monitor = nullptr;
    int targetWidth = config.width;
    int targetHeight = config.height;

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

        targetWidth = mode->width;
        targetHeight = mode->height;
    }

    window = glfwCreateWindow(
        targetWidth,
        targetHeight,
        config.title.c_str(),
        monitor,
        nullptr);

    if (!window)
    {
        glfwTerminate();
        glfwInitialized = false;
        return Result<bool>::error("Failed to create GLFW window");
    }

    glfwSetWindowUserPointer(window, this);
    glfwSetFramebufferSizeCallback(window, glfwFramebufferSizeCallback);

    width = targetWidth;
    height = targetHeight;

    glfwMakeContextCurrent(window);

    if (!gladLoadGL(glfwGetProcAddress))
    {
        destroy();
        return Result<bool>::error("Failed to initialize GLAD");
    }

    // Ensure initial viewport matches the active framebuffer size.
    onFramebufferResize(width, height);

    if (config.vsync)
        glfwSwapInterval(1);
    else
        glfwSwapInterval(0);

     return Result<bool>::success(true);
}

void Window::destroy()
{
    if (window)
    {
        glfwDestroyWindow(window);
        window = nullptr;
    }

    if (glfwInitialized)
    {
        glfwTerminate();
        glfwInitialized = false;
    }
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

int Window::getWidth() const
{
    return width;
}

int Window::getHeight() const
{
    return height;
}

bool Window::consumeResize()
{
    bool wasResized = resized;
    resized = false;
    return wasResized;
}

GLFWwindow *Window::getNative()
{
    return window;
}

void Window::onFramebufferResize(int width, int height)
{
    this->width = width;
    this->height = height;
    resized = true;
    glViewport(0, 0, width, height);
}

static void glfwErrorCallback(int error, const char* description)
{
    Log::error("GLFW Error {}: {}", error, description);
}

static void glfwFramebufferSizeCallback(GLFWwindow* window, int width, int height)
{
    auto* self = static_cast<Window*>(glfwGetWindowUserPointer(window));
    if (!self)
    {
        return;
    }

    self->onFramebufferResize(width, height);
}