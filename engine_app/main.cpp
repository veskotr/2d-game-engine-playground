#include <sle/core/EngineConfig.hpp>
#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/ProjectRuntime.hpp>
#include <optional>
#include <charconv>
#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifdef APIENTRY
#undef APIENTRY
#endif
#include <windows.h>
#endif

#include <nlohmann/json.hpp>

#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

namespace {

using Json = nlohmann::json;
namespace fs = std::filesystem;

struct SceneConfig
{
    std::string name;
    std::string file;
};

struct AppConfig
{
    sle::core::EngineConfig engine;
    std::string startScene;
    std::vector<SceneConfig> scenes;
};

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

fs::path resolveConfigPath()
{
    return resolveByWalkingParents("engine.json");
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

sle::core::ScreenMode parseScreenMode(const Json& windowJson)
{
    const std::string mode = windowJson.value("mode", std::string("Windowed"));
    if (mode == "Windowed")
        return sle::core::ScreenMode::Windowed;
    if (mode == "Fullscreen")
        return sle::core::ScreenMode::Fullscreen;
    if (mode == "BorderlessFullscreen")
        return sle::core::ScreenMode::BorderlessFullscreen;

    sle::core::Log::warn(
        "engine.json: invalid window.mode '{}' (expected Windowed, Fullscreen, or BorderlessFullscreen), using default",
        mode);
    return sle::core::ScreenMode::Windowed;
}

sle::core::Result<AppConfig> loadAppConfig(const fs::path& configPath)
{
    std::ifstream file(configPath);
    if (!file.is_open())
        return sle::core::Result<AppConfig>::error("Failed to open engine config: " + normalizePath(configPath));

    Json root;
    try
    {
        file >> root;
    }
    catch (const std::exception& ex)
    {
        return sle::core::Result<AppConfig>::error(
            "Malformed engine JSON in '" + normalizePath(configPath) + "': " + ex.what());
    }

    if (!root.is_object())
        return sle::core::Result<AppConfig>::error("engine.json root must be an object");

    AppConfig config;

    if (root.contains("window"))
    {
        if (!root["window"].is_object())
            return sle::core::Result<AppConfig>::error("engine.json 'window' must be an object");

        const auto& windowJson = root["window"];
        config.engine.title = windowJson.value("title", config.engine.title);
        config.engine.width = windowJson.value("width", config.engine.width);
        config.engine.height = windowJson.value("height", config.engine.height);
        config.engine.vsync = windowJson.value("vsync", config.engine.vsync);
        config.engine.screenMode = parseScreenMode(windowJson);
    }

    config.startScene = root.value("startScene", std::string());
    if (config.startScene.empty())
        return sle::core::Result<AppConfig>::error("engine.json must contain a non-empty 'startScene'");

    if (!root.contains("scenes") || !root["scenes"].is_array())
        return sle::core::Result<AppConfig>::error("engine.json must contain a 'scenes' array");

    const fs::path configDirectory = configPath.parent_path();
    for (const auto& sceneJson : root["scenes"])
    {
        if (!sceneJson.is_object())
            return sle::core::Result<AppConfig>::error("Each scene entry in engine.json must be an object");

        SceneConfig scene;
        scene.name = sceneJson.value("name", std::string());
        const std::string rawFile = sceneJson.value("file", std::string());

        if (scene.name.empty() || rawFile.empty())
            return sle::core::Result<AppConfig>::error("Each scene entry in engine.json needs non-empty 'name' and 'file'");

        const fs::path resolvedFile = resolveFromBase(rawFile, configDirectory);
        if (resolvedFile.empty())
            return sle::core::Result<AppConfig>::error("Failed to resolve scene file '" + rawFile + "'");

        scene.file = normalizePath(resolvedFile);
        config.scenes.push_back(std::move(scene));
    }

    return sle::core::Result<AppConfig>::success(std::move(config));
}

} // namespace

int main(int argc, char** argv)
{
    std::string option, path;
    std::optional<std::size_t> frames;
    for (int index=1;index<argc;++index) {
        const std::string argument=argv[index];
        if ((argument=="--config" || argument=="--project") && option.empty() && index+1<argc) {
            option=argument;path=argv[++index];
        } else if (argument=="--frames" && !frames && index+1<argc) {
            const std::string value=argv[++index];std::size_t count=0;
            const auto parsed=std::from_chars(value.data(),value.data()+value.size(),count);
            if (parsed.ec!=std::errc{} || parsed.ptr!=value.data()+value.size() || count==0) {
                sle::core::Log::error("--frames requires a positive integer");return 1;
            }
            frames=count;
        } else { sle::core::Log::error("Usage: engine_app [--config engine.json | --project game.sleproject] [--frames N]");return 1; }
    }
    fs::path executable=fs::absolute(argv[0]);
#ifdef _WIN32
    std::wstring executableBuffer(32768,L'\0');
    const auto length=GetModuleFileNameW(nullptr,executableBuffer.data(),static_cast<DWORD>(executableBuffer.size()));
    if (length>0 && length<executableBuffer.size()) { executableBuffer.resize(length);executable=fs::path(executableBuffer); }
#endif
    // A delivery folder owns its default project; explicit options retain priority.
    const auto deliveredProject=executable.parent_path()/"game"/"game.sleproject";
    if (option.empty() && fs::exists(deliveredProject)) {
        option="--project";path=deliveredProject.string();
    }
    std::optional<sle::ProjectRuntime> project;
    AppConfig appConfig;
    if (option=="--project") {
        auto loaded=sle::ProjectRuntime::open(fs::absolute(fs::path(path)));
        if (!loaded.ok()) { sle::core::Log::error("{}",loaded.error());return 1; }
        project=std::move(loaded.value());appConfig.engine=project->engineConfig();
    } else {
        const fs::path configPath=option=="--config"?resolveByWalkingParents(path):resolveConfigPath();
        if (configPath.empty()) { sle::core::Log::error("Failed to locate engine configuration");return 1; }
        try {
            auto loaded=loadAppConfig(configPath);
            if (!loaded.ok()) { sle::core::Log::error("{}",loaded.error());return 1; }
            appConfig=std::move(loaded.value());
        } catch (const std::exception& error) { sle::core::Log::error("Invalid engine config: {}",error.what());return 1; }
    }
    // CMake places engine-owned resources beside the executable. Game assets are
    // always resolved through the project and never depend on this directory.
    const auto engineAssets=executable.parent_path()/"assets";
    if (fs::is_directory(engineAssets/"shaders")) appConfig.engine.engineAssetRoot=engineAssets.generic_string();
    sle::Runtime runtime(appConfig.engine);

    auto initResult = runtime.init();
    if (!initResult.ok())
    {
        sle::core::Log::error("Failed to initialize engine: {}", initResult.error());
        return 1;
    }

    if (project) {
        const auto started=runtime.startProject(*project);
        if (!started.ok()) { sle::core::Log::error("Project startup failed: {}",started.error());return 1; }
    } else {
    for (const SceneConfig& scene : appConfig.scenes)
    {
        if (!runtime.registerSceneFromFile(scene.name, scene.file))
        {
            sle::core::Log::error("Failed to register scene '{}' from '{}'", scene.name, scene.file);
            return 1;
        }
    }

    auto loadSceneResult = runtime.loadScene(appConfig.startScene);
    if (!loadSceneResult.ok())
    {
        sle::core::Log::error("Failed to load startup scene '{}': {}", appConfig.startScene, loadSceneResult.error());
        return 1;
    }

    }
    if (frames) {
        const auto executed=runtime.runForFrames(*frames);
        sle::core::Log::info("Completed {} frames; scene: {}",executed,runtime.getCurrentSceneName());
    } else runtime.run();
    return 0;
}
