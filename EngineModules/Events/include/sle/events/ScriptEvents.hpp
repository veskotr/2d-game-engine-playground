#pragma once

#include <sle/scene/Entity.hpp>

#include <string>

namespace sle::events {

// Lightweight custom script event routed through the engine EventBus.
// name: logical channel, e.g. "zone.npc_outer.distance"
// sourceEntity: optional emitter entity (0 means none)
// payload: script-defined string payload
struct ScriptEvent
{
    std::string name;
    sle::entity::Entity sourceEntity;
    std::string payload;
};

} // namespace sle::events
