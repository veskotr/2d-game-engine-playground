#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>

// Forward-declare miniaudio engine to avoid leaking its large header into all consumers.
struct ma_engine;

namespace sle::entity { class Scene; }

namespace sle {

class AudioSystem
{
public:
    AudioSystem();
    ~AudioSystem();

    // Call once at engine startup. Returns false on backend init failure (headless CI safe).
    bool init();
    void shutdown();

    // Per-frame update: processes play/stop requests on AudioComponent entities.
    void update(sle::entity::Scene& scene);

    bool isInitialized() const { return initialized_; }

private:
    struct SoundHandle
    {
        void* sound       = nullptr; // ma_sound*
        void* audioBuffer = nullptr; // ma_audio_buffer* — only for OGG-decoded sounds
        short* pcmData    = nullptr; // raw PCM owned by this handle (freed on release)
    };

    void releaseHandle(SoundHandle& handle);

    ma_engine* engine_ = nullptr;
    bool initialized_ = false;

    // Map from runtimeHandle id -> SoundHandle
    std::unordered_map<uint32_t, SoundHandle> sounds_;
    uint32_t nextHandleId_ = 1;
};

} // namespace sle
