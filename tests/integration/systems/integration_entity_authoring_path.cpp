#include <sle/engine/EntityAuthoring.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/EntityPreviewSystem.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/engine/PrefabLoader.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <limits>
#include <cmath>

int main(int argc, char** argv)
{
    using Json = nlohmann::json;
    if (argc == 2)
    {
        sle::entity::Scene scene;
        const auto loaded = sle::PrefabLoader::instantiate(argv[1], scene, "authored");
        if (!loaded.ok()) { std::cerr << loaded.error(); return 1; }
        return scene.getRegistry().getComponent<sle::components::TransformComponent>(loaded.value()) ? 0 : 1;
    }
    const auto catalog = sle::EntityAuthoring::componentCatalog();
    if (!catalog.contains("Transform") || catalog.contains("WorldTransform") || catalog.contains("ChunkMap")) return 1;
    if (catalog["StateMachine"]["defaults"].contains("currentState") ||
        catalog["Audio"]["defaults"].contains("state") ||
        !catalog["SpriteRenderer"]["defaults"].contains("texture")) return 1;
    Json document = {{"root", "actor"}, {"entities", Json::array({{
        {"key", "actor"}, {"name", "Actor"}, {"components", {
            {"Transform", {{"position", {{"x",12},{"y",34}}}, {"scale",{{"x",1},{"y",1}}}}},
            {"BoxCollider", {{"size",{{"x",20},{"y",30}}}}}
        }}
    }})}};
    for (const auto& [name, entry] : catalog.items())
    {
        auto candidate = document;
        candidate["entities"][0]["components"][name] = entry["defaults"];
        const auto valid = sle::EntityAuthoring::validatePrefab(candidate);
        if (!valid.ok()) { std::cerr << name << ": " << valid.error(); return 1; }
    }
    auto partial = document;
    partial["entities"][0]["components"]["Transform"] = Json::object();
    partial["entities"][0]["components"]["Script"] = {{"asset", "assets/scripts/player_move.lua"}};
    const auto canonical = sle::EntityAuthoring::canonicalPrefab(partial);
    if (!canonical["entities"][0]["components"]["Transform"].contains("position") ||
        canonical["entities"][0]["components"]["Script"].contains("asset") ||
        canonical["entities"][0]["components"]["Script"]["scriptAsset"] != "assets/scripts/player_move.lua" ||
        canonical["entities"][0]["components"]["BoxCollider"]["size"]["y"] != 30 ||
        !sle::EntityAuthoring::validatePrefab(canonical).ok()) return 1;
    const auto path = std::filesystem::temp_directory_path() / "sle_entity_authoring_test.prefab.json";
    { std::ofstream file(path); file << document.dump(2); }
    sle::entity::Scene scene;
    const auto loaded = sle::PrefabLoader::instantiate(path.generic_string(), scene, "authored");
    std::filesystem::remove(path);
    if (!loaded.ok()) { std::cerr << loaded.error(); return 1; }
    const auto* transform = scene.getRegistry().getComponent<sle::components::TransformComponent>(loaded.value());
    const auto* collider = scene.getRegistry().getComponent<sle::components::BoxColliderComponent>(loaded.value());
    if (!transform || transform->getPosition().x != 12 || !collider || collider->size.y != 30) return 1;
    auto expectInvalid = [&](Json candidate) { return !sle::EntityAuthoring::validatePrefab(candidate).ok(); };
    auto invalid = document;
    invalid["entities"][0]["components"]["BoxCollider"]["size"]["x"] = -1;
    if (!expectInvalid(invalid)) return 1;
    invalid = document;
    invalid["entities"][0]["components"]["Unknown"] = Json::object();
    if (!expectInvalid(invalid)) return 1;
    invalid = document;
    invalid["entities"][0]["components"]["WorldTransform"] = Json::object();
    if (!expectInvalid(invalid)) return 1;
    invalid = document;
    invalid["entities"][0]["parent"] = "other";
    if (!expectInvalid(invalid)) return 1;
    invalid = document;
    invalid["root"] = "missing";
    if (!expectInvalid(invalid)) return 1;
    invalid = document;
    invalid["entities"][0]["components"]["Animator"] = {{"targetEntities", {{"self", "actor"}}}};
    if (!sle::EntityAuthoring::validatePrefab(invalid).ok()) return 1;
    invalid["entities"][0]["components"]["Animator"]["targetEntities"]["self"] = "missing";
    if (!expectInvalid(invalid)) return 1;
    Json sceneDocument = {{"name","Test scene"},{"entities",Json::array({
        {{"key","left"},{"components",{{"Transform",{{"position",{{"x",-100},{"y",0}}}}},
            {"Animator",{{"targetEntities",{{"other",2},{"self","left"}}}}}}}},
        {{"key","right"},{"components",{{"Transform",{{"position",{{"x",100},{"y",0}}}}}}}}
    })}};
    const auto normalized = sle::SceneAuthoring::canonicalScene(sceneDocument);
    if (normalized["entities"][0]["components"]["Animator"]["targetEntities"]["other"] != "right") return 1;
    sle::entity::Scene authoredScene;
    const auto staged = sle::SceneLoader::loadData(normalized,authoredScene,"test.scene.json");
    if (!staged.ok()) { std::cerr << staged.error(); return 1; }
    const auto left=authoredScene.findEntityByKey("left"), right=authoredScene.findEntityByKey("right");
    const auto* animator=authoredScene.getRegistry().getComponent<sle::components::AnimatorComponent>(left);
    if (!animator || animator->targetEntities.at("other")!=right.getID() || animator->targetEntities.at("self")!=left.getID()) return 1;
    auto rejectsScene = [&](Json candidate) {
        try { sle::SceneAuthoring::canonicalScene(candidate); return false; }
        catch (const std::exception&) { return true; }
    };
    auto badScene=sceneDocument;
    badScene["entities"][1]["key"]="left";
    if (!rejectsScene(badScene)) return 1;
    badScene=sceneDocument; badScene["entities"][0]["parent"]="missing";
    if (!rejectsScene(badScene)) return 1;
    badScene=sceneDocument; badScene["tiledMap"]="map.json";
    if (!rejectsScene(badScene)) return 1;
    badScene=sceneDocument; badScene["entities"][0]["components"]["Animator"]["targetEntities"]["other"]="missing";
    if (!rejectsScene(badScene)) return 1;
    badScene=sceneDocument; badScene["key"]="";
    if (!rejectsScene(badScene)) return 1;
    if (sle::SceneAuthoring::canonicalScene({{"entities",Json::array()}})["entities"].size()!=0) return 1;
    for (const auto* key : {"left","right"}) {
        const auto entity=authoredScene.findEntityByKey(key);
        auto& world=authoredScene.getRegistry().addComponent<sle::components::WorldTransformComponent>(entity);
        world.position=authoredScene.getRegistry().getComponent<sle::components::TransformComponent>(entity)->getPosition();
    }
    sle::core::Camera2D camera(10,10);
    sle::EntityPreviewSystem::frameCamera(authoredScene,camera,2.0f);
    const auto origin=camera.getPosition(), size=camera.getViewportSize();
    if (origin.x > -101 || origin.x+size.x < 101 || origin.y > 0 || origin.y+size.y < 0) {
        std::cerr << "Fit omitted scene entities"; return 1;
    }
    const auto extreme=std::numeric_limits<float>::max()*0.9f;
    auto extremeDocument=sceneDocument;
    for (auto& definition : extremeDocument["entities"])
        definition["components"]["Transform"]["position"]={{"x",extreme},{"y",extreme}};
    const auto accepted=sle::SceneAuthoring::canonicalScene(extremeDocument);
    authoredScene.getRegistry().view<sle::components::WorldTransformComponent>([&](auto, auto& world) {
        world.position={extreme,extreme}; world.scale={extreme,extreme};
    });
    sle::EntityPreviewSystem::frameCamera(authoredScene,camera,2.0f);
    const auto extremePosition=camera.getPosition(), extremeSize=camera.getViewportSize();
    if (!std::isfinite(extremePosition.x) || !std::isfinite(extremePosition.y) ||
        !std::isfinite(extremeSize.x) || !std::isfinite(extremeSize.y) || extremeSize.y > 1.0e6f) return 1;
    return 0;
}
