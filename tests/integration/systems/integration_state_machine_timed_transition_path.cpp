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
    def->initialState = "Warmup";

    sle::components::StateDefinition warmup;
    warmup.name = "Warmup";
    {
        sle::components::StateTransition transition;
        transition.toState = "Active";
        transition.type = sle::components::StateTransitionType::MinTimeInState;
        transition.minTimeSeconds = 0.09f;
        warmup.transitions.push_back(transition);
    }

    sle::components::StateDefinition active;
    active.name = "Active";

    def->states.emplace(warmup.name, warmup);
    def->states.emplace(active.name, active);

    const sle::entity::Entity entity = scene.createEntity();
    auto& sm = registry.addComponent<sle::components::StateMachineComponent>(entity);
    sm.definition = def;

    sle::StateMachineSystem system;

    system.update(scene, 0.01f);
    if (sm.currentState != "Warmup") {
        std::cerr << "State machine should initialize to Warmup\n";
        return 1;
    }

    for (int i = 0; i < 8; ++i) {
        system.update(scene, 0.01f);
    }

    if (sm.currentState != "Warmup") {
        std::cerr << "State machine transitioned too early before min time elapsed\n";
        return 1;
    }

    system.update(scene, 0.01f);
    system.update(scene, 0.01f);
    if (sm.currentState != "Active") {
        std::cerr << "Expected timed transition Warmup -> Active at min state time\n";
        return 1;
    }

    if (sm.stateTimeSeconds != 0.0f) {
        std::cerr << "State time should reset to zero after transition\n";
        return 1;
    }

    return 0;
}
