#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/Camera2DComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/UIComponent.hpp>
#include <cmath>
#include <iostream>

struct HostClick { bool bar = false; bool injected = false; };
int main()
{
    sle::core::EngineConfig config;
    config.width=960; config.height=600; config.vsync=false;
    config.title="Standalone camera input regression";
    sle::Runtime runtime(config);
    const auto initialized=runtime.init();
    if (!initialized.ok()) {
        if(initialized.error().find("Failed to create GLFW window")!=std::string::npos) return 125;
        std::cerr<<initialized.error(); return 1;
    }
    auto* window=glfwGetCurrentContext();
    auto cursor=glfwSetCursorPosCallback(window,nullptr);
    auto button=glfwSetMouseButtonCallback(window,nullptr);
    glfwSetCursorPosCallback(window,cursor); glfwSetMouseButtonCallback(window,button);
    if(!cursor || !button) return 1;
    auto& scene=runtime.getScene();
    auto& registry=scene.getRegistry();
    const auto camera=scene.createEntity();
    registry.addComponent<sle::components::TransformComponent>(camera).setRotation(0.75f);
    registry.addComponent<sle::components::Camera2DComponent>(camera);
    const auto hud=scene.createEntity();
    auto& ui=registry.addComponent<sle::components::UIComponent>(hud);
    ui.layoutAsset="tests/data/ui/embedded_pause.xml";
    ui.behaviorAsset="tests/data/scripts/embedded_ui_behavior.lua";
    if(!runtime.getScriptEngine().executeScriptAsset("tests/data/scripts/embedded_ui_probe.lua") || runtime.runForFrames(1)!=1) return 1;
    if(runtime.getWindowSize()!=glm::vec2(480,270)) return 1;
    bool mapped=true;
    runtime.getGlobalBus().subscribe<HostClick>([&](const HostClick& event) {
        button(window,GLFW_MOUSE_BUTTON_LEFT,GLFW_RELEASE,0);
        if(event.injected) {
            sle::input::Input::setCursorPosition(15,15);
            sle::input::Input::setMouseButtonState(GLFW_MOUSE_BUTTON_LEFT,true);
            return;
        }
        int ww=0,wh=0,fw=0,fh=0;
        glfwGetWindowSize(window,&ww,&wh); glfwGetFramebufferSize(window,&fw,&fh);
        const auto viewport=runtime.sceneGameViewport();
        const double top=fh-viewport[1]-viewport[3];
        const double px=viewport[0]+15.0*viewport[2]/viewport[4];
        const double py=event.bar ? top*0.5 : top+15.0*viewport[3]/viewport[5];
        // Exercise the registered GLFW host callback with window coordinates,
        // including the actual window/framebuffer ratio on HiDPI desktops.
        cursor(window,px*ww/fw,py*wh/fh);
        const auto& mouse=sle::input::Input::getMouse();
        if(event.bar) mapped=mapped && !mouse.inViewport;
        else mapped=mapped && mouse.inViewport && glm::length(mouse.position-glm::dvec2(15,15))<0.001;
        button(window,GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS,0);
        if(event.bar) mapped=mapped && !sle::input::Input::getMouse().leftPressed;
    });
    runtime.getGlobalBus().queue(HostClick{});
    if(runtime.runForFrames(1)!=1 || !mapped || !runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce")) {
        std::cerr<<"Host pixels did not map to logical HUD click"; return 1;
    }
    runtime.getGlobalBus().queue(HostClick{true});
    if(runtime.runForFrames(1)!=1 || !mapped || !runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce")) {
        std::cerr<<"Letterbox bar activated UI"; return 1;
    }
    runtime.getGlobalBus().queue(HostClick{false,true});
    if(runtime.runForFrames(1)!=1 || sle::input::Input::getMouse().position!=glm::dvec2(15,15) ||
        !sle::input::Input::getMouse().inViewport || runtime.getScriptEngine().callGlobalBoolFunction("EmbeddedUiClickedOnce")) {
        std::cerr<<"Logical injected cursor was mapped twice"; return 1;
    }
    registry.getComponent<sle::components::Camera2DComponent>(camera)->enabled=false;
    if(runtime.runForFrames(1)!=1) return 1;
    cursor(window,123,45);
    if(sle::input::Input::getMouse().position!=glm::dvec2(123,45) || !sle::input::Input::getMouse().inViewport) {
        std::cerr<<"Legacy host cursor behavior changed"; return 1;
    }
    return glGetError()==GL_NO_ERROR ? 0 : 1;
}
