#include <sle/engine/ChunkActivityTracker.hpp>
#include <sle/events/ChunkEvents.hpp>
#include <sle/events/EventBus.hpp>

#include <iostream>
#include <string>
#include <vector>

int main()
{
    sle::ChunkActivityTracker tracker(
        sle::ChunkManager({
            .chunkWidthTiles = 16,
            .chunkHeightTiles = 16,
            .tileWidth = 32,
            .tileHeight = 32,
            .activeRadiusX = 0,
            .activeRadiusY = 0
        }),
        "test_map");

    sle::events::EventBus eventBus;
    std::vector<std::string> eventLog;

    eventBus.subscribe<sle::events::ChunkActivatedEvent>(
        [&eventLog](const sle::events::ChunkActivatedEvent& event)
        {
            eventLog.push_back(
                "activate:" + event.mapId + ":" + std::to_string(event.chunkX) + ":" + std::to_string(event.chunkY));
        });

    eventBus.subscribe<sle::events::ChunkDeactivatedEvent>(
        [&eventLog](const sle::events::ChunkDeactivatedEvent& event)
        {
            eventLog.push_back(
                "deactivate:" + event.mapId + ":" + std::to_string(event.chunkX) + ":" + std::to_string(event.chunkY));
        });

    const auto first = tracker.updateFocusPosition({10.0f, 10.0f}, &eventBus);
    if (first.activated.size() != 1 || first.deactivated.size() != 0 || first.active.size() != 1)
    {
        std::cerr << "Unexpected first update activation state\n";
        return 1;
    }

    const auto second = tracker.updateFocusPosition({600.0f, 10.0f}, &eventBus);
    if (second.activated.size() != 1 || second.deactivated.size() != 1 || second.active.size() != 1)
    {
        std::cerr << "Unexpected second update activation state\n";
        return 1;
    }

    if (eventLog.size() != 3)
    {
        std::cerr << "Unexpected number of chunk lifecycle events\n";
        return 1;
    }

    if (eventLog[0] != "activate:test_map:0:0" ||
        eventLog[1] != "activate:test_map:1:0" ||
        eventLog[2] != "deactivate:test_map:0:0")
    {
        std::cerr << "Unexpected chunk lifecycle event order/content\n";
        return 1;
    }

    return 0;
}
