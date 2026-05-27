#include <sle/engine/PhysicsSystem.hpp>
#include <sle/engine/Context.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <glm/glm.hpp>
#include <vector>

namespace sle {

void PhysicsSystem::update(Context& ctx)
{
    if (!ctx.physicsWorld)
        return;

    // Re-inject EventBus whenever physics world changes (e.g., scene switch)
    if (ctx.physicsWorld != lastInjectedWorld_)
    {
        ctx.physicsWorld->setEventBus(&ctx.eventBus);
        lastInjectedWorld_ = ctx.physicsWorld;
    }

    // Phase 1: Create Box2D bodies for newly added RigidBody components
    createNewPhysicsBodies(ctx);

    // Phase 2: Sync current transforms to physics bodies
    syncTransformToPhysics(ctx);

    // Phase 3: Step the physics simulation
    stepPhysics(ctx);

    // Phase 4: Sync physics results back to transforms
    syncPhysicsToTransform(ctx);

    // Phase 5: Clean up physics bodies for destroyed entities
    destroyStalePhysics(ctx);
}

void PhysicsSystem::createNewPhysicsBodies(const Context& ctx)
{
    // Find all entities with RigidBodyComponent
    ctx.registry.view<sle::components::RigidBodyComponent>(
        [&ctx, this](sle::entity::Entity entity, sle::components::RigidBodyComponent& rigidBody)
        {
            // Skip if body already created
            if (rigidBody.box2dBodyId != 0)
                return;

            // Get transform to determine initial position
            auto* transform = ctx.registry.getComponent<sle::components::TransformComponent>(entity);
            if (!transform)
                return;

            glm::vec2 position = transform->getPosition();

            // Create the physics body
            uint32_t bodyId = ctx.physicsWorld->createBody(position, rigidBody);
            rigidBody.box2dBodyId = bodyId;

            // Store entity ID in the body for collision callbacks
            ctx.physicsWorld->setBodyEntityId(bodyId, entity.getID());

            // Track this entity as having physics
            activePhysicsEntities.insert(entity.getID());
            entityBodyIds[entity.getID()] = bodyId;

            // Create colliders/zones for this body
            auto* boxCollider = ctx.registry.getComponent<sle::components::BoxColliderComponent>(entity);
            if (boxCollider && boxCollider->box2dFixtureId == 0)
            {
                boxCollider->box2dFixtureId = ctx.physicsWorld->createBoxFixture(bodyId, *boxCollider);
            }

            auto* circleCollider = ctx.registry.getComponent<sle::components::CircleColliderComponent>(entity);
            if (circleCollider && circleCollider->box2dFixtureId == 0)
            {
                circleCollider->box2dFixtureId = ctx.physicsWorld->createCircleFixture(bodyId, *circleCollider);
            }

            auto* boxZone = ctx.registry.getComponent<sle::components::BoxZoneComponent>(entity);
            if (boxZone && boxZone->box2dFixtureId == 0)
            {
                boxZone->box2dFixtureId = ctx.physicsWorld->createBoxZone(bodyId, *boxZone);
            }

            auto* circleZone = ctx.registry.getComponent<sle::components::CircleZoneComponent>(entity);
            if (circleZone && circleZone->box2dFixtureId == 0)
            {
                circleZone->box2dFixtureId = ctx.physicsWorld->createCircleZone(bodyId, *circleZone);
            }
        }
    );
}

void PhysicsSystem::syncTransformToPhysics(const Context& ctx)
{
    // For each entity with RigidBodyComponent and TransformComponent,
    // update the physics body transform if the transform changed
    ctx.registry.view<sle::components::RigidBodyComponent, sle::components::TransformComponent>(
        [&ctx](sle::entity::Entity entity, sle::components::RigidBodyComponent& rigidBody, sle::components::TransformComponent& transform)
        {
            if (rigidBody.box2dBodyId == 0 || !rigidBody.enabled)
                return;

            // Only sync if transform is dirty (changed this frame)
            if (transform.isDirty())
            {
                glm::vec2 pos = transform.getPosition();
                float rot = transform.getRotation();
                ctx.physicsWorld->setBodyTransform(rigidBody.box2dBodyId, pos, rot);
            }
        }
    );
}

void PhysicsSystem::stepPhysics(const Context& ctx)
{
    // Step the physics world (uses accumulator for fixed timestep)
    ctx.physicsWorld->step(ctx.dt);
}

void PhysicsSystem::syncPhysicsToTransform(const Context& ctx)
{
    // For each entity with RigidBodyComponent and TransformComponent,
    // read the physics body transform and update the Transform
    ctx.registry.view<sle::components::RigidBodyComponent, sle::components::TransformComponent>(
        [&ctx](sle::entity::Entity entity, sle::components::RigidBodyComponent& rigidBody, sle::components::TransformComponent& transform)
        {
            if (rigidBody.box2dBodyId == 0 || !rigidBody.enabled)
                return;

            // Read physics body state
            glm::vec2 pos;
            float rot;
            ctx.physicsWorld->getBodyTransform(rigidBody.box2dBodyId, pos, rot);

            // Update transform (will set dirty flag internally)
            transform.setPosition(pos);
            transform.setRotation(rot);

            // Update velocity state in component
            rigidBody.velocity = ctx.physicsWorld->getBodyVelocity(rigidBody.box2dBodyId);
            rigidBody.angularVelocity = ctx.physicsWorld->getBodyAngularVelocity(rigidBody.box2dBodyId);
        }
    );
}

void PhysicsSystem::destroyStalePhysics(const Context& ctx)
{
    // Find entities that had physics but no longer exist or don't have RigidBodyComponent
    std::vector<uint32_t> toRemove;

    for (uint32_t entityId : activePhysicsEntities)
    {
        sle::entity::Entity entity(entityId);

        // Check if entity still exists
        if (!ctx.registry.hasEntity(entity))
        {
            toRemove.push_back(entityId);
            continue;
        }

        // Check if entity still has RigidBodyComponent
        auto* rigidBody = ctx.registry.getComponent<sle::components::RigidBodyComponent>(entity);
        if (!rigidBody)
        {
            toRemove.push_back(entityId);
            continue;
        }

        // If body was destroyed externally (box2dBodyId reset to 0), clear tracking
        if (rigidBody->box2dBodyId == 0)
        {
            toRemove.push_back(entityId);
        }
    }

    // Remove stale entities from tracking and destroy their physics bodies
    for (uint32_t entityId : toRemove)
    {
        // Clean up physics body even if component/entity was already removed.
        auto bodyIt = entityBodyIds.find(entityId);
        if (bodyIt != entityBodyIds.end() && bodyIt->second != 0)
        {
            ctx.physicsWorld->destroyBody(bodyIt->second);
            entityBodyIds.erase(bodyIt);
        }

        // Keep ECS state consistent if the entity/component still exists.
        sle::entity::Entity entity(entityId);
        if (ctx.registry.hasEntity(entity))
        {
            auto* rigidBody = ctx.registry.getComponent<sle::components::RigidBodyComponent>(entity);
            if (rigidBody)
            {
                rigidBody->box2dBodyId = 0;
            }
        }

        activePhysicsEntities.erase(entityId);
    }
}

} // namespace sle
