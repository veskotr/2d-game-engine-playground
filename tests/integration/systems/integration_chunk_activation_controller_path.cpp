#include <sle/core/ChunkActivationConfig.hpp>
#include <sle/engine/ChunkActivationController.hpp>
#include <sle/engine/ChunkOwnershipIndex.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/UIComponent.hpp>

#include <iostream>
#include <vector>

namespace {

bool expect(bool condition, const char* message)
{
    if (!condition)
        std::cerr << message << "\n";
    return condition;
}

} // namespace

int main()
{
    sle::entity::Scene scene;
    auto& registry = scene.getRegistry();

    const sle::entity::Entity autoEntity = scene.createEntity();
    registry.addComponent<sle::components::ChunkActivationComponent>(autoEntity);
    registry.addComponent<sle::components::ScriptComponent>(autoEntity);
    registry.addComponent<sle::components::RigidBodyComponent>(autoEntity);
    registry.addComponent<sle::components::StateMachineComponent>(autoEntity);
    registry.addComponent<sle::components::AnimatorComponent>(autoEntity);
    registry.addComponent<sle::components::BoxColliderComponent>(autoEntity);
    registry.addComponent<sle::components::BoxZoneComponent>(autoEntity);
    registry.addComponent<sle::components::UIComponent>(autoEntity);

    const sle::entity::Entity eventsOnlyEntity = scene.createEntity();
    auto& eventsOnlyActivation = registry.addComponent<sle::components::ChunkActivationComponent>(eventsOnlyEntity);
    eventsOnlyActivation.mode = sle::components::ChunkEntityActivationMode::EventsOnly;
    registry.addComponent<sle::components::ScriptComponent>(eventsOnlyEntity);

    const sle::entity::Entity alwaysActiveEntity = scene.createEntity();
    auto& alwaysActiveActivation = registry.addComponent<sle::components::ChunkActivationComponent>(alwaysActiveEntity);
    alwaysActiveActivation.mode = sle::components::ChunkEntityActivationMode::AlwaysActive;
    registry.addComponent<sle::components::ScriptComponent>(alwaysActiveEntity);

    const sle::entity::Entity keepEntity = scene.createEntity();
    auto& keepActivation = registry.addComponent<sle::components::ChunkActivationComponent>(keepEntity);
    keepActivation.mode = sle::components::ChunkEntityActivationMode::AutoToggle;
    keepActivation.keepScriptsWhenInactive = true;
    keepActivation.keepPhysicsWhenInactive = true;
    keepActivation.keepRenderingWhenInactive = true;
    registry.addComponent<sle::components::ScriptComponent>(keepEntity);
    registry.addComponent<sle::components::RigidBodyComponent>(keepEntity);
    registry.addComponent<sle::components::BoxColliderComponent>(keepEntity);
    registry.addComponent<sle::components::BoxZoneComponent>(keepEntity);
    registry.addComponent<sle::components::UIComponent>(keepEntity);
    registry.addComponent<sle::components::StateMachineComponent>(keepEntity);

    sle::ChunkOwnershipIndex ownershipIndex;
    ownershipIndex.rebuild(registry);

    sle::ChunkActivationController controller({
        .mode = sle::core::ChunkActivationMode::AutoToggle,
        .affectScripts = true,
        .affectStateMachines = true,
        .affectAnimation = true,
        .affectPhysics = true,
        .affectCollision = true,
        .affectZones = true,
        .affectUi = true
    });

    controller.applyActiveChunks({{0, 0}}, ownershipIndex, registry);

    if (!expect(registry.getComponent<sle::components::ScriptComponent>(autoEntity)->enabled, "Auto entity should start active") ||
        !expect(registry.getComponent<sle::components::UIComponent>(autoEntity)->visible, "Auto entity UI should start visible"))
    {
        return 1;
    }

    controller.applyActiveChunks({{5, 5}}, ownershipIndex, registry);

    if (!expect(!registry.getComponent<sle::components::ScriptComponent>(autoEntity)->enabled, "Auto entity script should disable outside active chunk") ||
        !expect(!registry.getComponent<sle::components::RigidBodyComponent>(autoEntity)->enabled, "Auto entity rigid body should disable outside active chunk") ||
        !expect(!registry.getComponent<sle::components::StateMachineComponent>(autoEntity)->enabled, "Auto entity state machine should disable outside active chunk") ||
        !expect(!registry.getComponent<sle::components::AnimatorComponent>(autoEntity)->enabled, "Auto entity animator should disable outside active chunk") ||
        !expect(!registry.getComponent<sle::components::BoxColliderComponent>(autoEntity)->enabled, "Auto entity collider should disable outside active chunk") ||
        !expect(!registry.getComponent<sle::components::BoxZoneComponent>(autoEntity)->enabled, "Auto entity zone should disable outside active chunk") ||
        !expect(!registry.getComponent<sle::components::UIComponent>(autoEntity)->visible, "Auto entity UI should hide outside active chunk"))
    {
        return 1;
    }

    if (!expect(registry.getComponent<sle::components::ScriptComponent>(eventsOnlyEntity)->enabled, "Events-only entity should not auto-toggle") ||
        !expect(registry.getComponent<sle::components::ScriptComponent>(alwaysActiveEntity)->enabled, "Always-active entity should stay enabled") ||
        !expect(registry.getComponent<sle::components::ScriptComponent>(keepEntity)->enabled, "keepScriptsWhenInactive should preserve script state") ||
        !expect(registry.getComponent<sle::components::RigidBodyComponent>(keepEntity)->enabled, "keepPhysicsWhenInactive should preserve rigid body state") ||
        !expect(registry.getComponent<sle::components::BoxColliderComponent>(keepEntity)->enabled, "keepPhysicsWhenInactive should preserve collider state") ||
        !expect(registry.getComponent<sle::components::BoxZoneComponent>(keepEntity)->enabled, "keepPhysicsWhenInactive should preserve zone state") ||
        !expect(registry.getComponent<sle::components::UIComponent>(keepEntity)->visible, "keepRenderingWhenInactive should preserve UI visibility") ||
        !expect(!registry.getComponent<sle::components::StateMachineComponent>(keepEntity)->enabled, "State machine should still disable when not preserved"))
    {
        return 1;
    }

    sle::ChunkActivationController disabledController({
        .mode = sle::core::ChunkActivationMode::Disabled
    });

    registry.getComponent<sle::components::ScriptComponent>(autoEntity)->enabled = true;
    disabledController.applyActiveChunks({}, ownershipIndex, registry);
    if (!expect(registry.getComponent<sle::components::ScriptComponent>(autoEntity)->enabled, "Disabled global chunk activation should not change entity state"))
        return 1;

    controller.applyActiveChunks({{0, 0}}, ownershipIndex, registry);
    if (!expect(registry.getComponent<sle::components::ScriptComponent>(autoEntity)->enabled, "Auto entity script should re-enable when chunk becomes active again") ||
        !expect(registry.getComponent<sle::components::UIComponent>(autoEntity)->visible, "Auto entity UI should re-show when chunk becomes active again"))
    {
        return 1;
    }

    return 0;
}
