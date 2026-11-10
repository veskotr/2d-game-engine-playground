#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/events/ZoneEvents.hpp>
#include <box2d/b2_world.h>
#include <cmath>
#include <iostream>

int main()
{
    sle::Runtime runtime(sle::core::EngineConfig{});
    const auto init=runtime.init();
    if(!init.ok()) return init.error().find("GLFW")!=std::string::npos?125:1;
    auto& lua=runtime.getScriptEngine();
    if(!lua.executeScriptAsset("tests/data/scripts/lua_runtime_lifecycle.lua") || !runtime.loadScene("lua_room").ok()) return 1;
    if(runtime.runForFrames(1)!=1) return 1;
    bool passed=true;
    auto check=[&](bool ok,const char* message) { if(!ok){std::cerr<<message<<"\n";passed=false;} };
    sle::entity::Entity entity=runtime.getScene().getRoots().front();
    auto& registry=runtime.getScene().getRegistry();
    auto* body=registry.getComponent<sle::components::RigidBodyComponent>(entity);
    const auto bodyId=body->box2dBodyId;
    const auto fixture=registry.getComponent<sle::components::BoxColliderComponent>(entity)->box2dFixtureId;
    check(lua.callGlobalFunction("configure_pending_body"),"New rigid bodies must accept physics settings before their first simulation tick");
    check(lua.callGlobalFunction("patch_body"),"Rigid body data patch must succeed");
    check(body->box2dBodyId==bodyId,"Ordinary body property edits must preserve native body/forces/velocity");
    if(runtime.runForFrames(1)!=1)return 1;
    check(std::fabs(registry.getComponent<sle::components::TransformComponent>(entity)->getRotation()-0.75f)<0.001f,
        "Body property edits must preserve the current orientation");
    std::string error;
    sle::ScriptApiImpl api(runtime);
    check(api.setComponent({entity.getID()},"BoxCollider",nlohmann::json::object(),error),"Empty patch must succeed");
    check(registry.getComponent<sle::components::BoxColliderComponent>(entity)->box2dFixtureId==fixture,
        "No-op patches must not destroy/recreate contact fixtures");
    check(api.removeComponent({entity.getID()},"BoxCollider",error),"Lua component removal must succeed");
    check(api.removeComponent({entity.getID()},"RigidBody",error),"Lua body removal must succeed");
    check(runtime.getPhysicsWorld()->getRawWorld()->GetBodyCount()==0,"Removing a rigid body must release native physics state");
    try {
        const auto bad=runtime.loadScene("bad_builder");
        check(!bad.ok() && runtime.getCurrentSceneName().empty(),"Failed imperative Lua builder must return a load error with no false active scene");
    } catch(const std::exception&) { check(false,"Lua scene builder errors must not escape the runtime result boundary"); }
    return passed?0:1;
}
