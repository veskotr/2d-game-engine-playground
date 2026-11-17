#pragma once
#include <sle/core/EngineConfig.hpp>
#include <sle/core/Result.hpp>
#include <filesystem>
#include <string>
#include <vector>

namespace sle {
/**
 * Standalone engine configuration (`engine.json` / a built game's `config.json`).
 * A config either lists scene files with a `startScene`, or lists `.slepkg`
 * game packages that carry their scenes and main scene.
 */
struct GameConfig
{
    struct Scene
    {
        std::string name;
        std::string file;
    };
    core::EngineConfig engine;
    std::string startScene;
    std::vector<Scene> scenes;
    // Absolute package paths, in mount order. Non-empty selects package mode.
    std::vector<std::filesystem::path> packages;

    static core::Result<GameConfig> load(const std::filesystem::path& path);
};
}
