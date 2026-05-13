#include <sle/engine/Engine.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <glm/glm.hpp>
#include <GLFW/glfw3.h>

namespace sle {

using namespace sle::core;
using namespace sle::entity;
using namespace sle::renderer;
using namespace sle::input;

Engine::Engine(const sle::core::EngineConfig &config)
    : config(config)
{
}

Engine::~Engine()
{
    scene.destroy();
    // Clear resources BEFORE destroying window (while GL context is still valid)
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
    auto shader = Resources::create<Shader>(
        "quad",
        "assets/shaders/quad.vert",
        "assets/shaders/quad.frag");

    if (!shader)
        return Result<bool>::error("Failed to load quad shader");

    renderer.setShader(shader);
    scene.init();

    return Result<bool>::success(true);
}

void Engine::run()
{
    auto texture = Resources::create<Texture>("player", "assets/textures/tile2.png");
  
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
        quadCmd.texture = texture ? texture->getID() : 0;
        renderer.submit(quadCmd);

        QuadCommand quadCmd1;
        quadCmd1.position = glm::vec2(64.0f, 64.0f);
        quadCmd1.size = glm::vec2(32.0f, 32.0f);
        quadCmd1.texture = texture ? texture->getID() : 0;
        renderer.submit(quadCmd1);

        sle::core::Log::info("FPS: {:.2f}", 1.0f / dt);

        scene.view<sle::components::Transform, sle::components::SpriteRenderer>(
            [this](sle::entity::Entity, sle::components::Transform& transform, sle::components::SpriteRenderer& sprite)
            {
                sle::renderer::QuadCommand cmd{};
                cmd.position = transform.position;
                cmd.size = sprite.size * transform.scale;
                cmd.color = sprite.color;
                cmd.texture = sprite.textureId;
                renderer.submit(cmd);
            });

        renderer.endFrame();

        window.swapBuffers();
    }
}

} // namespace sle
