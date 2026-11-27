#pragma once
#include <sle/scene/Scene.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <algorithm>
#include <sle/scene/TransformMath.hpp>
#include <sle/scene/components/TilemapComponent.hpp>
#include <sle/core/Log.hpp>
#include <limits>

namespace sle::detail {
struct TileLayerView
{
    const entity::TileLayer* layer;
    glm::vec2 translation{};
    std::string identity;
    std::uint32_t drawOrder = 0;
    bool always = false;
    bool rendering = true;
    bool collision = true;
};

template<class Visitor> void visitTileLayers(entity::Scene& scene, Visitor&& visitor)
{
    for (const auto& layer : scene.getTileLayers())
        visitor(TileLayerView{&layer, {}, "legacy:" + layer.id, layer.drawOrder, false});
    auto& registry = scene.getRegistry();
    for (auto& placement : scene.getTilemapPlacements()) {
        const entity::Entity owner(placement.entityId);
        if (!registry.hasEntity(owner)) continue;
        const auto* map = registry.getComponent<components::TilemapComponent>(owner);
        if (!map || map->asset != placement.asset) continue;
        const auto world = entity::transformMath::currentWorld(scene, owner);
        if (!entity::transformMath::finite(world)) continue;
        if (!placement.warnedTransform && (world.rotation != 0 || world.scale != glm::vec2(1))) {
            core::Log::warn("Tilemap '{}': rotation and scale are ignored; only translation affects map geometry", placement.asset);
            placement.warnedTransform = true;
        }
        for (const auto& layer : placement.layers) {
            const auto order = static_cast<std::int64_t>(layer.drawOrder) + map->sortingOffset;
            const auto checked = std::clamp<std::int64_t>(order, 0, std::numeric_limits<std::uint32_t>::max());
            visitor(TileLayerView{&layer, world.position,
                "map:" + std::to_string(placement.entityId) + ":" + layer.id,
                static_cast<std::uint32_t>(checked), true,
                entity::isChunkActive(registry, owner, components::ChunkSubsystem::Rendering),
                entity::isChunkActive(registry, owner, components::ChunkSubsystem::Collision) &&
                    entity::isChunkActive(registry, owner, components::ChunkSubsystem::Physics)});
        }
    }
}
}
