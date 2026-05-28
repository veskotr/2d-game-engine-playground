#include "sle/test/FrameStepper.hpp"

namespace sle::test {

void FrameStepper::runFixedSteps(std::size_t frameCount, float deltaSeconds, const StepCallback& callback) {
    float elapsedSeconds = 0.0f;

    for (std::size_t frameIndex = 0; frameIndex < frameCount; ++frameIndex) {
        if (callback) {
            callback(frameIndex, deltaSeconds, elapsedSeconds);
        }

        elapsedSeconds += deltaSeconds;
    }
}

} // namespace sle::test
