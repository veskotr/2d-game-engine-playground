#include <sle/core/EngineConfig.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/events/SceneLifecycleEvents.hpp>

#include <iostream>

int main() {
    sle::core::EngineConfig config;
    config.width = 320;
    config.height = 180;
    config.title = "Events Invalid Scene Switch Test";
    config.vsync = false;

    sle::Runtime runtime(config);

    int lifecycleEvents = 0;
    runtime.getGlobalBus().subscribe<sle::events::SceneLoadedEvent>(
        [&lifecycleEvents](const sle::events::SceneLoadedEvent&) {
            ++lifecycleEvents;
        });
    runtime.getGlobalBus().subscribe<sle::events::SceneUnloadedEvent>(
        [&lifecycleEvents](const sle::events::SceneUnloadedEvent&) {
            ++lifecycleEvents;
        });

    auto requestMissing = runtime.requestSceneSwitch("missing_scene");
    if (requestMissing.ok()) {
        std::cerr << "Requesting unregistered scene should fail\n";
        return 1;
    }

    auto processResult = runtime.getSceneManager().processPendingSwitch(runtime, runtime.getScene());
    if (!processResult.ok()) {
        std::cerr << "Processing with no pending scene should be a no-op success\n";
        return 1;
    }

    runtime.getGlobalBus().flushQueue();

    if (lifecycleEvents != 0) {
        std::cerr << "No lifecycle events should be emitted for invalid scene switch requests\n";
        return 1;
    }

    return 0;
}
