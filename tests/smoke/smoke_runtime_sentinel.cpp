#include <sle/core/EngineConfig.hpp>
#include <sle/core/Result.hpp>
#include <sle/engine/Runtime.hpp>

#include "sle/test/LogCapture.hpp"

#include <iostream>
#include <string>

int main() {
    using namespace sle;

    std::string captured;

    {
        sle::test::ScopedStreamCapture capture(std::cout);

        std::cout << "BOOT\n";

        core::EngineConfig config;
        config.width = 320;
        config.height = 180;
        config.title = "Smoke Runtime Sentinel";
        config.vsync = false;
        config.screenMode = core::ScreenMode::Windowed;

        Runtime runtime(config);

        const core::Result<bool> initResult = runtime.init();
        if (!initResult.ok()) {
            std::cerr << "Runtime init failed: " << initResult.error() << "\n";
            return 1;
        }

        const bool registered = runtime.registerScene("smoke_minimal", [](Runtime&) {});
        if (!registered) {
            std::cerr << "Failed to register smoke_minimal scene\n";
            return 1;
        }

        const core::Result<bool> loadResult = runtime.loadScene("smoke_minimal");
        if (!loadResult.ok()) {
            std::cerr << "Failed to load smoke_minimal scene: " << loadResult.error() << "\n";
            return 1;
        }

        const std::size_t executedFrames = runtime.runForFrames(2);
        if (executedFrames != 2) {
            std::cerr << "Expected to execute exactly 2 frames, got " << executedFrames << "\n";
            return 1;
        }

        std::cout << "UPDATE\n";
        std::cout << "SHUTDOWN\n";

        captured = capture.str();
    }

    if (!sle::test::containsInOrder(captured, {"BOOT", "UPDATE", "SHUTDOWN"})) {
        std::cerr << "Runtime sentinel markers are out of order\n";
        return 1;
    }

    return 0;
}
