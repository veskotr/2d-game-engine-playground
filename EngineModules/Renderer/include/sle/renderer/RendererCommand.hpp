#pragma once
#include <glm/glm.hpp>
#include <string>
#include <variant>

namespace sle::renderer {

struct QuadCommand
{
    glm::mat4 modelMatrix;
    glm::vec4 color = glm::vec4(1.0f);

    uint32_t texture_id = 0;
    uint32_t shader_id  = 0;

    uint32_t layer = 0;
    float z = 0.0f;
};

struct LineCommand
{
    glm::vec2 a;
    glm::vec2 b;
    glm::vec4 color;
    float thickness = 1.0f;
    float z = 0.0f;
};

struct TextCommand
{
    std::string text;
    glm::vec2 position;
    glm::vec4 color;
    float scale = 1.0f;
    float z = 0.0f;
};

using RenderCommand = std::variant<
    QuadCommand,
    LineCommand,
    TextCommand
>;

} // namespace sle::renderer
