#include <glad/gl.h>
#include <GLFW/glfw3.h>

#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/scene/components/Transform.hpp>

#include <array>
#include <chrono>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <stdexcept>

namespace {

void check(bool condition, const std::string& message)
{
    if (!condition) throw std::runtime_error(message);
}

using Pixel = std::array<unsigned char, 4>;

Pixel pixel(int x, int y)
{
    Pixel result{};
    glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, result.data());
    check(glGetError() == GL_NO_ERROR, "Tilemap pixel read failed");
    return result;
}

constexpr Pixel red{255, 0, 0, 255};
constexpr Pixel green{0, 255, 0, 255};

}

int main()
try {
    namespace fs = std::filesystem;
    const auto fixture = fs::absolute("tests/data/tilemap/Tiles.sleproject");
    const auto engineAssets = fs::absolute("assets").generic_string();
    fs::current_path(fixture.parent_path().parent_path());
    const auto project = sle::ProjectRuntime::open(fixture);
    check(project.ok(), project.error());
    auto config = project.value().engineConfig();
    config.engineAssetRoot = engineAssets;

    Pixel standaloneForeground{};
    Pixel standaloneBackdrop{};
    {
        sle::Runtime standalone(config);
        const auto initialized = standalone.init();
        if (!initialized.ok() && initialized.error().find("GLFW") != std::string::npos) {
            std::cerr << initialized.error() << '\n';
            return 125;
        }
        check(initialized.ok(), initialized.error());
        const auto started = standalone.startProject(project.value());
        check(started.ok(), started.error());
        // Read the standalone frame before it can be lost to the window compositor.
        const auto size = standalone.getWindowSize();
        GLuint framebuffer = 0, color = 0;
        glGenFramebuffers(1, &framebuffer);
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glGenTextures(1, &color);
        glBindTexture(GL_TEXTURE_2D, color);
        glTexStorage2D(GL_TEXTURE_2D, 1, GL_RGBA8, static_cast<int>(size.x), static_cast<int>(size.y));
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "Standalone render target is incomplete");
        check(standalone.runForFrames(1) == 1, "Standalone did not render the tile project");
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        const auto viewport = standalone.sceneGameViewport();
        const auto sample = [&](float x, float y) {
            return pixel(static_cast<int>(viewport[0] + x * viewport[2] / viewport[4]),
                static_cast<int>(viewport[1] + y * viewport[3] / viewport[5]));
        };
        standaloneForeground = sample(12, 20);
        standaloneBackdrop = sample(20, 20);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(1, &framebuffer);
        glDeleteTextures(1, &color);
        check(standaloneForeground == green && standaloneBackdrop == red, "Standalone tile colors are incorrect");
        const auto& layers = standalone.getScene().getTileLayers();
        check(layers.size() == 2 && layers[1].chunks.at({-1, 0}).cells.at(0).region.texture,
            "Standalone did not resolve the foreground spritesheet");
    }

    check(glfwInit() == GLFW_TRUE, "Could not initialize the embedded host");
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    std::unique_ptr<GLFWwindow, decltype(&glfwDestroyWindow)> host(
        glfwCreateWindow(64, 64, "Tilemap Play", nullptr, nullptr), glfwDestroyWindow);
    check(static_cast<bool>(host), "Could not create the embedded host");
    glfwMakeContextCurrent(host.get());
    check(gladLoadGL(glfwGetProcAddress) != 0, "Could not initialize OpenGL in the embedded host");
    {
        const auto v2Assets = fs::temp_directory_path() / ("sle-tilemap-render-v2-" +
            std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
        struct Cleanup {
            fs::path path;
            ~Cleanup() { std::error_code error; fs::remove_all(path, error); }
        } cleanup{v2Assets};
        fs::copy(project.value().assetRoot(), v2Assets, fs::copy_options::recursive);
        const auto v2Path = v2Assets / "Colors.tileset.json";
        auto v2 = nlohmann::json::parse(std::ifstream(v2Path));
        v2["version"] = 2;
        v2["grid"] = {{"margin", {0, 0}}, {"spacing", {0, 0}}, {"columns", 1}};
        v2["tiles"][0]["tags"] = {"terrain"};
        v2["tiles"][0]["properties"] = {{"friction", 0.0}};
        {
            std::ofstream file(v2Path);
            file << v2;
            file.close();
            check(static_cast<bool>(file), "Could not write the version 2 render fixture");
        }
        sle::Runtime editor(config);
        const auto initialized = editor.initEmbedded(false);
        check(initialized.ok(), initialized.error());
        editor.setEditorAssetRoot(v2Assets);
        auto source = project.value().sceneDocument(project.value().startupScene());
        const auto started = editor.startScenePlay(source);
        check(started.ok(), started.error());

        const auto draw = [&] {
            const auto drawn = editor.drawSceneView(64, 64, true);
            check(drawn.ok(), drawn.error());
        };
        draw();
        check(pixel(12, 20) == standaloneForeground, "V2 foreground must match V1 over the negative-X tile");
        check(pixel(20, 20) == standaloneBackdrop, "V2 backdrop must match V1 in the adjacent chunk");

        auto& foreground = editor.getScene().getTileLayers().at(1);
        foreground.visible = false;
        draw();
        check(pixel(12, 20) == red, "Hiding the foreground must reveal the backdrop");
        foreground.visible = true;
        draw();
        check(pixel(12, 20) == green, "Showing the foreground must restore the tile");

        // The same tiles in a shared map render through an ordinary movable entity.
        nlohmann::json map = {{"format", "sle-tilemap"}, {"version", 1}, {"name", "World"},
            {"grid", {{"cellWidth", v2["tileWidth"]}, {"cellHeight", v2["tileHeight"]}, {"pixelsPerUnit", 1}}},
            {"streaming", {{"mode", "always"}, {"chunkCells", 32}, {"loadRadius", 2}, {"unloadRadius", 3}}},
            {"tilesets", nlohmann::json::array({{{"key", 0}, {"asset", source["tileLayers"][0]["tileset"]}}})},
            {"layers", nlohmann::json::array()}};
        for (const auto& layer : source["tileLayers"]) {
            auto cells = layer["cells"];
            for (auto& cell : cells) cell["set"] = 0;
            map["layers"].push_back({{"id", layer["id"]}, {"name", layer["name"]}, {"drawOrder", layer["drawOrder"]},
                {"visible", layer["visible"]}, {"locked", layer["locked"]}, {"collision", layer["collisionEnabled"]}, {"cells", cells}});
        }
        const auto mapPath = v2Assets / "World.tilemap.json";
        { std::ofstream file(mapPath); file << map; }
        auto placedSource = source;
        placedSource.erase("tileLayers");
        placedSource["entities"].push_back({{"key", "world"}, {"components", {
            {"Transform", {{"position", {16, 16}}}}, {"Tilemap", {{"asset", mapPath.generic_string()}, {"sortingOffset", 0}}}
        }}});
        const auto mapStarted = editor.startScenePlay(placedSource);
        check(mapStarted.ok(), mapStarted.error());
        draw();
        check(pixel(12, 20) == green && pixel(20, 20) == red, "Native map placement changed layer rendering");
        const auto mapEntity = editor.getScene().findEntityByKey("world");
        editor.getScene().getRegistry().getComponent<sle::components::TransformComponent>(mapEntity)->setPosition({24, 16});
        draw();
        check(pixel(20, 20) == green, "Moving a native map did not move its rendered layers together");

        source["tileLayers"][1]["cells"][0]["tile"] = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        const auto missing = editor.startScenePlay(source);
        check(missing.ok(), missing.error());
        draw();
        const auto placeholder = pixel(9, 17);
        check(placeholder[0] > 0 && placeholder[1] == 0 && placeholder[0] == placeholder[2],
            "Unresolved tiles must show a magenta placeholder");

        // Start a new scene far beyond the previous camera's activation margin.
        source = project.value().sceneDocument(project.value().startupScene());
        source["entities"][0]["components"]["Transform"]["position"] = {1232, 32};
        for (auto& layer : source["tileLayers"]) layer["origin"] = {1216, 16};
        source["entities"].push_back({
            {"key", "player"}, {"name", "Player"}, {"components", {
                {"Transform", {{"position", {1218, 24}}}},
                {"RigidBody", {{"bodyType", "Dynamic"}}},
                {"CircleCollider", {{"radius", 1}, {"friction", 0}}}
            }}
        });
        const auto farScene = editor.startScenePlay(source);
        check(farScene.ok(), farScene.error());
        const auto firstStep = editor.advanceEmbeddedFrame(1.0f / 60.0f);
        check(firstStep.ok(), firstStep.error());
        bool hasSlope = false;
        editor.getPhysicsWorld()->visitTerrainEdges([&](glm::vec2 a, glm::vec2 b) {
            hasSlope |= a.x >= 1215.99f && b.x >= 1215.99f && std::abs(a.y - b.y) > 3.9f;
        });
        check(hasSlope, "The first physics step did not activate terrain at the scene camera");
        for (int frame = 0; frame < 12; ++frame) {
            const auto stepped = editor.advanceEmbeddedFrame(1.0f / 60.0f);
            check(stepped.ok(), stepped.error());
        }
        const auto player = editor.getScene().findEntityByKey("player");
        const auto* transform = editor.getScene().getRegistry().getComponent<sle::components::TransformComponent>(player);
        check(transform && transform->getPosition().x > 1218.1f && transform->getPosition().y > 16,
            "The painted slope did not support and redirect the player");

        const auto renderDebugView = [&] {
            const auto result = editor.renderEmbeddedFrame(64, 64);
            check(result.ok(), result.error());
        };
        const auto overlayAtSlope = [] {
            for (int x = 19; x <= 21; ++x)
                for (int y = 17; y <= 19; ++y) {
                    const auto color = pixel(x, y);
                    if (color[2] > 200 && color[1] > 160 && color[0] < 100) return true;
                }
            return false;
        };
        renderDebugView();
        check(!overlayAtSlope(), "The slope showed debug geometry with the overlay disabled");
        editor.setPhysicsDebugEnabled(true);
        renderDebugView();
        check(overlayAtSlope(), "The fixture overlay missed the painted slope");

        const auto projectPlay = editor.startProject(project.value());
        check(projectPlay.ok(), projectPlay.error());
        draw();
        check(pixel(12, 20) == standaloneForeground && pixel(20, 20) == standaloneBackdrop,
            "Run Project must render the same project tiles as standalone");
    }
    host.reset();
    glfwTerminate();
    return 0;
} catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
}
