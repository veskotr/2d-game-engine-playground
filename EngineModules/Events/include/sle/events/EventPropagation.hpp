#pragma once

namespace sle::events {

// Controls where an event is dispatched:
// - LOCAL_ONLY: Only dispatch to the local/entity event bus (future extension)
// - TO_SCENE: Dispatch to the per-scene event bus (default)
// - TO_GLOBAL: Dispatch to the engine-wide global event bus
enum class EventPropagation
{
    LOCAL_ONLY = 0,
    TO_SCENE = 1,
    TO_GLOBAL = 2
};

} // namespace sle::events
