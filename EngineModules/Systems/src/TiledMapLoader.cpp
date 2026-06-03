#include <sle/engine/TiledMapLoader.hpp>

#include <sle/core/Log.hpp>
#include <sle/engine/ChunkMapComponent.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/renderer/TextureRegion.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/engine/ChunkManager.hpp>

#include <nlohmann/json.hpp>

#include <algorithm>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace sle {
namespace {

using Json = nlohmann::json;
namespace fs = std::filesystem;

constexpr std::uint32_t kTiledFlipHorizontal = 0x80000000u;
constexpr std::uint32_t kTiledFlipVertical = 0x40000000u;
constexpr std::uint32_t kTiledFlipDiagonal = 0x20000000u;
constexpr std::uint32_t kTiledFlipMask =
    kTiledFlipHorizontal | kTiledFlipVertical | kTiledFlipDiagonal;

struct TilesetInfo
{
    int firstGid = 0;
    int tileWidth = 0;
    int tileHeight = 0;
    int columns = 0;
    int tileCount = 0;
    int spacing = 0;
    int margin = 0;
    int imageWidth = 0;
    int imageHeight = 0;
    std::string imagePath;
    std::shared_ptr<sle::renderer::Texture> texture;
};

std::string resolveAssetPath(const std::string& relativePath)
{
    fs::path path(relativePath);
    if (fs::exists(path))
        return path.lexically_normal().generic_string();

    fs::path current = fs::current_path();
    while (!current.empty())
    {
        fs::path candidate = current / relativePath;
        if (fs::exists(candidate))
            return candidate.lexically_normal().generic_string();

        if (current == current.root_path())
            break;
        current = current.parent_path();
    }

    return relativePath;
}

std::string resolvePathRelativeTo(const fs::path& basePath, const std::string& rawPath)
{
    fs::path path(rawPath);
    if (path.is_absolute() && fs::exists(path))
        return path.lexically_normal().generic_string();

    const fs::path candidate = basePath / path;
    if (fs::exists(candidate))
        return candidate.lexically_normal().generic_string();

    return resolveAssetPath(rawPath);
}

glm::vec2 readVec2Property(const Json& value, const glm::vec2& fallback)
{
    if (!value.is_array() || value.size() < 2)
        return fallback;

    return {value[0].get<float>(), value[1].get<float>()};
}

Json readPropertiesObject(const Json& objectJson)
{
    Json result = Json::object();
    if (!objectJson.contains("properties") || !objectJson["properties"].is_array())
        return result;

    for (const auto& propertyJson : objectJson["properties"])
    {
        if (!propertyJson.is_object() || !propertyJson.contains("name") || !propertyJson.contains("value"))
            continue;

        const std::string name = propertyJson["name"].get<std::string>();
        result[name] = propertyJson["value"];
    }

    return result;
}

std::optional<TilesetInfo> parseEmbeddedTileset(
    const Json& tilesetJson,
    const fs::path& tmjDirectory,
    const std::string& tmjPath)
{
    if (tilesetJson.contains("source"))
    {
        sle::core::Log::warn(
            "Tiled map '{}': external TSJ tilesets are not supported in this first slice ('{}')",
            tmjPath,
            tilesetJson["source"].get<std::string>());
        return std::nullopt;
    }

    if (!tilesetJson.contains("firstgid") ||
        !tilesetJson.contains("tilewidth") ||
        !tilesetJson.contains("tileheight") ||
        !tilesetJson.contains("columns") ||
        !tilesetJson.contains("tilecount") ||
        !tilesetJson.contains("image") ||
        !tilesetJson.contains("imagewidth") ||
        !tilesetJson.contains("imageheight"))
    {
        sle::core::Log::warn("Tiled map '{}': tileset is missing required fields, skipping", tmjPath);
        return std::nullopt;
    }

    TilesetInfo info;
    info.firstGid = tilesetJson["firstgid"].get<int>();
    info.tileWidth = tilesetJson["tilewidth"].get<int>();
    info.tileHeight = tilesetJson["tileheight"].get<int>();
    info.columns = tilesetJson["columns"].get<int>();
    info.tileCount = tilesetJson["tilecount"].get<int>();
    info.spacing = tilesetJson.value("spacing", 0);
    info.margin = tilesetJson.value("margin", 0);
    info.imageWidth = tilesetJson["imagewidth"].get<int>();
    info.imageHeight = tilesetJson["imageheight"].get<int>();
    info.imagePath = resolvePathRelativeTo(tmjDirectory, tilesetJson["image"].get<std::string>());
    return info;
}

const TilesetInfo* findTilesetForGid(const std::vector<TilesetInfo>& tilesets, int gid)
{
    const TilesetInfo* best = nullptr;
    for (const auto& tileset : tilesets)
    {
        if (gid < tileset.firstGid)
            continue;

        if (!best || tileset.firstGid > best->firstGid)
            best = &tileset;
    }
    return best;
}

renderer::TextureRegion makeRegionFromTiledTile(
    const TilesetInfo& tileset,
    int localTileId)
{
    renderer::TextureRegion region;
    region.texture = tileset.texture;

    if (!tileset.texture || tileset.columns <= 0 || tileset.tileWidth <= 0 || tileset.tileHeight <= 0 ||
        tileset.imageWidth <= 0 || tileset.imageHeight <= 0)
    {
        return region;
    }

    const int column = localTileId % tileset.columns;
    const int row = localTileId / tileset.columns;

    const int x = tileset.margin + column * (tileset.tileWidth + tileset.spacing);
    const int yTop = tileset.margin + row * (tileset.tileHeight + tileset.spacing);
    const int yFlipped = tileset.imageHeight - yTop - tileset.tileHeight;

    return renderer::TextureRegion::fromPixels(
        tileset.texture,
        x,
        yFlipped,
        tileset.tileWidth,
        tileset.tileHeight);
}

void loadTileLayer(
    const Json& layerJson,
    const std::vector<TilesetInfo>& tilesets,
    int mapWidth,
    int mapHeight,
    int mapTileWidth,
    int mapTileHeight,
    sle::entity::Scene& scene,
    const std::string& tmjPath,
    int renderLayerBase)
{
    if (!layerJson.contains("data") || !layerJson["data"].is_array())
    {
        sle::core::Log::warn("Tiled map '{}': tile layer is missing array 'data', skipping", tmjPath);
        return;
    }

    auto& registry = scene.getRegistry();
    const auto& data = layerJson["data"];
    const std::size_t expectedCellCount = static_cast<std::size_t>(mapWidth) * static_cast<std::size_t>(mapHeight);
    if (data.size() < expectedCellCount)
    {
        sle::core::Log::warn(
            "Tiled map '{}': tile layer data has {} cells, expected at least {}, skipping",
            tmjPath,
            data.size(),
            expectedCellCount);
        return;
    }

    for (int tileY = 0; tileY < mapHeight; ++tileY)
    {
        for (int tileX = 0; tileX < mapWidth; ++tileX)
        {
            const std::size_t index = static_cast<std::size_t>(tileY) * static_cast<std::size_t>(mapWidth) + static_cast<std::size_t>(tileX);
            std::uint32_t rawGid = data[index].get<std::uint32_t>();
            const bool hasUnsupportedFlip = (rawGid & kTiledFlipMask) != 0;
            rawGid &= ~kTiledFlipMask;
            if (rawGid == 0)
                continue;

            const TilesetInfo* tileset = findTilesetForGid(tilesets, static_cast<int>(rawGid));
            if (!tileset)
            {
                sle::core::Log::warn("Tiled map '{}': no tileset found for gid {}, skipping tile", tmjPath, rawGid);
                continue;
            }

            if (hasUnsupportedFlip)
            {
                sle::core::Log::warn(
                    "Tiled map '{}': tile gid {} uses flip flags, which are ignored in this first slice",
                    tmjPath,
                    rawGid);
            }

            auto entity = scene.createEntity();

            auto& transform = registry.addComponent<sle::components::TransformComponent>(entity);
            transform.setPosition({
                (static_cast<float>(tileX) + 0.5f) * static_cast<float>(mapTileWidth),
                (static_cast<float>(mapHeight - tileY) - 0.5f) * static_cast<float>(mapTileHeight)
            });
            transform.setScale({
                static_cast<float>(mapTileWidth),
                static_cast<float>(mapTileHeight)
            });

            auto& sprite = registry.addComponent<sle::components::SpriteRenderer>(entity);
            sprite.region = makeRegionFromTiledTile(*tileset, static_cast<int>(rawGid) - tileset->firstGid);
            sprite.layer = renderLayerBase;
        }
    }
}

void loadObjectLayer(
    const Json& layerJson,
    int mapHeightPixels,
    const sle::ChunkManager& chunkManager,
    sle::entity::Scene& scene,
    const fs::path& tmjDirectory,
    const std::string& tmjPath)
{
    if (!layerJson.contains("objects") || !layerJson["objects"].is_array())
    {
        sle::core::Log::warn("Tiled map '{}': object layer is missing array 'objects', skipping", tmjPath);
        return;
    }

    auto& registry = scene.getRegistry();

    for (const auto& objectJson : layerJson["objects"])
    {
        if (!objectJson.is_object())
            continue;

        const std::string type = objectJson.value("type", "");
        const std::string name = objectJson.value("name", "");
        const float x = objectJson.value("x", 0.0f);
        const float y = objectJson.value("y", 0.0f);
        const float width = objectJson.value("width", 0.0f);
        const float height = objectJson.value("height", 0.0f);
        const Json properties = readPropertiesObject(objectJson);

        const float centerX = x + width * 0.5f;
        const float centerY = static_cast<float>(mapHeightPixels) - y - height * 0.5f;

        auto entity = scene.createEntity();
        auto& transform = registry.addComponent<sle::components::TransformComponent>(entity);
        transform.setPosition({centerX, centerY});
        const sle::ChunkCoord homeChunk = chunkManager.worldToChunk(transform.getPosition());
        auto& chunkActivation = registry.addComponent<sle::components::ChunkActivationComponent>(entity);
        chunkActivation.homeChunkX = homeChunk.x;
        chunkActivation.homeChunkY = homeChunk.y;

        if (type == "StaticCollider")
        {
            auto& rigidBody = registry.addComponent<sle::components::RigidBodyComponent>(entity);
            rigidBody.bodyType = sle::components::BodyType::Static;

            auto& collider = registry.addComponent<sle::components::BoxColliderComponent>(entity);
            collider.size = {width, height};
        }
        else if (type == "Zone")
        {
            auto& zone = registry.addComponent<sle::components::BoxZoneComponent>(entity);
            zone.size = {width, height};

            if (properties.contains("zoneId") && properties["zoneId"].is_string())
                zone.zoneId = properties["zoneId"].get<std::string>();
            else if (!name.empty())
                zone.zoneId = name;
            else
                zone.zoneId = "zone";
        }
        else if (type == "ScriptEntity")
        {
            auto& script = registry.addComponent<sle::components::ScriptComponent>(entity);
            if (properties.contains("script") && properties["script"].is_string())
                script.scriptAsset = resolvePathRelativeTo(tmjDirectory, properties["script"].get<std::string>());
            else
                sle::core::Log::warn("Tiled map '{}': ScriptEntity missing string property 'script'", tmjPath);
        }
        else if (type == "SpawnPoint" || type == "Marker" || type.empty())
        {
            // Transform-only marker entity for the first slice.
        }
        else
        {
            sle::core::Log::warn(
                "Tiled map '{}': unsupported object type '{}' (entity kept as transform-only)",
                tmjPath,
                type);
        }
    }
}

} // namespace

sle::core::Result<bool> TiledMapLoader::load(const std::string& tmjPath, sle::entity::Scene& scene)
{
    std::ifstream file(tmjPath);
    if (!file.is_open())
        return sle::core::Result<bool>::error("Failed to open Tiled map file: " + tmjPath);

    Json root;
    try
    {
        file >> root;
    }
    catch (const std::exception& ex)
    {
        return sle::core::Result<bool>::error("Malformed Tiled map JSON in '" + tmjPath + "': " + ex.what());
    }

    if (!root.is_object())
        return sle::core::Result<bool>::error("Tiled map root must be an object: " + tmjPath);

    if (root.value("type", std::string()) != "map")
        return sle::core::Result<bool>::error("Tiled file is not a map: " + tmjPath);

    if (root.value("orientation", std::string("orthogonal")) != "orthogonal")
        return sle::core::Result<bool>::error("Only orthogonal Tiled maps are supported in this first slice: " + tmjPath);

    if (root.value("infinite", false))
        return sle::core::Result<bool>::error("Infinite Tiled maps are not supported in this first slice: " + tmjPath);

    if (!root.contains("layers") || !root["layers"].is_array())
        return sle::core::Result<bool>::error("Tiled map must contain a 'layers' array: " + tmjPath);

    if (!root.contains("tilesets") || !root["tilesets"].is_array())
        return sle::core::Result<bool>::error("Tiled map must contain a 'tilesets' array: " + tmjPath);

    const int mapWidth = root.value("width", 0);
    const int mapHeight = root.value("height", 0);
    const int mapTileWidth = root.value("tilewidth", 0);
    const int mapTileHeight = root.value("tileheight", 0);
    if (mapWidth <= 0 || mapHeight <= 0 || mapTileWidth <= 0 || mapTileHeight <= 0)
        return sle::core::Result<bool>::error("Tiled map dimensions are invalid: " + tmjPath);

    const fs::path tmjDirectory = fs::path(tmjPath).parent_path();
    std::vector<TilesetInfo> tilesets;
    tilesets.reserve(root["tilesets"].size());

    for (const auto& tilesetJson : root["tilesets"])
    {
        auto info = parseEmbeddedTileset(tilesetJson, tmjDirectory, tmjPath);
        if (!info)
            continue;

        info->texture = sle::core::Resources::create<sle::renderer::Texture>(info->imagePath, info->imagePath);
        if (!info->texture)
        {
            sle::core::Log::warn("Tiled map '{}': failed to load tileset image '{}'", tmjPath, info->imagePath);
        }

        tilesets.push_back(std::move(*info));
    }

    std::sort(
        tilesets.begin(),
        tilesets.end(),
        [](const TilesetInfo& lhs, const TilesetInfo& rhs) { return lhs.firstGid < rhs.firstGid; });

    const int mapHeightPixels = mapHeight * mapTileHeight;
    const sle::ChunkGridConfig chunkGridConfig{
        .chunkWidthTiles = 16,
        .chunkHeightTiles = 16,
        .tileWidth = mapTileWidth,
        .tileHeight = mapTileHeight,
        .activeRadiusX = 1,
        .activeRadiusY = 1
    };
    const sle::ChunkManager chunkManager(chunkGridConfig);

    {
        auto metadataEntity = scene.createEntity();
        auto& chunkMap = scene.getRegistry().addComponent<sle::ChunkMapComponent>(metadataEntity);
        chunkMap.mapId = tmjPath;
        chunkMap.gridConfig = chunkGridConfig;
    }

    int tileLayerIndex = 0;
    for (const auto& layerJson : root["layers"])
    {
        if (!layerJson.is_object() || !layerJson.value("visible", true))
            continue;

        const std::string type = layerJson.value("type", "");
        if (type == "tilelayer")
        {
            loadTileLayer(
                layerJson,
                tilesets,
                mapWidth,
                mapHeight,
                mapTileWidth,
                mapTileHeight,
                scene,
                tmjPath,
                tileLayerIndex);
            ++tileLayerIndex;
        }
        else if (type == "objectgroup")
        {
            loadObjectLayer(layerJson, mapHeightPixels, chunkManager, scene, tmjDirectory, tmjPath);
        }
        else
        {
            sle::core::Log::warn("Tiled map '{}': unsupported layer type '{}', skipping", tmjPath, type);
        }
    }

    return sle::core::Result<bool>::success(true);
}

} // namespace sle
