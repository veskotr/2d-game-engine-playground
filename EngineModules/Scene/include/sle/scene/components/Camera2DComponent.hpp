#pragma once
#include <glm/vec2.hpp>
namespace sle::components {
struct Camera2DComponent {
    bool enabled = true;
    bool primary = true;
    glm::vec2 viewSize{480.0f, 270.0f};
    float zoom = 1.0f;
};
}
