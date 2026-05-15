#include <sle/engine/SceneManager.hpp>
#include <sle/engine/Runtime.hpp>

namespace sle {

bool SceneManager::registerScene(const std::string& sceneName, SceneBuilder builder)
{
    if (sceneName.empty() || !builder)
        return false;

    sceneBuilders[sceneName] = std::move(builder);
    return true;
}

bool SceneManager::hasScene(const std::string& sceneName) const
{
    return sceneBuilders.find(sceneName) != sceneBuilders.end();
}

sle::core::Result<bool> SceneManager::loadScene(
    const std::string& sceneName,
    Runtime& runtime,
    sle::entity::Scene& scene)
{
    auto it = sceneBuilders.find(sceneName);
    if (it == sceneBuilders.end())
        return sle::core::Result<bool>::error("Scene not registered: " + sceneName);

    scene.destroy();
    it->second(runtime);
    currentSceneName = sceneName;

    return sle::core::Result<bool>::success(true);
}

sle::core::Result<bool> SceneManager::requestSceneSwitch(const std::string& sceneName)
{
    if (!hasScene(sceneName))
        return sle::core::Result<bool>::error("Scene not registered: " + sceneName);

    pendingSceneName = sceneName;
    return sle::core::Result<bool>::success(true);
}

sle::core::Result<bool> SceneManager::processPendingSwitch(Runtime& runtime, sle::entity::Scene& scene)
{
    if (!pendingSceneName.has_value())
        return sle::core::Result<bool>::success(true);

    const std::string nextScene = *pendingSceneName;
    pendingSceneName.reset();
    return loadScene(nextScene, runtime, scene);
}

} // namespace sle