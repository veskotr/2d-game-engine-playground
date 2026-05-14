#pragma once
#include <glm/glm.hpp>

namespace sle::components {

// Read-only output written by TransformSystem each frame.
// Represents the final world-space transform for an entity after
// the full parent-child hierarchy has been resolved.
struct WorldTransformComponent
{
    glm::vec2 position{0.0f};
    float     rotation = 0.0f;
    glm::vec2 scale{1.0f, 1.0f};
};

} // namespace sle::components
