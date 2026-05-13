#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Timer.hpp>
#include <sle/platform/Window.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/platform/Input.hpp>
#include <sle/renderer/Renderer.hpp>

namespace sle {

class Engine
{
public:
    Engine(const sle::core::EngineConfig &config);
    ~Engine();

    sle::core::Result<bool> init();
    void run();

    sle::entity::Scene& getScene() { return scene; }

private:
    sle::core::EngineConfig config;

    sle::core::Window window;
    sle::renderer::Renderer renderer;
    sle::entity::Scene scene;
    sle::core::Camera2D camera{static_cast<float>(config.width), static_cast<float>(config.height)};
    sle::core::Timer timer;
};

} // namespace sle
