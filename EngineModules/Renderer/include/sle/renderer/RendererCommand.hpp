#pragma once
#include <glm/glm.hpp>
#include <string>
#include <variant>

namespace sle::renderer {

struct QuadCommand
{
    glm::mat4 modelMatrix;
    glm::vec4 color = glm::vec4(1.0f);

    // Normalised UV rectangle (u0, v0, u1, v1). Defaults to full texture.
    glm::vec4 uvRect{0.0f, 0.0f, 1.0f, 1.0f};

    uint32_t texture_id = 0;
    uint32_t shader_id  = 0;

    uint32_t layer = 0;
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

struct BatchKey
{
    uint32_t layer;
    uint32_t shader_id;
    uint32_t texture_id;

    bool operator==(const BatchKey& other) const
    {
        return layer == other.layer &&
               shader_id == other.shader_id &&
               texture_id == other.texture_id;
    }
};
struct BatchKeyHash
{
    std::size_t operator()(const BatchKey& k) const
    {
        return (std::hash<uint32_t>()(k.layer) ^
               (std::hash<uint32_t>()(k.shader_id) << 1)) ^
               (std::hash<uint32_t>()(k.texture_id) << 2);
    }
};

using RenderCommand = std::variant<
    QuadCommand,
    LineCommand,
    TextCommand
>;

} // namespace sle::renderer
