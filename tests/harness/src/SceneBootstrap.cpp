#include "sle/test/SceneBootstrap.hpp"

namespace sle::test {

void SceneBootstrap::runSceneLifecycle(
    const SceneBootstrapConfig& config,
    const InitCallback& onInit,
    const UpdateCallback& onUpdate,
    const ShutdownCallback& onShutdown) {
    if (onInit) {
        onInit();
    }

    for (std::size_t frameIndex = 0; frameIndex < config.warmupFrames; ++frameIndex) {
        if (onUpdate) {
            onUpdate(frameIndex, config.deltaSeconds);
        }
    }

    if (onShutdown) {
        onShutdown();
    }
}

} // namespace sle::test
