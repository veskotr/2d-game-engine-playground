#include <sle/events/CollisionEvents.hpp>
#include <sle/events/EventBus.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>

#include <iostream>

int main() {
    sle::events::EventBus bus;
    sle::physics::PhysicsWorld world({0.0f, -9.81f});
    world.setFixedTimestep(1.0f / 120.0f);
    world.setEventBus(&bus);

    int collisionBeginCount = 0;
    bus.subscribe<sle::events::CollisionBeginEvent>(
        [&collisionBeginCount](const sle::events::CollisionBeginEvent& event) {
            const uint32_t a = event.entityA.getID();
            const uint32_t b = event.entityB.getID();
            if ((a == 100 && b == 200) || (a == 200 && b == 100)) {
                ++collisionBeginCount;
            }
        });

    sle::components::RigidBodyComponent groundBody;
    groundBody.bodyType = sle::components::BodyType::Static;
    const uint32_t groundId = world.createBody({0.0f, 0.0f}, groundBody);
    world.setBodyEntityId(groundId, 100);

    sle::components::BoxColliderComponent groundCollider;
    groundCollider.size = {120.0f, 10.0f};
    const uintptr_t groundFixture = world.createBoxFixture(groundId, groundCollider);

    sle::components::RigidBodyComponent fallingBody;
    fallingBody.bodyType = sle::components::BodyType::Dynamic;
    const uint32_t fallingId = world.createBody({0.0f, 90.0f}, fallingBody);
    world.setBodyEntityId(fallingId, 200);

    sle::components::BoxColliderComponent fallingCollider;
    fallingCollider.size = {10.0f, 10.0f};
    const uintptr_t fallingFixture = world.createBoxFixture(fallingId, fallingCollider);

    (void)groundFixture;
    (void)fallingFixture;

    for (int i = 0; i < 600 && collisionBeginCount == 0; ++i) {
        world.step(1.0f / 120.0f);
        bus.flushQueue();
    }

    if (collisionBeginCount == 0) {
        std::cerr << "Expected at least one collision begin event between dynamic body and ground\n";
        return 1;
    }

    return 0;
}
