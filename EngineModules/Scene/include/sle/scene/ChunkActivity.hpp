#pragma once

#include <sle/scene/Registry.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>

namespace sle::entity {

inline bool isChunkActive(const Registry& registry, Entity entity, components::ChunkSubsystem subsystem)
{
    const auto* chunk = registry.getComponent<components::ChunkActivationComponent>(entity);
    return !chunk || (chunk->suspendedSubsystems & static_cast<std::uint8_t>(subsystem)) == 0;
}

} // namespace sle::entity
