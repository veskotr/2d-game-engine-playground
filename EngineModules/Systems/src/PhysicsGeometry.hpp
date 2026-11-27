#pragma once

#include <sle/scene/ChunkActivity.hpp>
#include <sle/engine/Context.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/TransformMath.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>

namespace sle {
// Share the body suspension gate with debug outlines, including inactive shapes
// that must not invalidate otherwise usable geometry. Call with a PhysicsWorld.
inline bool usablePhysicsPose(const Context& ctx, sle::entity::Entity entity)
{
    using namespace sle::entity::transformMath;
    const auto parent = currentParentWorld(ctx.scene, entity);
    if (!ctx.registry.getComponent<sle::components::TransformComponent>(entity) ||
        !invertible(parent) || !finite(currentWorld(ctx.scene, entity))) return false;
    using Subsystem = sle::components::ChunkSubsystem;
    auto boxSafe = [&](const auto* shape, Subsystem subsystem) {
        return !shape || !shape->enabled || !sle::entity::isChunkActive(ctx.registry, entity, subsystem) ||
            ctx.physicsWorld->isBoxGeometryValid(shape->size * glm::abs(parent.scale), shape->offset * parent.scale);
    };
    auto circleSafe = [&](const auto* shape, Subsystem subsystem) {
        return !shape || !shape->enabled || !sle::entity::isChunkActive(ctx.registry, entity, subsystem) ||
            ctx.physicsWorld->isCircleGeometryValid(shape->radius, shape->offset, parent.scale);
    };
    return boxSafe(ctx.registry.getComponent<sle::components::BoxColliderComponent>(entity), Subsystem::Collision) &&
        boxSafe(ctx.registry.getComponent<sle::components::BoxZoneComponent>(entity), Subsystem::Zones) &&
        circleSafe(ctx.registry.getComponent<sle::components::CircleColliderComponent>(entity), Subsystem::Collision) &&
        circleSafe(ctx.registry.getComponent<sle::components::CircleZoneComponent>(entity), Subsystem::Zones);
}
}
