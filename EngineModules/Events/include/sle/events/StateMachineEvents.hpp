#pragma once

#include <sle/scene/Entity.hpp>

#include <string>

namespace sle::events {

struct StateMachineTransitionEvent
{
    sle::entity::Entity entity;
    std::string fromState;
    std::string toState;
    std::string onExitCallback;
    std::string onEnterCallback;
};

} // namespace sle::events
