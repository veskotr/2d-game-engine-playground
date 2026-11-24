#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/engine/InputActions.hpp>
#include <sle/core/Result.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <nlohmann/json.hpp>
#include <sle/renderer/Texture.hpp>
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
    static core::Result<ProjectRuntime> open(const std::filesystem::path& descriptor,
        bool validateTileAssets = false);
    // Mounts `.slepkg` game packages in order as one namespace of assets and scenes.
    // The first package declaring a main scene provides the startup scene.
    static core::Result<ProjectRuntime> openPackages(const std::vector<std::filesystem::path>& packages,
        const core::EngineConfig& config);
    const core::EngineConfig& engineConfig() const { return config; }
    const physics::CollisionMatrix& collisionMatrix() const { return blockedPairs; }
    const std::map<std::string, std::string>& tagsByName() const { return tags; }
    const InputActionMap& inputActions() const { return actions; }
    const std::string& startupScene() const { return startup; }
    const std::filesystem::path& assetRoot() const { return roots.front(); }
    const std::vector<std::filesystem::path>& assetRoots() const { return roots; }
    const nlohmann::json& sceneDocument(const std::string& id) const;
    const nlohmann::json* findSceneDocument(const std::string& nameOrPath) const;
    renderer::TextureFilter textureFilter(const std::filesystem::path& path) const;
    struct SpriteRegion {
        int x, y, width, height;
    };
    static std::map<std::string, SpriteRegion> readSpriteRegions(const std::filesystem::path& texture);
    static renderer::TextureFilter previewTextureFilter(const std::filesystem::path& root, const std::filesystem::path& path,
        renderer::TextureFilter fallback = renderer::TextureFilter::Default);
    static std::string resolveEditorAsset(const std::filesystem::path& root, const std::string& reference);
    static std::filesystem::path resolveEditorSource(const std::filesystem::path& root, const std::filesystem::path& path);
    void registerScenes(Runtime& runtime) const;
    std::string resolveAsset(const std::string& value) const;
    nlohmann::json resolveComponentAssets(const nlohmann::json& components, bool portable = false) const;
private:
    struct Scene { std::string key, path; nlohmann::json document; };
    struct Cache;
    void index(bool validateTileAssets = false);
    std::shared_ptr<Cache> cache;
    std::vector<std::shared_ptr<void>> mounts;
    core::EngineConfig config;
    physics::CollisionMatrix blockedPairs{};
    std::map<std::string, std::string> tags;
    InputActionMap actions;
    std::string startup;
    std::filesystem::path root, runtimeFolder;
    std::vector<std::filesystem::path> roots;
    std::map<std::string,std::filesystem::path> paths;
    std::map<std::filesystem::path,renderer::TextureFilter> textureFilters;
    std::map<std::filesystem::path,renderer::TextureFilter> textureDefaults;
    std::map<std::string,std::string> derived;
    std::map<std::string,Scene> scenes;
};
}
