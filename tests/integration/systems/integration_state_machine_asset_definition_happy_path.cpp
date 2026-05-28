#include <sle/engine/StateMachineSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>

#include <iostream>

int main() {
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();

    const auto entity = scene.createEntity();
    auto& sm = registry.addComponent<sle::components::StateMachineComponent>(entity);
    sm.definitionAsset = "tests/data/state_machines/integration_state_machine_definition.json";
    sm.boolParameters["shouldStop"] = false;

    sle::StateMachineSystem system;

    system.update(scene, 1.0f / 60.0f);
    if (!sm.initialized || sm.currentState != "Idle") {
        std::cerr << "Expected state machine to initialize from definition asset\n";
        return 1;
    }

    sm.triggers.insert("start");
    system.update(scene, 1.0f / 60.0f);
    if (sm.currentState != "Run") {
        std::cerr << "Expected asset-defined trigger transition Idle -> Run\n";
        return 1;
    }

    sm.boolParameters["shouldStop"] = true;
    system.update(scene, 1.0f / 60.0f);
    if (sm.currentState != "Idle") {
        std::cerr << "Expected asset-defined bool transition Run -> Idle\n";
        return 1;
    }

    return 0;
}
