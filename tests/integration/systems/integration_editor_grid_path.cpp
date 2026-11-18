#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <sle/engine/Runtime.hpp>
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <vector>

// The editor grid setting changes only editor drawing: Scene view pixels follow it, while the
// Game view pixels, the game camera, the scene state and invalid settings are left untouched.
namespace {
void require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
}
using Json = nlohmann::json;
std::vector<unsigned char> frame(sle::Runtime& runtime, bool gameView, bool fit) {
    require(runtime.drawSceneView(480, 270, gameView, fit).ok(), "draw view");
    std::vector<unsigned char> pixels(480 * 270 * 4);
    glReadPixels(0, 0, 480, 270, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
    return pixels;
}
void exercise() {
    sle::core::EngineConfig config;
    config.width = 480; config.height = 270;
    sle::Runtime runtime(config);
    require(runtime.initEmbedded(false).ok(), "initialize embedded runtime");
    const Json document = {{"entities", Json::array({
        {{"key", "camera"}, {"components", {{"Transform", Json::object()},
            {"Camera2D", {{"viewSize", {48, 27}}, {"zoom", 1}}}}}},
        {{"key", "box"}, {"components", {{"Transform", {{"position", {3, 2}}}},
            {"BoxCollider", {{"size", {2, 2}}}}}}}
    })}};
    require(runtime.startScenePlay(document).ok(), "start scene");
    require(runtime.advanceEmbeddedFrame(0).ok(), "advance once");

    GLuint target = 0, texture = 0;
    glGenFramebuffers(1, &target);
    glGenTextures(1, &texture);
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 480, 270, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindFramebuffer(GL_FRAMEBUFFER, target);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
    require(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "host FBO complete");

    require(runtime.getEditorGrid() == sle::EditorGrid{}, "automatic grid by default");
    const auto game = frame(runtime, true, false);
    const auto scene = frame(runtime, false, true);
    const auto transforms = runtime.sceneTransforms();
    const auto cameraPosition = runtime.getCameraPosition();
    const auto cameraSize = runtime.getCameraViewportSize();
    const auto preview = runtime.getPreviewPosition();
    const auto previewSize = runtime.getPreviewViewportSize();

    for (const sle::EditorGrid invalid : {sle::EditorGrid{-1.0f, 10}, sle::EditorGrid{std::numeric_limits<float>::quiet_NaN(), 10},
        sle::EditorGrid{1.0f, -2}, sle::EditorGrid{1.0f, 5000}})
        require(!runtime.setEditorGrid(invalid) && runtime.getEditorGrid() == sle::EditorGrid{}, "invalid grid rejected");
    require(runtime.setEditorGrid({0.5f, 4}), "valid grid accepted");
    require(runtime.getEditorGrid() == sle::EditorGrid{0.5f, 4}, "grid stored");

    require(frame(runtime, true, false) == game, "Game view pixels ignore the editor grid");
    require(frame(runtime, false, false) != scene, "Scene view grid follows the setting");
    require(runtime.sceneTransforms() == transforms, "grid leaves scene transforms");
    require(runtime.getCameraPosition() == cameraPosition && runtime.getCameraViewportSize() == cameraSize, "grid leaves the game camera");
    require(runtime.getPreviewPosition() == preview && runtime.getPreviewViewportSize() == previewSize, "grid leaves the editor camera");

    require(runtime.setEditorGrid({}), "automatic grid restored");
    require(frame(runtime, false, false) == scene, "automatic grid draws as before");
    require(frame(runtime, true, false) == game, "Game view unchanged after restore");

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glDeleteFramebuffers(1, &target);
    glDeleteTextures(1, &texture);
    require(glGetError() == GL_NO_ERROR, "no GL errors");
}
}
int main() {
    if (!glfwInit()) return 125;
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    auto* host = glfwCreateWindow(480, 270, "Editor grid regression", nullptr, nullptr);
    if (!host) { glfwTerminate(); return 125; }
    glfwMakeContextCurrent(host);
    int result = 0;
    try { require(gladLoadGL(glfwGetProcAddress) != 0, "GLAD initialization"); exercise(); }
    catch (const std::exception& error) { std::cerr << error.what() << '\n'; result = 1; }
    glfwDestroyWindow(host);
    glfwTerminate();
    return result;
}
