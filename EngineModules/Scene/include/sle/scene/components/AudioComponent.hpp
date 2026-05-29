#pragma once

#include <cstdint>
#include <string>

namespace sle::components {

// Playback state driven by AudioSystem. Do not set runtimeHandle manually.
enum class AudioPlaybackState : uint8_t
{
    Stopped = 0,
    Playing,
    Paused,
};

struct AudioComponent
{
    // Asset path to an audio file (wav, mp3, flac, ogg, etc.)
    std::string assetPath;

    // If true the sound loops until explicitly stopped.
    bool loop = false;

    // Volume in [0.0, 1.0].
    float volume = 1.0f;

    // Playback pitch multiplier.
    float pitch = 1.0f;

    // Whether the AudioSystem should start playback on the next update.
    bool playRequested = false;

    // Whether the AudioSystem should stop playback on the next update.
    bool stopRequested = false;

    // Current playback state (written by AudioSystem, read-only for scripts).
    AudioPlaybackState state = AudioPlaybackState::Stopped;

    // Internal handle index used by AudioSystem (not for external use).
    uint32_t runtimeHandle = 0;
};

} // namespace sle::components
