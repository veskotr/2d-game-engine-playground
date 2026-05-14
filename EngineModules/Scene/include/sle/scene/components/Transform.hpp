#pragma once
#include <glm/glm.hpp>
#include <sle/scene/Entity.hpp>

namespace sle::components {

struct TransformComponent
{
    glm::vec2 position{0.0f};
    float rotation = 0.0f;
    glm::vec2 scale{1.0f, 1.0f};

    // Hierarchy: invalid (default) entity means no parent
    sle::entity::Entity parent{};
};

} // namespace sle::components
