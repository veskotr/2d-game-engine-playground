#include <sle/events/EventBus.hpp>

#include <iostream>

struct TickEvent {
    int value = 0;
};

int main() {
    sle::events::EventBus bus;

    int delivered = 0;
    bool queuedFromHandler = false;

    bus.subscribe<TickEvent>([&](const TickEvent& event) {
        ++delivered;
        if (event.value == 1) {
            bus.queue(TickEvent{2});
            queuedFromHandler = true;
        }
    });

    bus.queue(TickEvent{1});
    bus.flushQueue();

    if (!queuedFromHandler) {
        std::cerr << "Expected handler to queue follow-up event\n";
        return 1;
    }

    if (delivered != 1) {
        std::cerr << "Event queued during flush should not dispatch in same flush\n";
        return 1;
    }

    bus.flushQueue();
    if (delivered != 2) {
        std::cerr << "Expected follow-up event to dispatch on next flush\n";
        return 1;
    }

    return 0;
}
