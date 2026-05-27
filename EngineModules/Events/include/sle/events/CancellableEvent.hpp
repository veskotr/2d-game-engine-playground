#pragma once

namespace sle::events {

// Base trait for events that can be cancelled/vetoed by handlers.
// Handlers can set cancelled = true to prevent further handlers from executing.
// Check with std::is_base_of_v<CancellableEvent, T> in emit() to support optional cancellation.
struct CancellableEvent
{
    mutable bool cancelled = false;
};

} // namespace sle::events
