#pragma once
#include <string>

namespace sle::events {

// Dispatched when a scene is loaded (queued to global event bus)
struct SceneLoadedEvent
{
    std::string sceneName;
};

// Dispatched when a scene is unloaded (queued to global event bus)
struct SceneUnloadedEvent
{
    std::string sceneName;
};

} // namespace sle::events
