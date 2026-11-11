#include <sle/engine/Runtime.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <cmath>
#include <iostream>
#include <nlohmann/json.hpp>
#include <filesystem>
#include <fstream>
#include <chrono>
#include <vector>

namespace {
bool check(bool ok, const char* message)
{
    if (!ok) std::cerr << message << '\n';
    return ok;
}

bool rejectInvalidReferences()
{
    using Json = nlohmann::json;
    const auto path = std::filesystem::temp_directory_path() /
        ("sle-scene-references-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".json");
    struct Cleanup { std::filesystem::path path; ~Cleanup() { std::error_code ec; std::filesystem::remove(path,ec); } } cleanup{path};
    const Json base = {{"entities", Json::array({
        {{"key","a"},{"components",{{"Animator",{{"targetEntities",{{"target","b"}}}}}}}},
        {{"key","b"},{"components",Json::object()}}
    })}};
    std::vector<std::pair<Json,std::string>> cases;
    auto bad=base; bad["entities"][1]["key"]="a"; cases.emplace_back(bad,"duplicate");
    bad=base; bad["entities"][0]["key"]=""; cases.emplace_back(bad,"key");
    bad=base; bad["entities"][0]["key"]=3; cases.emplace_back(bad,"key");
    bad=base; bad["entities"][0]["parent"]="missing"; cases.emplace_back(bad,"parent");
    bad=base; bad["entities"][0]["parent"]="a"; cases.emplace_back(bad,"parent");
    bad=base; bad["entities"][0]["parent"]="b"; bad["entities"][1]["parent"]="a"; cases.emplace_back(bad,"parent");
    bad=base; bad["entities"][0]["parent"]=Json::array(); cases.emplace_back(bad,"parent");
    for (const Json target : {Json("missing"),Json("outside"),Json(""),Json(0),Json(-1),Json(1.5),Json(true),Json(),Json(3),Json(uint64_t{4294967297})}) {
        bad=base; bad["entities"][0]["components"]["Animator"]["targetEntities"]["target"]=target;
        cases.emplace_back(bad,"targetEntities");
    }
    bad=base; bad["entities"][0]["components"]["Animator"]["targetEntities"]=Json::array(); cases.emplace_back(bad,"targetEntities");
    // A skipped non-object still occupies its authored position for legacy references.
    bad=base; bad["entities"][1]=17; bad["entities"][0]["components"]["Animator"]["targetEntities"]["target"]=2;
    cases.emplace_back(bad,"targetEntities");

    sle::Runtime runtime(sle::core::EngineConfig{});
    runtime.registerScene("original",[](auto& rt) { rt.getScene().setEntityKey(rt.getScene().createEntity(),"outside"); });
    if (!runtime.loadScene("original").ok()) return false;
    const auto original=runtime.getScene().getRoots().front();
    runtime.registerSceneFromFile("invalid",path.string());
    bool passed=true;
    for (const auto& [input,expected] : cases) {
        { std::ofstream file(path); file << input.dump(); }
        const auto result=runtime.loadScene("invalid");
        passed=check(!result.ok() && result.error().find(expected)!=std::string::npos &&
                     result.error().find(path.string())!=std::string::npos,
                     "Invalid key/reference must produce a contextual error") && passed;
        passed=check(runtime.getCurrentSceneName()=="original" && runtime.getScene().getRegistry().hasEntity(original) &&
                     runtime.getScene().getRoots().size()==1 && runtime.getScene().findEntityByKey("outside")==original,
                     "Invalid scene references must preserve the active scene") && passed;
    }
    return passed;
}
}

int main()
{
    if (!rejectInvalidReferences()) return 1;
    sle::Runtime runtime(sle::core::EngineConfig{});
    auto& scene = runtime.getScene();
    auto& registry = scene.getRegistry();
    // Ensure file positions cannot accidentally match runtime IDs on first load.
    for (int i=0; i<5; ++i) scene.createEntity();
    runtime.registerSceneFromFile("references", "tests/data/scenes/test_scene_references.json");
    sle::ScriptApiImpl api(runtime);
    auto& lua = runtime.getScriptEngine();
    if (!lua.init(&api)) return 1;
    struct LuaCleanup { sle::scripting::ScriptEngine& engine; ~LuaCleanup() { engine.shutdown(); } } luaCleanup{lua};
    if (!lua.executeScriptAsset("tests/data/scripts/scene_key_lookup.lua")) return 1;
    sle::entity::Entity oldChild;
    for (int load=0; load<3; ++load)
    {
        const auto result = runtime.loadScene("references");
        if (!check(result.ok(), "Valid scene references must load")) return 1;
        sle::entity::Entity child, rig, leaf;
        registry.view<sle::components::TransformComponent>([&](auto entity, const auto& transform) {
            if (transform.getPosition().x==3) child=entity;
            else if (transform.getPosition().x==10) rig=entity;
            else if (transform.getPosition().x==1) leaf=entity;
        });
        if (!check(child.valid() && rig.valid() && leaf.valid(), "Expected all authored entities")) return 1;
        if (!check(scene.getParent(child)==rig && scene.getRoots().size()==1,
                   "Forward parent references must build a single hierarchy")) return 1;
        const auto* animator = registry.getComponent<sle::components::AnimatorComponent>(child);
        if (!check(animator && animator->targetEntities.contains("rig") && animator->targetEntities.at("rig")==rig.getID() &&
                   animator->targetEntities.at("legacy")==rig.getID() && animator->targetEntities.at("leaf")==leaf.getID(),
                   "Key and legacy file references must resolve to this load's entities")) return 1;
        if (!check(!registry.hasEntity(oldChild), "Previous runtime entity IDs must remain dead")) return 1;
        sle::renderer::Renderer renderer;
        sle::core::Camera2D camera(320,180);
        sle::Context ctx{scene,registry,scene.getEventBus(),runtime.getGlobalBus(),renderer,camera,nullptr,0.01f};
        sle::TransformSystem transforms;
        transforms.update(ctx);
        const auto* childWorld=registry.getComponent<sle::components::WorldTransformComponent>(child);
        const auto* leafWorld=registry.getComponent<sle::components::WorldTransformComponent>(leaf);
        if (!check(childWorld && leafWorld && std::fabs(childWorld->position.x-16)<0.001f &&
                   std::fabs(childWorld->position.y-28)<0.001f && std::fabs(leafWorld->position.x-12)<0.001f &&
                   std::fabs(leafWorld->position.y-24)<0.001f, "Hierarchy must resolve world transforms through transformless parents")) return 1;
        if (!check(scene.findEntityByKey("rig")==rig && scene.findEntityByKey("child")==child,
                   "Scene key metadata must follow staged replacement")) return 1;
        if (!lua.callGlobalFunction("check_scene_keys")) return 1;
        oldChild=child;
    }
    if (!lua.callGlobalFunction("remove_scene_keys") || !scene.getRoots().empty()) return 1;
    const auto replacement=scene.createEntity();
    if (!check(scene.setEntityKey(replacement,"rig") && scene.findEntityByKey("rig")==replacement,
               "Destroyed keys must be reusable by newly created entities")) return 1;
    const auto another=scene.createEntity();
    if (!check(scene.setEntityKey(another,"another") && !scene.setEntityKey(another,"rig") &&
               scene.findEntityByKey("another")==another && scene.findEntityByKey("rig")==replacement &&
               !scene.setEntityKey(sle::entity::Entity{},"invalid") && !scene.setEntityKey(another,""),
               "Key assignment must reject dead IDs, duplicates and empty keys atomically")) return 1;
    if (!check(scene.setEntityKey(another,"renamed") && !scene.findEntityByKey("another").valid(),
               "Renaming a key must remove its previous lookup")) return 1;
    scene.destroy();
    if (!check(!scene.findEntityByKey("rig").valid() && !scene.findEntityByKey("renamed").valid(),
               "Scene destruction must clear key metadata")) return 1;
    runtime.registerSceneFromFile("legacy", "tests/data/scenes/test_scene_legacy_references.json");
    sle::entity::Entity oldTarget;
    for (int load=0; load<2; ++load)
    {
        if (!runtime.loadScene("legacy").ok() || scene.getRoots().size()!=1) return 1;
        const auto parent=scene.getRoots().front();
        const auto& children=scene.getChildren(parent);
        if (!check(children.size()==1 && parent.getID()>2 && !registry.hasEntity(oldTarget),
                   "Unkeyed legacy files must remap numeric parents without reusing runtime IDs")) return 1;
        const auto* animator=registry.getComponent<sle::components::AnimatorComponent>(children.front());
        if (!check(animator && animator->targetEntities.at("other")==parent.getID(),
                   "Unkeyed legacy animator targets must survive repeated loading")) return 1;
        oldTarget=parent;
    }
    return 0;
}
