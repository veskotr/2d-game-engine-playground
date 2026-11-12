#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/events/SceneLifecycleEvents.hpp>
#include <iostream>
#include <cmath>
using Json=nlohmann::json;
int main() {
    if(!glfwInit()) return 125;
    glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
    glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
    auto* host=glfwCreateWindow(480,270,"Current scene play",nullptr,nullptr);
    if(!host) { glfwTerminate(); return 125; }
    glfwMakeContextCurrent(host);
    if(!gladLoadGL(glfwGetProcAddress)) return 1;
    Json document={{"entities",Json::array({
        {{"key","actor"},{"components",{{"Transform",{{"position",{12,34}},{"scale",{100,100}}}},
            {"Script",{{"scriptAsset","tests/data/scripts/embedded_runtime_update.lua"}}}}}},
        {{"key","camera"},{"parent","actor"},{"components",{{"Transform",{{"rotation",0.75}}},
            {"Camera2D",{{"viewSize",{80,40}},{"zoom",2}}}}}},
        {{"key","hud"},{"components",{{"UI",{{"layoutAsset","tests/data/ui/embedded_pause.xml"},
            {"behaviorAsset","tests/data/scripts/embedded_ui_behavior.lua"}}}}}},
        {{"key","body"},{"components",{{"Transform",Json::object()},
            {"RigidBody",{{"bodyType","Dynamic"},{"gravityScale",0},{"velocity",{60,0}}}}}}}
    })}};
    {
        sle::core::EngineConfig config; config.width=480; config.height=270;
        sle::Runtime runtime(config);
        if(!runtime.initEmbedded(false).ok() || !runtime.loadScenePreview(document).ok() ||
            !runtime.renderSceneView(480,270,0,false,false,true).ok()) return 1;
        const auto editorOrigin=runtime.getPreviewPosition();
        const auto authored=runtime.sceneTransforms();
        if(authored["actor"]["position"]["x"]!=12) return 1;
        if(!runtime.getScriptEngine().executeScriptAsset("tests/data/scripts/embedded_ui_probe.lua")) return 1;
        int loadedEvents=0;
        runtime.getGlobalBus().subscribe<sle::events::SceneLoadedEvent>([&](const auto& e){if(e.sceneName=="editor_play") ++loadedEvents;});
        if(!runtime.startScenePlay(document).ok()) return 1;
        sle::input::Input::update(); sle::input::Input::setKeyState(GLFW_KEY_D,true);
        if(!runtime.renderSceneView(480,270,1.0f/60,false,true).ok()) return 1;
        auto live=runtime.sceneTransforms();
        if(loadedEvents!=1 || live["actor"]["position"]["x"].get<float>()<=120 || live["body"]["position"]["x"].get<float>()<=0) {
            std::cerr<<"Current-scene lifecycle/scripts/physics did not advance"; return 1;
        }
        const auto gameOrigin=runtime.getCameraPosition(), gameSize=runtime.getCameraViewportSize();
        if(gameSize!=glm::vec2(40,20) || runtime.getPreviewPosition()!=editorOrigin) return 1;
        runtime.panEntityPreview({10,20}); runtime.zoomEntityPreview(0.5f);
        if(runtime.getCameraPosition()!=gameOrigin || runtime.getCameraViewportSize()!=gameSize) return 1;
        runtime.setEmbeddedPaused(true);
        if(!runtime.renderSceneView(480,270,0.5f,true,true).ok() || runtime.sceneTransforms()!=live) return 1;
        const auto viewport=runtime.sceneGameViewport();
        if(viewport[0]!=0 || viewport[1]!=15 || viewport[2]!=480 || viewport[3]!=240 || viewport[4]!=40 || viewport[5]!=20) return 1;
        runtime.setEmbeddedPaused(false); sle::input::Input::releaseAll();
        sle::input::Input::update();
        sle::input::Input::setCursorPosition(15,15);
        sle::input::Input::setMouseButtonState(GLFW_MOUSE_BUTTON_LEFT,true);
        if(!runtime.renderSceneView(480,270,1.0f/60,true,true).ok() ||
            runtime.sceneTransforms()["body"]["position"]["x"].get<float>()<=live["body"]["position"]["x"].get<float>() || glGetError()!=GL_NO_ERROR) return 1;
        if(!runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce")) {
            std::cerr<<"Rotated game camera broke screen UI input"; return 1;
        }
        unsigned char hudPixel[4]{},background[4]{};
        glReadPixels(180,75,1,1,GL_RGBA,GL_UNSIGNED_BYTE,hudPixel);
        glReadPixels(450,240,1,1,GL_RGBA,GL_UNSIGNED_BYTE,background);
        if(hudPixel[0]==background[0] && hudPixel[1]==background[1] && hudPixel[2]==background[2]) {
            std::cerr<<"Rotated game camera displaced screen UI"; return 1;
        }
    }
    // Stop is host recreation: the same authored snapshot is restored without gameplay initialization.
    {
        sle::core::EngineConfig config; config.width=480; config.height=270;
        sle::Runtime runtime(config);
        if(!runtime.initEmbedded(false).ok() || !runtime.loadScenePreview(document).ok() ||
            !runtime.renderSceneView(480,270,0,false,false,true).ok() ||
            runtime.sceneTransforms()["actor"]["position"]["x"]!=12 ||
            runtime.sceneTransforms()["body"]["position"]["x"]!=0) return 1;
    }
    glfwDestroyWindow(host); glfwTerminate(); return 0;
}
