#include <sle/engine/StateMachineSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/StateMachineDefinition.hpp>

#include <iostream>
#include <memory>

int main() {
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();

    auto def = std::make_shared<sle::components::StateMachineDefinition>();
    def->initialState = "Idle";

    sle::components::StateDefinition idle;
    idle.name = "Idle";
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
    run.transitions.push_back({
        .toState = "Idle",
        .type = sle::components::StateTransitionType::BoolEquals,
        .key = "shouldStop",
        .expectedBool = true,
        .minTimeSeconds = 0.0f,
        .consumeTrigger = true,
    });

    def->states.emplace(idle.name, idle);
    def->states.emplace(run.name, run);

    const sle::entity::Entity entity = scene.createEntity();
    auto& sm = registry.addComponent<sle::components::StateMachineComponent>(entity);
    sm.definition = def;
    sm.boolParameters["shouldStop"] = false;

    sle::StateMachineSystem system;

    system.update(scene, 1.0f / 60.0f);
    if (!sm.initialized || sm.currentState != "Idle") {
        std::cerr << "State machine should initialize to Idle\n";
        return 1;
    }

    sm.triggers.insert("start");
    system.update(scene, 1.0f / 60.0f);
    if (sm.currentState != "Run") {
        std::cerr << "Expected transition Idle -> Run on start trigger\n";
        return 1;
    }

    if (!sm.triggers.empty()) {
        std::cerr << "Trigger should be consumed on transition\n";
        return 1;
    }

    sm.boolParameters["shouldStop"] = true;
    system.update(scene, 1.0f / 60.0f);
    if (sm.currentState != "Idle") {
        std::cerr << "Expected transition Run -> Idle on bool condition\n";
        return 1;
    }

    return 0;
}
