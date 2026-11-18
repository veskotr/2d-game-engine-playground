#pragma once

#include <glm/vec2.hpp>
#include <vector>

namespace sle::entity { class Scene; }
namespace sle::core { class Camera2D; }
namespace sle::renderer { class Renderer; }

namespace sle {
// Editor-only grid of the Scene/Entity preview. It never affects gameplay, the game camera
// or Game view drawing. The editor sets it from its move snap step.
struct EditorGrid
{
    // World units between minor lines; 0 keeps the automatic power-of-ten spacing.
    float spacing = 0.0f;
    // Every Nth line is a major line; 0 or 1 draws no separate major lines.
    int majorEvery = 10;
    bool operator==(const EditorGrid&) const = default;
    // Finite non-negative spacing and a major interval in [0, 1000].
    bool valid() const;
};

struct EditorGridLine
{
    glm::vec2 from;
    glm::vec2 to;
    bool major = false;
};

class EntityPreviewSystem
{
public:
    static void frameCamera(entity::Scene& scene, core::Camera2D& camera, float aspect);
    // Lines covering the camera view. A spacing too fine for the view is coarsened by the
    // major interval, so at most about a hundred lines are drawn per axis.
    static std::vector<EditorGridLine> gridLines(const core::Camera2D& camera, const EditorGrid& grid);
    static void submitOverlay(entity::Scene& scene, renderer::Renderer& renderer, const core::Camera2D& camera,
        const EditorGrid& grid = {});
};
}
