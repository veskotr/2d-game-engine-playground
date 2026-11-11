#include <sle/engine/Runtime.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <iostream>

int main()
{
    sle::Runtime runtime(sle::core::EngineConfig{});
    sle::ScriptApiImpl api(runtime);
    auto& lua=runtime.getScriptEngine();
    if(!lua.init(&api)) return 1;
    struct Cleanup { sle::scripting::ScriptEngine& lua; ~Cleanup(){lua.shutdown();} } cleanup{lua};
    if(!lua.executeScriptAsset("tests/data/scripts/multi_behaviour_probe.lua")) return 1;
    auto& scene=runtime.getScene();
    auto& registry=scene.getRegistry();
    const auto entity=api.createEntity();
    const auto other=api.createEntity();
    auto entry=[](const char* key,int order) { return nlohmann::json{{"key",key},{"scriptAsset","tests/data/scripts/multi_behaviour.lua"},{"order",order}}; };
    const auto a=entry("a",10), b=entry("b",-10), c=entry("c",10);
    std::string error;
    if(!api.setComponent(entity,"Script",{{"behaviours",nlohmann::json::array({a,b,c})}},error) ||
       !api.setComponent(other,"Script",{{"behaviours",nlohmann::json::array({entry("other",-10)})}},error)) {
        std::cerr<<"Multiple behaviours must be configurable: "<<error<<'\n'; return 1;
    }
    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(320,180);
    sle::Context ctx{scene,registry,scene.getEventBus(),runtime.getGlobalBus(),renderer,camera,nullptr,0.01f};
    sle::ScriptSystem scripts; scripts.setScriptRuntime(&lua);
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"init:b,init:other,init:a,init:c,update:b:1,update:other:1,update:a:1,update:c:1")) return 1;
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"update:b:2,update:other:2,update:a:2,update:c:2")) return 1;
    if(!lua.callEntityFunctionByName(entity.id,"ping") ||
       !lua.callGlobalBoolFunction("expect_behaviour_trace",0,"ping:b,ping:a,ping:c")) return 1;
    if(!api.setComponent(entity,"Script",{{"behaviours",nlohmann::json::array({b,c})}},error) ||
       !lua.callGlobalBoolFunction("expect_behaviour_trace",0,"destroy:a")) return 1;
    if(!api.emitEvent("multi.tick",0,"")) return 1;
    scene.getEventBus().flushQueue();
    // EventBus keeps registration order, independent of update ordering.
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"event:b,event:other,event:c")) return 1;
    auto disabled=b; disabled["enabled"]=false;
    if(!api.setComponent(entity,"Script",{{"behaviours",nlohmann::json::array({disabled,c})}},error)) return 1;
    if(!lua.callEntityFunctionByName(entity.id,"ping") ||
       !lua.callGlobalBoolFunction("expect_behaviour_trace",0,"ping:c")) return 1;
    if(!api.emitEvent("multi.tick",0,"")) return 1;
    scene.getEventBus().flushQueue();
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"event:other,event:c")) return 1;
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"update:other:3,update:c:3")) return 1;
    if(!api.setComponent(entity,"Script",{{"behaviours",nlohmann::json::array({b,c})}},error)) return 1;
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"update:b:3,update:other:4,update:c:4")) return 1;
    const auto before=api.getComponent(entity,"Script");
    if(api.setComponent(entity,"Script",{{"behaviours",nlohmann::json::array({b,b})}},error) || api.getComponent(entity,"Script")!=before) return 1;
    auto bad=entry("bad",-200); bad["scriptAsset"]="tests/data/scripts/multi_behaviour_failed.lua";
    if(!api.setComponent(entity,"Script",{{"behaviours",nlohmann::json::array({bad,b,c})}},error)) return 1;
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"failed:bad,update:b:4,update:other:5,update:c:5")) return 1;
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"update:b:5,update:other:6,update:c:6")) return 1;
    if(!api.emitEvent("multi.tick",0,"")) return 1;
    scene.getEventBus().flushQueue();
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"event:b,event:other,event:c")) return 1;
    const auto reentrant=api.createEntity();
    auto ra=entry("ra",0), rb=entry("rb",0), survivor=entry("survivor",0);
    ra["scriptAsset"]=rb["scriptAsset"]="tests/data/scripts/multi_behaviour_reentrant.lua";
    if(!api.setComponent(reentrant,"Script",{{"behaviours",nlohmann::json::array({ra,rb,survivor})}},error)) return 1;
    scripts.update(ctx);
    lua.callGlobalFunction("clear_behaviour_trace");
    if(!api.setComponent(reentrant,"Script",{{"behaviours",nlohmann::json::array({survivor})}},error)) return 1;
    lua.callGlobalFunction("clear_behaviour_trace");
    if(!lua.callEntityFunctionByName(reentrant.id,"ping") ||
       !lua.callGlobalBoolFunction("expect_behaviour_trace",0,"ping:survivor")) return 1;
    lua.clearSceneScripts();
    scene.destroy();
    lua.callGlobalFunction("clear_behaviour_trace");
    nlohmann::json data;
    data["components"]["Script"]["behaviours"]=nlohmann::json::array({entry("scene",-100),entry("scene_second",-99)});
    data["components"]["Transform"]={{"position",{10,20}},{"scale",{2,2}}};
    nlohmann::json childData;
    childData["key"]="child";
    childData["components"]["Script"]["behaviours"]=nlohmann::json::array({entry("child",0)});
    childData["components"]["Transform"]={{"position",{3,4}}};
    data["entities"]=nlohmann::json::array({childData});
    const auto loaded=sle::SceneLoader::loadData(data,scene,"scene-behaviours.json");
    if(!loaded.ok()) {std::cerr<<loaded.error();return 1;}
    if(!lua.executeScriptAsset("tests/data/scripts/scene_behaviours.lua") || !lua.callGlobalFunction("check_scene_behaviours")) return 1;
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"init:scene,init:scene_second,init:child,update:scene:1,update:scene_second:1,update:child:1")) return 1;
    sle::TransformSystem transforms; transforms.update(ctx);
    const auto child=scene.findEntityByKey("child");
    const auto* world=registry.getComponent<sle::components::WorldTransformComponent>(child);
    if(!world || world->position.x!=16 || world->position.y!=28) return 1;
    if(!lua.callGlobalBoolFunction("spawn_scripted_prefab")) return 1;
    scripts.update(ctx);
    if(!lua.callGlobalBoolFunction("expect_behaviour_trace",0,"init:prefab,update:scene:2,update:scene_second:2,update:child:2,update:prefab:1")) return 1;
    const auto root=scene.getSceneEntity();
    auto staged=scene.createStagingScene();
    const auto stagingRoot=staged->ensureSceneEntity();
    scene.replaceWith(*staged);
    if(scene.getSceneEntity()!=stagingRoot || registry.hasEntity(root) || scene.findEntityByKey("spawned").valid()) return 1;
    lua.clearSceneScripts();
    return 0;
}
