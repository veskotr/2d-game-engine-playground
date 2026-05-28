#include <sle/events/EventBus.hpp>
#include <sle/scene/Scene.hpp>

#include <iostream>

struct SceneDeferredEvent {
    int value = 0;
};

int main() {
    sle::entity::Scene scene;

    int delivered = 0;
    auto sub = scene.getEventBus().subscribe<SceneDeferredEvent>(
        [&delivered](const SceneDeferredEvent&) {
            ++delivered;
        });

    scene.getEventBus().queue(SceneDeferredEvent{1});

    // Simulate scene switch teardown: all scene event state should reset.
    scene.destroy();

    // Register a fresh listener for the next scene lifecycle.
    auto nextSub = scene.getEventBus().subscribe<SceneDeferredEvent>(
        [&delivered](const SceneDeferredEvent&) {
            ++delivered;
        });

    (void)sub;
    (void)nextSub;

    scene.getEventBus().flushQueue();

    if (delivered != 0) {
        std::cerr << "Deferred events from previous scene should not leak after scene.destroy()\n";
        return 1;
    }

    scene.getEventBus().emit(SceneDeferredEvent{2});
    if (delivered != 1) {
        std::cerr << "Expected newly subscribed listener to receive fresh scene event\n";
        return 1;
    }

    return 0;
}
