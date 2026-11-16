#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/ChunkMapComponent.hpp>
#include <sle/events/SceneLifecycleEvents.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>

namespace {
void require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
}
using Json = nlohmann::json;
void exercise() {
    sle::core::EngineConfig config;
    config.width = 480; config.height = 270;
    config.chunkActivation.mode = sle::core::ChunkActivationMode::AutoToggle;
    sle::Runtime runtime(config);
    require(runtime.initEmbedded(false).ok(), "initialize embedded runtime");
    const Json document = {{"entities", Json::array({
        {{"key", "actor"}, {"components", {{"Transform", Json::object()},
            {"Script", {{"scriptAsset", "tests/data/scripts/embedded_multiview.lua"}}}}}},
        {{"key", "body"}, {"components", {{"Transform", Json::object()},
            {"RigidBody", {{"bodyType", "Dynamic"}, {"gravityScale", 0}, {"velocity", {60, 0}}}}}}},
        {{"key", "camera"}, {"components", {{"Transform", {{"rotation", 0.75}}},
            {"Camera2D", {{"viewSize", {80, 40}}, {"zoom", 2}}}}}},
        {{"key", "hud"}, {"components", {{"UI", {{"layoutAsset", "tests/data/ui/embedded_pause.xml"},
            {"behaviorAsset", "tests/data/scripts/embedded_ui_behavior.lua"}}}}}}
    })}};
    require(runtime.getScriptEngine().executeScriptAsset("tests/data/scripts/embedded_ui_probe.lua"), "UI probe");
    require(runtime.startScenePlay(document).ok(), "start scene");
    int loaded = 0;
    runtime.getGlobalBus().subscribe<sle::events::SceneLoadedEvent>([&](const auto&) { ++loaded; });
    sle::input::Input::update();
    sle::input::Input::setCursorPosition(15, 15);
    sle::input::Input::setMouseButtonState(GLFW_MOUSE_BUTTON_LEFT, true);
    glClearColor(1, 0, 0, 1);
    glClear(GL_COLOR_BUFFER_BIT);
    require(runtime.advanceEmbeddedFrame(1.0f / 60).ok(), "advance without a visible view");
    unsigned char untouched[4]{};
    glReadPixels(15, 15, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, untouched);
    require(untouched[0] == 255 && untouched[1] == 0 && untouched[2] == 0, "advance must not draw or clear host target");
    require(loaded == 1, "lifecycle event advances once");
    require(runtime.getScriptEngine().callGlobalBoolFunction("MultiviewUpdatedOnce"), "script advances once");
    require(runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiLoadedOnce") &&
        runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce"), "UI updates at advance using game camera");
    const auto snapshot = runtime.sceneTransforms();
    require(snapshot["body"]["position"]["x"].get<float>() > 0, "physics advances");
    const auto camera = runtime.getCameraPosition();
    const auto size = runtime.getCameraViewportSize();
    require(size == glm::vec2(40, 20), "game camera selected before drawing");
    runtime.setPhysicsDebugEnabled(true);
    glViewport(3, 4, 123, 124);
    for (bool gameFirst : {false, true}) {
        require(runtime.drawSceneView(gameFirst ? 480 : 200, gameFirst ? 270 : 700, gameFirst, true).ok(), "first draw");
        require(runtime.drawSceneView(gameFirst ? 200 : 480, gameFirst ? 700 : 270, !gameFirst, false).ok(), "second draw");
        require(runtime.sceneTransforms() == snapshot, "draw must not move transforms");
        require(runtime.getCameraPosition() == camera && runtime.getCameraViewportSize() == size, "independent game camera");
        require(runtime.sceneGameViewport() == std::array<float,6>{0,15,480,240,40,20}, "Scene size must not overwrite Game metrics");
        require(runtime.isPhysicsDebugEnabled(), "Game debug override restored");
        GLint viewport[4]{};
        glGetIntegerv(GL_VIEWPORT, viewport);
        require(viewport[0] == 3 && viewport[1] == 4 && viewport[2] == 123 && viewport[3] == 124, "host viewport restored");
    }
    // Rendering uses the host's FBO, preserves its bindings, and draws the same
    // rotated-camera UI as the legacy single-view path (including letterboxing).
    GLuint target = 0, texture = 0;
    glGenFramebuffers(1, &target);
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 480, 270, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindFramebuffer(GL_FRAMEBUFFER, target);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "host FBO complete");
    require(runtime.drawSceneView(480, 270, true, false).ok(), "Game draws into host FBO");
    GLint drawTarget = 0, readTarget = 0;
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &drawTarget);
    glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &readTarget);
    require(drawTarget == static_cast<GLint>(target) && readTarget == static_cast<GLint>(target), "host framebuffer bindings preserved");
    unsigned char hudPixel[4]{}, background[4]{}, bar[4]{};
    glReadPixels(180, 75, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, hudPixel);
    glReadPixels(450, 240, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, background);
    glReadPixels(180, 5, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, bar);
    require(hudPixel[0] != background[0] || hudPixel[1] != background[1] || hudPixel[2] != background[2], "Game camera/UI pixels rendered");
    require(bar[0] == background[0] && bar[1] == background[1] && bar[2] == background[2], "letterbox excludes UI geometry");
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &target);
    glDeleteTextures(1, &texture);
    require(runtime.getScriptEngine().callGlobalBoolFunction("MultiviewUpdatedOnce") &&
        runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce"), "draws do not dispatch scripts/UI");
    require(loaded == 1, "draws do not flush events");
    for (float dt : {-1.0f, std::numeric_limits<float>::infinity(), std::numeric_limits<float>::quiet_NaN()})
        require(!runtime.advanceEmbeddedFrame(dt).ok(), "invalid dt rejected");
    require(!runtime.drawSceneView(0, 270, true, true).ok() &&
        !runtime.drawSceneView(480, -1, false, false).ok(), "invalid sizes rejected");
    require(!runtime.tickEmbeddedFrame(0, 270, 1).ok() &&
        !runtime.renderSceneView(480, 270, -1, true, true).ok(), "invalid wrappers do not advance");
    require(runtime.sceneTransforms() == snapshot, "invalid calls preserve state");
    sle::input::Input::update();
    sle::input::Input::releaseAll();
    require(runtime.renderSceneView(480, 270, 1.0f / 60, false, true).ok(), "legacy Scene wrapper");
    require(runtime.getScriptEngine().callGlobalBoolFunction("MultiviewUpdatedTwice"), "wrapper advances exactly once");
    const auto paused = runtime.sceneTransforms();
    runtime.setEmbeddedPaused(true);
    runtime.registerSceneFromData("next", {{"entities", Json::array()}}, "next.json");
    require(runtime.requestSceneSwitch("next").ok(), "queue switch");
    sle::input::Input::setMouseButtonState(GLFW_MOUSE_BUTTON_LEFT, true);
    require(runtime.advanceEmbeddedFrame(0.5f).ok() && runtime.drawSceneView(480, 270, true, false).ok() &&
        runtime.drawSceneView(200, 700, false, false).ok(), "paused redraw");
    require(runtime.sceneTransforms() == paused && runtime.getCurrentSceneName() == "editor_play", "pause defers switch and simulation");
    require(runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce"), "paused UI does not click");
    runtime.setEmbeddedPaused(false);
    require(runtime.drawSceneView(480, 270, true, false).ok() && runtime.getCurrentSceneName() == "editor_play", "draw defers switch");
    require(runtime.advanceEmbeddedFrame(0).ok() && runtime.getCurrentSceneName() == "next" && loaded == 2, "advance commits switch once");
    require(runtime.drawSceneView(480, 270, true, false).ok() && runtime.drawSceneView(200, 700, false, false).ok() && loaded == 2, "draws preserve committed scene");
    require(runtime.tickEmbeddedFrame(480, 270, 0).ok(), "legacy tick wrapper");
    sle::entity::Entity resident;
    runtime.registerScene("chunks", [&](sle::Runtime& rt) {
        auto& scene = rt.getScene();
        auto& registry = scene.getRegistry();
        const auto map = scene.createEntity();
        auto& grid = registry.addComponent<sle::ChunkMapComponent>(map);
        grid.gridConfig.activeRadiusX = grid.gridConfig.activeRadiusY = 0;
        resident = scene.createEntity();
        registry.addComponent<sle::components::ChunkActivationComponent>(resident);
    });
    require(runtime.loadScene("chunks").ok(), "load chunk scene");
    runtime.setCameraPosition({10, 10});
    require(runtime.advanceEmbeddedFrame(0).ok(), "advance chunk gates");
    const auto suspended = [&] {
        return runtime.getScene().getRegistry().getComponent<sle::components::ChunkActivationComponent>(resident)->suspendedSubsystems;
    };
    require(suspended() == 0, "origin resident active");
    runtime.setCameraPosition({10000, 10000});
    runtime.panEntityPreview({-5000, 2000});
    require(runtime.drawSceneView(50, 900, false, false).ok() &&
        runtime.drawSceneView(480, 270, true, false).ok() && suspended() == 0, "draw order/cameras cannot update chunk gates");
    require(runtime.advanceEmbeddedFrame(0).ok() && suspended() != 0, "only advance updates gameplay chunk gates");
    runtime.setPhysicsDebugEnabled(false);
    sle::input::Input::update();
    sle::input::Input::setKeyState(GLFW_KEY_F3, true);
    require(runtime.renderSceneView(480, 270, 0, true, true).ok() && !runtime.isPhysicsDebugEnabled(),
        "legacy Game wrapper ignores editor debug shortcut");
    sle::input::Input::update();
    sle::input::Input::releaseAll();
    sle::input::Input::setKeyState(GLFW_KEY_F3, true);
    require(runtime.advanceEmbeddedFrame(0, false).ok() && !runtime.isPhysicsDebugEnabled(),
        "separate Game advance ignores editor debug shortcut");
    require(runtime.drawSceneView(480, 270, true, false).ok() && !runtime.isPhysicsDebugEnabled(),
        "Game draw preserves suppressed debug state");
    sle::input::Input::update();
    sle::input::Input::releaseAll();
    sle::input::Input::setKeyState(GLFW_KEY_F3, true);
    require(runtime.advanceEmbeddedFrame(0).ok() && runtime.isPhysicsDebugEnabled(),
        "Sandbox advance retains editor debug shortcut");
    sle::input::Input::update();
    sle::input::Input::releaseAll();
    require(glGetError() == GL_NO_ERROR, "no GL errors");
}
}
int main() {
    if (!glfwInit()) return 125;
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    auto* host = glfwCreateWindow(480, 270, "Embedded multiview regression", nullptr, nullptr);
    if (!host) { glfwTerminate(); return 125; }
    glfwMakeContextCurrent(host);
    int result = 0;
    try { require(gladLoadGL(glfwGetProcAddress) != 0, "GLAD initialization"); exercise(); }
    catch (const std::exception& error) { std::cerr << error.what() << '\n'; result = 1; }
    glfwDestroyWindow(host);
    glfwTerminate();
    return result;
}
