#include <sle/core/EngineConfig.hpp>
#include <sle/core/Result.hpp>
#include <sle/platform/Window.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/RendererCommand.hpp>

#include <iostream>

int main() {
    sle::core::EngineConfig config;
    config.width = 320;
    config.height = 180;
    config.title = "Integration Renderer Edge Test";
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

    // Edge path: no camera set, submit text command (currently a no-op), and run empty frame.
    renderer.beginFrame();
    sle::renderer::TextCommand text;
    text.text = "edge";
    renderer.submit(text);
    renderer.endFrame();

    renderer.shutdown();
    window.destroy();
    return 0;
}
