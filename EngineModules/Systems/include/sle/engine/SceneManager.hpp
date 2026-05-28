#pragma once

#include <sle/core/Result.hpp>
#include <sle/scene/Scene.hpp>

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
    bool hasScene(const std::string& sceneName) const;

    sle::core::Result<bool> loadScene(
        const std::string& sceneName,
        Runtime& runtime,
        sle::entity::Scene& scene);

    sle::core::Result<bool> requestSceneSwitch(const std::string& sceneName);

    sle::core::Result<bool> processPendingSwitch(Runtime& runtime, sle::entity::Scene& scene);

    const std::string& getCurrentSceneName() const { return currentSceneName; }

private:
    std::unordered_map<std::string, SceneBuilder> sceneBuilders;
    // Coalesced pending switch: if multiple requests arrive before processing,
    // the latest request replaces earlier ones.
    std::optional<std::string> pendingSceneName;
    std::string currentSceneName;
};

} // namespace sle