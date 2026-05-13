#include <sle/core/Timer.hpp>

namespace sle::core {

Timer::Timer()
{
    startTime = clock::now();
    lastFrame = startTime;
}

void Timer::tick()
{
    auto now = clock::now();

    std::chrono::duration<float> dt = now - lastFrame;
    deltaTime = dt.count();

    lastFrame = now;

    std::chrono::duration<float> total = now - startTime;
    totalTime = total.count();

    // FPS calculation
    fpsTimer += deltaTime;
    frameCount++;

    if (fpsTimer >= 1.0f)
    {
        fps = frameCount / fpsTimer;
        frameCount = 0;
        fpsTimer = 0.0f;
    }
}

float Timer::getDeltaTime() const
{
    return deltaTime;
}

float Timer::getTime() const
{
    return totalTime;
}

float Timer::getFPS() const
{
    return fps;
}

} // namespace sle::core
