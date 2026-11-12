#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <glm/glm.hpp>
#include <chrono>
#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/Context.hpp>
#include <sle/engine/CameraSystem.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/events/ScriptEvents.hpp>
#include <sle/resources/Resources.hpp>

#include <algorithm>
#include <cmath>
#include <filesystem>
#include <string>

namespace sle
{
    namespace
    {
        std::string resolveAssetPath(const std::string& relativePath)
        {
            namespace fs = std::filesystem;

            fs::path path(relativePath);
            if (fs::exists(path))
                return path.lexically_normal().generic_string();

            fs::path current = fs::current_path();
            while (!current.empty())
            {
                fs::path candidate = current / relativePath;
                if (fs::exists(candidate))
                    return candidate.lexically_normal().generic_string();

                if (current == current.root_path())
                    break;
                current = current.parent_path();
            }

            return relativePath;
        }
    }

    using namespace sle::core;
    using namespace sle::entity;
    using namespace sle::renderer;
    using namespace sle::input;

    Runtime::Runtime(const sle::core::EngineConfig &config)
        : config(config)
        , chunkRuntimeState(config.chunkActivation)
        , scriptApi(*this)
    {
    }

    bool Runtime::registerScene(const std::string& sceneName, SceneManager::SceneBuilder builder)
    {
        return sceneManager.registerScene(sceneName, std::move(builder));
    }

    bool Runtime::registerSceneFromFile(const std::string& sceneName, const std::string& jsonPath)
    {
        return sceneManager.registerSceneFromFile(sceneName, jsonPath);
    }

    bool Runtime::registerSceneFromData(const std::string& sceneName, const nlohmann::json& data, const std::string& sourcePath)
    {
        return sceneManager.registerSceneFromData(sceneName, data, sourcePath);
    }

    bool Runtime::hasScene(const std::string& sceneName) const
    {
        return sceneManager.hasScene(sceneName);
    }

    Result<bool> Runtime::loadScene(const std::string& sceneName)
    {
        auto result = sceneManager.loadScene(sceneName, *this, scene);
        if (result.ok())
        {
            if (entityPreview)
            {
                camera.setViewport(static_cast<float>(config.width), static_cast<float>(config.height));
                camera.setPosition({0.0f, 0.0f});
                camera.setZoom(1.0f);
                camera.setRotation(0.0f);
                renderer.setClearColor(config.backgroundColor);
            }
            entityPreview = false;
            refreshChunkRuntimeState();
            Log::info("Loaded scene: {}", sceneName);
        }

        return result;
    }

    void Runtime::refreshChunkRuntimeState()
    {
        chunkRuntimeState.refreshFromScene(scene);
    }

    Result<bool> Runtime::requestSceneSwitch(const std::string& sceneName)
    {
        return sceneManager.requestSceneSwitch(sceneName);
    }

    float Runtime::getDeltaTime() const
    {
        return frameDeltaTime;
    }

    glm::vec2 Runtime::getWindowSize() const
    {
        if (embedded || componentCameraActive) return camera.getViewportSize();
        return {
            static_cast<float>(window.getWidth()),
            static_cast<float>(window.getHeight())};
    }

    glm::vec2 Runtime::getCameraPosition() const
    {
        return camera.getPosition();
    }

    void Runtime::setCameraPosition(const glm::vec2& position)
    {
        camera.setPosition(position);
    }

    void Runtime::moveCamera(const glm::vec2& delta)
    {
        camera.move(delta);
    }

    float Runtime::getCameraZoom() const
    {
        return camera.getZoom();
    }

    void Runtime::setCameraZoom(float zoom)
    {
        camera.setZoom(zoom);
    }

    bool Runtime::setUIBinding(const std::string& key, const std::string& value)
    {
        if (key.empty())
            return false;

        return uiSystem.setBinding(key, value);
    }

    bool Runtime::emitScriptEvent(const std::string& eventName, uint32_t sourceEntity, const std::string& payload)
    {
        if (eventName.empty())
            return false;

        scene.getEventBus().queue(sle::events::ScriptEvent{
            eventName,
            sle::entity::Entity(sourceEntity),
            payload,
        });
        return true;
    }

    void Runtime::setPhysicsDebugEnabled(bool enabled)
    {
        renderSystem.setPhysicsDebugEnabled(enabled);
    }

    bool Runtime::isPhysicsDebugEnabled() const
    {
        return renderSystem.isPhysicsDebugEnabled();
    }

    Runtime::~Runtime()
    {
        scriptEngine.shutdown();
        scene.destroy();
        // Release every runtime-owned GPU resource while the GL context is valid.
        // Clearing the shared cache alone cannot release UI and shader references.
        uiSystem.shutdown();
        defaultQuadShader.reset();
        defaultDebugShader.reset();
        Resources::clear();
        renderer.shutdown();
        window.destroy();
    }

    Result<bool> Runtime::init()
    {
        if (initializationStarted)
            return Result<bool>::error("Runtime initialization already attempted");
        initializationStarted = true;
        auto windowResult = window.create(config);
        if (!windowResult.ok())
            return Result<bool>::error(windowResult.error());

        camera.setViewport(
            static_cast<float>(window.getWidth()),
            static_cast<float>(window.getHeight()));

        return initServices();
    }

    Result<bool> Runtime::initEmbedded(bool initializeAudio)
    {
        if (initializationStarted)
            return Result<bool>::error("Runtime initialization already attempted");
        if (config.width <= 0 || config.height <= 0)
            return Result<bool>::error("Embedded viewport dimensions must be positive");
        if (!glad_glGetString || !glGetString(GL_VERSION) || !GLAD_GL_VERSION_4_3)
            return Result<bool>::error("Embedded runtime requires a current OpenGL 4.3+ context and loaded GLAD");
        initializationStarted = true;
        initializeAudioOnInit = initializeAudio;
        embedded = true;
        camera.setViewport(static_cast<float>(config.width), static_cast<float>(config.height));
        return initServices();
    }

    Result<bool> Runtime::renderEmbeddedFrame(int framebufferWidth, int framebufferHeight)
    {
        if (!embedded || !initialized)
            return Result<bool>::error("Embedded runtime is not initialized");
        if (framebufferWidth <= 0 || framebufferHeight <= 0)
            return Result<bool>::error("Framebuffer dimensions must be positive");
        hostWidth=framebufferWidth; hostHeight=framebufferHeight;
        Context gameCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, physicsWorld.get(), 0.0f};
        transformSystem.update(gameCtx);
        componentCameraActive=CameraSystem::update(scene.getRegistry(),camera);
        chunkRuntimeState.update(scene, camera.getPosition());
        const bool editorView=entityPreview || sceneViewOverride;
        if (editorView && fitPreview) {
            EntityPreviewSystem::frameCamera(scene, editorCamera, static_cast<float>(framebufferWidth)/framebufferHeight);
            fitPreview=false;
        }
        auto& displayCamera=editorView ? editorCamera : camera;
        renderer.setCamera(&displayCamera);
        configureRenderViewport();
        Context ctx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, displayCamera, physicsWorld.get(), 0.0f};
        renderer.beginFrame();
        renderSystem.update(ctx);
        if (editorView) EntityPreviewSystem::submitOverlay(scene,renderer,editorCamera);
        sle::ui::UIFrameContext uiCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, displayCamera, scriptEngine, 0.0f};
        uiSystem.renderSnapshot(uiCtx);
        renderer.endFrame();
        return Result<bool>::success(true);
    }

    void Runtime::enableEmbeddedAudio()
    {
        if (!embedded || audioAttempted) return;
        audioAttempted = true;
        audioSystem.init();
        if (embeddedPaused) audioSystem.setPaused(true);
    }

    Result<bool> Runtime::loadEntityPreview(const nlohmann::json& document, bool preserveView)
    {
        if (!embedded || !initialized) return Result<bool>::error("Embedded runtime is not initialized");
        const auto valid = EntityAuthoring::validatePrefab(document);
        if (!valid.ok()) return valid;
        return loadScenePreview({{"entities", document.at("entities")}}, preserveView);
    }

    Result<bool> Runtime::loadScenePreview(const nlohmann::json& document, bool preserveView)
    {
        if (!embedded || !initialized) return Result<bool>::error("Embedded runtime is not initialized");
        nlohmann::json canonical;
        try { canonical = SceneAuthoring::canonicalScene(document); }
        catch (const std::exception& error) { return Result<bool>::error(error.what()); }
        const bool keepView = preserveView && entityPreview;
        registerSceneFromData("entity_preview", canonical, "editor.scene.json");
        const auto loaded = sceneManager.loadScene("entity_preview", *this, scene, false);
        if (!loaded.ok()) return loaded;
        authoredSceneKeys.clear();
        for (const auto& e : canonical.at("entities")) authoredSceneKeys.push_back(e.at("key").get<std::string>());
        refreshChunkRuntimeState();
        editorCamera.setZoom(1.0f);
        uiSystem.setDocumentVisible(debugFpsDocumentIndex, false);
        entityPreview = true;
        fitPreview = !keepView;
        setEmbeddedPaused(true);
        renderSystem.setPhysicsDebugEnabled(true);
        renderer.setClearColor({0.09f, 0.10f, 0.12f, 1.0f});
        return Result<bool>::success(true);
    }

    Result<bool> Runtime::renderEntityPreview(int width, int height, bool fit)
    {
        if (!entityPreview || width <= 0 || height <= 0)
            return Result<bool>::error("Entity preview is not ready or has invalid dimensions");
        const float aspect = static_cast<float>(width) / height;
        fitPreview = fitPreview || fit || std::abs(aspect - previewAspect) > 0.001f;
        previewAspect = aspect;
        return renderEmbeddedFrame(width, height);
    }

    void Runtime::zoomEntityPreview(float factor)
    {
        if (!std::isfinite(factor) || factor <= 0.0f) return;
        const auto size=editorCamera.getViewportSize();
        const auto next=glm::clamp(size*factor,glm::vec2(0.01f),glm::vec2(1.0e6f));
        const auto center=editorCamera.getPosition()+size*0.5f;
        editorCamera.setViewport(next.x,next.y);
        editorCamera.setPosition(center-next*0.5f);
    }

    void Runtime::panEntityPreview(glm::vec2 delta) {
        if (std::isfinite(delta.x) && std::isfinite(delta.y)) editorCamera.move(delta);
    }

    Result<bool> Runtime::startScenePlay(const nlohmann::json& document) {
        if (!embedded || !initialized) return Result<bool>::error("Embedded runtime is not initialized");
        nlohmann::json canonical;
        try { canonical=SceneAuthoring::canonicalScene(document); }
        catch (const std::exception& e) { return Result<bool>::error(e.what()); }
        if (!registerSceneFromData("editor_play",canonical,"editor.scene.json")) return Result<bool>::error("Cannot register scene play");
        const auto loaded=loadScene("editor_play");
        if (!loaded.ok()) return loaded;
        authoredSceneKeys.clear();
        for (const auto& e : canonical.at("entities")) authoredSceneKeys.push_back(e.at("key").get<std::string>());
        setPhysicsDebugEnabled(false);
        setEmbeddedPaused(false);
        return loaded;
    }

    Result<bool> Runtime::renderSceneView(int width,int height,float dt,bool gameView,bool playing,bool fit) {
        if (width<=0 || height<=0 || !std::isfinite(dt) || dt<0) return Result<bool>::error("Invalid scene viewport frame");
        sceneViewOverride=!gameView;
        gameViewOverride=gameView;
        hostWidth=width; hostHeight=height;
        if (!gameView) {
            const float aspect=static_cast<float>(width)/height;
            fitPreview=fitPreview || fit || std::abs(aspect-previewAspect)>0.001f;
            previewAspect=aspect;
        }
        // Authoring uses a frozen snapshot; Play uses the normal update/pause path.
        const bool previousDebug=isPhysicsDebugEnabled();
        if (gameView) setPhysicsDebugEnabled(false);
        const bool previousPreview=entityPreview;
        if (gameView) entityPreview=false;
        auto result=playing ? tickEmbeddedFrame(width,height,dt) : renderEmbeddedFrame(width,height);
        entityPreview=previousPreview;
        setPhysicsDebugEnabled(previousDebug);
        sceneViewOverride=false; gameViewOverride=false;
        renderer.setCamera(&camera);
        return result;
    }

    std::array<float,6> Runtime::sceneGameViewport() const {
        const auto size=camera.getViewportSize();
        const float scale=std::min(hostWidth/size.x,hostHeight/size.y);
        const float width=std::max(1.0f,std::floor(size.x*scale)),height=std::max(1.0f,std::floor(size.y*scale));
        return {std::floor((hostWidth-width)*0.5f),std::floor((hostHeight-height)*0.5f),width,height,size.x,size.y};
    }

    void Runtime::configureRenderViewport() {
        glViewport(0,0,hostWidth,hostHeight);
        if (gameViewOverride || (!embedded && !sceneViewOverride)) {
            const auto rect=sceneGameViewport();
            glViewport(static_cast<int>(rect[0]),static_cast<int>(rect[1]),static_cast<int>(rect[2]),static_cast<int>(rect[3]));
            if (!embedded && componentCameraActive)
                Input::setHostCursorViewport({rect[0],rect[1],rect[2],rect[3]},{rect[4],rect[5]});
            else if (!embedded) Input::clearHostCursorViewport();
        }
    }

    nlohmann::json Runtime::sceneTransforms() {
        nlohmann::json result=nlohmann::json::object();
        for (const auto& key : authoredSceneKeys) {
            const auto* t=scene.getRegistry().getComponent<components::TransformComponent>(scene.findEntityByKey(key));
            if (!t) continue;
            const auto p=t->getPosition(),s=t->getScale();
            result[key]={{"position",{{"x",p.x},{"y",p.y}}},{"rotation",t->getRotation()},{"scale",{{"x",s.x},{"y",s.y}}}};
        }
        return result;
    }

    Result<bool> Runtime::tickEmbeddedFrame(int framebufferWidth, int framebufferHeight, float dt)
    {
        if (!embedded || !initialized)
            return Result<bool>::error("Embedded runtime is not initialized");
        if (framebufferWidth <= 0 || framebufferHeight <= 0 || !std::isfinite(dt) || dt < 0.0f)
            return Result<bool>::error("Embedded frame requires positive dimensions and finite nonnegative dt");
        if (embeddedPaused) return renderEmbeddedFrame(framebufferWidth, framebufferHeight);
        processPendingSceneSwitch();
        handleDebugInput();
        hostWidth=framebufferWidth; hostHeight=framebufferHeight;
        (void)advanceAndRender(dt);
        return Result<bool>::success(true);
    }

    void Runtime::setEmbeddedPaused(bool paused)
    {
        if (!embedded || embeddedPaused == paused) return;
        embeddedPaused = paused;
        Input::releaseAll();
        audioSystem.setPaused(paused);
    }

    void Runtime::processPendingSceneSwitch()
    {
        const std::string previousSceneName = sceneManager.getCurrentSceneName();
        auto switchResult = sceneManager.processPendingSwitch(*this, scene);
        if (!switchResult.ok())
        {
            Log::error("Failed to switch pending scene: {}", switchResult.error());
        }
        else if (previousSceneName != sceneManager.getCurrentSceneName())
        {
            refreshChunkRuntimeState();
        }
    }

    void Runtime::handleDebugInput()
    {
        if (gameViewOverride) return;
        if (Input::isKeyPressed(static_cast<int>(Input::Key::F3)))
        {
            const bool enabled = !renderSystem.isPhysicsDebugEnabled();
            renderSystem.setPhysicsDebugEnabled(enabled);
            uiSystem.setDocumentVisible(debugFpsDocumentIndex, enabled);
            Log::info("Physics debug rendering: {}", enabled ? "ON" : "OFF");
        }
    }

    Runtime::FrameTimings Runtime::advanceAndRender(float dt)
    {
        using Clock = std::chrono::high_resolution_clock;
        using Ms = std::chrono::duration<double, std::milli>;
        frameDeltaTime = dt;
        fpsUiAccumulator += dt;
        ++fpsUiFrameCount;

        if (fpsUiAccumulator >= 1.0f)
        {
            const int fpsUi = static_cast<int>(std::lround(static_cast<float>(fpsUiFrameCount) / std::max(fpsUiAccumulator, 0.0001f)));
            uiSystem.setBinding("fpsText", std::to_string(fpsUi));
            fpsUiAccumulator = 0.0f;
            fpsUiFrameCount = 0;
        }
        // Construct per-frame context with all system dependencies.
        Context ctx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, physicsWorld.get(), dt};

        // === FRAME START ===
        // Flush queued events from previous frame.
        ctx.eventBus.flushQueue();
        ctx.globalBus.flushQueue();

        // === LOGIC UPDATE PHASE ===
        // 0. Update chunk activation before simulation-heavy systems so
        // inactive chunk residents can skip work in the same frame.
        chunkRuntimeState.update(scene, camera.getPosition());

        // Advance animation before scripts and physics consume local transforms.
        animationSystem.update(scene, dt);

        // 1b. Process audio play/stop requests.
        audioSystem.update(scene);

        auto t1 = Clock::now();

        // Scripts can edit local transforms before physics synchronization.
        scriptSystem.update(ctx);
        auto t2 = Clock::now();

        // Update state machines after scripts mutate parameters/triggers.
        stateMachineSystem.update(scene, dt, &scriptEngine);

        // Consume dirty local transforms, step physics, and write back results.
        physicsSystem.update(ctx);
        auto t3 = Clock::now();

        // Resolve the final pose after all simulation writes. Doing this earlier
        // clears dirty flags before physics and leaves rendering one frame behind.
        transformSystem.update(ctx);
        componentCameraActive=CameraSystem::update(scene.getRegistry(),camera);
        if (sceneViewOverride && fitPreview) {
            EntityPreviewSystem::frameCamera(scene,editorCamera,static_cast<float>(hostWidth)/hostHeight);
            fitPreview=false;
        }
        auto tTransform = Clock::now();

        // === RENDER PHASE ===
        auto& displayCamera=sceneViewOverride ? editorCamera : camera;
        renderer.setCamera(&displayCamera);
        configureRenderViewport();
        Context renderCtx{scene,scene.getRegistry(),scene.getEventBus(),globalBus_,renderer,displayCamera,physicsWorld.get(),dt};
        renderer.beginFrame();

        // Submit sprite renders for all entities with transforms.
        renderSystem.update(renderCtx);
        if (sceneViewOverride) EntityPreviewSystem::submitOverlay(scene,renderer,editorCamera);
        sle::ui::UIFrameContext uiCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, displayCamera, scriptEngine, dt};
        uiSystem.update(uiCtx);
        auto t4 = Clock::now();

        renderer.endFrame();
        auto t5 = Clock::now();
        return {Ms(tTransform - t3).count(), Ms(t2 - t1).count(),
            Ms(t3 - t2).count(), Ms(t4 - tTransform).count(), Ms(t5 - t4).count()};
    }

    Result<bool> Runtime::initServices()
    {
        auto rendererResult = renderer.init();
        if (!rendererResult.ok())
            return Result<bool>::error(rendererResult.error());
        renderer.setClearColor(config.backgroundColor);

        defaultQuadShader = Resources::create<Shader>(
            "default_quad_shader",
            "assets/shaders/quad.vert",
            "assets/shaders/quad.frag");
        if (!defaultQuadShader)
        {
            return Result<bool>::error("Failed to load default quad shader resource");
        }

        defaultDebugShader = Resources::create<Shader>(
            "default_debug_shader",
            "assets/shaders/debug.vert",
            "assets/shaders/debug.frag");
        if (!defaultDebugShader)
        {
            return Result<bool>::error("Failed to load default debug shader resource");
        }

        renderSystem.setDefaultShaderID(defaultQuadShader->getID());
        renderSystem.setDefaultTextureID(0);
        renderSystem.setPhysicsDebugEnabled(false);
        renderer.setDebugShaderID(defaultDebugShader->getID());

        if (!scriptEngine.init(&scriptApi))
        {
            return Result<bool>::error("Failed to initialize ScriptEngine");
        }
        scriptSystem.setScriptRuntime(&scriptEngine);

        auto uiResult = uiSystem.init(&scriptEngine);
        if (!uiResult.ok())
        {
            return Result<bool>::error(uiResult.error());
        }
        uiSystem.setDefaultShaderID(defaultQuadShader->getID());

        {
            sle::ui::UIDocumentDescriptor descriptor;
            descriptor.layoutAsset = resolveAssetPath("assets/ui/fps_hud.xml");
            descriptor.fontAsset = resolveAssetPath("assets/fonts/Roboto-Regular.ttf");
            descriptor.spaceMode = sle::components::UISpaceMode::Screen;
            descriptor.visible = false;
            descriptor.layer = 1000;
            uiSystem.createDocument(std::move(descriptor));
            debugFpsDocumentIndex = uiSystem.getDocuments().size() - 1;
        }

        Input::init(embedded ? nullptr : window.getNative());

        renderer.setCamera(&camera);

        // Initialize physics world with default gravity
        physicsWorld = std::make_unique<sle::physics::PhysicsWorld>(glm::vec2(0.0f, -9.81f));
        physicsWorld->setFixedTimestep(1.0f / 120.0f);

        // Audio init is best-effort; failure is non-fatal (headless CI, no audio device, etc.)
        if (initializeAudioOnInit)
        {
            audioAttempted = true;
            audioSystem.init();
        }
        initialized = true;

        return Result<bool>::success(true);
    }

    void Runtime::run()
    {
        (void)runMainLoop(false, 0);
    }

    std::size_t Runtime::runForFrames(std::size_t maxFrames)
    {
        return runMainLoop(true, maxFrames);
    }

    std::size_t Runtime::runMainLoop(bool bounded, std::size_t maxFrames)
    {
        if (embedded || !initialized) return 0;
        using Clock = std::chrono::high_resolution_clock;
        using Ms = std::chrono::duration<double, std::milli>;

        float fpsLogAccumulator = 0.0f;
        int fpsFrameCount = 0;
        double transformMsAccum = 0.0;
        double scriptMsAccum = 0.0;
        double physicsMsAccum = 0.0;
        double renderSubmitMsAccum = 0.0;
        double renderFlushMsAccum = 0.0;
        double swapMsAccum = 0.0;

        std::size_t executedFrames = 0;

        while (!window.shouldClose() && (!bounded || executedFrames < maxFrames))
        {
            processPendingSceneSwitch();

            Input::update();

            timer.tick();

            float dt = timer.getDeltaTime();
            fpsLogAccumulator += dt;
            ++fpsFrameCount;
            window.pollEvents();

            if (window.consumeResize() && !componentCameraActive)
            {
                camera.setViewport(
                    static_cast<float>(window.getWidth()),
                    static_cast<float>(window.getHeight()));
            }

            handleDebugInput();

            if (Input::isKeyDown(GLFW_KEY_ESCAPE))
                break;

            hostWidth=window.getWidth(); hostHeight=window.getHeight();
            const auto timings = advanceAndRender(dt);
            auto t5 = Clock::now();

            window.swapBuffers();
            auto t6 = Clock::now();

            transformMsAccum += timings.transform;
            scriptMsAccum += timings.script;
            physicsMsAccum += timings.physics;
            renderSubmitMsAccum += timings.renderSubmit;
            renderFlushMsAccum += timings.renderFlush;
            swapMsAccum += Ms(t6 - t5).count();

            if (fpsLogAccumulator >= 1.0f)
            {
                const float fps = static_cast<float>(fpsFrameCount) / fpsLogAccumulator;
                const double frameCount = static_cast<double>(fpsFrameCount);
                sle::core::Log::info(
                    "FPS: {:.2f} | avg ms: transform {:.3f}, script {:.3f}, physics {:.3f}, render-submit {:.3f}, render-flush {:.3f}, swap {:.3f}",
                    fps,
                    transformMsAccum / frameCount,
                    scriptMsAccum / frameCount,
                    physicsMsAccum / frameCount,
                    renderSubmitMsAccum / frameCount,
                    renderFlushMsAccum / frameCount,
                    swapMsAccum / frameCount);
                fpsLogAccumulator = 0.0f;
                fpsFrameCount = 0;
                transformMsAccum = 0.0;
                scriptMsAccum = 0.0;
                physicsMsAccum = 0.0;
                renderSubmitMsAccum = 0.0;
                renderFlushMsAccum = 0.0;
                swapMsAccum = 0.0;
            }

            ++executedFrames;
        }

        return executedFrames;
    }

} // namespace sle
