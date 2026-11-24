#pragma once

#include <sle/engine/SceneLoader.hpp>

namespace sle::detail {

nlohmann::json canonicalTileLayers(const nlohmann::json& data);
std::vector<entity::TileLayer> loadTileLayers(const nlohmann::json& data,
    const SceneLoader::AssetLookup& resolve, const SceneLoader::TextureFilterLookup& filter,
    bool allowMissing, bool uploadTextures);

}
