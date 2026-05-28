#include <GLFW/glfw3.h>
#include <glm/glm.hpp>
#include <chrono>
#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/Context.hpp>
#include <sle/resources/Resources.hpp>

#include <algorithm>
#include <cmath>
#include <string>

namespace sle
{

    using namespace sle::core;
    using namespace sle::entity;
    using namespace sle::renderer;
    using namespace sle::input;

    Runtime::Runtime(const sle::core::EngineConfig &config)
        : config(config)
        , scriptApi(*this)
    {
    }

    bool Runtime::registerScene(const std::string& sceneName, SceneManager::SceneBuilder builder)
    {
        return sceneManager.registerScene(sceneName, std::move(builder));
    }

    bool Runtime::hasScene(const std::string& sceneName) const
    {
        return sceneManager.hasScene(sceneName);
    }

    Result<bool> Runtime::loadScene(const std::string& sceneName)
    {
        auto result = sceneManager.loadScene(sceneName, *this, scene);
        if (result.ok())
            Log::info("Loaded scene: {}", sceneName);

        return result;
    }

    Result<bool> Runtime::requestSceneSwitch(const std::string& sceneName)
    {
        return sceneManager.requestSceneSwitch(sceneName);
    }

    float Runtime::getDeltaTime() const
    {
        return timer.getDeltaTime();
    }

    glm::vec2 Runtime::getWindowSize() const
    {
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
        // Clear resources BEFORE destroying window (while GL context is still valid)
        Resources::clear();
        renderer.shutdown();
        window.destroy();
    }

    Result<bool> Runtime::init()
    {
        auto windowResult = window.create(config);
        if (!windowResult.ok())
            return Result<bool>::error(windowResult.error());

        camera.setViewport(
            static_cast<float>(window.getWidth()),
            static_cast<float>(window.getHeight()));

        auto rendererResult = renderer.init();
        if (!rendererResult.ok())
            return Result<bool>::error(rendererResult.error());

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
        scriptSystem.setScriptEngine(&scriptEngine);

        auto uiResult = uiSystem.init(&scriptEngine);
        if (!uiResult.ok())
        {
            return Result<bool>::error(uiResult.error());
        }
        uiSystem.setDefaultShaderID(defaultQuadShader->getID());

        Input::init(window.getNative());

        renderer.setCamera(&camera);

        // Initialize physics world with default gravity
        physicsWorld = std::make_unique<sle::physics::PhysicsWorld>(glm::vec2(0.0f, -9.81f));
        physicsWorld->setFixedTimestep(1.0f / 120.0f);

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
        using Clock = std::chrono::high_resolution_clock;
        using Ms = std::chrono::duration<double, std::milli>;

        float fpsLogAccumulator = 0.0f;
        int fpsFrameCount = 0;
        float fpsUiAccumulator = 0.0f;
        int fpsUiFrameCount = 0;
        double transformMsAccum = 0.0;
        double scriptMsAccum = 0.0;
        double physicsMsAccum = 0.0;
        double renderSubmitMsAccum = 0.0;
        double renderFlushMsAccum = 0.0;
        double swapMsAccum = 0.0;

        std::size_t executedFrames = 0;

        while (!window.shouldClose() && (!bounded || executedFrames < maxFrames))
        {
            auto switchResult = sceneManager.processPendingSwitch(*this, scene);
            if (!switchResult.ok())
            {
                Log::error("Failed to switch pending scene: {}", switchResult.error());
            }

            Input::update();

            timer.tick();

            float dt = timer.getDeltaTime();
            fpsLogAccumulator += dt;
            ++fpsFrameCount;
            fpsUiAccumulator += dt;
            ++fpsUiFrameCount;

            if (fpsUiAccumulator >= 1.0f)
            {
                const int fpsUi = static_cast<int>(std::lround(static_cast<float>(fpsUiFrameCount) / std::max(fpsUiAccumulator, 0.0001f)));
                uiSystem.setBinding("fpsText", std::to_string(fpsUi));
                fpsUiAccumulator = 0.0f;
                fpsUiFrameCount = 0;
            }

            window.pollEvents();

            if (window.consumeResize())
            {
                camera.setViewport(
                    static_cast<float>(window.getWidth()),
                    static_cast<float>(window.getHeight()));
            }

            if (Input::isKeyPressed(static_cast<int>(Input::Key::F3)))
            {
                const bool enabled = !renderSystem.isPhysicsDebugEnabled();
                renderSystem.setPhysicsDebugEnabled(enabled);
                Log::info("Physics debug rendering: {}", enabled ? "ON" : "OFF");
            }

            if (Input::isKeyDown(GLFW_KEY_ESCAPE))
                break;

            // Construct per-frame context with all system dependencies.
            Context ctx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, physicsWorld.get(), dt};

            // === FRAME START ===
            // Flush queued events from previous frame.
            ctx.eventBus.flushQueue();
            ctx.globalBus.flushQueue();

            // === LOGIC UPDATE PHASE ===
            // 1. Resolve world-space transforms (required by all other systems).
            auto t0 = Clock::now();
            transformSystem.update(ctx);
            auto t1 = Clock::now();

            // 2. Update scripted entities (placeholder for Lua integration).
            scriptSystem.update(ctx);
            auto t2 = Clock::now();

            // 2.5 Update state machines after scripts mutate parameters/triggers.
            stateMachineSystem.update(scene, dt, &scriptEngine);

            // 3. Step physics simulation and resolve collisions (placeholder for Box2D).
            physicsSystem.update(ctx);
            auto t3 = Clock::now();

            // === RENDER PHASE ===
            renderer.beginFrame();

            // 4. Submit sprite renders for all entities with transforms.
            renderSystem.update(ctx);
            sle::ui::UIFrameContext uiCtx{scene, scene.getRegistry(), scene.getEventBus(), globalBus_, renderer, camera, scriptEngine, dt};
            uiSystem.update(uiCtx);
            auto t4 = Clock::now();

            renderer.endFrame();
            auto t5 = Clock::now();

            window.swapBuffers();
            auto t6 = Clock::now();

            transformMsAccum += Ms(t1 - t0).count();
            scriptMsAccum += Ms(t2 - t1).count();
            physicsMsAccum += Ms(t3 - t2).count();
            renderSubmitMsAccum += Ms(t4 - t3).count();
            renderFlushMsAccum += Ms(t5 - t4).count();
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
