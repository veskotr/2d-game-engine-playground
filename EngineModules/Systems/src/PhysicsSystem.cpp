#include <sle/engine/PhysicsSystem.hpp>
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
#include <glm/glm.hpp>
#include <vector>

namespace sle {

namespace {
bool usablePose(const Context& ctx, sle::entity::Entity entity)
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
    authoredPoses_.erase(entity.getID());
    fixtureScales_.erase(entity.getID());
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

    destroyStalePhysics(ctx);

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

            if (!usablePose(ctx, entity)) return;
            const auto pose = sle::entity::transformMath::currentWorld(ctx.scene, entity);
            glm::vec2 position = pose.position;

            // Create the physics body
            uint32_t bodyId = ctx.physicsWorld->createBody(position, rigidBody);
            rigidBody.box2dBodyId = bodyId;
            ctx.physicsWorld->setBodyTransform(bodyId, position, pose.rotation);
            authoredPoses_[entity.getID()] = pose;

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
        [&ctx, this](sle::entity::Entity entity, sle::components::RigidBodyComponent& body)
        {
            if (body.box2dBodyId == 0)
                return;
            using Subsystem = sle::components::ChunkSubsystem;
            const bool active = body.enabled && usablePose(ctx, entity) && sle::entity::isChunkActive(ctx.registry, entity, Subsystem::Physics);
            const bool changed = ctx.physicsWorld->setBodyEnabled(body.box2dBodyId, active);
            if (!active)
                return; // Keep fixtures/velocity intact while the whole body is suspended.
            if (changed)
            {
                const auto pose = sle::entity::transformMath::currentWorld(ctx.scene, entity);
                ctx.physicsWorld->setBodyTransform(body.box2dBodyId, pose.position, pose.rotation);
                authoredPoses_[entity.getID()] = pose;
            }
            const auto scale = sle::entity::transformMath::currentParentWorld(ctx.scene, entity).scale;
            const auto oldScale = fixtureScales_.find(entity.getID());
            const bool rebuild = oldScale != fixtureScales_.end() && oldScale->second != scale;
            fixtureScales_[entity.getID()] = scale;
            const auto velocity = ctx.physicsWorld->getBodyVelocity(body.box2dBodyId);
            const auto angularVelocity = ctx.physicsWorld->getBodyAngularVelocity(body.box2dBodyId);

            auto syncFixture = [&](auto* component, Subsystem subsystem, auto createFixture)
            {
                if (!component)
                    return;
                const bool enabled = active && component->enabled && sle::entity::isChunkActive(ctx.registry, entity, subsystem);
                if ((!enabled || rebuild) && component->box2dFixtureId != 0)
                {
                    ctx.physicsWorld->destroyFixture(body.box2dBodyId, component->box2dFixtureId);
                    component->box2dFixtureId = 0;
                }
                if (enabled && component->box2dFixtureId == 0)
                {
                    component->box2dFixtureId = createFixture(*component);
                }
            };
            syncFixture(ctx.registry.getComponent<sle::components::BoxColliderComponent>(entity),
                Subsystem::Collision, [&](auto shape) { shape.size *= glm::abs(scale); shape.offset *= scale; return ctx.physicsWorld->createBoxFixture(body.box2dBodyId, shape); });
            syncFixture(ctx.registry.getComponent<sle::components::CircleColliderComponent>(entity),
                Subsystem::Collision, [&](auto shape) { return ctx.physicsWorld->createCircleFixture(body.box2dBodyId, shape, scale); });
            syncFixture(ctx.registry.getComponent<sle::components::BoxZoneComponent>(entity),
                Subsystem::Zones, [&](auto shape) { shape.size *= glm::abs(scale); shape.offset *= scale; return ctx.physicsWorld->createBoxZone(body.box2dBodyId, shape); });
            syncFixture(ctx.registry.getComponent<sle::components::CircleZoneComponent>(entity),
                Subsystem::Zones, [&](auto shape) { return ctx.physicsWorld->createCircleZone(body.box2dBodyId, shape, scale); });
            if (rebuild)
            {
                // ResetMassData during fixture replacement moves the center of
                // mass; preserve the API's world velocity across that operation.
                ctx.physicsWorld->setBodyVelocity(body.box2dBodyId, velocity);
                ctx.physicsWorld->setBodyAngularVelocity(body.box2dBodyId, angularVelocity);
            }
        });
}

void PhysicsSystem::syncTransformToPhysics(const Context& ctx)
{
    ctx.registry.view<sle::components::RigidBodyComponent, sle::components::TransformComponent>(
        [&ctx, this](sle::entity::Entity entity, sle::components::RigidBodyComponent& body, sle::components::TransformComponent&)
        {
            if (!body.box2dBodyId || !body.enabled || !usablePose(ctx, entity) ||
                !sle::entity::isChunkActive(ctx.registry, entity, sle::components::ChunkSubsystem::Physics)) return;
            const auto pose = sle::entity::transformMath::currentWorld(ctx.scene, entity);
            const auto previous = authoredPoses_.find(entity.getID());
            if (previous == authoredPoses_.end() || previous->second.position != pose.position || previous->second.rotation != pose.rotation)
                ctx.physicsWorld->setBodyTransform(body.box2dBodyId, pose.position, pose.rotation);
            authoredPoses_[entity.getID()] = pose;
        });
}

void PhysicsSystem::stepPhysics(const Context& ctx)
{
    // Step the physics world (uses accumulator for fixed timestep)
    ctx.physicsWorld->step(ctx.dt);
}

void PhysicsSystem::syncPhysicsToTransform(const Context& ctx)
{
    using namespace sle::entity::transformMath;
    using sle::components::WorldTransformComponent;
    // Snapshot every native pose before modifying authored locals. A simulated
    // ancestor and child are independent world bodies, regardless of view order.
    std::unordered_map<uint32_t, WorldTransformComponent> simulated;
    ctx.registry.view<sle::components::RigidBodyComponent>([&](sle::entity::Entity entity, auto& body) {
        if (!body.box2dBodyId || !body.enabled || !usablePose(ctx, entity) ||
            !sle::entity::isChunkActive(ctx.registry, entity, sle::components::ChunkSubsystem::Physics)) return;
        auto& pose = simulated[entity.getID()];
        ctx.physicsWorld->getBodyTransform(body.box2dBodyId, pose.position, pose.rotation);
        body.velocity = ctx.physicsWorld->getBodyVelocity(body.box2dBodyId);
        body.angularVelocity = ctx.physicsWorld->getBodyAngularVelocity(body.box2dBodyId);
    });
    struct Entry { sle::entity::Entity entity; WorldTransformComponent parent; };
    std::vector<Entry> stack;
    for (auto root : ctx.scene.getRoots()) stack.push_back({root, {}});
    while (!stack.empty())
    {
        auto entry = stack.back(); stack.pop_back();
        auto resolved = entry.parent;
        if (auto* local = ctx.registry.getComponent<sle::components::TransformComponent>(entry.entity))
        {
            if (const auto it = simulated.find(entry.entity.getID()); it != simulated.end() && invertible(entry.parent))
            {
                const auto position = toLocalPosition(entry.parent, it->second.position);
                const float rotation = it->second.rotation - entry.parent.rotation;
                if (std::isfinite(position.x) && std::isfinite(position.y) && std::isfinite(rotation))
                {
                    local->setPosition(position);
                    local->setRotation(rotation);
                }
            }
            resolved = compose(entry.parent, *local);
            if (simulated.contains(entry.entity.getID())) authoredPoses_[entry.entity.getID()] = resolved;
        }
        for (auto child : ctx.scene.getChildren(entry.entity)) stack.push_back({child, resolved});
    }
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
        if (!rigidBody || !ctx.registry.getComponent<sle::components::TransformComponent>(entity))
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

        if (ctx.registry.hasEntity(entity)) {
            if (auto* shape = ctx.registry.getComponent<sle::components::BoxColliderComponent>(entity)) shape->box2dFixtureId = 0;
            if (auto* shape = ctx.registry.getComponent<sle::components::CircleColliderComponent>(entity)) shape->box2dFixtureId = 0;
            if (auto* shape = ctx.registry.getComponent<sle::components::BoxZoneComponent>(entity)) shape->box2dFixtureId = 0;
            if (auto* shape = ctx.registry.getComponent<sle::components::CircleZoneComponent>(entity)) shape->box2dFixtureId = 0;
        }
        authoredPoses_.erase(entityId);
        fixtureScales_.erase(entityId);
        activePhysicsEntities.erase(entityId);
    }
}

} // namespace sle
