#pragma once

#include <cstddef>
#include <functional>

namespace sle::test {

class FrameStepper {
public:
    using StepCallback = std::function<void(std::size_t frameIndex, float deltaSeconds, float elapsedSeconds)>;

    static void runFixedSteps(std::size_t frameCount, float deltaSeconds, const StepCallback& callback);
};

} // namespace sle::test
