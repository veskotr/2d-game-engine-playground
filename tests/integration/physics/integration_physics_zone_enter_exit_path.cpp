#include <sle/events/EventBus.hpp>
#include <sle/events/ZoneEvents.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>

#include <iostream>

int main() {
    sle::events::EventBus bus;
    sle::physics::PhysicsWorld world({0.0f, -9.81f});
    world.setFixedTimestep(1.0f / 120.0f);
    world.setEventBus(&bus);

    int zoneEnterCount = 0;
    int zoneExitCount = 0;

    bus.subscribe<sle::events::ZoneEnterEvent>(
        [&zoneEnterCount](const sle::events::ZoneEnterEvent& event) {
            if (event.zoneEntity.getID() == 3001 &&
                event.otherEntity.getID() == 4002 &&
                event.zoneId == "phase1_trigger") {
                ++zoneEnterCount;
            }
        });

    bus.subscribe<sle::events::ZoneExitEvent>(
        [&zoneExitCount](const sle::events::ZoneExitEvent& event) {
            if (event.zoneEntity.getID() == 3001 &&
                event.otherEntity.getID() == 4002 &&
                event.zoneId == "phase1_trigger") {
                ++zoneExitCount;
            }
        });

    sle::components::RigidBodyComponent zoneBody;
    zoneBody.bodyType = sle::components::BodyType::Static;
    const uint32_t zoneBodyId = world.createBody({0.0f, 60.0f}, zoneBody);
    world.setBodyEntityId(zoneBodyId, 3001);

    sle::components::BoxZoneComponent zone;
    zone.size = {90.0f, 20.0f};
    zone.zoneId = "phase1_trigger";
    world.createBoxZone(zoneBodyId, zone);

    sle::components::RigidBodyComponent movingBody;
    movingBody.bodyType = sle::components::BodyType::Dynamic;
    const uint32_t movingBodyId = world.createBody({0.0f, 180.0f}, movingBody);
    world.setBodyEntityId(movingBodyId, 4002);

    sle::components::BoxColliderComponent movingCollider;
    movingCollider.size = {8.0f, 8.0f};
    world.createBoxFixture(movingBodyId, movingCollider);

    for (int i = 0; i < 900; ++i) {
        world.step(1.0f / 120.0f);

        // Contact callbacks must enqueue events, not dispatch immediately.
        if (zoneEnterCount != 0 || zoneExitCount != 0) {
            std::cerr << "Zone event dispatched during physics step; expected deferred dispatch\n";
            return 1;
        }
    }

    bus.flushQueue();

    if (zoneEnterCount == 0) {
        std::cerr << "Expected at least one queued ZoneEnterEvent after flushQueue\n";
        return 1;
    }

    if (zoneExitCount == 0) {
        std::cerr << "Expected at least one queued ZoneExitEvent after flushQueue\n";
        return 1;
    }

    return 0;
}
