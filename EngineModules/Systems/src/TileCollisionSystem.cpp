#include <sle/engine/TileCollisionSystem.hpp>

#include "CameraViewBounds.hpp"

#include <sle/physics/PhysicsWorld.hpp>

#include <set>

namespace sle {
namespace {

using Polygon = std::vector<glm::vec2>;

std::vector<Polygon> chunkPolygons(const entity::TileLayer& layer, const entity::TileChunk& chunk)
{
    std::vector<Polygon> polygons;
    // Use 64-bit grid coordinates so a run can end one past INT32_MAX.
    using Coordinate = std::pair<std::int64_t, std::int64_t>;
    std::set<Coordinate> fullCells;
    for (const auto& cell : chunk.cells) {
        if (cell.missing) continue;
        const auto tile = layer.collisionByTile.find(cell.tileId);
        if (tile == layer.collisionByTile.end()) continue;
        if (tile->second.fullCell) {
            fullCells.emplace(cell.coordinate.first, cell.coordinate.second);
            continue;
        }
        const auto offset = cell.lowerLeft - chunk.lowerLeft;
        for (const auto& piece : tile->second.pieces) {
            auto& polygon = polygons.emplace_back();
            polygon.reserve(piece.size());
            for (const auto vertex : piece) polygon.push_back(offset + vertex);
        }
    }
    while (!fullCells.empty()) {
        const auto [x, y] = *fullCells.begin();
        auto right = x + 1;
        while (fullCells.contains({right, y})) ++right;
        auto top = y + 1;
        for (;; ++top) {
            auto column = x;
            while (column < right && fullCells.contains({column, top})) ++column;
            if (column < right) break;
        }
        for (auto row = y; row < top; ++row)
            for (auto column = x; column < right; ++column)
                fullCells.erase({column, row});
        const auto minimum = (layer.origin + glm::vec2(x, y) * layer.cellSize) - chunk.lowerLeft;
        const auto maximum = (layer.origin + glm::vec2(right, top) * layer.cellSize) - chunk.lowerLeft;
        polygons.push_back({minimum, {maximum.x, minimum.y}, maximum, {minimum.x, maximum.y}});
    }
    return polygons;
}

} // namespace

TileCollisionSystem::~TileCollisionSystem()
{
    clear();
}

void TileCollisionSystem::clear()
{
    for (const auto& [key, group] : groups_) world_.destroyBody(group.body);
    groups_.clear();
}

void TileCollisionSystem::update(std::span<const entity::TileLayer> layers, const core::Camera2D& camera)
{
    const auto view = viewBounds(camera);
    std::set<Key> active;
    for (const auto& layer : layers) {
        if (!layer.collisionEnabled || layer.collisionByTile.empty()) continue;
        const auto margin = static_cast<float>(entity::TileChunkSize) * layer.cellSize;
        for (const auto& [coordinate, chunk] : layer.chunks) {
            if (!view.intersects(chunk.lowerLeft - margin, chunk.upperRight + margin)) continue;

            auto polygons = chunkPolygons(layer, chunk);
            if (polygons.empty()) continue;
            auto key = Key{layer.id, coordinate};
            active.insert(key);
            auto& group = groups_[key];
            if (group.origin == chunk.lowerLeft && group.polygons == polygons) continue;

            world_.destroyBody(group.body);
            group.origin = chunk.lowerLeft;
            group.polygons = std::move(polygons);
            components::RigidBodyComponent settings;
            settings.bodyType = components::BodyType::Static;
            group.body = world_.createBody(group.origin, settings);
            bool hasFixtures = false;
            for (const auto& polygon : group.polygons)
                hasFixtures |= world_.createTerrainFixture(group.body, polygon);
            if (!hasFixtures) {
                world_.destroyBody(group.body);
                group.body = 0;
            }
        }
    }
    std::erase_if(groups_, [&](const auto& entry) {
        if (active.contains(entry.first)) return false;
        world_.destroyBody(entry.second.body);
        return true;
    });
}

} // namespace sle
