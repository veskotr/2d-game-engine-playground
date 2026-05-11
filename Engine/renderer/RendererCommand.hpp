#pragma once
#include <glm/glm.hpp>
#include <string>
#include <variant>

struct QuadCommand
{
    glm::vec2 position;
    glm::vec2 size;
    glm::vec4 color;
    uint32_t texture = 0;
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