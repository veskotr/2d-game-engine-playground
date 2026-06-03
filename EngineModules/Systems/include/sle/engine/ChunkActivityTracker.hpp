#pragma once

#include <sle/engine/ChunkManager.hpp>

#include <glm/vec2.hpp>

#include <string>
#include <vector>

namespace sle::events {
class EventBus;
}

namespace sle {

struct ChunkActivityUpdate
{
    std::vector<ChunkCoord> active;
    std::vector<ChunkCoord> activated;
    std::vector<ChunkCoord> deactivated;
};

class ChunkActivityTracker
{
public:
    explicit ChunkActivityTracker(ChunkManager manager, std::string mapId = {});

    const ChunkManager& getManager() const { return manager; }
    const std::string& getMapId() const { return mapId; }
    const std::vector<ChunkCoord>& getActiveChunks() const { return activeChunks; }

    ChunkActivityUpdate updateFocusPosition(const glm::vec2& focusWorldPosition, sle::events::EventBus* eventBus = nullptr);

private:
    void emitActivated(const ChunkCoord& coord, sle::events::EventBus& eventBus) const;
    void emitDeactivated(const ChunkCoord& coord, sle::events::EventBus& eventBus) const;

    ChunkManager manager;
    std::string mapId;
    std::vector<ChunkCoord> activeChunks;
};

} // namespace sle
