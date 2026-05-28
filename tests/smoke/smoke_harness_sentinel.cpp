#include "sle/test/FrameStepper.hpp"
#include "sle/test/LogCapture.hpp"
#include "sle/test/SceneBootstrap.hpp"

#include <cmath>
#include <iostream>
#include <string>
#include <vector>

namespace {

bool almostEqual(float lhs, float rhs, float epsilon = 0.0001f) {
    return std::fabs(lhs - rhs) <= epsilon;
}

} // namespace

int main() {
    using namespace sle::test;

    std::vector<float> elapsedSamples;
    FrameStepper::runFixedSteps(3, 0.5f, [&elapsedSamples](std::size_t, float, float elapsedSeconds) {
        elapsedSamples.push_back(elapsedSeconds);
    });

    if (elapsedSamples.size() != 3 || !almostEqual(elapsedSamples[0], 0.0f) || !almostEqual(elapsedSamples[1], 0.5f) ||
        !almostEqual(elapsedSamples[2], 1.0f)) {
        std::cerr << "FrameStepper deterministic progression check failed\n";
        return 1;
    }

    std::string captured;
    {
        ScopedStreamCapture capture(std::cout);

        SceneBootstrapConfig config;
        config.sceneName = "sentinel_scene";
        config.warmupFrames = 2;
        config.deltaSeconds = 1.0f / 60.0f;

        SceneBootstrap::runSceneLifecycle(
            config,
            []() { std::cout << "BOOT\n"; },
            [](std::size_t frameIndex, float) { std::cout << "UPDATE:" << frameIndex << "\n"; },
            []() { std::cout << "SHUTDOWN\n"; });

        captured = capture.str();
    }

    if (!containsInOrder(captured, {"BOOT", "UPDATE:0", "UPDATE:1", "SHUTDOWN"})) {
        std::cerr << "Log ordering capture check failed\n";
        return 1;
    }

    return 0;
}
