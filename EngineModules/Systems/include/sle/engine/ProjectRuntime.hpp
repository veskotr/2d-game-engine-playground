#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Result.hpp>
#include <nlohmann/json.hpp>
#include <filesystem>
#include <map>
#include <memory>
#include <string>

namespace sle {
class Runtime;
/** Read-only project discovery and a resolved launch snapshot; never changes sources/metadata. */
class ProjectRuntime
{
public:
    static core::Result<ProjectRuntime> open(const std::filesystem::path& descriptor);
    const core::EngineConfig& engineConfig() const { return config; }
    const std::string& startupScene() const { return startup; }
    const std::filesystem::path& assetRoot() const { return assets; }
    const nlohmann::json& sceneDocument(const std::string& id) const;
    const nlohmann::json* findSceneDocument(const std::string& nameOrPath) const;
    void registerScenes(Runtime& runtime) const;
    std::string resolveAsset(const std::string& value) const;
    nlohmann::json resolveComponentAssets(const nlohmann::json& components, bool portable = false) const;
private:
    struct Scene { std::string key, path; nlohmann::json document; };
    struct Cache;
    std::shared_ptr<Cache> cache;
    core::EngineConfig config;
    std::string startup;
    std::filesystem::path root, assets;
    std::map<std::string,std::filesystem::path> paths;
    std::map<std::string,std::string> derived;
    std::map<std::string,Scene> scenes;
};
}
