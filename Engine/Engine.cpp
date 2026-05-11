#include "Engine.hpp"
#include "core/Log.hpp"
#include "core/Resources.hpp"

Engine::Engine(const EngineConfig &config)
    : config(config)
{
}

Engine::~Engine()
{
    Resources::clear();
    renderer.shutdown();
    window.destroy();
}

Result<bool> Engine::init()
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
    renderer.setShader(Resources::create<Shader>(
                           "quad",
                           "assets/shaders/quad.vert",
                           "assets/shaders/quad.frag")
                           .get());

    return Result<bool>::success(true);
}

void Engine::run()
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

        renderer.beginFrame();

        QuadCommand quadCmd;
        quadCmd.position = glm::vec2(0.0f, 0.0f);
        quadCmd.size = glm::vec2(32.0f, 32.0f);
        renderer.submit(quadCmd);

        QuadCommand quadCmd1;
        quadCmd1.position = glm::vec2(64.0f, 64.0f);
        quadCmd1.size = glm::vec2(32.0f, 32.0f);
        renderer.submit(quadCmd1);

        renderer.endFrame();

        window.swapBuffers();
    }
}