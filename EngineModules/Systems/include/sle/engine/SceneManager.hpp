#pragma once

#include <sle/core/Result.hpp>
#include <sle/scene/Scene.hpp>

#include <nlohmann/json.hpp>
#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <unordered_map>

namespace sle {

class Runtime;

class SceneManager
{
public:
    using SceneBuilder = std::function<void(Runtime&)>;

    bool registerScene(const std::string& sceneName, SceneBuilder builder);
    // File loads are staged; failure preserves the live scene and lifecycle state.
    bool registerSceneFromFile(const std::string& sceneName, const std::string& jsonPath);
    bool registerSceneFromData(const std::string& sceneName, const nlohmann::json& data, const std::string& sourcePath);
    bool hasScene(const std::string& sceneName) const;

    sle::core::Result<bool> loadScene(
        const std::string& sceneName,
        Runtime& runtime,
        sle::entity::Scene& scene, bool emitLifecycleEvents = true, bool* errorReported = nullptr);

    sle::core::Result<bool> requestSceneSwitch(const std::string& sceneName);

    sle::core::Result<bool> processPendingSwitch(Runtime& runtime, sle::entity::Scene& scene, bool& errorReported);

    const std::unordered_map<uint32_t, std::string>& getAuthoredEntityKeys() const { return authoredEntityKeys; }
    uint64_t getSceneInstance() const { return sceneInstance; }
    const std::string& getCurrentSceneName() const { return currentSceneName; }

private:
    struct SceneRegistration
    {
        SceneBuilder builder;
        std::string jsonPath;
        std::optional<nlohmann::json> jsonData;
    };
    std::unordered_map<std::string, SceneRegistration> sceneBuilders;
    // Coalesced pending switch: if multiple requests arrive before processing,
    // the latest request replaces earlier ones.
    std::optional<std::string> pendingSceneName;
    // Identity from the committed scene data, independent of keys added during Play.
    std::unordered_map<uint32_t, std::string> authoredEntityKeys;
    uint64_t sceneInstance = 0;
    std::string currentSceneName;
    bool currentSceneEmitsLifecycle = true;
};

} // namespace sle