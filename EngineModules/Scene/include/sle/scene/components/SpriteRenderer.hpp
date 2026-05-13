#pragma once
#include <glm/glm.hpp>
#include <cstdint>

namespace sle::components {

struct SpriteRenderer
{
    glm::vec4 color{1, 1, 1, 1};
    glm::vec2 size{1, 1};
    uint32_t textureId = 0; // raw GL texture ID; 0 = use default white
};

} // namespace sle::components
