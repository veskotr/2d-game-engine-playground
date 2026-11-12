#include <sle/engine/SceneManager.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <exception>
#include <sle/events/SceneLifecycleEvents.hpp>

namespace sle {

bool SceneManager::registerScene(const std::string& sceneName, SceneBuilder builder)
{
    if (sceneName.empty() || !builder)
        return false;

    sceneBuilders[sceneName] = {std::move(builder), {}};
    return true;
}

bool SceneManager::registerSceneFromFile(const std::string& sceneName, const std::string& jsonPath)
{
    if (sceneName.empty() || jsonPath.empty())
        return false;

    sceneBuilders[sceneName] = {{}, jsonPath};
    return true;
}

bool SceneManager::registerSceneFromData(const std::string& sceneName,
    const nlohmann::json& data, const std::string& sourcePath)
{
    if (sceneName.empty() || !data.is_object()) return false;
    sceneBuilders[sceneName] = {{}, sourcePath, data};
    return true;
}

bool SceneManager::hasScene(const std::string& sceneName) const
{
    return sceneBuilders.find(sceneName) != sceneBuilders.end();
}

sle::core::Result<bool> SceneManager::loadScene(
    const std::string& sceneName,
    Runtime& runtime,
    sle::entity::Scene& scene, bool emitLifecycleEvents)
{
    auto it = sceneBuilders.find(sceneName);
    if (it == sceneBuilders.end())
        return sle::core::Result<bool>::error("Scene not registered: " + sceneName);

    std::unique_ptr<sle::entity::Scene> staged;
    if (it->second.jsonData || !it->second.jsonPath.empty())
    {
        staged = scene.createStagingScene();
        const std::string& path = it->second.jsonPath;
        try
        {
            const auto result = it->second.jsonData
                ? SceneLoader::loadData(*it->second.jsonData, *staged, path)
                : SceneLoader::load(path, *staged);
            if (!result.ok())
                return sle::core::Result<bool>::error(
                    "Failed to load scene '" + sceneName + "' from '" + path + "': " + result.error());
        }
        catch (const std::exception& ex)
        {
            // Typed JSON fields and filesystem operations can throw after some
            // entities have been constructed. Discard staging, not the live scene.
            return sle::core::Result<bool>::error(
                "Failed to load scene '" + sceneName + "' from '" + path + "': " + ex.what());
        }
    }

    if (staged)
        scene.reserveStagedEntityIds(*staged);
    runtime.getScriptEngine().clearSceneScripts();

    // Emit unload only after a file-based replacement has loaded successfully.
    if (!currentSceneName.empty() && currentSceneEmitsLifecycle)
    {
        sle::events::SceneUnloadedEvent unloadEvent{currentSceneName};
        runtime.getGlobalBus().queue(unloadEvent);
    }

    if (staged)
    {
        scene.replaceWith(*staged);
    }
    else
    {
        // Existing C++ builders are imperative and keep their original contract.
        scene.destroy();
        currentSceneName.clear();
        // Builders can register more scenes and invalidate the map iterator.
        const auto builder = it->second.builder;
        try {
            builder(runtime);
        } catch (const std::exception& ex) {
            runtime.getScriptEngine().clearSceneScripts();
            scene.destroy();
            return sle::core::Result<bool>::error("Failed to build scene '" + sceneName + "': " + ex.what());
        }
    }
    currentSceneName = sceneName;
    currentSceneEmitsLifecycle = emitLifecycleEvents;

    // Emit load event for new scene
    sle::events::SceneLoadedEvent loadEvent{sceneName};
    if (emitLifecycleEvents) runtime.getGlobalBus().queue(loadEvent);

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