#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/UIComponent.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <cmath>
#include <iostream>
#include <limits>

int main()
{
    if (!glfwInit()) return 125;
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    auto* host = glfwCreateWindow(480, 270, "Embedded play regression", nullptr, nullptr);
    if (!host) { glfwTerminate(); return 125; }
    glfwMakeContextCurrent(host);
    if (!gladLoadGL(glfwGetProcAddress)) return 1;
    bool passed = true;
    {
        sle::core::EngineConfig config;
        config.width = 480;
        config.height = 270;
        sle::Runtime runtime(config);
        const auto init = runtime.initEmbedded();
        if (!init.ok()) { std::cerr << init.error(); return 1; }
        if (!runtime.getScriptEngine().executeScriptAsset("tests/data/scripts/embedded_ui_probe.lua")) return 1;
        auto& scene = runtime.getScene();
        auto& registry = scene.getRegistry();
        const auto scripted = scene.createEntity();
        registry.addComponent<sle::components::TransformComponent>(scripted);
        registry.addComponent<sle::components::ScriptComponent>(scripted).scriptAsset =
            "tests/data/scripts/embedded_runtime_update.lua";
        const auto bodyEntity = scene.createEntity();
        registry.addComponent<sle::components::TransformComponent>(bodyEntity);
        auto& body = registry.addComponent<sle::components::RigidBodyComponent>(bodyEntity);
        body.bodyType = sle::components::BodyType::Dynamic;
        body.gravityScale = 0;
        body.velocity = {60, 0};
        const auto animated = scene.createEntity();
        registry.addComponent<sle::components::TransformComponent>(animated);
        auto& animator = registry.addComponent<sle::components::AnimatorComponent>(animated);
        animator.clip = std::make_shared<sle::components::AnimationClipDefinition>();
        animator.clip->lengthSeconds = 1.0f;
        sle::components::AnimationFloatTrack track;
        track.bindingPath = "self.Transform.position.x";
        track.keyframes.push_back({0.0f, 0.0f});
        track.keyframes.push_back({1.0f, 60.0f});
        animator.clip->floatTracks.push_back(track);
        const auto uiEntity = scene.createEntity();
        auto& ui = registry.addComponent<sle::components::UIComponent>(uiEntity);
        ui.layoutAsset = "tests/data/ui/embedded_pause.xml";
        ui.behaviorAsset = "tests/data/scripts/embedded_ui_behavior.lua";
        runtime.setEmbeddedPaused(true);
        if (!runtime.tickEmbeddedFrame(480, 270, 0.5f).ok()) passed = false;
        unsigned char uiPixel[4]{};
        glReadPixels(15, 255, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, uiPixel);
        unsigned char emptyPixel[4]{};
        glReadPixels(400, 255, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, emptyPixel);
        // Test document submission independently of the renderer's existing
        // untextured-panel color behavior.
        if ((uiPixel[0] == emptyPixel[0] && uiPixel[1] == emptyPixel[1] && uiPixel[2] == emptyPixel[2]) ||
            !runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiUnloaded"))
        { std::cerr << "Stopped UI pixel=" << static_cast<int>(uiPixel[0]) << "," << static_cast<int>(uiPixel[1]) << "," << static_cast<int>(uiPixel[2]) << " unloaded=" << runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiUnloaded") << "\n"; passed = false; }
        runtime.setEmbeddedPaused(false);
        sle::input::Input::update();
        sle::input::Input::setCursorPosition(15, 15);
        sle::input::Input::setMouseButtonState(GLFW_MOUSE_BUTTON_LEFT, true);
        sle::input::Input::setKeyState(GLFW_KEY_D, true);
        const auto step = runtime.tickEmbeddedFrame(480, 270, 1.0f / 60.0f);
        if (!step.ok()) { std::cerr << step.error(); passed = false; }
        if (!runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiLoadedOnce") ||
            !runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce"))
        { std::cerr << "Running UI behavior/click did not execute\n"; passed = false; }
        const auto* scriptedWorld = registry.getComponent<sle::components::WorldTransformComponent>(scripted);
        const auto* animatedWorld = registry.getComponent<sle::components::WorldTransformComponent>(animated);
        const auto* bodyWorld = registry.getComponent<sle::components::WorldTransformComponent>(bodyEntity);
        if (!scriptedWorld || std::fabs(scriptedWorld->position.x - (120.0f + 1.0f / 60.0f)) > 0.001f)
        { std::cerr << "Lua/input did not reach rendered world pose\n"; passed = false; }
        if (!animatedWorld || animatedWorld->position.x <= 0 || !bodyWorld || bodyWorld->position.x <= 0)
        { std::cerr << "Animation/physics did not advance\n"; passed = false; }
        if (std::fabs(runtime.getDeltaTime() - 1.0f / 60.0f) > 0.0001f)
        { std::cerr << "Host dt was not published\n"; passed = false; }
        sle::input::Input::update();
        if (sle::input::Input::isKeyPressed(GLFW_KEY_D) || !sle::input::Input::isKeyDown(GLFW_KEY_D)) passed = false;
        sle::input::Input::releaseAll();
        if (sle::input::Input::isKeyDown(GLFW_KEY_D) || !sle::input::Input::isKeyReleased(GLFW_KEY_D)) passed = false;
        if (runtime.tickEmbeddedFrame(480, 270, std::numeric_limits<float>::quiet_NaN()).ok()) passed = false;
        const auto frozenPosition = registry.getComponent<sle::components::TransformComponent>(bodyEntity)->getPosition();
        const auto frozenTime = registry.getComponent<sle::components::AnimatorComponent>(animated)->timeSeconds;
        runtime.setEmbeddedPaused(true);
        sle::input::Input::setMouseButtonState(GLFW_MOUSE_BUTTON_LEFT, true);
        if (!runtime.tickEmbeddedFrame(480, 270, 0.5f).ok()) passed = false;
        if (!runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce"))
        { std::cerr << "Paused UI dispatched a click\n"; passed = false; }
        if (registry.getComponent<sle::components::TransformComponent>(bodyEntity)->getPosition() != frozenPosition ||
            registry.getComponent<sle::components::AnimatorComponent>(animated)->timeSeconds != frozenTime)
        { std::cerr << "Paused systems advanced\n"; passed = false; }
        runtime.setEmbeddedPaused(false);
        if (!runtime.tickEmbeddedFrame(480, 270, 1.0f / 60.0f).ok() ||
            registry.getComponent<sle::components::TransformComponent>(bodyEntity)->getPosition().x <= frozenPosition.x)
        { std::cerr << "Resume did not advance physics\n"; passed = false; }
        runtime.registerSceneFromFile("sandbox", "assets/scenes/npc_zone_demo.json");
        runtime.requestSceneSwitch("sandbox");
        if (!runtime.tickEmbeddedFrame(480, 270, 1.0f / 60.0f).ok() || runtime.getCurrentSceneName() != "sandbox") passed = false;
        if (glfwGetCurrentContext() != host || glGetError() != GL_NO_ERROR) passed = false;
    }
    glfwDestroyWindow(host);
    glfwTerminate();
    return passed ? 0 : 1;
}
