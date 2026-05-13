#pragma once
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
};

} // namespace sle::core
