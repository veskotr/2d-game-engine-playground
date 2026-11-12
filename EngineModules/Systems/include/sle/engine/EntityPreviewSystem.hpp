#pragma once

namespace sle::entity { class Scene; }
namespace sle::core { class Camera2D; }
namespace sle::renderer { class Renderer; }

namespace sle {
class EntityPreviewSystem
{
public:
    static void frameCamera(entity::Scene& scene, core::Camera2D& camera, float aspect);
    static void submitOverlay(entity::Scene& scene, renderer::Renderer& renderer, const core::Camera2D& camera);
};
}
