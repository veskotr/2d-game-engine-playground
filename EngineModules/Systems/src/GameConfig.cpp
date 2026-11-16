#include <sle/engine/GameConfig.hpp>
#include <sle/core/Log.hpp>
#include <nlohmann/json.hpp>
#include <fstream>

namespace sle {
namespace {
using Json = nlohmann::json;
namespace fs = std::filesystem;
using Result = core::Result<GameConfig>;

std::string normalizePath(const fs::path& path)
{
    return path.lexically_normal().generic_string();
}

fs::path resolveByWalkingParents(const fs::path& relativePath)
{
    if (relativePath.is_absolute() && fs::exists(relativePath))
        return relativePath;

    if (fs::exists(relativePath))
        return fs::absolute(relativePath);

    fs::path current = fs::current_path();
    while (!current.empty())
    {
        const fs::path candidate = current / relativePath;
        if (fs::exists(candidate))
            return candidate;

        if (current == current.root_path())
            break;
        current = current.parent_path();
    }

    return {};
}

fs::path resolveFromBase(const std::string& rawPath, const fs::path& baseDirectory)
{
    const fs::path path(rawPath);
    if (path.is_absolute() && fs::exists(path))
        return path;

    const fs::path fromBase = baseDirectory / path;
    if (fs::exists(fromBase))
        return fromBase;

    return resolveByWalkingParents(path);
}

core::ScreenMode parseScreenMode(const Json& windowJson)
{
    const std::string mode = windowJson.value("mode", std::string("Windowed"));
    if (mode == "Windowed")
        return core::ScreenMode::Windowed;
    if (mode == "Fullscreen")
        return core::ScreenMode::Fullscreen;
    if (mode == "BorderlessFullscreen")
        return core::ScreenMode::BorderlessFullscreen;

    core::Log::warn(
        "engine.json: invalid window.mode '{}' (expected Windowed, Fullscreen, or BorderlessFullscreen), using default",
        mode);
    return core::ScreenMode::Windowed;
}

Result loadPackages(const Json& root, const fs::path& configDirectory, GameConfig config)
{
    // Scene data and the main scene belong to the packages, never the config.
    if (root.contains("startScene") || root.contains("scenes"))
        return Result::error("engine config with 'packages' must not contain 'startScene' or 'scenes'");
    const auto& packages = root["packages"];
    if (!packages.is_array() || packages.empty())
        return Result::error("engine config 'packages' must be a non-empty array");
    for (const auto& entry : packages)
    {
        if (!entry.is_string() || entry.get<std::string>().empty())
            return Result::error("Each engine config package must be a non-empty path");
        // Packages are delivered beside their config: no cwd or parent search.
        fs::path path = fs::u8path(entry.get<std::string>());
        if (!path.is_absolute())
            path = configDirectory / path;
        if (!fs::is_regular_file(path))
            return Result::error("Missing game package: " + normalizePath(path));
        config.packages.push_back(fs::absolute(path).lexically_normal());
    }
    return Result::success(std::move(config));
}
}

Result GameConfig::load(const fs::path& configPath)
{
    std::ifstream file(configPath);
    if (!file.is_open())
        return Result::error("Failed to open engine config: " + normalizePath(configPath));

    Json root;
    try
    {
        file >> root;
    }
    catch (const std::exception& ex)
    {
        return Result::error(
            "Malformed engine JSON in '" + normalizePath(configPath) + "': " + ex.what());
    }

    if (!root.is_object())
        return Result::error("engine.json root must be an object");

    GameConfig config;

    if (root.contains("window"))
    {
        if (!root["window"].is_object())
            return Result::error("engine.json 'window' must be an object");

        const auto& windowJson = root["window"];
        config.engine.title = windowJson.value("title", config.engine.title);
        config.engine.width = windowJson.value("width", config.engine.width);
        config.engine.height = windowJson.value("height", config.engine.height);
        config.engine.vsync = windowJson.value("vsync", config.engine.vsync);
        config.engine.screenMode = parseScreenMode(windowJson);
    }

    if (root.contains("targetFPS"))
    {
        const auto& fps = root["targetFPS"];
        if (!fps.is_number_integer() || fps.get<long long>() < 0 || fps.get<long long>() > 100000)
            return Result::error("engine config 'targetFPS' must be a nonnegative integer");
        config.engine.targetFPS = fps.get<int>();
    }

    const fs::path configDirectory = fs::absolute(configPath).parent_path();
    if (root.contains("packages"))
        return loadPackages(root, configDirectory, std::move(config));

    config.startScene = root.value("startScene", std::string());
    if (config.startScene.empty())
        return Result::error("engine.json must contain a non-empty 'startScene'");

    if (!root.contains("scenes") || !root["scenes"].is_array())
        return Result::error("engine.json must contain a 'scenes' array");

    for (const auto& sceneJson : root["scenes"])
    {
        if (!sceneJson.is_object())
            return Result::error("Each scene entry in engine.json must be an object");

        Scene scene;
        scene.name = sceneJson.value("name", std::string());
        const std::string rawFile = sceneJson.value("file", std::string());

        if (scene.name.empty() || rawFile.empty())
            return Result::error("Each scene entry in engine.json needs non-empty 'name' and 'file'");

        const fs::path resolvedFile = resolveFromBase(rawFile, configDirectory);
        if (resolvedFile.empty())
            return Result::error("Failed to resolve scene file '" + rawFile + "'");

        scene.file = normalizePath(resolvedFile);
        config.scenes.push_back(std::move(scene));
    }

    return Result::success(std::move(config));
}
}
