#include <sle/events/CollisionEvents.hpp>
#include <sle/events/EventBus.hpp>

#include <iostream>

int main() {
    sle::events::EventBus bus;

    int deliveredCount = 0;
    bus.subscribe<sle::events::CollisionBeginEvent>(
        [&deliveredCount](const sle::events::CollisionBeginEvent&) {
            ++deliveredCount;
        },
        [](const sle::events::CollisionBeginEvent& event) {
            return event.entityA.getID() == 1;
        });

    bus.queue(sle::events::CollisionBeginEvent{sle::entity::Entity{1}, sle::entity::Entity{2}});
    if (deliveredCount != 0) {
        std::cerr << "Queued event should not be delivered before flushQueue\n";
        return 1;
    }

    bus.flushQueue();
    if (deliveredCount != 1) {
        std::cerr << "Expected exactly one delivered collision event after flush\n";
        return 1;
    }

    bus.emit(sle::events::CollisionBeginEvent{sle::entity::Entity{9}, sle::entity::Entity{2}});
    if (deliveredCount != 1) {
        std::cerr << "Filter should block non-matching collision event\n";
        return 1;
    }

    return 0;
}
