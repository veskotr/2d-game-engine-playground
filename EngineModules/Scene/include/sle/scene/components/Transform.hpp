#pragma once
#include <glm/glm.hpp>

namespace sle::components {

struct Transform
{
    glm::vec2 position{0.0f};
    float rotation = 0.0f;
    glm::vec2 scale{1.0f, 1.0f};
};

} // namespace sle::components
