#pragma once
#include <glm/glm.hpp>
#include <cstdint>
#include "sle/renderer/Texture.hpp"
#include "sle/renderer/Shader.hpp"

namespace sle::components {

struct SpriteRenderer
{
    glm::vec4 color{1, 1, 1, 1};
    glm::vec2 size{1, 1};
    std::shared_ptr<renderer::Texture> texture;
    std::shared_ptr<renderer::Shader> shader;
};

} // namespace sle::components
