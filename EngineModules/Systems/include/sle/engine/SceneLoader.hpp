#pragma once

#include <sle/core/Result.hpp>
#include <sle/scene/Scene.hpp>

#include <nlohmann/json_fwd.hpp>
#include <string>
#include <functional>
#include <sle/renderer/Texture.hpp>

namespace sle {

class SceneLoader
{
public:
    using TextureFilterLookup = std::function<renderer::TextureFilter(const std::string&)>;
    using AssetLookup = std::function<std::string(const std::string&)>;
    static sle::core::Result<bool> loadData(const nlohmann::json& data, sle::entity::Scene& scene, const std::string& sourcePath,
        const TextureFilterLookup& textureFilter = {}, const AssetLookup& assetLookup = {},
        bool allowMissingTiles = false);
    static sle::core::Result<bool> load(const std::string& jsonPath, sle::entity::Scene& scene,
        const TextureFilterLookup& textureFilter = {}, const AssetLookup& assetLookup = {},
        bool allowMissingTiles = false);
};

} // namespace sle
