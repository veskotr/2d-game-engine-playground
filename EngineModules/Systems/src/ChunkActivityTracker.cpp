#include <sle/engine/ChunkActivityTracker.hpp>

#include <sle/events/ChunkEvents.hpp>
#include <sle/events/EventBus.hpp>

#include <algorithm>
#include <iterator>
#include <utility>

namespace sle {
namespace {

void sortUnique(std::vector<ChunkCoord>& values)
{
    std::sort(values.begin(), values.end());
    values.erase(std::unique(values.begin(), values.end()), values.end());
}

template <typename T>
std::vector<T> sortedDifference(const std::vector<T>& lhs, const std::vector<T>& rhs)
{
    std::vector<T> result;
    std::set_difference(lhs.begin(), lhs.end(), rhs.begin(), rhs.end(), std::back_inserter(result));
    return result;
}

} // namespace

ChunkActivityTracker::ChunkActivityTracker(ChunkManager managerIn, std::string mapIdIn)
    : manager(std::move(managerIn)), mapId(std::move(mapIdIn))
{
}

ChunkActivityUpdate ChunkActivityTracker::updateFocusPosition(
    const glm::vec2& focusWorldPosition,
    sle::events::EventBus* eventBus)
{
    ChunkActivityUpdate update;
    update.active = manager.computeActiveWindow(focusWorldPosition);
    sortUnique(update.active);

    std::vector<ChunkCoord> previous = activeChunks;
    sortUnique(previous);

    update.activated = sortedDifference(update.active, previous);
    update.deactivated = sortedDifference(previous, update.active);

    activeChunks = update.active;

    if (eventBus)
    {
        for (const ChunkCoord& coord : update.activated)
            emitActivated(coord, *eventBus);
        for (const ChunkCoord& coord : update.deactivated)
            emitDeactivated(coord, *eventBus);
    }

    return update;
}

void ChunkActivityTracker::emitActivated(const ChunkCoord& coord, sle::events::EventBus& eventBus) const
{
    const ChunkBounds bounds = manager.getChunkBounds(coord);
    eventBus.emit(sle::events::ChunkActivatedEvent{
        mapId,
        coord.x,
        coord.y,
        bounds.min.x,
        bounds.min.y,
        bounds.max.x,
        bounds.max.y
    });
}

void ChunkActivityTracker::emitDeactivated(const ChunkCoord& coord, sle::events::EventBus& eventBus) const
{
    const ChunkBounds bounds = manager.getChunkBounds(coord);
    eventBus.emit(sle::events::ChunkDeactivatedEvent{
        mapId,
        coord.x,
        coord.y,
        bounds.min.x,
        bounds.min.y,
        bounds.max.x,
        bounds.max.y
    });
}

} // namespace sle
