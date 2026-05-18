#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Timer.hpp>
#include <sle/platform/Window.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/platform/Input.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/Shader.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/PhysicsSystem.hpp>
#include <sle/engine/RenderSystem.hpp>
#include <sle/engine/SceneManager.hpp>
#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/engine/Context.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <sle/physics/PhysicsWorld.hpp>

#include <glm/vec2.hpp>
#include <memory>
#include <string>

namespace sle {

class Runtime
{
public:
    Runtime(const sle::core::EngineConfig &config);
    ~Runtime();

    sle::core::Result<bool> init();
    void run();

    bool registerScene(const std::string& sceneName, SceneManager::SceneBuilder builder);
    bool hasScene(const std::string& sceneName) const;
    sle::core::Result<bool> loadScene(const std::string& sceneName);
    sle::core::Result<bool> requestSceneSwitch(const std::string& sceneName);
    const std::string& getCurrentSceneName() const { return sceneManager.getCurrentSceneName(); }

    float getDeltaTime() const;
    glm::vec2 getWindowSize() const;

    glm::vec2 getCameraPosition() const;
    void setCameraPosition(const glm::vec2& position);
    void moveCamera(const glm::vec2& delta);
    float getCameraZoom() const;
    void setCameraZoom(float zoom);

    void setPhysicsDebugEnabled(bool enabled);
    bool isPhysicsDebugEnabled() const;

    sle::entity::Scene& getScene() { return scene; }
    SceneManager& getSceneManager() { return sceneManager; }
    const SceneManager& getSceneManager() const { return sceneManager; }
    sle::physics::PhysicsWorld* getPhysicsWorld() { return physicsWorld.get(); }

private:
    sle::core::EngineConfig config;

    sle::core::Window window;
    sle::renderer::Renderer renderer;
    sle::entity::Scene scene;
    std::unique_ptr<sle::physics::PhysicsWorld> physicsWorld;
    TransformSystem transformSystem;
    ScriptSystem scriptSystem;
    PhysicsSystem physicsSystem;
    RenderSystem renderSystem;
    SceneManager sceneManager;
    ScriptApiImpl scriptApi;
    sle::scripting::ScriptEngine scriptEngine;
    std::shared_ptr<sle::renderer::Shader> defaultQuadShader;
    std::shared_ptr<sle::renderer::Shader> defaultDebugShader;
    sle::core::Camera2D camera{static_cast<float>(config.width), static_cast<float>(config.height)};
    sle::core::Timer timer;
};

} // namespace sle
