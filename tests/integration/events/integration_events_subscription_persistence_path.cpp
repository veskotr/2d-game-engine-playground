#include <sle/core/EngineConfig.hpp>
#include <sle/engine/Runtime.hpp>

#include <iostream>

struct SceneLocalEvent {
    int value = 0;
};

int main() {
    sle::core::EngineConfig config;
    config.width = 320;
    config.height = 180;
    config.title = "Events Subscription Persistence Test";
    config.vsync = false;

    sle::Runtime runtime(config);

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

    int delivered = 0;

    runtime.getScene().getEventBus().subscribe<SceneLocalEvent>(
        [&delivered](const SceneLocalEvent&) {
            ++delivered;
        });

    runtime.getScene().getEventBus().queue(SceneLocalEvent{1});
    runtime.getScene().getEventBus().flushQueue();
    if (delivered != 1) {
        std::cerr << "Expected first queued event to be delivered\n";
        return 1;
    }

    runtime.getScene().getEventBus().queue(SceneLocalEvent{2});
    runtime.getScene().getEventBus().flushQueue();
    if (delivered != 2) {
        std::cerr << "Listener should persist across normal event flush cycles\n";
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

    runtime.getScene().getEventBus().emit(SceneLocalEvent{3});
    if (delivered != 2) {
        std::cerr << "Old scene listener should be removed after scene teardown\n";
        return 1;
    }

    runtime.getScene().getEventBus().subscribe<SceneLocalEvent>(
        [&delivered](const SceneLocalEvent&) {
            ++delivered;
        });

    runtime.getScene().getEventBus().emit(SceneLocalEvent{4});
    if (delivered != 3) {
        std::cerr << "New scene listener should receive events after resubscription\n";
        return 1;
    }

    return 0;
}
