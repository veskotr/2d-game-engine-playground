#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Result.hpp>
#include <nlohmann/json.hpp>
#include <filesystem>
#include <map>
#include <memory>
#include <string>
#include <vector>

namespace sle {
class Runtime;
/** Read-only project discovery and a resolved launch snapshot; never changes sources/metadata. */
class ProjectRuntime
{
public:
    static core::Result<ProjectRuntime> open(const std::filesystem::path& descriptor);
    // Mounts `.slepkg` game packages in order as one namespace of assets and scenes.
    // The first package declaring a main scene provides the startup scene.
    static core::Result<ProjectRuntime> openPackages(const std::vector<std::filesystem::path>& packages,
        const core::EngineConfig& config);
    const core::EngineConfig& engineConfig() const { return config; }
    const std::string& startupScene() const { return startup; }
    const std::filesystem::path& assetRoot() const { return roots.front(); }
    const std::vector<std::filesystem::path>& assetRoots() const { return roots; }
    const nlohmann::json& sceneDocument(const std::string& id) const;
    const nlohmann::json* findSceneDocument(const std::string& nameOrPath) const;
    void registerScenes(Runtime& runtime) const;
    std::string resolveAsset(const std::string& value) const;
    nlohmann::json resolveComponentAssets(const nlohmann::json& components, bool portable = false) const;
private:
    struct Scene { std::string key, path; nlohmann::json document; };
    struct Cache;
    void index();
    std::shared_ptr<Cache> cache;
    std::vector<std::shared_ptr<void>> mounts;
    core::EngineConfig config;
    std::string startup;
    std::filesystem::path root, runtimeFolder;
    std::vector<std::filesystem::path> roots;
    std::map<std::string,std::filesystem::path> paths;
    std::map<std::string,std::string> derived;
    std::map<std::string,Scene> scenes;
};
}
