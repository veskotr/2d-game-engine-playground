#pragma once
#include "TileLayerLoader.hpp"
namespace sle::detail {
nlohmann::json canonicalTilemap(const nlohmann::json& source);
std::vector<entity::TileLayer> loadTilemap(const std::string& asset,
    const SceneLoader::AssetLookup& resolve, const SceneLoader::TextureFilterLookup& filter,
    bool allowMissing, bool uploadTextures);
}
