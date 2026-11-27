#include <array>
#include "TileLayerLoader.hpp"
#include "SpriteTexture.hpp"

#include <sle/core/Log.hpp>
#include <sle/engine/ProjectRuntime.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <glad/gl.h>
#include <stb_image.h>

#include <algorithm>
#include <cmath>
#include <fstream>
#include <limits>
#include <memory>
#include <set>
#include <string_view>
#include <stdexcept>

namespace sle::detail {
namespace {

using Json = nlohmann::json;

void require(bool condition, const std::string& message)
{
    if (!condition) throw std::invalid_argument(message);
}

void fields(const Json& value, std::initializer_list<std::string_view> allowed)
{
    require(value.is_object(), "Expected tilemap object");
    for (const auto& [key, child] : value.items())
        require(std::find(allowed.begin(), allowed.end(), key) != allowed.end(), "Unknown tilemap field: " + key);
}

std::string id(const Json& value)
{
    const auto text = value.get<std::string>();
    require(text.size() == 36, "Invalid tilemap UUID: " + text);
    for (std::size_t i = 0; i < text.size(); ++i) {
        const auto c = text[i];
        require(i == 8 || i == 13 || i == 18 || i == 23 ? c == '-' :
            (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'), "Invalid tilemap UUID: " + text);
    }
    return text;
}

std::string asset(const Json& value)
{
    const auto ref = value.get<std::string>();
    constexpr std::string_view prefix = "project-asset://";
    require(ref.starts_with(prefix), "Expected tilemap project asset UUID reference: " + ref);
    id(ref.substr(prefix.size()));
    return ref;
}

int integer(const Json& value, int minimum = std::numeric_limits<int>::min())
{
    require(value.is_number_integer() && value.get<double>() >= minimum &&
        value.get<double>() <= std::numeric_limits<int>::max(), "Tilemap integer is outside the supported range");
    return value.get<int>();
}

float finite(const Json& value, float maximum)
{
    require(value.is_number() && std::isfinite(value.get<double>()) &&
        std::abs(value.get<double>()) <= maximum, "Tilemap number is outside the supported range");
    return value.get<float>();
}

std::string name(const Json& value)
{
    const auto text = value.get<std::string>();
    require(text.size() <= 256 && text.find_first_not_of(" \t\r\n") != std::string::npos, "Invalid tilemap name");
    return text;
}

bool validMetadataName(std::string_view text)
{
    if (text.size() > 256) return false;
    // JSON parsing has validated UTF-8. Match the editor's Kotlin whitespace rules.
    while (!text.empty()) {
        const auto first = static_cast<unsigned char>(text.front());
        char32_t codePoint = first;
        if (first < 0x80) {
            text.remove_prefix(1);
        } else if (first < 0xE0) {
            codePoint = ((first & 0x1F) << 6) | (static_cast<unsigned char>(text[1]) & 0x3F);
            text.remove_prefix(2);
        } else if (first < 0xF0) {
            codePoint = ((first & 0x0F) << 12) | ((static_cast<unsigned char>(text[1]) & 0x3F) << 6) |
                (static_cast<unsigned char>(text[2]) & 0x3F);
            text.remove_prefix(3);
        } else {
            return true; // No whitespace code points need four UTF-8 bytes.
        }
        const bool whitespace = (codePoint >= 0x09 && codePoint <= 0x0D) ||
            (codePoint >= 0x1C && codePoint <= 0x20) || codePoint == 0xA0 || codePoint == 0x1680 ||
            (codePoint >= 0x2000 && codePoint <= 0x200A) || codePoint == 0x2028 || codePoint == 0x2029 ||
            codePoint == 0x202F || codePoint == 0x205F || codePoint == 0x3000;
        if (!whitespace) return true;
    }
    return false;
}

void validateGrid(const Json& grid)
{
    fields(grid, {"margin", "spacing", "columns"});
    for (const auto* field : {"margin", "spacing"}) {
        const auto& pair = grid.at(field);
        require(pair.is_array() && pair.size() == 2, std::string("Tileset grid ") + field + " requires two axes");
        for (const auto& axis : pair) integer(axis, 0);
    }
    integer(grid.at("columns"), 1);
}

void validateTileMetadata(const Json& tile)
{
    require(validMetadataName(tile.at("name").get_ref<const std::string&>()), "Invalid tilemap name");
    if (tile.contains("tags")) {
        const auto& tags = tile.at("tags");
        require(tags.is_array() && tags.size() <= 256, "Expected a tile tags array with at most 256 entries");
        std::set<std::string_view> names;
        for (const auto& tag : tags) {
            const auto& text = tag.get_ref<const std::string&>();
            require(validMetadataName(text), "Invalid tile tag");
            require(names.insert(text).second, "Duplicate tile tag: " + text);
        }
    }
    if (tile.contains("properties")) {
        const auto& properties = tile.at("properties");
        require(properties.is_object() && properties.size() <= 256, "Expected a tile properties object with at most 256 entries");
        for (const auto& [key, value] : properties.items()) {
            require(validMetadataName(key), "Invalid tile property name");
            require(value.is_boolean() || (value.is_number() && std::isfinite(value.get<double>())) ||
                (value.is_string() && value.get_ref<const std::string&>().size() <= 4096), "Invalid tile property: " + key);
        }
    }
    require(!tile.contains("animation") || tile.at("animation").is_null(), "Tile animation is reserved and must be null");
}

using CollisionPolygon = std::vector<glm::dvec2>;

std::vector<CollisionPolygon> readCollision(const Json& value)
{
    fields(value, {"mode", "pieces"});
    const auto mode = value.at("mode").get<std::string>();
    const auto& pieces = value.at("pieces");
    require(mode == "none" || mode == "fullCell" || mode == "custom", "Unknown tile collision mode");
    require(pieces.is_array() && pieces.size() <= 4096 && (mode == "custom" || pieces.empty()),
        "Invalid tile collision pieces");

    std::vector<CollisionPolygon> result;
    for (const auto& piece : pieces) {
        require(piece.is_array() && piece.size() >= 3 && piece.size() <= 8,
            "Tile collision fixture requires 3 to 8 vertices");
        CollisionPolygon vertices;
        for (const auto& point : piece) {
            require(point.is_array() && point.size() == 2 && point[0].is_number() && point[1].is_number(),
                "Invalid tile collision vertex");
            const glm::dvec2 vertex{point[0].get<double>(), point[1].get<double>()};
            require(std::isfinite(vertex.x) && std::isfinite(vertex.y) &&
                vertex.x >= 0 && vertex.y >= 0 &&
                vertex.x <= std::numeric_limits<int>::max() && vertex.y <= std::numeric_limits<int>::max(),
                "Tile collision vertex is outside pixel bounds");
            vertices.push_back(vertex);
        }
        for (std::size_t i = 0; i < vertices.size(); ++i) {
            const auto next = (i + 1) % vertices.size();
            const auto edge = vertices[next] - vertices[i];
            for (std::size_t j = 0; j < vertices.size(); ++j) {
                if (j == i || j == next) continue;
                const auto delta = vertices[j] - vertices[i];
                require(edge.x * delta.y - edge.y * delta.x > 0,
                    "Tile collision fixture must be strictly convex and counterclockwise");
            }
        }
        result.push_back(std::move(vertices));
    }
    return result;
}

struct Tileset
{
    std::string spritesheet;
    glm::ivec2 cellPixels{1};
    std::map<std::string, std::string> regionsByTile;
    struct TileCollision {
        bool fullCell;
        std::vector<CollisionPolygon> pieces;
    };
    std::map<std::string, TileCollision> collisionByTile;
    std::map<std::string, ProjectRuntime::SpriteRegion> regions;
    std::shared_ptr<renderer::Texture> texture;
    int imageWidth = 0;
    int imageHeight = 0;

    const ProjectRuntime::SpriteRegion* findRegion(const std::string& regionId) const
    {
        const auto region = regions.find(regionId);
        if (region == regions.end() || region->second.x > imageWidth - region->second.width ||
            region->second.y > imageHeight - region->second.height)
            return nullptr;
        return &region->second;
    }
};

std::string resolvedAsset(const SceneLoader::AssetLookup& resolve, const std::string& ref)
{
    require(static_cast<bool>(resolve), "No project asset resolver for " + ref);
    const auto path = resolve(ref);
    require(!path.empty(), "Missing project asset " + ref);
    return path;
}

Tileset readTileset(const std::string& ref, const SceneLoader::AssetLookup& resolve)
{
    const auto path = resolvedAsset(resolve, ref);
    require(std::filesystem::is_regular_file(path) && std::filesystem::file_size(path) <= 4 * 1024 * 1024,
        "Missing or oversized tileset: " + path);
    std::ifstream file(path);
    const auto json = Json::parse(file);
    const auto version = integer(json.at("version"), 1);
    require(json.at("format") == "sle-tileset" && (version == 1 || version == 2), "Unsupported tileset format/version");
    if (version == 2) {
        fields(json, {"format", "version", "spritesheet", "tileWidth", "tileHeight", "grid", "tiles"});
        validateGrid(json.at("grid"));
    } else {
        fields(json, {"format", "version", "spritesheet", "tileWidth", "tileHeight", "tiles"});
    }
    Tileset set;
    set.spritesheet = asset(json.at("spritesheet"));
    set.cellPixels = {integer(json.at("tileWidth"), 1), integer(json.at("tileHeight"), 1)};
    const auto& tiles = json.at("tiles");
    require(tiles.is_array() && tiles.size() <= 4096, "Expected a tiles array with at most 4096 entries");
    for (const auto& tile : tiles) {
        if (version == 2) {
            fields(tile, {"id", "name", "region", "collision", "tags", "properties", "animation"});
            validateTileMetadata(tile);
        } else {
            fields(tile, {"id", "name", "region", "collision"});
            name(tile.at("name"));
        }
        const auto tileId = id(tile.at("id"));
        require(set.regionsByTile.emplace(tileId, id(tile.at("region"))).second, "Duplicate tile UUID " + tileId);
        if (tile.contains("collision")) {
            const auto& collision = tile.at("collision");
            auto pieces = readCollision(collision);
            const bool fullCell = collision.at("mode") == "fullCell";
            if (fullCell || !pieces.empty())
                set.collisionByTile.emplace(tileId, Tileset::TileCollision{fullCell, std::move(pieces)});
        }
    }
    return set;
}

void readSpritesheet(Tileset& set, const std::string& ref, const SceneLoader::AssetLookup& resolve,
    const SceneLoader::TextureFilterLookup& filter, bool uploadTextures)
{
    const auto image = resolvedAsset(resolve, ref);
    set.regions = ProjectRuntime::readSpriteRegions(image);
    if (uploadTextures) {
        const auto sampler = filter ? filter(image) : renderer::TextureFilter::Default;
        set.texture = loadSpriteTexture(image, sampler, true);
        require(static_cast<bool>(set.texture), "Cannot load spritesheet image " + ref + " at " + image);
        set.imageWidth = set.texture->getWidth();
        set.imageHeight = set.texture->getHeight();
    } else {
        int width, height, channels;
        const std::unique_ptr<stbi_uc, decltype(&stbi_image_free)> pixels(
            stbi_load(image.c_str(), &width, &height, &channels, 0), stbi_image_free);
        require(static_cast<bool>(pixels), "Invalid or missing spritesheet image " + ref + " at " + image);
        set.imageWidth = width;
        set.imageHeight = height;
    }
}

} // namespace

Json canonicalTileLayers(const Json& data)
{
    require(data.is_array(), "tileLayers must be an array");
    Json result = data;
    std::set<std::string> ids;
    std::set<int> orders;
    for (auto& layer : result) {
        fields(layer, {"id", "name", "tileset", "origin", "pixelsPerUnit", "drawOrder",
            "visible", "locked", "collisionEnabled", "cells"});
        const auto layerId = id(layer.at("id"));
        require(ids.insert(layerId).second, "Duplicate tile layer UUID " + layerId);
        name(layer.at("name"));
        asset(layer.at("tileset"));
        const auto order = integer(layer.at("drawOrder"), 0);
        require(orders.insert(order).second, "Duplicate tile layer draw order " + std::to_string(order));
        if (!layer.contains("origin")) layer["origin"] = Json::array({0, 0});
        const auto& origin = layer.at("origin");
        require(origin.is_array() && origin.size() == 2, "Tile layer origin must have two coordinates");
        for (const auto& axis : origin) finite(axis, 1.0e9f);
        if (!layer.contains("pixelsPerUnit")) layer["pixelsPerUnit"] = 1;
        require(finite(layer.at("pixelsPerUnit"), 1.0e6f) >= 0.001f, "Invalid tile layer pixelsPerUnit");
        for (const auto* flag : {"visible", "locked", "collisionEnabled"}) {
            if (!layer.contains(flag)) layer[flag] = std::string_view(flag) != "locked";
            require(layer.at(flag).is_boolean(), std::string("Expected boolean tile layer ") + flag);
        }
        auto& cells = layer.at("cells");
        require(cells.is_array(), "Tile layer cells must be an array");
        std::set<entity::TileCoordinate> coordinates;
        for (const auto& cell : cells) {
            fields(cell, {"x", "y", "tile"});
            const auto x = integer(cell.at("x"));
            const auto y = integer(cell.at("y"));
            require(coordinates.emplace(x, y).second, "Duplicate tile cell in layer " + layerId);
            id(cell.at("tile"));
        }
        std::sort(cells.begin(), cells.end(), [](const Json& a, const Json& b) {
            return std::pair(a["y"].get<int>(), a["x"].get<int>()) < std::pair(b["y"].get<int>(), b["x"].get<int>());
        });
    }
    std::sort(result.begin(), result.end(), [](const Json& a, const Json& b) { return a["drawOrder"] < b["drawOrder"]; });
    return result;
}

std::vector<entity::TileLayer> loadTileLayers(const Json& data, const SceneLoader::AssetLookup& resolve,
    const SceneLoader::TextureFilterLookup& filter, bool allowMissing, bool uploadTextures, glm::vec2 gridPixels, int chunkCells)
{
    uploadTextures = uploadTextures && glGetString && glGetString(GL_VERSION);
    std::vector<entity::TileLayer> layers;
    std::map<std::string, Tileset> tilesets;
    for (const auto& source : canonicalTileLayers(data)) {
        entity::TileLayer layer;
        layer.id = source["id"];
        layer.name = source["name"];
        layer.tileset = source["tileset"];
        layer.drawOrder = source["drawOrder"];
        layer.pixelsPerUnit = source["pixelsPerUnit"];
        layer.origin = {source["origin"][0].get<float>(), source["origin"][1].get<float>()};
        layer.visible = source["visible"];
        layer.locked = source["locked"];
        layer.collisionEnabled = source["collisionEnabled"];

        const auto report = [&](const std::string& message) {
            const auto diagnostic = "Tile layer '" + layer.name + "' (" + layer.id + "), tileset " + layer.tileset + ": " + message;
            if (!allowMissing) throw std::invalid_argument(diagnostic);
            core::Log::warn("{}", diagnostic);
        };
        auto [entry, inserted] = tilesets.try_emplace(layer.tileset);
        auto& set = entry->second;
        if (inserted) {
            try {
                set = readTileset(layer.tileset, resolve);
                readSpritesheet(set, set.spritesheet, resolve, filter, uploadTextures);
                if (!allowMissing) {
                    for (const auto& [tileId, regionId] : set.regionsByTile)
                        require(set.findRegion(regionId) != nullptr,
                            "Tile " + tileId + " has missing or invalid region " + regionId + " in " + set.spritesheet);
                }
            } catch (const std::exception& error) {
                report(error.what());
            }
        }
        layer.cellSize = (gridPixels.x > 0 ? gridPixels : glm::vec2(set.cellPixels)) / layer.pixelsPerUnit;
        layer.chunkCells = chunkCells;
        for (const auto& [tileId, collision] : set.collisionByTile) {
            const auto* region = set.findRegion(set.regionsByTile.at(tileId));
            if (!region) continue;
            entity::TileCollision geometry;
            geometry.fullCell = collision.fullCell;
            bool valid = true;
            if (geometry.fullCell && layer.collisionEnabled) {
                const std::array<glm::vec2, 4> rectangle{{{0, 0}, {layer.cellSize.x, 0},
                    layer.cellSize, {0, layer.cellSize.y}}};
                valid = physics::PhysicsWorld::isConvexPolygonGeometryValid(rectangle);
            }
            for (const auto& piece : collision.pieces) {
                auto& scaled = geometry.pieces.emplace_back();
                scaled.reserve(piece.size());
                for (const auto& vertex : piece) {
                    if (vertex.x > region->width || vertex.y > region->height) valid = false;
                    scaled.push_back(glm::vec2(vertex) / layer.pixelsPerUnit);
                }
                if (layer.collisionEnabled && !physics::PhysicsWorld::isConvexPolygonGeometryValid(scaled))
                    valid = false;
            }
            if (!valid) {
                report("Tile " + tileId + " has a collision fixture outside its region or Box2D limits at the layer scale");
                continue;
            }
            layer.collisionByTile.emplace(tileId, std::move(geometry));
        }
        std::set<std::string> reported;
        for (const auto& cellSource : source["cells"]) {
            entity::TileCell cell;
            const auto x = cellSource["x"].get<int>();
            const auto y = cellSource["y"].get<int>();
            cell.coordinate = {x, y};
            cell.tileId = cellSource["tile"];
            cell.lowerLeft = layer.origin + glm::vec2(x, y) * layer.cellSize;
            cell.size = layer.cellSize;
            const auto tile = set.regionsByTile.find(cell.tileId);
            const auto* region = tile == set.regionsByTile.end() ? nullptr : set.findRegion(tile->second);
            if (region) {
                cell.size = glm::vec2(region->width, region->height) / layer.pixelsPerUnit;
                if (set.texture)
                    cell.region = renderer::TextureRegion::fromPixels(set.texture, region->x,
                        set.imageHeight - region->y - region->height, region->width, region->height);
            } else {
                cell.missing = true;
                if (reported.insert(cell.tileId).second)
                    report(tile == set.regionsByTile.end() ? "Missing tile UUID " + cell.tileId :
                        "Tile " + cell.tileId + " has missing or invalid region " + tile->second + " in " + set.spritesheet);
            }
            const auto chunkIndex = [chunkCells](int value) { return value / chunkCells - (value % chunkCells < 0 ? 1 : 0); };
            auto& chunk = layer.chunks[{chunkIndex(x), chunkIndex(y)}];
            const auto collision = layer.collisionByTile.find(cell.tileId);
            const auto boundsSize = collision != layer.collisionByTile.end() && collision->second.fullCell ?
                glm::max(cell.size, layer.cellSize) : cell.size;
            const auto upperRight = cell.lowerLeft + boundsSize;
            chunk.lowerLeft = chunk.cells.empty() ? cell.lowerLeft : glm::min(chunk.lowerLeft, cell.lowerLeft);
            chunk.upperRight = chunk.cells.empty() ? upperRight : glm::max(chunk.upperRight, upperRight);
            chunk.cells.push_back(std::move(cell));
        }
        layers.push_back(std::move(layer));
    }
    return layers;
}

}
