#include <sle/engine/CameraSystem.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/components/Camera2DComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/events/EventBus.hpp>
#include <cmath>
#include <iostream>
using Json=nlohmann::json;
int main() {
    const auto catalog=sle::EntityAuthoring::componentCatalog();
    if (catalog.at("Camera2D").at("defaults") != Json{{"enabled",true},{"primary",true},{"viewSize",{{"x",480.0},{"y",270.0}}},{"zoom",1.0}}) return 1;
    Json source={{"entities",Json::array({
        {{"key","actor"},{"components",{{"Transform",{{"position",{10,20}},{"scale",{100,50}},{"rotation",0.5}}}}}},
        {{"key","camera"},{"parent",1},{"components",{{"Transform",{{"position",{1,2}},{"rotation",0.25}}},{"Camera2D",{{"viewSize",{80,40}},{"zoom",2}}}}}}
    })}};
    auto canonical=sle::SceneAuthoring::canonicalScene(source);
    if(canonical["entities"][1]["parent"]!="actor" || sle::SceneAuthoring::canonicalScene(canonical)!=canonical) return 1;
    for (const auto& invalidParent:Json::array({"missing","camera",0,3})) {
        auto invalid=source; invalid["entities"][1]["parent"]=invalidParent;
        try { sle::SceneAuthoring::canonicalScene(invalid); return 1; } catch(const std::invalid_argument&) {}
    }
    auto cycle=source; cycle["entities"][0]["parent"]="camera";
    try { sle::SceneAuthoring::canonicalScene(cycle); return 1; } catch(const std::invalid_argument&) {}
    for(const auto& patch:Json::array({Json{{"zoom",0}},Json{{"zoom",-1}},Json{{"viewSize",{0,1}}},Json{{"viewSize",{1,-1}}}})) {
        auto invalid=source; invalid["entities"][1]["components"]["Camera2D"]=patch;
        try { sle::SceneAuthoring::canonicalScene(invalid); return 1; } catch(const std::invalid_argument&) {}
    }
    sle::entity::Scene scene;
    const auto loaded=sle::SceneLoader::loadData(canonical,scene,"camera.test.json");
    if(!loaded.ok()) { std::cerr<<loaded.error(); return 1; }
    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(480,270);
    sle::events::EventBus global;
    sle::Context ctx{scene,scene.getRegistry(),scene.getEventBus(),global,renderer,camera,nullptr,0};
    sle::TransformSystem{}.update(ctx);
    if(!sle::CameraSystem::update(scene.getRegistry(),camera) || camera.getViewportSize()!=glm::vec2(40,20) || std::abs(camera.getRotation()-0.75f)>0.0001f) return 1;
    const glm::vec2 expected{10+100*std::cos(0.5f)-100*std::sin(0.5f),20+100*std::sin(0.5f)+100*std::cos(0.5f)};
    const auto clip=camera.getViewProjection()*glm::vec4(expected,0,1);
    if(glm::length(glm::vec2(clip))>0.0001f) { std::cerr<<"Camera center is not inherited world position"; return 1; }
    const auto selected=scene.findEntityByKey("camera");
    const auto parent=scene.findEntityByKey("actor");
    scene.getRegistry().getComponent<sle::components::TransformComponent>(parent)->setPosition({110,220});
    sle::TransformSystem{}.update(ctx);
    sle::CameraSystem::update(scene.getRegistry(),camera);
    const auto followed=camera.getViewProjection()*glm::vec4(expected+glm::vec2(100,200),0,1);
    if(glm::length(glm::vec2(followed))>0.0001f) return 1;
    const auto later=scene.createEntity();
    scene.getRegistry().addComponent<sle::components::TransformComponent>(later).setPosition({500,500});
    scene.getRegistry().addComponent<sle::components::Camera2DComponent>(later).viewSize={1,1};
    sle::TransformSystem{}.update(ctx);
    sle::CameraSystem::update(scene.getRegistry(),camera);
    if(camera.getViewportSize()!=glm::vec2(40,20)) return 1;
    scene.getRegistry().getComponent<sle::components::Camera2DComponent>(selected)->enabled=false;
    sle::CameraSystem::update(scene.getRegistry(),camera);
    if(camera.getViewportSize()!=glm::vec2(1,1)) return 1;
    scene.getRegistry().getComponent<sle::components::Camera2DComponent>(later)->primary=false;
    const auto retained=camera.getPosition();
    if(sle::CameraSystem::update(scene.getRegistry(),camera) || camera.getPosition()!=retained) return 1;
    return 0;
}
