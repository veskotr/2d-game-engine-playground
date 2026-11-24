#pragma once

#include <sle/scene/TileLayer.hpp>

#include <cstdint>
#include <map>
#include <span>
#include <string>

namespace sle {
namespace core { class Camera2D; }
namespace physics { class PhysicsWorld; }

class TileCollisionSystem
{
public:
    explicit TileCollisionSystem(physics::PhysicsWorld& world) : world_(world) {}
    ~TileCollisionSystem();
    TileCollisionSystem(const TileCollisionSystem&) = delete;
    TileCollisionSystem& operator=(const TileCollisionSystem&) = delete;

    void update(std::span<const entity::TileLayer> layers, const core::Camera2D& camera);
    void clear();

private:
    using Key = std::pair<std::string, entity::TileCoordinate>;
    struct Group
    {
        std::uint32_t body{};
        glm::vec2 origin{};
        std::vector<std::vector<glm::vec2>> polygons;
    };
    physics::PhysicsWorld& world_;
    std::map<Key, Group> groups_;
};
}
