#pragma once
#include <chrono>

class Timer
{
public:
    Timer();

    void tick(); // call once per frame

    float getDeltaTime() const;
    float getTime() const;

    float getFPS() const;

private:
    using clock = std::chrono::high_resolution_clock;

    clock::time_point lastFrame;
    clock::time_point startTime;

    float deltaTime = 0.0f;
    float totalTime = 0.0f;

    float fps = 0.0f;
    int frameCount = 0;
    float fpsTimer = 0.0f;
};