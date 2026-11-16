#include <sle/core/EngineConfig.hpp>
#include <sle/core/Log.hpp>
#include <sle/core/Version.hpp>
#include <iostream>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/GameConfig.hpp>
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

#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

namespace {

namespace fs = std::filesystem;

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

} // namespace

int main(int argc, char** argv)
{
    std::string option, path;
    std::optional<std::size_t> frames;
    for (int index=1;index<argc;++index) {
        const std::string argument=argv[index];
        if (argument=="--version") {
            std::cout<<"SLE engine "<<sle::core::EngineVersion::text<<'\n';return 0;
        }
        if ((argument=="--config" || argument=="--project") && option.empty() && index+1<argc) {
            option=argument;path=argv[++index];
        } else if (argument=="--frames" && !frames && index+1<argc) {
            const std::string value=argv[++index];std::size_t count=0;
            const auto parsed=std::from_chars(value.data(),value.data()+value.size(),count);
            if (parsed.ec!=std::errc{} || parsed.ptr!=value.data()+value.size() || count==0) {
                sle::core::Log::error("--frames requires a positive integer");return 1;
            }
            frames=count;
        } else { sle::core::Log::error("Usage: engine_app [--config engine.json|config.json | --project game.sleproject] [--frames N] | --version");return 1; }
    }
    sle::core::Log::info("SLE engine {}",sle::core::EngineVersion::text);
    fs::path executable=fs::absolute(argv[0]);
#ifdef _WIN32
    std::wstring executableBuffer(32768,L'\0');
    const auto length=GetModuleFileNameW(nullptr,executableBuffer.data(),static_cast<DWORD>(executableBuffer.size()));
    if (length>0 && length<executableBuffer.size()) { executableBuffer.resize(length);executable=fs::path(executableBuffer); }
#endif
    // A delivery folder owns its default project; explicit options retain priority.
    const auto deliveredProject=executable.parent_path()/"game"/"game.sleproject";
    // A built game delivers config.json and its .slepkg packages beside the executable.
    const auto deliveredConfig=executable.parent_path()/"config.json";
    if (option.empty() && fs::exists(deliveredProject)) {
        option="--project";path=deliveredProject.string();
    } else if (option.empty() && fs::is_regular_file(deliveredConfig)) {
        option="--config";path=deliveredConfig.string();
    }
    std::optional<sle::ProjectRuntime> project;
    sle::GameConfig appConfig;
    if (option=="--project") {
        auto loaded=sle::ProjectRuntime::open(fs::absolute(fs::path(path)));
        if (!loaded.ok()) { sle::core::Log::error("{}",loaded.error());return 1; }
        project=std::move(loaded.value());appConfig.engine=project->engineConfig();
    } else {
        const fs::path configPath=option=="--config"?resolveByWalkingParents(path):resolveConfigPath();
        if (configPath.empty()) { sle::core::Log::error("Failed to locate engine configuration");return 1; }
        try {
            auto loaded=sle::GameConfig::load(configPath);
            if (!loaded.ok()) { sle::core::Log::error("{}",loaded.error());return 1; }
            appConfig=std::move(loaded.value());
        } catch (const std::exception& error) { sle::core::Log::error("Invalid engine config: {}",error.what());return 1; }
        if (!appConfig.packages.empty()) {
            auto loaded=sle::ProjectRuntime::openPackages(appConfig.packages,appConfig.engine);
            if (!loaded.ok()) { sle::core::Log::error("{}",loaded.error());return 1; }
            project=std::move(loaded.value());
        }
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
    for (const auto& scene : appConfig.scenes)
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
