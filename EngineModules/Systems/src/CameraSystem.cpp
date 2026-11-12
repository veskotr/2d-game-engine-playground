#include <sle/engine/CameraSystem.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/Camera2DComponent.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <limits>
#include <cmath>
namespace sle {
bool CameraSystem::update(entity::Registry& registry, core::Camera2D& camera) {
    auto selected = std::numeric_limits<unsigned int>::max();
    glm::vec2 center{}, size{};
    float rotation = 0;
    registry.view<components::Camera2DComponent,components::WorldTransformComponent>(
        [&](entity::Entity e, const auto& c, const auto& w) {
            const auto effective = c.viewSize / c.zoom;
            if (!c.enabled || !c.primary || e.getID() >= selected || !std::isfinite(c.zoom) || c.zoom <= 0 ||
                !std::isfinite(effective.x) || !std::isfinite(effective.y) || effective.x <= 0 || effective.y <= 0 ||
                !std::isfinite(w.position.x) || !std::isfinite(w.position.y) || !std::isfinite(w.rotation)) return;
            selected=e.getID(); center=w.position; size=effective; rotation=w.rotation;
        });
    if (selected==std::numeric_limits<unsigned int>::max()) return false;
    const float c=std::cos(rotation),s=std::sin(rotation);
    const auto half=size*0.5f;
    camera.setZoom(1.0f);
    camera.setViewport(size.x,size.y);
    camera.setRotation(rotation);
    camera.setPosition(center-glm::vec2(c*half.x-s*half.y,s*half.x+c*half.y));
    return true;
}
}
