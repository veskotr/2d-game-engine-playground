#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>

#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>

namespace {

using Json = nlohmann::json;

void checkTilesetVersions(const Json& source, const sle::entity::Scene& legacy,
    const std::filesystem::path& fixture, const sle::SceneLoader::AssetLookup& resolve)
{
    const auto reference = source["tileLayers"][0]["tileset"].get<std::string>();
    const auto v1 = Json::parse(std::ifstream(resolve(reference)));
    auto v2 = v1;
    v2["version"] = 2;
    v2["grid"] = {{"margin", {2, 3}}, {"spacing", {4, 5}}, {"columns", 1}};
    v2["tiles"][0]["tags"] = {"terrain", "slope"};
    v2["tiles"][0]["properties"] = {{"surface", "stone"}, {"friction", 0.75}, {"solid", true}};
    v2["tiles"][0]["animation"] = nullptr;

    const auto path = std::filesystem::temp_directory_path() / ("sle-tileset-versions-" +
        std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".tileset.json");
    struct Cleanup {
        std::filesystem::path path;
        ~Cleanup() { std::error_code error; std::filesystem::remove(path, error); }
    } cleanup{path};
    const auto load = [&](const std::string& input, sle::entity::Scene& scene) {
        std::ofstream file(path, std::ios::binary);
        file << input;
        file.close();
        if (!file) throw std::runtime_error("Could not write tileset test file");
        return sle::SceneLoader::loadData(source, scene, fixture.string(), {}, [&](const std::string& ref) {
            return ref == reference ? path.generic_string() : resolve(ref);
        });
    };

    sle::entity::Scene latest;
    const auto loaded = load(v2.dump(), latest);
    if (!loaded.ok()) throw std::runtime_error("Could not load a version 2 tileset: " + loaded.error());
    const auto& previousLayers = legacy.getTileLayers();
    const auto& latestLayers = latest.getTileLayers();
    if (previousLayers.size() != latestLayers.size()) throw std::runtime_error("V2 changed tile layer count");
    for (std::size_t i = 0; i < previousLayers.size(); ++i) {
        const auto& previous = previousLayers[i];
        const auto& current = latestLayers[i];
        if (previous.cellSize != current.cellSize || previous.chunks.size() != current.chunks.size() ||
            previous.drawOrder != current.drawOrder)
            throw std::runtime_error("V2 changed layer drawing geometry");
        for (const auto& [coordinate, chunk] : previous.chunks) {
            const auto& currentChunk = current.chunks.at(coordinate);
            if (chunk.cells.size() != currentChunk.cells.size() || chunk.lowerLeft != currentChunk.lowerLeft ||
                chunk.upperRight != currentChunk.upperRight)
                throw std::runtime_error("V2 changed tile chunk bounds");
            for (std::size_t cell = 0; cell < chunk.cells.size(); ++cell) {
                const auto& a = chunk.cells[cell];
                const auto& b = currentChunk.cells[cell];
                if (b.missing || a.tileId != b.tileId || a.lowerLeft != b.lowerLeft || a.size != b.size)
                    throw std::runtime_error("V2 changed placed tile drawing geometry");
            }
        }
        if (previous.collisionByTile.size() != current.collisionByTile.size())
            throw std::runtime_error("V2 changed the number of tiles with collision");
        for (const auto& [tile, collision] : previous.collisionByTile) {
            const auto& currentCollision = current.collisionByTile.at(tile);
            if (collision.fullCell != currentCollision.fullCell || collision.pieces != currentCollision.pieces)
                throw std::runtime_error("V2 changed tile collision geometry");
        }
    }

    const auto reject = [&](const std::string& input, const std::string& reason) {
        sle::entity::Scene scene;
        if (load(input, scene).ok()) throw std::runtime_error("Accepted malformed tileset: " + reason);
    };
    const auto replace = [&](const char* field, const Json& value) {
        auto broken = v2;
        broken[Json::json_pointer(field)] = value;
        reject(broken.dump(), field);
    };

    auto missingGrid = v2;
    missingGrid.erase("grid");
    reject(missingGrid.dump(), "missing grid");
    replace("/grid", nullptr);
    replace("/grid/margin", {0});
    replace("/grid/margin", {-1, 0});
    replace("/grid/spacing", {0, 1.25});
    replace("/grid/columns", 0);
    replace("/grid/columns", "1");
    replace("/grid/extra", 0);
    replace("/extra", false);
    replace("/version", "2");
    replace("/version", 3);
    replace("/tiles/0/extra", false);
    replace("/tiles/0/tags", "terrain");
    replace("/tiles/0/tags", {"terrain", "terrain"});
    replace("/tiles/0/tags", {" \t\r\n"});
    replace("/tiles/0/tags", {"\xC2\xA0"});
    replace("/tiles/0/tags", {1});
    replace("/tiles/0/tags", {std::string(255, 'a') + "\xC3\xA9"});
    replace("/tiles/0/properties", Json::array());
    replace("/tiles/0/properties", {{" ", true}});
    replace("/tiles/0/properties", {{std::string(255, 'a') + "\xC3\xA9", true}});
    replace("/tiles/0/properties/surface", std::string(4095, 'a') + "\xC3\xA9");
    replace("/tiles/0/properties/surface", Json::array({1}));
    replace("/tiles/0/properties/surface", {{"nested", true}});
    replace("/tiles/0/properties/surface", nullptr);
    replace("/tiles/0/animation", Json::object());

    auto tooMany = v2;
    auto& tooManyTags = tooMany["tiles"][0]["tags"] = Json::array();
    for (int i = 0; i < 257; ++i) tooManyTags.push_back("tag-" + std::to_string(i));
    reject(tooMany.dump(), "257 tags");
    tooMany = v2;
    auto& tooManyProperties = tooMany["tiles"][0]["properties"] = Json::object();
    for (int i = 0; i < 257; ++i) tooManyProperties["property-" + std::to_string(i)] = true;
    reject(tooMany.dump(), "257 properties");
    auto overflow = v2.dump();
    overflow.replace(overflow.find("0.75"), 4, "1e400");
    reject(overflow, "nonfinite property number");

    auto legacyWithMetadata = v1;
    legacyWithMetadata["grid"] = v2["grid"];
    reject(legacyWithMetadata.dump(), "v1 with grid");
    legacyWithMetadata = v1;
    legacyWithMetadata["tiles"][0]["tags"] = v2["tiles"][0]["tags"];
    reject(legacyWithMetadata.dump(), "v1 with tags");
}

void checkNativeMaps(const Json& legacy, const sle::SceneLoader::AssetLookup& resolve)
{
    const auto path = std::filesystem::temp_directory_path() / ("sle-map-" +
        std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()) + ".tilemap.json");
    struct Cleanup { std::filesystem::path path; ~Cleanup() { std::error_code ec; std::filesystem::remove(path, ec); } } cleanup{path};
    const auto tile = legacy["tileLayers"][0]["cells"][0]["tile"];
    const auto set = legacy["tileLayers"][0]["tileset"];
    Json map = {{"format", "sle-tilemap"}, {"version", 1}, {"name", "Shared map"},
        {"grid", {{"cellWidth", 32}, {"cellHeight", 16}, {"pixelsPerUnit", 1}}},
        {"streaming", {{"mode", "always"}, {"chunkCells", 8}, {"loadRadius", 2}, {"unloadRadius", 3}}},
        {"tilesets", Json::array({{{"key", 0}, {"asset", set}}, {{"key", 1}, {"asset", set}}})},
        {"layers", Json::array({{{"id", "11111111-1111-4111-8111-111111111111"}, {"name", "Ground"},
            {"drawOrder", 4}, {"visible", true}, {"locked", false}, {"collision", true},
            {"cells", Json::array({{{"x", -9}, {"y", 0}, {"set", 0}, {"tile", tile}},
                {{"x", 1}, {"y", 0}, {"set", 1}, {"tile", tile}}})}}})}};
    const Json placement = {{"Tilemap", {{"asset", path.generic_string()}, {"sortingOffset", -2}}},
        {"Transform", {{"position", {50, 70}}}}};
    const Json scene = {{"entities", Json::array({{{"key", "first"}, {"components", placement}},
        {{"key", "second"}, {"components", placement}}})}};
    const auto load = [&](const Json& input, sle::entity::Scene& target) {
        std::ofstream out(path); out << input; out.close();
        return sle::SceneLoader::loadData(scene, target, "maps.scene.json", {}, [&](const std::string& ref) {
            return ref == path.generic_string() ? path.generic_string() : resolve(ref);
        });
    };
    sle::entity::Scene target;
    auto loaded = load(map, target);
    if (!loaded.ok()) throw std::runtime_error(loaded.error());
    const auto& maps = target.getTilemapPlacements();
    if (maps.size() != 2 || maps[0].entityId == maps[1].entityId || maps[0].layers.size() != 2)
        throw std::runtime_error("Shared map placements or multi-tileset layer were lost");
    const auto& first = maps[0].layers[0];
    if (first.origin != glm::vec2(0) || first.cellSize != glm::vec2(32, 16) ||
        !first.chunks.contains({-2, 0}) || first.chunks.at({-2, 0}).cells[0].lowerLeft != glm::vec2(-288, 0))
        throw std::runtime_error("Map grid, local zero origin, or configured negative chunk coordinates changed");
    // Lua component mutation stages the asset before publishing a new reference.
    { std::ofstream out(path); out << map; }
    sle::Runtime runtime(sle::core::EngineConfig{});
    runtime.setEditorAssetRoot(std::filesystem::path(resolve(set.get<std::string>())).parent_path());
    sle::ScriptApiImpl api(runtime);
    const auto owner = runtime.getScene().createEntity();
    std::string error;
    if (!api.setComponent({owner.getID()}, "Tilemap", {{"asset", path.generic_string()}}, error))
        throw std::runtime_error(error);
    if (runtime.getScene().getTilemapPlacements().size() != 1)
        throw std::runtime_error("Lua Tilemap insertion did not stage map resources");
    if (api.setComponent({owner.getID()}, "Tilemap", {{"asset", "missing.tilemap.json"}}, error) ||
        api.getComponent({owner.getID()}, "Tilemap")["asset"] != path.generic_string())
        throw std::runtime_error("Failed Lua map replacement changed the authored component");
    if (!api.removeComponent({owner.getID()}, "Tilemap", error) || !runtime.getScene().getTilemapPlacements().empty())
        throw std::runtime_error("Removing Lua Tilemap retained resolved resources");
    const auto reject = [&](const char* pointer, const Json& value) {
        auto bad = map; bad[Json::json_pointer(pointer)] = value;
        sle::entity::Scene rejected;
        if (load(bad, rejected).ok()) throw std::runtime_error(std::string("Accepted invalid map ") + pointer);
    };
    reject("/version", 2); reject("/version", "1"); reject("/layers/0/origin", {2, 3});
    reject("/layers/0/cells/0/set", 9); reject("/layers/0/cells/0/x", 1.5);
    reject("/layers/0/cells/0/tile", "broken"); reject("/grid/cellWidth", 0);
    reject("/grid/pixelsPerUnit", 0); reject("/streaming/chunkCells", 0);
    reject("/streaming/unloadRadius", 1); reject("/tilesets/1/key", 0);
    reject("/layers/0/cells/1", map["layers"][0]["cells"][0]);
    reject("/layers/1", map["layers"][0]);
}

} // namespace

int main()
try {
    using Json = nlohmann::json;
    const auto catalog = sle::EntityAuthoring::componentCatalog();
    if (!catalog.contains("Tilemap") || catalog["Tilemap"]["defaults"] != Json{{"asset", ""}, {"sortingOffset", 0}})
        throw std::runtime_error("Tilemap placement component must expose only asset and sortingOffset");
    sle::entity::Scene missingMap;
    const auto missingMapResult = sle::SceneLoader::loadData({{"entities", Json::array({
        {{"key", "map"}, {"components", {{"Tilemap", {{"asset", "missing.tilemap.json"}}}}}}
    })}}, missingMap, "test.scene.json");
    if (missingMapResult.ok()) throw std::runtime_error("Missing tilemap asset must fail scene loading");
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
    checkTilesetVersions(canonical, scene, fixture, resolve);
    checkNativeMaps(canonical, resolve);

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
