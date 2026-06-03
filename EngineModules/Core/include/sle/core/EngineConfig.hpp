#pragma once
#include <sle/core/ChunkActivationConfig.hpp>
#include <glm/vec4.hpp>
#include <string>

namespace sle::core {

enum class ScreenMode
{
    Windowed,
    Fullscreen,
    BorderlessFullscreen
};

struct EngineConfig
{
    int width = 1280;
    int height = 720;

    bool vsync = true;
    std::string title = "My Engine";

    ScreenMode screenMode = ScreenMode::Windowed;

    int targetFPS = 0;
    glm::vec4 backgroundColor{0.75f, 0.75f, 0.75f, 1.0f};
    ChunkActivationConfig chunkActivation{};
};

} // namespace sle::core
