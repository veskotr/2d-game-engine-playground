#include <sle/core/EngineConfig.hpp>
#include <sle/core/Result.hpp>
#include <sle/platform/Window.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/RendererCommand.hpp>

#include <glm/mat4x4.hpp>

#include <iostream>

int main() {
    sle::core::EngineConfig config;
    config.width = 320;
    config.height = 180;
    config.title = "Integration Renderer Test";
    config.vsync = false;
    config.screenMode = sle::core::ScreenMode::Windowed;

    sle::core::Window window;
    const auto windowResult = window.create(config);
    if (!windowResult.ok()) {
        std::cerr << "Window creation failed: " << windowResult.error() << "\n";
        return 1;
    }

    sle::renderer::Renderer renderer;
    const auto initResult = renderer.init();
    if (!initResult.ok()) {
        std::cerr << "Renderer init failed: " << initResult.error() << "\n";
        window.destroy();
        return 1;
    }

    sle::core::Camera2D camera(static_cast<float>(config.width), static_cast<float>(config.height));
    renderer.setCamera(&camera);

    renderer.beginFrame();

    sle::renderer::QuadCommand quad;
    quad.modelMatrix = glm::mat4(1.0f);
    quad.layer = 1;
    renderer.submit(quad);

    sle::renderer::LineCommand line;
    line.a = {-10.0f, -10.0f};
    line.b = {10.0f, 10.0f};
    line.color = {1.0f, 0.0f, 0.0f, 1.0f};
    line.layer = 2;
    renderer.submit(line);

    renderer.endFrame();

    renderer.shutdown();
    window.destroy();

    return 0;
}
