#pragma once
#include <sle/scene/Entity.hpp>
#include <string>

namespace sle::events {

// Dispatched when an entity enters a zone (trigger area)
struct ZoneEnterEvent
{
    sle::entity::Entity zoneEntity;  // Entity that owns the zone
    std::string zoneId;              // Zone identifier (from BoxZoneComponent or CircleZoneComponent)
    sle::entity::Entity otherEntity; // Entity entering the zone
};

// Dispatched when an entity exits a zone (trigger area)
struct ZoneExitEvent
{
    sle::entity::Entity zoneEntity;  // Entity that owns the zone
    std::string zoneId;              // Zone identifier
    sle::entity::Entity otherEntity; // Entity exiting the zone
};

} // namespace sle::events
