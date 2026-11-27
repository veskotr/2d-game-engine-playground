#include "TilemapLoader.hpp"
#include "ComponentRegistry.hpp"
#include <sle/core/Log.hpp>
#include <algorithm>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <set>

namespace sle::detail {
namespace {
using Json = nlohmann::json;
void require(bool condition, const std::string& message)
{
    if (!condition) throw std::invalid_argument("Tilemap: " + message);
}
void fields(const Json& source, std::initializer_list<const char*> names)
{
    require(source.is_object(), "expected object");
    std::set<std::string> expected;
    for (const auto* name : names) expected.insert(name);
    require(source.size() == expected.size(), "unsupported or missing fields");
    for (const auto& [name, value] : source.items()) require(expected.contains(name), "unsupported field " + name);
}
int integer(const Json& value, int minimum = std::numeric_limits<int>::min(), int maximum = std::numeric_limits<int>::max())
{
    require(value.is_number_integer() && value.get<double>() >= minimum && value.get<double>() <= maximum,
        "integer outside supported range");
    return value.get<int>();
}
std::string text(const Json& value)
{
    require(value.is_string(), "expected string");
    auto result = value.get<std::string>();
    require(!result.empty() && result.size() <= 256 && result.find_first_not_of(" \t\r\n") != std::string::npos,
        "expected nonempty name");
    return result;
}
std::string uuid(const Json& value)
{
    const auto id = text(value);
    require(id.size() == 36, "invalid UUID");
    for (std::size_t i = 0; i < id.size(); ++i)
        require(i == 8 || i == 13 || i == 18 || i == 23 ? id[i] == '-' :
            std::string_view("0123456789abcdef").find(id[i]) != std::string_view::npos, "invalid UUID");
    return id;
}
}

nlohmann::json canonicalTilemap(const Json& source)
{
    require(source.dump().size() <= 16 * 1024 * 1024, "asset exceeds 16 MiB");
    fields(source, {"format", "version", "name", "grid", "tilesets", "streaming", "layers"});
    require(source.at("format") == "sle-tilemap" && integer(source.at("version")) == 1, "unsupported format/version");
    text(source.at("name"));
    const auto& grid = source.at("grid");
    fields(grid, {"cellWidth", "cellHeight", "pixelsPerUnit"});
    integer(grid.at("cellWidth"), 1, 65536);
    integer(grid.at("cellHeight"), 1, 65536);
    const auto& ppu = grid.at("pixelsPerUnit");
    require(ppu.is_number() && std::isfinite(ppu.get<double>()) && ppu.get<double>() >= 0.001 && ppu.get<double>() <= 1.0e6,
        "invalid pixelsPerUnit");
    const auto& streaming = source.at("streaming");
    fields(streaming, {"mode", "chunkCells", "loadRadius", "unloadRadius"});
    require(streaming.at("mode") == "always" || streaming.at("mode") == "aroundFocus", "invalid streaming mode");
    integer(streaming.at("chunkCells"), 1, 1024);
    const auto load = integer(streaming.at("loadRadius"), 0, 1024);
    integer(streaming.at("unloadRadius"), load, 1024);
    require(source.at("tilesets").is_array() && source.at("tilesets").size() <= 256, "invalid tilesets array");
    std::set<int> sets;
    for (const auto& set : source.at("tilesets")) {
        fields(set, {"key", "asset"});
        require(sets.insert(integer(set.at("key"), 0)).second, "duplicate tileset key");
        const auto ref = text(set.at("asset"));
        require(ref.starts_with("project-asset://"), "tileset must use a project asset UUID");
        uuid(ref.substr(16));
    }
    require(source.at("layers").is_array() && source.at("layers").size() <= 4096, "invalid layers array");
    auto result = source;
    std::set<std::string> ids;
    std::set<int> orders;
    std::size_t count = 0;
    for (auto& layer : result["layers"]) {
        fields(layer, {"id", "name", "drawOrder", "visible", "locked", "collision", "cells"});
        require(ids.insert(uuid(layer.at("id"))).second, "duplicate layer UUID");
        text(layer.at("name"));
        require(orders.insert(integer(layer.at("drawOrder"), 0)).second, "duplicate layer draw order");
        for (const auto* flag : {"visible", "locked", "collision"}) require(layer.at(flag).is_boolean(), "expected layer boolean");
        auto& cells = layer.at("cells");
        require(cells.is_array(), "expected cells array");
        count += cells.size();
        require(count <= 262144, "map exceeds 262144 cells");
        std::set<entity::TileCoordinate> coordinates;
        for (const auto& cell : cells) {
            fields(cell, {"x", "y", "set", "tile"});
            require(coordinates.emplace(integer(cell.at("x")), integer(cell.at("y"))).second, "duplicate cell coordinate");
            require(sets.contains(integer(cell.at("set"), 0)), "cell references unknown tileset key");
            uuid(cell.at("tile"));
        }
        std::sort(cells.begin(), cells.end(), [](const Json& a, const Json& b) {
            return std::pair(a.at("y").get<int>(), a.at("x").get<int>()) < std::pair(b.at("y").get<int>(), b.at("x").get<int>());
        });
    }
    std::sort(result["layers"].begin(), result["layers"].end(), [](const Json& a, const Json& b) { return a.at("drawOrder") < b.at("drawOrder"); });
    std::sort(result["tilesets"].begin(), result["tilesets"].end(), [](const Json& a, const Json& b) { return a.at("key") < b.at("key"); });
    return result;
}

std::vector<entity::TileLayer> loadTilemap(const std::string& asset,
    const SceneLoader::AssetLookup& resolve, const SceneLoader::TextureFilterLookup& filter,
    bool allowMissing, bool uploadTextures)
{
    const auto path = resolve && asset.starts_with("project-asset://") ? resolve(asset) : componentdata::resolveAssetPath(asset);
    require(!path.empty() && std::filesystem::is_regular_file(path) && std::filesystem::file_size(path) <= 16 * 1024 * 1024,
        "missing or oversized asset " + asset);
    std::ifstream file(path, std::ios::binary);
    Json source;
    file >> source;
    const auto map = canonicalTilemap(source);
    if (map["streaming"]["mode"] != "always")
        core::Log::warn("Tilemap '{}': focus streaming is not available yet; loading the whole map", asset);
    std::vector<entity::TileLayer> result;
    for (const auto& layer : map["layers"]) {
        for (const auto& set : map["tilesets"]) {
            Json cells = Json::array();
            for (const auto& cell : layer["cells"]) if (cell["set"] == set["key"])
                cells.push_back({{"x", cell["x"]}, {"y", cell["y"]}, {"tile", cell["tile"]}});
            Json legacy = {{"id", layer["id"]}, {"name", layer["name"]}, {"tileset", set["asset"]},
                {"origin", {0, 0}}, {"pixelsPerUnit", map["grid"]["pixelsPerUnit"]}, {"drawOrder", layer["drawOrder"]},
                {"visible", layer["visible"]}, {"locked", layer["locked"]}, {"collisionEnabled", layer["collision"]}, {"cells", cells}};
            auto resolved = loadTileLayers(Json::array({legacy}), resolve, filter, allowMissing, uploadTextures,
                {map["grid"]["cellWidth"].get<float>(), map["grid"]["cellHeight"].get<float>()}, map["streaming"]["chunkCells"].get<int>());
            auto& output = resolved.front();
            // Private runtime partitions preserve tileset-qualified tile identities.
            output.id += ":" + std::to_string(set["key"].get<int>());
            result.push_back(std::move(output));
        }
    }
    return result;
}
}
