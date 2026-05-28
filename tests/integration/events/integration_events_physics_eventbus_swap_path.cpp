#include <sle/events/CollisionEvents.hpp>
#include <sle/events/EventBus.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>

#include <iostream>

namespace {

void createCollisionPair(
    sle::physics::PhysicsWorld& world,
    uint32_t groundEntity,
    uint32_t fallingEntity,
    float groundY,
    float fallingY) {
    sle::components::RigidBodyComponent groundBody;
    groundBody.bodyType = sle::components::BodyType::Static;
    const uint32_t groundId = world.createBody({0.0f, groundY}, groundBody);
    world.setBodyEntityId(groundId, groundEntity);

    sle::components::BoxColliderComponent groundCollider;
    groundCollider.size = {120.0f, 10.0f};
    world.createBoxFixture(groundId, groundCollider);

    sle::components::RigidBodyComponent fallingBody;
    fallingBody.bodyType = sle::components::BodyType::Dynamic;
    const uint32_t fallingId = world.createBody({0.0f, fallingY}, fallingBody);
    world.setBodyEntityId(fallingId, fallingEntity);

    sle::components::BoxColliderComponent fallingCollider;
    fallingCollider.size = {10.0f, 10.0f};
    world.createBoxFixture(fallingId, fallingCollider);
}

void stepUntilCollisionQueued(sle::physics::PhysicsWorld& world, int maxSteps) {
    for (int i = 0; i < maxSteps; ++i) {
        world.step(1.0f / 120.0f);
    }
}

} // namespace

int main() {
    sle::events::EventBus busA;
    sle::events::EventBus busB;

    sle::physics::PhysicsWorld world({0.0f, -9.81f});
    world.setFixedTimestep(1.0f / 120.0f);
    world.setEventBus(&busA);

    int aCollisionCount = 0;
    int bCollisionCount = 0;

    busA.subscribe<sle::events::CollisionBeginEvent>(
        [&aCollisionCount](const sle::events::CollisionBeginEvent&) {
            ++aCollisionCount;
        });

    busB.subscribe<sle::events::CollisionBeginEvent>(
        [&bCollisionCount](const sle::events::CollisionBeginEvent&) {
            ++bCollisionCount;
        });

    createCollisionPair(world, 101, 201, 0.0f, 90.0f);
    stepUntilCollisionQueued(world, 700);

    if (aCollisionCount != 0 || bCollisionCount != 0) {
        std::cerr << "Collision dispatched before explicit EventBus flush during busA phase\n";
        return 1;
    }

    busA.flushQueue();

    if (aCollisionCount == 0) {
        std::cerr << "Expected first collision to dispatch through busA after flush\n";
        return 1;
    }

    if (bCollisionCount != 0) {
        std::cerr << "busB should not receive events before swap\n";
        return 1;
    }

    world.setEventBus(&busB);

    createCollisionPair(world, 102, 202, -300.0f, -210.0f);
    stepUntilCollisionQueued(world, 700);

    if (bCollisionCount != 0) {
        std::cerr << "Collision dispatched before explicit EventBus flush during busB phase\n";
        return 1;
    }

    const int aBeforeSecondFlush = aCollisionCount;
    busA.flushQueue();
    busB.flushQueue();

    if (aCollisionCount != aBeforeSecondFlush) {
        std::cerr << "Stale routing detected: second collision should not dispatch through busA\n";
        return 1;
    }

    if (bCollisionCount == 0) {
        std::cerr << "Expected second collision to dispatch through swapped busB after flush\n";
        return 1;
    }

    return 0;
}
