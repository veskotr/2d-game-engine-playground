#include <sle/engine/StateMachineSystem.hpp>
#include <sle/events/StateMachineEvents.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/StateMachineDefinition.hpp>

#include <iostream>
#include <memory>

int main() {
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();
    auto& eventBus = scene.getEventBus();

    auto def = std::make_shared<sle::components::StateMachineDefinition>();
    def->initialState = "Idle";

    sle::components::StateDefinition idle;
    idle.name = "Idle";
    idle.onExitCallback = "on_exit_idle";
    idle.transitions.push_back({
        .toState = "Run",
        .type = sle::components::StateTransitionType::Trigger,
        .key = "start",
        .expectedBool = true,
        .minTimeSeconds = 0.0f,
        .consumeTrigger = true,
    });

    sle::components::StateDefinition run;
    run.name = "Run";
    run.onEnterCallback = "on_enter_run";

    def->states.emplace(idle.name, idle);
    def->states.emplace(run.name, run);

    const auto entity = scene.createEntity();
    auto& sm = registry.addComponent<sle::components::StateMachineComponent>(entity);
    sm.definition = def;

    int transitionEvents = 0;
    bool sawCallbacks = false;
    eventBus.subscribe<sle::events::StateMachineTransitionEvent>(
        [&transitionEvents, &sawCallbacks, expected = entity.getID()](const sle::events::StateMachineTransitionEvent& event) {
            if (event.entity.getID() == expected && event.fromState == "Idle" && event.toState == "Run") {
                ++transitionEvents;
                if (event.onExitCallback == "on_exit_idle" && event.onEnterCallback == "on_enter_run") {
                    sawCallbacks = true;
                }
            }
        });

    sle::StateMachineSystem system;

    system.update(scene, 1.0f / 60.0f);
    sm.triggers.insert("start");
    system.update(scene, 1.0f / 60.0f);

    if (transitionEvents != 0) {
        std::cerr << "Transition event should be deferred until event bus flush\n";
        return 1;
    }

    eventBus.flushQueue();

    if (transitionEvents != 1) {
        std::cerr << "Expected exactly one transition event after flushQueue\n";
        return 1;
    }

    if (!sawCallbacks) {
        std::cerr << "Transition event should carry onExit/onEnter callback metadata\n";
        return 1;
    }

    return 0;
}
