#include <sle/core/EngineConfig.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/events/SceneLifecycleEvents.hpp>

#include <iostream>
#include <string>
#include <vector>

int main() {
    sle::core::EngineConfig config;
    config.width = 320;
    config.height = 180;
    config.title = "Events Scene Lifecycle Order Test";
    config.vsync = false;

    sle::Runtime runtime(config);

    std::vector<std::string> eventLog;

    runtime.getGlobalBus().subscribe<sle::events::SceneUnloadedEvent>(
        [&eventLog](const sle::events::SceneUnloadedEvent& event) {
            eventLog.push_back("unload:" + event.sceneName);
        });

    runtime.getGlobalBus().subscribe<sle::events::SceneLoadedEvent>(
        [&eventLog](const sle::events::SceneLoadedEvent& event) {
            eventLog.push_back("load:" + event.sceneName);
        });

    runtime.registerScene("scene_a", [](sle::Runtime& rt) {
        rt.getScene().createEntity();
    });

    runtime.registerScene("scene_b", [](sle::Runtime& rt) {
        rt.getScene().createEntity();
    });

    auto loadA = runtime.loadScene("scene_a");
    if (!loadA.ok()) {
        std::cerr << "Failed to load scene_a: " << loadA.error() << "\n";
        return 1;
    }

    if (!eventLog.empty()) {
        std::cerr << "Scene lifecycle events should be deferred until global bus flush\n";
        return 1;
    }

    runtime.getGlobalBus().flushQueue();

    if (eventLog.size() != 1 || eventLog[0] != "load:scene_a") {
        std::cerr << "Expected only load:scene_a after first load\n";
        return 1;
    }

    auto requestB = runtime.requestSceneSwitch("scene_b");
    if (!requestB.ok()) {
        std::cerr << "Failed to request scene_b switch: " << requestB.error() << "\n";
        return 1;
    }

    auto processB = runtime.getSceneManager().processPendingSwitch(runtime, runtime.getScene());
    if (!processB.ok()) {
        std::cerr << "Failed processing pending scene switch: " << processB.error() << "\n";
        return 1;
    }

    if (eventLog.size() != 1) {
        std::cerr << "Queued switch lifecycle events should not be delivered before flush\n";
        return 1;
    }

    runtime.getGlobalBus().flushQueue();

    if (eventLog.size() != 3) {
        std::cerr << "Expected three lifecycle events total after second flush\n";
        return 1;
    }

    if (eventLog[1] != "unload:scene_a" || eventLog[2] != "load:scene_b") {
        std::cerr << "Expected unload:scene_a then load:scene_b ordering\n";
        return 1;
    }

    if (runtime.getCurrentSceneName() != "scene_b") {
        std::cerr << "Runtime should report scene_b as current scene\n";
        return 1;
    }

    return 0;
}
