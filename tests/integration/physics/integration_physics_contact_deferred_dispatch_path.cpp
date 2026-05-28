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
        [&collisionBeginCount](const sle::events::CollisionBeginEvent&) {
            ++collisionBeginCount;
        });

    sle::components::RigidBodyComponent groundBody;
    groundBody.bodyType = sle::components::BodyType::Static;
    const uint32_t groundId = world.createBody({0.0f, 0.0f}, groundBody);
    world.setBodyEntityId(groundId, 1001);

    sle::components::BoxColliderComponent groundCollider;
    groundCollider.size = {120.0f, 10.0f};
    world.createBoxFixture(groundId, groundCollider);

    sle::components::RigidBodyComponent dynamicBody;
    dynamicBody.bodyType = sle::components::BodyType::Dynamic;
    const uint32_t dynamicId = world.createBody({0.0f, 90.0f}, dynamicBody);
    world.setBodyEntityId(dynamicId, 2002);

    sle::components::BoxColliderComponent dynamicCollider;
    dynamicCollider.size = {10.0f, 10.0f};
    world.createBoxFixture(dynamicId, dynamicCollider);

    for (int i = 0; i < 600; ++i) {
        world.step(1.0f / 120.0f);

        // Collision callbacks should queue events, not dispatch immediately.
        if (collisionBeginCount != 0) {
            std::cerr << "Collision event dispatched during physics step; expected deferred dispatch\n";
            return 1;
        }
    }

    bus.flushQueue();

    if (collisionBeginCount == 0) {
        std::cerr << "Expected queued collision event to dispatch after explicit flushQueue\n";
        return 1;
    }

    return 0;
}
