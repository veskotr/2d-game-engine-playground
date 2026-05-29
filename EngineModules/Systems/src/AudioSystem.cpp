#define MINIAUDIO_IMPLEMENTATION
#include <miniaudio.h>

// Include stb_vorbis declarations only (implementation compiled separately as C by CMake).
#define STB_VORBIS_HEADER_ONLY
#include <stb_vorbis.c>

#include <sle/engine/AudioSystem.hpp>
#include <sle/scene/components/AudioComponent.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/core/Log.hpp>

#include <cstdlib>
#include <cstring>
#include <new>

namespace sle {

using namespace sle::core;

AudioSystem::AudioSystem() = default;

AudioSystem::~AudioSystem()
{
    shutdown();
}

bool AudioSystem::init()
{
    engine_ = static_cast<ma_engine*>(std::malloc(sizeof(ma_engine)));
    if (!engine_)
    {
        Log::warn("AudioSystem: failed to allocate ma_engine");
        return false;
    }

    ma_engine_config cfg = ma_engine_config_init();
    const ma_result result = ma_engine_init(&cfg, engine_);
    if (result != MA_SUCCESS)
    {
        Log::warn("AudioSystem: ma_engine_init failed ({}). Audio disabled.", static_cast<int>(result));
        std::free(engine_);
        engine_ = nullptr;
        return false;
    }

    initialized_ = true;
    Log::info("AudioSystem: initialized");
    return true;
}

void AudioSystem::shutdown()
{
    if (!initialized_)
        return;

    for (auto& [id, handle] : sounds_)
        releaseHandle(handle);
    sounds_.clear();

    if (engine_)
    {
        ma_engine_uninit(engine_);
        std::free(engine_);
        engine_ = nullptr;
    }

    initialized_ = false;
}

void AudioSystem::releaseHandle(SoundHandle& handle)
{
    if (handle.sound)
    {
        auto* sound = static_cast<ma_sound*>(handle.sound);
        ma_sound_uninit(sound);
        delete sound;
        handle.sound = nullptr;
    }

    if (handle.audioBuffer)
    {
        auto* buf = static_cast<ma_audio_buffer*>(handle.audioBuffer);
        ma_audio_buffer_uninit(buf);
        delete buf;
        handle.audioBuffer = nullptr;
    }

    if (handle.pcmData)
    {
        free(handle.pcmData);
        handle.pcmData = nullptr;
    }
}

void AudioSystem::update(sle::entity::Scene& scene)
{
    if (!initialized_)
        return;

    auto& registry = scene.getRegistry();

    registry.view<sle::components::AudioComponent>(
        [this](sle::entity::Entity, sle::components::AudioComponent& audio)
        {
            // --- Stop request ---
            if (audio.stopRequested)
            {
                audio.stopRequested = false;
                if (audio.runtimeHandle != 0)
                {
                    auto it = sounds_.find(audio.runtimeHandle);
                    if (it != sounds_.end())
                    {
                        releaseHandle(it->second);
                        sounds_.erase(it);
                    }
                    audio.runtimeHandle = 0;
                }
                audio.state = sle::components::AudioPlaybackState::Stopped;
                return;
            }

            // --- Play request ---
            if (audio.playRequested)
            {
                audio.playRequested = false;

                if (audio.assetPath.empty())
                    return;

                // Stop any currently playing instance first.
                if (audio.runtimeHandle != 0)
                {
                    auto it = sounds_.find(audio.runtimeHandle);
                    if (it != sounds_.end())
                    {
                        releaseHandle(it->second);
                        sounds_.erase(it);
                    }
                    audio.runtimeHandle = 0;
                }

                auto* sound = new (std::nothrow) ma_sound();
                if (!sound)
                    return;

                SoundHandle handle{sound, nullptr, nullptr};

                // OGG/Vorbis: decode to raw PCM with stb_vorbis, then feed as audio buffer.
                const bool isOgg = audio.assetPath.size() >= 4 &&
                    audio.assetPath.compare(audio.assetPath.size() - 4, 4, ".ogg") == 0;

                ma_result result = MA_SUCCESS;
                if (isOgg)
                {
                    int channels = 0, sampleRate = 0, samples = 0;
                    short* pcm = nullptr;
                    samples = stb_vorbis_decode_filename(audio.assetPath.c_str(), &channels, &sampleRate, &pcm);
                    if (samples < 0 || !pcm)
                    {
                        Log::warn("AudioSystem: failed to decode OGG '{}'", audio.assetPath);
                        delete sound;
                        audio.state = sle::components::AudioPlaybackState::Stopped;
                        return;
                    }

                    const int frameCount = channels > 0 ? samples / channels : 0;

                    auto* buf = new (std::nothrow) ma_audio_buffer();
                    if (!buf)
                    {
                        free(pcm);
                        delete sound;
                        audio.state = sle::components::AudioPlaybackState::Stopped;
                        return;
                    }

                    ma_audio_buffer_config bufCfg = ma_audio_buffer_config_init(
                        ma_format_s16,
                        static_cast<ma_uint32>(channels),
                        static_cast<ma_uint64>(frameCount),
                        pcm,
                        nullptr);
                    result = ma_audio_buffer_init(&bufCfg, buf);
                    if (result != MA_SUCCESS)
                    {
                        free(pcm);
                        delete buf;
                        delete sound;
                        audio.state = sle::components::AudioPlaybackState::Stopped;
                        return;
                    }

                    ma_audio_buffer_seek_to_pcm_frame(buf, 0);
                    result = ma_sound_init_from_data_source(engine_, buf, 0u, nullptr, sound);
                    handle.audioBuffer = buf;
                    handle.pcmData = pcm;
                }
                else
                {
                    const uint32_t flags = audio.loop ? MA_SOUND_FLAG_STREAM : 0u;
                    result = ma_sound_init_from_file(
                        engine_,
                        audio.assetPath.c_str(),
                        flags,
                        nullptr,
                        nullptr,
                        sound);
                }

                if (result != MA_SUCCESS)
                {
                    Log::warn("AudioSystem: failed to load '{}' ({})", audio.assetPath, static_cast<int>(result));
                    releaseHandle(handle); // cleans up pcmData/audioBuffer if set
                    audio.state = sle::components::AudioPlaybackState::Stopped;
                    return;
                }

                ma_sound_set_volume(sound, audio.volume);
                ma_sound_set_pitch(sound, audio.pitch);
                ma_sound_set_looping(sound, audio.loop ? MA_TRUE : MA_FALSE);
                ma_sound_start(sound);

                const uint32_t handleId = nextHandleId_++;
                sounds_[handleId] = handle;
                audio.runtimeHandle = handleId;
                audio.state = sle::components::AudioPlaybackState::Playing;
                return;
            }

            // --- Passive state sync: mark finished sounds as stopped ---
            if (audio.runtimeHandle != 0 &&
                audio.state == sle::components::AudioPlaybackState::Playing)
            {
                auto it = sounds_.find(audio.runtimeHandle);
                if (it != sounds_.end())
                {
                    auto* sound = static_cast<ma_sound*>(it->second.sound);
                    if (sound && ma_sound_at_end(sound) && !audio.loop)
                    {
                        releaseHandle(it->second);
                        sounds_.erase(it);
                        audio.runtimeHandle = 0;
                        audio.state = sle::components::AudioPlaybackState::Stopped;
                    }
                }
            }
        });
}

} // namespace sle
