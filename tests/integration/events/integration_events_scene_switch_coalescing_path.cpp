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
    config.title = "Events Scene Switch Coalescing Test";
    config.vsync = false;

    sle::Runtime runtime(config);

    runtime.registerScene("scene_a", [](sle::Runtime& rt) {
        rt.getScene().createEntity();
    });
    runtime.registerScene("scene_b", [](sle::Runtime& rt) {
        rt.getScene().createEntity();
    });
    runtime.registerScene("scene_c", [](sle::Runtime& rt) {
        rt.getScene().createEntity();
    });

    std::vector<std::string> events;
    runtime.getGlobalBus().subscribe<sle::events::SceneUnloadedEvent>(
        [&events](const sle::events::SceneUnloadedEvent& event) {
            events.push_back("unload:" + event.sceneName);
        });
    runtime.getGlobalBus().subscribe<sle::events::SceneLoadedEvent>(
        [&events](const sle::events::SceneLoadedEvent& event) {
            events.push_back("load:" + event.sceneName);
        });

    auto loadA = runtime.loadScene("scene_a");
    if (!loadA.ok()) {
        std::cerr << "Failed to load scene_a: " << loadA.error() << "\n";
        return 1;
    }
    runtime.getGlobalBus().flushQueue();

    if (events.size() != 1 || events[0] != "load:scene_a") {
        std::cerr << "Unexpected initial lifecycle events\n";
        return 1;
    }

    auto requestB = runtime.requestSceneSwitch("scene_b");
    if (!requestB.ok()) {
        std::cerr << "Failed to request scene_b switch\n";
        return 1;
    }

    auto requestC = runtime.requestSceneSwitch("scene_c");
    if (!requestC.ok()) {
        std::cerr << "Failed to request scene_c switch\n";
        return 1;
    }

    auto processSwitch = runtime.getSceneManager().processPendingSwitch(runtime, runtime.getScene());
    if (!processSwitch.ok()) {
        std::cerr << "Failed processing pending switch\n";
        return 1;
    }

    if (runtime.getCurrentSceneName() != "scene_c") {
        std::cerr << "Expected last requested scene (scene_c) to win before processing\n";
        return 1;
    }

    runtime.getGlobalBus().flushQueue();

    if (events.size() != 3) {
        std::cerr << "Expected exactly three lifecycle events total\n";
        return 1;
    }

    if (events[1] != "unload:scene_a" || events[2] != "load:scene_c") {
        std::cerr << "Expected unload:scene_a then load:scene_c after coalesced switch\n";
        return 1;
    }

    auto secondProcess = runtime.getSceneManager().processPendingSwitch(runtime, runtime.getScene());
    if (!secondProcess.ok()) {
        std::cerr << "Second processPendingSwitch should be no-op success\n";
        return 1;
    }

    return 0;
}
