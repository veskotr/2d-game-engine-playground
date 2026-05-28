#pragma once

#include <cstddef>
#include <functional>
#include <string>

namespace sle::test {

struct SceneBootstrapConfig {
    std::string sceneName = "test_scene";
    std::size_t warmupFrames = 0;
    float deltaSeconds = 1.0f / 60.0f;
};

class SceneBootstrap {
public:
    using InitCallback = std::function<void()>;
    using UpdateCallback = std::function<void(std::size_t frameIndex, float dt)>;
    using ShutdownCallback = std::function<void()>;

    static void runSceneLifecycle(
        const SceneBootstrapConfig& config,
        const InitCallback& onInit,
        const UpdateCallback& onUpdate,
        const ShutdownCallback& onShutdown);
};

} // namespace sle::test
