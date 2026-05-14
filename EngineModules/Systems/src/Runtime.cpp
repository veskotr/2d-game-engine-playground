#include <GLFW/glfw3.h>
#include <glm/glm.hpp>
#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/Context.hpp>
#include <sle/resources/Resources.hpp>

namespace sle
{

    using namespace sle::core;
    using namespace sle::entity;
    using namespace sle::renderer;
    using namespace sle::input;

    Runtime::Runtime(const sle::core::EngineConfig &config)
        : config(config)
    {
    }

    Runtime::~Runtime()
    {
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

        Input::init(window.getNative());

        renderer.setCamera(&camera);

        return Result<bool>::success(true);
    }

    void Runtime::run()
    {
        while (!window.shouldClose())
        {
            Input::update();

            timer.tick();

            float dt = timer.getDeltaTime();

            window.pollEvents();

            if (window.consumeResize())
            {
                camera.setViewport(
                    static_cast<float>(window.getWidth()),
                    static_cast<float>(window.getHeight()));
            }

            if (Input::isKeyDown(GLFW_KEY_ESCAPE))
                break;

            // camera control (temporary here, later move to game layer)
            glm::vec2 move(0.0f);

            if (Input::isKeyDown(GLFW_KEY_W))
                move.y += 1.0f;
            if (Input::isKeyDown(GLFW_KEY_S))
                move.y -= 1.0f;
            if (Input::isKeyDown(GLFW_KEY_A))
                move.x -= 1.0f;
            if (Input::isKeyDown(GLFW_KEY_D))
                move.x += 1.0f;

            camera.move(move * 32.0f * dt);

            // Construct per-frame context with all system dependencies.
            Context ctx{scene, scene.getRegistry(), scene.getEventBus(), renderer, dt};

            // === FRAME START ===
            // Clear accumulated events from previous frame.
            ctx.eventBus.clear();

            // === LOGIC UPDATE PHASE ===
            // 1. Resolve world-space transforms (required by all other systems).
            transformSystem.update(ctx);

            // 2. Update scripted entities (placeholder for Lua integration).
            scriptSystem.update(ctx);

            // 3. Step physics simulation and resolve collisions (placeholder for Box2D).
            physicsSystem.update(ctx);

            // === RENDER PHASE ===
            renderer.beginFrame();

            // 4. Submit sprite renders for all entities with transforms.
            renderSystem.update(ctx);

            sle::core::Log::info("FPS: {:.2f}", 1.0f / dt);

            renderer.endFrame();

            window.swapBuffers();
        }
    }

} // namespace sle
