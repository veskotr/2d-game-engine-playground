#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <filesystem>
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
    lua.setProfiling(true);
    scripts.update(ctx);
    const auto profile = lua.finishProfileFrame();
    if (profile.rows.size() != 4 || profile.memoryBytes == 0 || profile.elapsedNs == 0) return 1;
    for (const auto& row : profile.rows)
        if (row.calls[0] != 1 || row.calls[1] != 1 || row.source.empty()) return 1;
    lua.setProfiling(false);
    if (!lua.finishProfileFrame().rows.empty()) return 1;
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
    std::vector<sle::core::LogEntry> errors;
    sle::core::Log::setSink([&](const auto& entry) {
        if (entry.level == sle::core::LogLevel::Error) errors.push_back(entry);
    });
    scripts.update(ctx);
    sle::core::Log::setSink({});
    if (errors.size() != 1 || !errors.front().lua ||
        errors.front().lua->entity != entity.id || errors.front().lua->behaviour != "bad" ||
        errors.front().lua->line <= 0 || errors.front().lua->traceback.empty()) {
        std::cerr << "Expected one source-linked behaviour initialization error\n";
        return 1;
    }
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
    const auto parameterOwner = api.createEntity();
    auto parameterEntry = entry("parameters", 0);
    parameterEntry["scriptAsset"] = "tests/data/scripts/behaviour_parameters.lua";
    parameterEntry["parameters"] = {{"speed", 7}, {"active", "stale"}, {"removed", true}};
    if (!api.setComponent(parameterOwner, "Script",
            {{"behaviours", nlohmann::json::array({parameterEntry})}}, error)) return 1;
    scripts.update(ctx);
    if (!lua.hasBehaviour(parameterOwner.id, "parameters") ||
        api.getComponent(parameterOwner, "Script").at("behaviours").at(0).at("parameters")
            != parameterEntry.at("parameters")) return 1;
    const auto parameterAsset = parameterEntry.at("scriptAsset").get<std::string>();
    const auto reloadedParameters = lua.reloadBehaviour(parameterOwner.id, "parameters", parameterAsset, R"(
        return {
            properties = {
                speed = {type = "number", default = 1},
                active = {type = "boolean", default = true}
            },
            init = function(_, parameters)
                assert(parameters.speed == 7 and parameters.active == true and parameters.removed == nil)
            end,
        }
    )", [] { return true; });
    if (reloadedParameters != sle::scripting::BehaviourReloadResult::Restarted) return 1;
    const auto authoredParameters = sle::SceneAuthoring::canonicalScene({
        {"entities", nlohmann::json::array({
            {{"key", "parameter-owner"}, {"components", {
                {"Script", {{"behaviours", nlohmann::json::array({parameterEntry})}}}
            }}}
        })}
    });
    if (authoredParameters.at("entities").at(0).at("components").at("Script").at("behaviours")
            .at(0).at("parameters") != parameterEntry.at("parameters")) return 1;
    const auto diagnosticOwner = api.createEntity();
    const std::string syntax = "tests/data/scripts/console syntax.lua";
    const std::string callbacks = "tests/data/scripts/console callbacks.lua";
    auto console = entry("console", 0);
    console["scriptAsset"] = callbacks;
    if (!api.setComponent(diagnosticOwner, "Script", {{"behaviours", nlohmann::json::array({console})}}, error))
        return 1;
    errors.clear();
    sle::core::Log::setSink([&](const auto& entry) {
        if (entry.lua) errors.push_back(entry);
    });
    const bool acceptedSyntax = lua.executeScriptAssetForEntity(syntax, diagnosticOwner.id);
    const bool loadedCallbacks = lua.ensureBehaviour(diagnosticOwner.id, "console", callbacks);
    lua.runBehaviourUpdate(diagnosticOwner.id, "console", 0.01f);
    const bool emitted = api.emitEvent("console.probe", 0, "");
    scene.getEventBus().flushQueue();
    sle::core::Log::setSink({});
    const auto syntaxPath = std::filesystem::absolute(syntax).generic_string();
    const auto callbacksPath = std::filesystem::absolute(callbacks).generic_string();
    if (acceptedSyntax || !loadedCallbacks || !emitted || errors.size() != 3 ||
        errors[0].lua->file != syntaxPath || errors[0].lua->line != 2 ||
        errors[1].lua->file != callbacksPath || errors[1].lua->line != 6 ||
        errors[2].lua->file != callbacksPath || errors[2].lua->line != 3 ||
        errors[1].lua->traceback.empty() || errors[2].lua->traceback.empty() ||
        errors[1].lua->entity != diagnosticOwner.id || errors[2].lua->behaviour != "console") {
        std::cerr << "Expected syntax, update and event Lua diagnostics with source locations\n";
        return 1;
    }
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
    lua.callGlobalFunction("clear_behaviour_trace");
    const std::string asset = "tests/data/scripts/multi_behaviour.lua";
    const auto owner = scene.getSceneEntity().getID();
    const auto generation = lua.getBehaviourGeneration(owner, "scene");
    const auto reload = [&](uint32_t entity, const std::string& key, const std::string& source) {
        return lua.reloadBehaviour(entity, key, asset, source, [] { return true; });
    };
    using Reload = sle::scripting::BehaviourReloadResult;
    if (generation == 0 || reload(owner, "scene", "return {") != Reload::StageFailed ||
        reload(owner, "scene", "Engine.Events.emit('multi.tick')") != Reload::StageFailed ||
        reload(owner, "scene", "local api = Engine; return {init=function() api.getBehaviourKey() end}") != Reload::StageFailed ||
        reload(owner, "scene", "error('top-level failure')") != Reload::StageFailed ||
        lua.getBehaviourGeneration(owner, "scene") != generation ||
        !lua.callEntityFunctionByName(owner, "ping") ||
        !lua.callGlobalBoolFunction("expect_behaviour_trace", 0, "ping:scene,ping:scene_second")) return 1;
    const std::string corrected = R"(
        local counter = 0
        return {
            init = function()
                local key = Engine.getBehaviourKey()
                record_behaviour('restart:'..key)
                Engine.Events.subscribe('multi.tick', function() record_behaviour('new:'..key) end)
            end,
            update = function()
                counter = counter + 1
                record_behaviour('reloaded:'..Engine.getBehaviourKey()..':'..counter)
            end
        }
    )";
    if (!api.emitEvent("multi.tick", 0, "") ||
        reload(owner, "scene", corrected) != Reload::Restarted ||
        reload(child.getID(), "child", corrected) != Reload::Restarted ||
        !lua.callGlobalBoolFunction("expect_behaviour_trace", 0,
            "destroy:scene,restart:scene,destroy:child,restart:child")) return 1;
    scene.getEventBus().flushQueue();
    if (!lua.callGlobalBoolFunction("expect_behaviour_trace", 0, "event:scene_second,event:prefab,new:scene,new:child") ||
        !api.emitEvent("multi.tick", 0, "")) return 1;
    scene.getEventBus().flushQueue();
    if (!lua.callGlobalBoolFunction("expect_behaviour_trace", 0,
            "event:scene_second,event:prefab,new:scene,new:child")) return 1;
    if (reload(owner, "scene", "return {init=function() error('init failure') end}") != Reload::InitFailed ||
        lua.hasBehaviour(owner, "scene") || reload(owner, "scene", corrected) != Reload::Restarted ||
        !lua.callGlobalBoolFunction("expect_behaviour_trace", 0, "restart:scene")) return 1;
    lua.runBehaviourUpdate(owner, "scene", 0.01f);
    lua.runBehaviourUpdate(child.getID(), "child", 0.01f);
    if (!lua.callGlobalBoolFunction("expect_behaviour_trace", 0, "reloaded:scene:1,reloaded:child:1")) return 1;
    const auto later = api.createEntity();
    if (!lua.ensureBehaviour(later.id, "later", asset) ||
        !lua.callGlobalBoolFunction("expect_behaviour_trace", 0, "restart:later")) return 1;
    const auto legacy = api.createEntity();
    const std::string legacyAsset = "tests/data/scripts/state_machine_guard.lua";
    lua.configureBehaviours(legacy.id, {{"", legacyAsset, true, 0}});
    if (!lua.ensureBehaviour(legacy.id, "", legacyAsset) ||
        !lua.callEntityBoolFunction(legacy.id, "canTransitionToRun") ||
        lua.reloadBehaviour(legacy.id, "", legacyAsset, "return {}", [] { return true; }) != Reload::Restarted ||
        lua.callEntityBoolFunction(legacy.id, "canTransitionToRun") ||
        !lua.callGlobalBoolFunction("canTransitionToRun", legacy.id)) return 1;
    const auto profiled = api.createEntity();
    if (!api.setComponent(profiled, "Script", {{"behaviours", nlohmann::json::array({
        entry("outer", 0), entry("inner", 1)})}}, error)) return 1;
    const auto installProfileProbe = [&](const char* key, const char* source) {
        return lua.reloadBehaviour(profiled.id, key, "tests/data/scripts/multi_behaviour.lua",
            source, [] { return true; }) == Reload::Restarted;
    };
    if (!installProfileProbe("outer", R"(
        return {
            update = function() end,
            ping = function(entity)
                collectgarbage("collect")
                local script = Engine.Components.get(entity, "Script")
                assert(Engine.Components.set(entity, "Script", {behaviours = {script.behaviours[1]}}))
                assert(Engine.getBehaviourKey() == "outer")
            end
        }
    )") || !installProfileProbe("inner", R"(
        return {
            update = function() end,
            destroy = function() _G.profileRetained = string.rep("x", 65536) end
        }
    )")) return 1;

    if (std::getenv("SLE_PROFILE_BENCHMARK"))
    {
        const auto measure = [&](bool capture) {
            lua.setProfiling(capture);
            const auto start = std::chrono::steady_clock::now();
            for (int frame = 0; frame < 20000; ++frame)
            {
                lua.runBehaviourUpdate(profiled.id, "outer", 0.01f);
                lua.runBehaviourUpdate(profiled.id, "inner", 0.01f);
                if (capture) (void)lua.finishProfileFrame();
            }
            return std::chrono::duration<double, std::micro>(std::chrono::steady_clock::now() - start).count() / 20000;
        };
        const auto disabled = measure(false);
        const auto enabled = measure(true);
        std::cout << "LUA_PROFILE_US_PER_FRAME disabled=" << disabled << " enabled=" << enabled << '\n';
    }

    lua.setProfiling(true);
    const auto beforeNested = lua.finishProfileFrame();
    if (!lua.callEntityFunctionByName(profiled.id, "ping")) return 1;
    const auto nested = lua.finishProfileFrame();
    const auto findRow = [&](const char* key) {
        return std::find_if(nested.rows.begin(), nested.rows.end(), [&](const auto& row) {
            return row.entity == profiled.id && row.behaviour == key;
        });
    };
    const auto outer = findRow("outer"), inner = findRow("inner");
    if (nested.rows.size() != 2 || outer == nested.rows.end() || inner == nested.rows.end() ||
        outer->calls[2] != 1 || inner->calls[2] != 1 || inner->allocatedBytes < 65536 ||
        outer->allocatedBytes >= 65536 || nested.memoryBytes < beforeNested.memoryBytes + 32768 ||
        nested.explicitGcNs == 0 || nested.elapsedNs != outer->elapsedNs[2] + inner->elapsedNs[2])
    {
        std::cerr << "Expected exclusive time and allocations attributed to the nested behaviour\n";
        return 1;
    }
    lua.setProfiling(false);
    lua.runBehaviourUpdate(profiled.id, "outer", 0.01f);
    if (lua.hasProfileActivity() || lua.finishProfileFrame().memoryBytes != 0) return 1;
    const auto root=scene.getSceneEntity();
    auto staged=scene.createStagingScene();
    const auto stagingRoot=staged->ensureSceneEntity();
    scene.replaceWith(*staged);
    if(scene.getSceneEntity()!=stagingRoot || registry.hasEntity(root) || scene.findEntityByKey("spawned").valid()) return 1;
    lua.clearSceneScripts();
    return 0;
}
