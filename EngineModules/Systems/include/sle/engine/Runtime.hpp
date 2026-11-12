#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Timer.hpp>
#include <sle/events/EventBus.hpp>
#include <sle/platform/Window.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/platform/Input.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/Shader.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/engine/AnimationSystem.hpp>
#include <sle/engine/AudioSystem.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/StateMachineSystem.hpp>
#include <sle/engine/PhysicsSystem.hpp>
#include <sle/engine/RenderSystem.hpp>
#include <sle/engine/ChunkSceneRuntimeState.hpp>
#include <sle/engine/SceneManager.hpp>
#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/engine/Context.hpp>
#include <sle/engine/EntityPreviewSystem.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <sle/ui/UISystem.hpp>
#include <sle/physics/PhysicsWorld.hpp>

#include <glm/vec2.hpp>
#include <limits>
#include <cstddef>
#include <memory>
#include <array>
#include <vector>
#include <string>

namespace sle {

class Runtime
{
public:
    Runtime(const sle::core::EngineConfig &config);
    ~Runtime();

    sle::core::Result<bool> init();
    // Host supplies a current OpenGL 4.3+ context with GLAD loaded. All calls,
    // including destruction, must occur with that context current on its owner thread.
    sle::core::Result<bool> initEmbedded(bool initializeAudio = true);
    void enableEmbeddedAudio();
    sle::core::Result<bool> loadScenePreview(const nlohmann::json& document, bool preserveView = false);
    sle::core::Result<bool> loadEntityPreview(const nlohmann::json& document, bool preserveView = false);
    glm::vec2 getCameraViewportSize() const { return camera.getViewportSize(); }
    sle::core::Result<bool> renderEntityPreview(int width, int height, bool fit);
    void zoomEntityPreview(float factor);
    void panEntityPreview(glm::vec2 delta);
    glm::vec2 getPreviewPosition() const { return editorCamera.getPosition(); }
    glm::vec2 getPreviewViewportSize() const { return editorCamera.getViewportSize(); }
    sle::core::Result<bool> startScenePlay(const nlohmann::json& document);
    sle::core::Result<bool> renderSceneView(int width, int height, float dt, bool gameView, bool playing, bool fit = false);
    nlohmann::json sceneTransforms();
    std::array<float,6> sceneGameViewport() const;

    sle::core::Result<bool> renderEmbeddedFrame(int framebufferWidth, int framebufferHeight);
    // Host clears/transfers input before ticking. Advances the same systems as run().
    sle::core::Result<bool> tickEmbeddedFrame(int framebufferWidth, int framebufferHeight, float dt);
    void setEmbeddedPaused(bool paused);
    void run();
    std::size_t runForFrames(std::size_t maxFrames);

    bool registerScene(const std::string& sceneName, SceneManager::SceneBuilder builder);
    bool registerSceneFromFile(const std::string& sceneName, const std::string& jsonPath);
    bool registerSceneFromData(const std::string& sceneName, const nlohmann::json& data, const std::string& sourcePath);
    bool hasScene(const std::string& sceneName) const;
    sle::core::Result<bool> loadScene(const std::string& sceneName);
    sle::core::Result<bool> requestSceneSwitch(const std::string& sceneName);
    const std::string& getCurrentSceneName() const { return sceneManager.getCurrentSceneName(); }

    void synchronizeScriptBehaviours(uint32_t entityId);
    void prepareComponentMutation(uint32_t entityId, const std::string& name,
        const nlohmann::json& patch, bool removing);

    float getDeltaTime() const;
    glm::vec2 getWindowSize() const;

    glm::vec2 getCameraPosition() const;
    void setCameraPosition(const glm::vec2& position);
    void moveCamera(const glm::vec2& delta);
    float getCameraZoom() const;
    void setCameraZoom(float zoom);

    bool setUIBinding(const std::string& key, const std::string& value);
    bool emitScriptEvent(const std::string& eventName, uint32_t sourceEntity, const std::string& payload);

    void setPhysicsDebugEnabled(bool enabled);
    bool isPhysicsDebugEnabled() const;

    sle::entity::Scene& getScene() { return scene; }
    SceneManager& getSceneManager() { return sceneManager; }
    const SceneManager& getSceneManager() const { return sceneManager; }
    sle::physics::PhysicsWorld* getPhysicsWorld() { return physicsWorld.get(); }
    sle::scripting::ScriptEngine& getScriptEngine() { return scriptEngine; }
    sle::events::EventBus& getGlobalBus() { return globalBus_; }

private:
    struct FrameTimings {
        double transform, script, physics, renderSubmit, renderFlush;
    };
    FrameTimings advanceAndRender(float dt);
    void processPendingSceneSwitch();
    void handleDebugInput();
    float frameDeltaTime = 0.0f;
    float fpsUiAccumulator = 0.0f;
    int fpsUiFrameCount = 0;
    sle::core::Result<bool> initServices();
    bool embedded = false;
    bool embeddedPaused = false;
    bool initializeAudioOnInit = true;
    bool audioAttempted = false;
    bool entityPreview = false;
    bool fitPreview = true;
    std::vector<std::string> authoredSceneKeys;
    bool sceneViewOverride = false;
    bool gameViewOverride = false;
    bool componentCameraActive = false;
    int hostWidth = 480, hostHeight = 270;
    void configureRenderViewport();
    sle::core::Camera2D editorCamera{480,270};
    float previewAspect = 0.0f;
    bool initializationStarted = false;
    bool initialized = false;
    std::size_t runMainLoop(bool bounded, std::size_t maxFrames);
    void refreshChunkRuntimeState();

    sle::core::EngineConfig config;

    sle::core::Window window;
    sle::renderer::Renderer renderer;
    sle::entity::Scene scene;
    std::unique_ptr<sle::physics::PhysicsWorld> physicsWorld;
    sle::events::EventBus globalBus_;  // Engine-wide event bus for lifecycle events
    TransformSystem transformSystem;
    AnimationSystem animationSystem;
    ScriptSystem scriptSystem;
    StateMachineSystem stateMachineSystem;
    PhysicsSystem physicsSystem;
    RenderSystem renderSystem;
    sle::ui::UISystem uiSystem;
    SceneManager sceneManager;
    AudioSystem audioSystem;
    ScriptApiImpl scriptApi;
    sle::scripting::ScriptEngine scriptEngine;
    std::shared_ptr<sle::renderer::Shader> defaultQuadShader;
    std::shared_ptr<sle::renderer::Shader> defaultDebugShader;
    sle::core::Camera2D camera{static_cast<float>(config.width), static_cast<float>(config.height)};
    sle::core::Timer timer;
    ChunkSceneRuntimeState chunkRuntimeState;
    std::size_t debugFpsDocumentIndex = std::numeric_limits<std::size_t>::max();
};

} // namespace sle
