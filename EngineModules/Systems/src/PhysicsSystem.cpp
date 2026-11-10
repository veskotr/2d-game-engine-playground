#include <sle/engine/PhysicsSystem.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/engine/Context.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/Scene.hpp>
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

void PhysicsSystem::releaseEntity(sle::entity::Scene& scene, sle::physics::PhysicsWorld* world, sle::entity::Entity entity)
{
    auto& registry = scene.getRegistry();
    auto it = entityBodyIds.find(entity.getID());
    if (it != entityBodyIds.end()) {
        if (world) world->destroyBody(it->second);
        entityBodyIds.erase(it);
    } else if (const auto* body = registry.getComponent<sle::components::RigidBodyComponent>(entity); world && body) {
        world->destroyBody(body->box2dBodyId);
    }
    activePhysicsEntities.erase(entity.getID());
    if (auto* body = registry.getComponent<sle::components::RigidBodyComponent>(entity)) body->box2dBodyId = 0;
    if (auto* shape = registry.getComponent<sle::components::BoxColliderComponent>(entity)) shape->box2dFixtureId = 0;
    if (auto* shape = registry.getComponent<sle::components::CircleColliderComponent>(entity)) shape->box2dFixtureId = 0;
    if (auto* shape = registry.getComponent<sle::components::BoxZoneComponent>(entity)) shape->box2dFixtureId = 0;
    if (auto* shape = registry.getComponent<sle::components::CircleZoneComponent>(entity)) shape->box2dFixtureId = 0;
}

void PhysicsSystem::update(Context& ctx)
{
    if (!ctx.physicsWorld)
        return;

    // Re-inject EventBus whenever physics world or scene EventBus changes.
    if (ctx.physicsWorld != lastInjectedWorld_ || &ctx.eventBus != lastInjectedEventBus_)
    {
        ctx.physicsWorld->setEventBus(&ctx.eventBus);
        lastInjectedWorld_ = ctx.physicsWorld;
        lastInjectedEventBus_ = &ctx.eventBus;
    }

    // Phase 1: Create Box2D bodies for newly added RigidBody components
    createNewPhysicsBodies(ctx);
    syncActivationAndFixtures(ctx);

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
            if (rigidBody.box2dBodyId != 0 || !rigidBody.enabled ||
                !sle::entity::isChunkActive(ctx.registry, entity, sle::components::ChunkSubsystem::Physics))
                return;

            // Get transform to determine initial position
            auto* transform = ctx.registry.getComponent<sle::components::TransformComponent>(entity);
            if (!transform)
                return;

            glm::vec2 position = transform->getPosition();

            // Create the physics body
            uint32_t bodyId = ctx.physicsWorld->createBody(position, rigidBody);
            rigidBody.box2dBodyId = bodyId;
            ctx.physicsWorld->setBodyTransform(bodyId, position, transform->getRotation());

            // Store entity ID in the body for collision callbacks
            ctx.physicsWorld->setBodyEntityId(bodyId, entity.getID());

            // Track this entity as having physics
            activePhysicsEntities.insert(entity.getID());
            entityBodyIds[entity.getID()] = bodyId;

        }
    );
}

void PhysicsSystem::syncActivationAndFixtures(const Context& ctx)
{
    ctx.registry.view<sle::components::RigidBodyComponent>(
        [&ctx](sle::entity::Entity entity, sle::components::RigidBodyComponent& body)
        {
            if (body.box2dBodyId == 0)
                return;
            using Subsystem = sle::components::ChunkSubsystem;
            const bool active = body.enabled && sle::entity::isChunkActive(ctx.registry, entity, Subsystem::Physics);
            const bool changed = ctx.physicsWorld->setBodyEnabled(body.box2dBodyId, active);
            if (!active)
                return; // Keep fixtures/velocity intact while the whole body is suspended.
            if (changed)
            {
                // Presentation may already have consumed dirty flags while this
                // body was suspended. The latest local pose wins on reactivation.
                if (const auto* transform = ctx.registry.getComponent<sle::components::TransformComponent>(entity))
                    ctx.physicsWorld->setBodyTransform(body.box2dBodyId, transform->getPosition(), transform->getRotation());
            }

            auto syncFixture = [&](auto* component, Subsystem subsystem, auto createFixture)
            {
                if (!component)
                    return;
                const bool enabled = active && component->enabled && sle::entity::isChunkActive(ctx.registry, entity, subsystem);
                if (!enabled && component->box2dFixtureId != 0)
                {
                    ctx.physicsWorld->destroyFixture(body.box2dBodyId, component->box2dFixtureId);
                    component->box2dFixtureId = 0;
                }
                else if (enabled && component->box2dFixtureId == 0)
                {
                    component->box2dFixtureId = (ctx.physicsWorld->*createFixture)(body.box2dBodyId, *component);
                }
            };
            syncFixture(ctx.registry.getComponent<sle::components::BoxColliderComponent>(entity),
                Subsystem::Collision, &sle::physics::PhysicsWorld::createBoxFixture);
            syncFixture(ctx.registry.getComponent<sle::components::CircleColliderComponent>(entity),
                Subsystem::Collision, &sle::physics::PhysicsWorld::createCircleFixture);
            syncFixture(ctx.registry.getComponent<sle::components::BoxZoneComponent>(entity),
                Subsystem::Zones, &sle::physics::PhysicsWorld::createBoxZone);
            syncFixture(ctx.registry.getComponent<sle::components::CircleZoneComponent>(entity),
                Subsystem::Zones, &sle::physics::PhysicsWorld::createCircleZone);
        });
}

void PhysicsSystem::syncTransformToPhysics(const Context& ctx)
{
    // For each entity with RigidBodyComponent and TransformComponent,
    // update the physics body transform if the transform changed
    ctx.registry.view<sle::components::RigidBodyComponent, sle::components::TransformComponent>(
        [&ctx](sle::entity::Entity entity, sle::components::RigidBodyComponent& rigidBody, sle::components::TransformComponent& transform)
        {
            if (rigidBody.box2dBodyId == 0 || !rigidBody.enabled ||
                !sle::entity::isChunkActive(ctx.registry, entity, sle::components::ChunkSubsystem::Physics))
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
            if (rigidBody.box2dBodyId == 0 || !rigidBody.enabled ||
                !sle::entity::isChunkActive(ctx.registry, entity, sle::components::ChunkSubsystem::Physics))
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
