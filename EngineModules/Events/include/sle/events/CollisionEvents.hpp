#pragma once
#include <sle/scene/Entity.hpp>

namespace sle::events {

// Dispatched when two solid bodies begin colliding
struct CollisionBeginEvent
{
    sle::entity::Entity entityA;
    sle::entity::Entity entityB;
};

// Dispatched when two solid bodies stop colliding (separate)
struct CollisionEndEvent
{
    sle::entity::Entity entityA;
    sle::entity::Entity entityB;
};

} // namespace sle::events
