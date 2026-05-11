#pragma once
#include "core/EngineConfig.hpp"
#include "core/Timer.hpp"
#include "core/Window.hpp"
#include "input/Input.hpp"
#include "renderer/Renderer.hpp"

class Engine
{
public:
    Engine(const EngineConfig &config);
    ~Engine();

    Result<bool> init();
    void run();

private:
    EngineConfig config;

    Window window;
    Renderer renderer;
    Camera2D camera{static_cast<float>(config.width), static_cast<float>(config.height)};
    Timer timer;
};