#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <array>
#include <iostream>

int main()
{
    if (!glfwInit()) return 125;
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    GLFWwindow* host = glfwCreateWindow(480, 270, "External host regression", nullptr, nullptr);
    if (!host) { glfwTerminate(); return 125; }
    glfwMakeContextCurrent(host);
    if (!gladLoadGL(glfwGetProcAddress)) return 1;
    bool passed = true;
    for (int attempt = 0; attempt < 2; ++attempt)
    {
        sle::core::EngineConfig config;
        config.width = 480;
        config.height = 270;
        sle::Runtime runtime(config);
        const auto initialized = runtime.initEmbedded();
        if (!initialized.ok()) { std::cerr << initialized.error(); passed = false; break; }
        runtime.registerSceneFromFile("sandbox", "assets/scenes/npc_zone_demo.json");
        const auto loaded = runtime.loadScene("sandbox");
        if (!loaded.ok()) { std::cerr << loaded.error(); passed = false; break; }
        runtime.setPhysicsDebugEnabled(true);
        auto& scene = runtime.getScene();
        const auto player = scene.findEntityByKey("player");
        if (!player.valid()) { passed = false; break; }
        const auto position = scene.getRegistry().getComponent<sle::components::TransformComponent>(player)->getPosition();
        if (runtime.renderEmbeddedFrame(0, 270).ok()) { passed = false; break; }
        for (int frame = 0; frame < 3; ++frame)
        {
            if (!runtime.renderEmbeddedFrame(480, 270).ok()) { passed = false; break; }
        }
        if (glfwGetCurrentContext() != host) { passed = false; break; }
        const auto after = scene.getRegistry().getComponent<sle::components::TransformComponent>(player)->getPosition();
        if (after != position) { std::cerr << "Edit preview advanced simulation"; passed = false; break; }
        std::array<unsigned char, 4> background{}, pixel{};
        glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, background.data());
        bool foundSprite = false;
        for (int y = 125; y <= 145; ++y)
            for (int x = 153; x <= 173; ++x)
            {
                glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel.data());
                if (pixel[0] > 150 && pixel[1] < 100 && pixel[2] < 100 && pixel != background)
                    foundSprite = true;
            }
        if (!foundSprite || !runtime.renderEmbeddedFrame(320, 180).ok())
        { std::cerr << "Missing sprite or resize failure"; passed = false; break; }
        GLint viewport[4]{};
        glGetIntegerv(GL_VIEWPORT, viewport);
        if (viewport[2] != 320 || viewport[3] != 180 || glGetError() != GL_NO_ERROR)
        { passed = false; break; }
        // Runtime is destroyed while the host context is current. The next
        // attempt must reuse this same context without stale caches/resources.
    }
    if (glfwGetCurrentContext() != host || glGetString(GL_VERSION) == nullptr) passed = false;
    glfwDestroyWindow(host);
    glfwTerminate();
    return passed ? 0 : 1;
}
