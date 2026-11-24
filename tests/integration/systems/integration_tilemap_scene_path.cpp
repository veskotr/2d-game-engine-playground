#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>

#include <filesystem>
#include <iostream>
#include <stdexcept>

int main()
try {
    using Json = nlohmann::json;
    const auto fixture = std::filesystem::absolute("tests/data/tilemap/Tiles.sleproject");
    sle::entity::Scene tiled;
    const auto legacy = sle::SceneLoader::load("tests/data/scenes/test_tiled_tile_chunks_scene.json", tiled);
    if (!legacy.ok()) throw std::runtime_error(legacy.error());
    int sprites = 0;
    tiled.view<sle::components::SpriteRenderer>([&](auto, auto&) { ++sprites; });
    if (!sprites) throw std::runtime_error("Legacy Tiled scene lost its sprites");

    std::filesystem::current_path(fixture.parent_path().parent_path());
    const auto project = sle::ProjectRuntime::open(fixture);
    if (!project.ok()) throw std::runtime_error(project.error());
    const auto source = project.value().sceneDocument(project.value().startupScene());
    const auto canonical = sle::SceneAuthoring::canonicalScene(Json::parse(source.dump()));
    if (canonical != source || source["tileLayers"][0]["cells"][0]["x"] != -33 ||
        source["tileLayers"][0]["drawOrder"] != 2 || source["tileLayers"][1]["drawOrder"] != 5 ||
        !source["tileLayers"][0]["tileset"].get<std::string>().starts_with("project-asset://"))
        throw std::runtime_error("Tile layers did not retain canonical draw order, sparse cells and UUIDs");

    const auto resolve = [&](const std::string& ref) { return project.value().resolveAsset(ref); };
    sle::entity::Scene scene;
    const auto loaded = sle::SceneLoader::loadData(canonical, scene, fixture.string(), {}, resolve);
    if (!loaded.ok()) throw std::runtime_error(loaded.error());
    const auto& chunks = scene.getTileLayers().at(0).chunks;
    if (!chunks.contains({-2, 0}) || !chunks.contains({-1, 0}) || !chunks.contains({0, 0}) || chunks.size() != 3)
        throw std::runtime_error("Cells did not land in sparse floor-divided chunks");

    auto undersized = canonical;
    undersized["tileLayers"][0]["pixelsPerUnit"] = 100;
    const auto invalidCollision = sle::SceneLoader::loadData(undersized, scene, fixture.string(), {}, resolve);
    if (invalidCollision.ok() || invalidCollision.error().find("collision fixture") == std::string::npos)
        throw std::runtime_error("Native load accepted a collision fixture below the Box2D size limit");

    auto broken = canonical;
    broken["tileLayers"][0]["cells"][0]["tile"] = "ffffffff-ffff-ffff-ffff-ffffffffffff";
    const auto missing = sle::SceneLoader::loadData(broken, scene, fixture.string(), {}, resolve);
    if (missing.ok() || missing.error().find("ffffffff-ffff-ffff-ffff-ffffffffffff") == std::string::npos)
        throw std::runtime_error("Missing tile diagnostic must identify the ID");
    auto staged = scene.createStagingScene();
    const auto preview = sle::SceneLoader::loadData(broken, *staged, fixture.string(), {}, resolve, true);
    if (!preview.ok()) throw std::runtime_error(preview.error());
    scene.replaceWith(*staged);
    if (!scene.getTileLayers().at(0).chunks.at({-2, 0}).cells.at(0).missing)
        throw std::runtime_error("Editor preview must preserve the missing cell");
    return 0;
} catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
}
