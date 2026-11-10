#include <glad/gl.h>
#include <sle/platform/Window.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/Shader.hpp>
#include <sle/engine/Context.hpp>
#include <sle/engine/RenderSystem.hpp>
#include <sle/engine/ChunkActivationController.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scripting/ScriptEngine.hpp>
#include <sle/ui/UISystem.hpp>
#include <iostream>

int main()
{
    sle::core::EngineConfig config;
    config.width = config.height = 64;
    config.vsync = false;
    config.title = "Chunk visibility regression";
    sle::core::Window window;
    const auto init = window.create(config);
    if (!init.ok())
    {
        std::cerr << init.error() << "\n";
        return init.error().find("GLFW") != std::string::npos ? 125 : 1;
    }
    sle::renderer::Renderer renderer;
    if (!renderer.init().ok()) return 1;
    renderer.setClearColor({0.0f, 0.0f, 0.0f, 1.0f});
    sle::renderer::Shader shader;
    if (!shader.loadFromFiles("assets/shaders/quad.vert", "tests/data/shaders/chunk_visibility.frag")) return 1;
    sle::core::Camera2D camera(64.0f, 64.0f);
    renderer.setCamera(&camera);
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();
    const auto entity = scene.createEntity();
    registry.addComponent<sle::components::ChunkActivationComponent>(entity);
    auto& world = registry.addComponent<sle::components::WorldTransformComponent>(entity);
    world.position = {32.0f, 32.0f};
    world.scale = {40.0f, 40.0f};
    auto& sprite = registry.addComponent<sle::components::SpriteRenderer>(entity);
    sprite.color = {1.0f, 1.0f, 1.0f, 1.0f};
    sle::events::EventBus globalBus;
    sle::Context ctx{scene, registry, scene.getEventBus(), globalBus, renderer, camera, nullptr, 0.0f};
    sle::RenderSystem rendering;
    rendering.setDefaultShaderID(shader.getID());
    sle::ChunkOwnershipIndex index;
    index.rebuild(registry);
    sle::ChunkActivationController controller({.mode = sle::core::ChunkActivationMode::AutoToggle});
    sle::scripting::ScriptEngine scripts;
    sle::ui::UISystem ui;
    ui.init(nullptr);
    ui.setDefaultShaderID(shader.getID());
    sle::ui::UIDocumentDescriptor descriptor;
    descriptor.ownerEntityId = entity.getID();
    auto& document = ui.createDocument(descriptor);
    sle::ui::UIElement panel;
    panel.type = "Panel";
    panel.attributes = {{"width", "64"}, {"height", "64"}, {"color", "#FFFFFF"}};
    document.setLayoutRoot(std::move(panel));
    sle::ui::UIFrameContext uiCtx{scene, registry, scene.getEventBus(), globalBus, renderer, camera, scripts, 0.0f};
    bool passed = true;
    auto draw = [&](bool uiPass, bool expected, const char* message)
    {
        renderer.beginFrame();
        if (uiPass) ui.update(uiCtx); else rendering.update(ctx);
        renderer.endFrame();
        unsigned char pixel[4]{};
        glReadBuffer(GL_BACK);
        glReadPixels(32, 32, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        if ((pixel[0] > 200) != expected || glGetError() != GL_NO_ERROR)
        {
            std::cerr << message << " (red=" << static_cast<int>(pixel[0]) << ")\n";
            passed = false;
        }
    };
    for (const bool uiPass : {false, true})
    {
        controller.applyActiveChunks({{0, 0}}, index, registry);
        draw(uiPass, true, "Active chunk must draw through the real consumer");
        controller.applyActiveChunks({}, index, registry);
        draw(uiPass, false, "Suspended chunk must not draw even while gameplay visibility stays true");
        controller.applyActiveChunks({{0, 0}}, index, registry);
        draw(uiPass, true, "Reactivated chunk must draw again");
        if (uiPass) document.setVisible(false); else sprite.visible = false;
        controller.applyActiveChunks({}, index, registry);
        controller.applyActiveChunks({{0, 0}}, index, registry);
        draw(uiPass, false, "Gameplay-hidden content must stay hidden after chunk reactivation");
    }
    ui.shutdown();
    renderer.shutdown();
    return passed ? 0 : 1;
}
