#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Timer.hpp>
#include <sle/platform/Window.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/platform/Input.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/PhysicsSystem.hpp>
#include <sle/engine/RenderSystem.hpp>
#include <sle/engine/Context.hpp>

namespace sle {

class Runtime
{
public:
    Runtime(const sle::core::EngineConfig &config);
    ~Runtime();

    sle::core::Result<bool> init();
    void run();

    sle::entity::Scene& getScene() { return scene; }

private:
    sle::core::EngineConfig config;

    sle::core::Window window;
    sle::renderer::Renderer renderer;
    sle::entity::Scene scene;
    TransformSystem transformSystem;
    ScriptSystem scriptSystem;
    PhysicsSystem physicsSystem;
    RenderSystem renderSystem;
    sle::core::Camera2D camera{static_cast<float>(config.width), static_cast<float>(config.height)};
    sle::core::Timer timer;
};

} // namespace sle
