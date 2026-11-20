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
#include <algorithm>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <limits>
#include <set>
#include <cmath>

int main(int argc, char** argv)
{
    using Json = nlohmann::json;
    if (argc == 2 && std::string_view(argv[1]) == "--dump-catalog")
    {
        std::cout << sle::EntityAuthoring::componentCatalog().dump(2) << '\n';
        return 0;
    }
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
    const Json expectedEnums = {
        {"RigidBody", {{"bodyType", {"Static", "Dynamic", "Kinematic"}}}},
        {"Animator", {{"loopMode", {"Once", "Loop"}}}},
        {"UI", {{"spaceMode", {"Screen", "World"}}}},
        {"ChunkActivation", {{"mode", {"Inherit", "AlwaysActive", "EventsOnly", "AutoToggle"}}}}
    };
    for (const auto& [name, entry] : catalog.items())
    {
        const auto expected = expectedEnums.value(name, Json::object());
        if (entry.at("enums") != expected) { std::cerr << "Enum catalog: " << name; return 1; }
        for (const auto& [field, choices] : expected.items())
        {
            auto candidate = document;
            auto& value = candidate["entities"][0]["components"][name][field];
            for (const auto& choice : choices)
            {
                value = choice;
                if (!sle::EntityAuthoring::validatePrefab(candidate).ok()) return 1;
            }
            value = "unknown";
            if (sle::EntityAuthoring::validatePrefab(candidate).ok()) return 1;
        }
    }
    for (const auto* name : {"Transform", "Camera2D", "RigidBody", "SpriteRenderer", "BoxCollider", "CircleCollider"})
    {
        const auto& entry = catalog.at(name);
        if (!entry.contains("fields")) { std::cerr << "Missing descriptors: " << name; return 1; }
        std::set<Json> paths;
        for (const auto& field : entry.at("fields"))
            if (!paths.insert(field.at("path")).second) return 1;
        const auto covered = [&](const auto& self, const Json& object, const Json& prefix) -> bool {
            for (const auto& [key, value] : object.items())
            {
                auto path = prefix;
                path.push_back(key);
                if (!paths.erase(path))
                {
                    std::cerr << "Uncovered field: " << name << " " << path << '\n';
                    return false;
                }
                if (value.is_object() && !self(self, value, path)) return false;
            }
            return true;
        };
        if (!covered(covered, entry.at("defaults"), Json::array()) || !paths.empty()) return 1;
    }
    const auto accepts = [&](const char* component, Json patch) {
        auto candidate = document;
        candidate["entities"][0]["components"][component] = std::move(patch);
        return sle::EntityAuthoring::validatePrefab(candidate).ok();
    };
    if (!accepts("Transform", {{"scale", {{"x", 0}, {"y", -1}}}, {"rotation", -7}}) ||
        !accepts("RigidBody", {{"linearDamping", 0}, {"gravityScale", -2}}) ||
        accepts("RigidBody", {{"angularDamping", -1}}) ||
        !accepts("BoxCollider", {{"friction", 2}, {"density", 0}, {"maskBits", 65535}}) ||
        accepts("BoxCollider", {{"maskBits", 65536}}) ||
        accepts("BoxCollider", {{"maskBits", 1.5}}) ||
        !accepts("CircleCollider", {{"radius", 1}, {"restitution", 1}, {"categoryBits", 0}}) ||
        accepts("CircleCollider", {{"radius", 0}}) ||
        accepts("CircleCollider", {{"restitution", 1.01}}) ||
        accepts("CircleCollider", {{"categoryBits", -1}}) ||
        !accepts("SpriteRenderer", {{"color", Json::array({-1, 2, 3, 4})},
            {"region", {{"uv", Json::array({-1, 2, 3, 4})}}}, {"layer", std::numeric_limits<int>::min()}}) ||
        accepts("SpriteRenderer", {{"layer", 1.5}})) return 1;
    auto cameraCandidate = document;
    auto& zoom = cameraCandidate["entities"][0]["components"]["Camera2D"]["zoom"];
    zoom = 0;
    if (sle::EntityAuthoring::validatePrefab(cameraCandidate).ok()) return 1;
    zoom = 1e-100;
    if (sle::EntityAuthoring::validatePrefab(cameraCandidate).ok()) return 1;
    zoom = 6;
    if (!sle::EntityAuthoring::validatePrefab(cameraCandidate).ok()) return 1;
    auto partial = document;
    partial["entities"][0]["components"]["Transform"] = Json::object();
    partial["entities"][0]["components"]["Script"] = {{"asset", "assets/scripts/player_move.lua"}};
    const auto canonical = sle::EntityAuthoring::canonicalPrefab(partial);
    if (!canonical["entities"][0]["components"]["Transform"].contains("position") ||
        canonical["entities"][0]["components"]["Script"].contains("asset") ||
        canonical["entities"][0]["components"]["Script"]["scriptAsset"] != "assets/scripts/player_move.lua" ||
        canonical["entities"][0]["components"]["BoxCollider"]["size"]["y"] != 30 ||
        !sle::EntityAuthoring::validatePrefab(canonical).ok()) return 1;
    auto legacy = document;
    auto& legacyComponents = legacy["entities"][0]["components"];
    legacyComponents["Camera2D"] = {{"viewSize", Json::array({480, 270})}, {"zoom", 6}};
    legacyComponents["RigidBody"] = {{"mass", 2}, {"bodyType", "Dynamic"}};
    legacyComponents["SpriteRenderer"] = {{"texture", ""}, {"region", {{"uv", Json::array({0, 0, 1, 1})}}}};
    const auto legacyCanonical = sle::EntityAuthoring::canonicalPrefab(legacy);
    const auto& legacyValues = legacyCanonical.at("entities").at(0).at("components");
    if (!sle::EntityAuthoring::validatePrefab(legacyCanonical).ok() ||
        legacyValues.at("Camera2D").at("viewSize").at("x") != 480 ||
        legacyValues.at("RigidBody").at("bodyType") != "Dynamic" ||
        legacyValues.at("SpriteRenderer").at("region").at("uv").at("w") != 1) return 1;
    for (const auto& values : legacyValues)
        if (values.contains("fields") || values.contains("groups") || values.contains("enums")) return 1;
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

    // The editor grid follows the move snap step: lines on multiples of the spacing, every Nth
    // one major, coarsened by the major interval when too dense, automatic when unset or invalid.
    auto gridFails = [](const char* message) { std::cerr << "Editor grid: " << message << '\n'; return true; };
    sle::core::Camera2D gridCamera(48,27);
    gridCamera.setPosition({-25.5f,-3.0f});
    const auto onMultiple = [](float value, double step) { return std::abs(value/step-std::round(value/step)) < 1e-4; };
    const auto unit = sle::EntityPreviewSystem::gridLines(gridCamera,{1.0f,10});
    int majors = 0;
    for (const auto& line : unit) {
        const bool vertical = line.from.x == line.to.x;
        const float at = vertical ? line.from.x : line.from.y;
        if (!onMultiple(at,1.0) && gridFails("unit lines off the step")) return 1;
        if (line.major != onMultiple(at,10.0) && gridFails("major interval")) return 1;
        majors += line.major ? 1 : 0;
    }
    if ((unit.size() < 48+27 || unit.size() > 48+27+4 || majors < 5) && gridFails("unit line count")) return 1;
    if (unit.front().from.x > -25.5f && gridFails("first line starts at or before the view")) return 1;
    gridCamera.setViewport(4800,2700);
    const auto coarse = sle::EntityPreviewSystem::gridLines(gridCamera,{1.0f,10});
    if ((coarse.empty() || coarse.size() > 204) && gridFails("coarse line count")) return 1;
    for (const auto& line : coarse) {
        const float at = line.from.x == line.to.x ? line.from.x : line.from.y;
        if ((!onMultiple(at,100.0) || line.major != onMultiple(at,1000.0)) && gridFails("dense grid not coarsened by the major interval")) return 1;
    }
    const auto automatic = sle::EntityPreviewSystem::gridLines(gridCamera,{});
    if ((automatic.empty() || std::any_of(automatic.begin(),automatic.end(),[](const auto& line) { return line.major; })) &&
        gridFails("automatic grid has no major lines")) return 1;
    for (const sle::EditorGrid invalid : {sle::EditorGrid{-1.0f,10},sle::EditorGrid{std::numeric_limits<float>::quiet_NaN(),10},
        sle::EditorGrid{std::numeric_limits<float>::infinity(),10},sle::EditorGrid{1.0f,-1},sle::EditorGrid{1.0f,1001}}) {
        const auto lines = sle::EntityPreviewSystem::gridLines(gridCamera,invalid);
        if ((invalid.valid() || lines.size() != automatic.size()) && gridFails("invalid grid falls back to automatic")) return 1;
    }
    gridCamera.setPosition({1.0e38f,-1.0e38f});
    const auto far = sle::EntityPreviewSystem::gridLines(gridCamera,{0.001f,10});
    if (far.size() > 204 && gridFails("extreme positions stay bounded")) return 1;
    return 0;
}
