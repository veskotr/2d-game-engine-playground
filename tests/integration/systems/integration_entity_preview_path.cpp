#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <sle/engine/Runtime.hpp>
#include <sle/events/SceneLifecycleEvents.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <vector>
#include <iostream>

int main(int argc, char** argv)
{
    if (!glfwInit()) return 125;
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    auto* host = glfwCreateWindow(480, 270, "Entity preview regression", nullptr, nullptr);
    if (!host) { glfwTerminate(); return 125; }
    glfwMakeContextCurrent(host);
    if (!gladLoadGL(glfwGetProcAddress)) return 1;
    bool passed = true;
    {
        sle::core::EngineConfig config;
        config.width = 480;
        config.height = 270;
        sle::Runtime runtime(config);
        if (!runtime.initEmbedded(false).ok()) return 1;
        if (argc == 2) {
            runtime.registerSceneFromFile("packaged_scene",argv[1]);
            const auto loaded=runtime.loadScene("packaged_scene");
            if (!loaded.ok()) { std::cerr << loaded.error(); return 1; }
            const auto rendered=runtime.tickEmbeddedFrame(480,270,1.0f/60.0f);
            if (!rendered.ok() || glGetError()!=GL_NO_ERROR) return 1;
            std::cout << "ENGINE_SCENE_PACKAGE_RUNTIME_PASS\n";
        } else {
        int previewEvents=0;
        runtime.getGlobalBus().subscribe<sle::events::SceneLoadedEvent>([&](const auto& event) {
            if (event.sceneName == "entity_preview") ++previewEvents;
        });
        runtime.getGlobalBus().subscribe<sle::events::SceneUnloadedEvent>([&](const auto& event) {
            if (event.sceneName == "entity_preview") ++previewEvents;
        });
        nlohmann::json doc = {{"root","entity"},{"entities",nlohmann::json::array({{
            {"key","entity"},{"components",{
                {"Transform",{{"position",{{"x",12},{"y",34}}}}},
                {"BoxCollider",{{"size",{{"x",2},{"y",4}}}}},
                {"Script",{{"scriptAsset","tests/data/scripts/embedded_runtime_update.lua"}}}
            }}
        }})}};
        if (!runtime.loadEntityPreview(doc).ok() || !runtime.renderEntityPreview(480,270,true).ok()) return 1;
        const auto entity = runtime.getScene().findEntityByKey("entity");
        const auto position = runtime.getScene().getRegistry().getComponent<sle::components::TransformComponent>(entity)->getPosition();
        if (position.x != 12 || position.y != 34) { std::cerr << "Preview ran gameplay script"; passed=false; }
        std::vector<unsigned char> first(480*270*4), second(first.size());
        glReadPixels(0,0,480,270,GL_RGBA,GL_UNSIGNED_BYTE,first.data());
        int cyan=0;
        for (std::size_t p=0;p<first.size();p+=4)
            if (first[p]<100 && first[p+1]>150 && first[p+2]>150) ++cyan;
        if (cyan < 40) { std::cerr << "Missing collider preview pixels: " << cyan; passed=false; }
        auto invalid=doc;
        invalid["entities"][0]["components"]["BoxCollider"]["size"]["x"]=-1;
        if (runtime.loadEntityPreview(invalid).ok() || runtime.getScene().findEntityByKey("entity") != entity) passed=false;
        doc["entities"][0]["components"]["BoxCollider"]["size"]={{"x",4},{"y",1}};
        if (!runtime.loadEntityPreview(doc).ok() || !runtime.renderEntityPreview(480,270,true).ok()) return 1;
        glReadPixels(0,0,480,270,GL_RGBA,GL_UNSIGNED_BYTE,second.data());
        if (first==second) { std::cerr << "Collider edit did not change rendered image"; passed=false; }
        doc["entities"][0]["components"]["SpriteRenderer"]={
            {"texture","assets/textures/tile2.png"},
            {"color",{{"x",1},{"y",0},{"z",0},{"w",1}}},
            {"size",{{"x",1},{"y",1}}},{"offset",{{"x",0},{"y",0}}}};
        auto redPixels=[&]() {
            glReadPixels(0,0,480,270,GL_RGBA,GL_UNSIGNED_BYTE,second.data());
            int red=0; for (std::size_t p=0;p<second.size();p+=4)
                if (second[p]>100 && second[p+1]<70 && second[p+2]<70) ++red;
            return red;
        };
        if (!runtime.loadEntityPreview(doc,true).ok() || !runtime.renderEntityPreview(480,270,false).ok()) return 1;
        const auto originalRed=redPixels();
        doc["entities"][0]["components"]["SpriteRenderer"]["size"]["x"]=2;
        if (!runtime.loadEntityPreview(doc,true).ok() || !runtime.renderEntityPreview(480,270,false).ok()) return 1;
        if (originalRed<100 || redPixels()<originalRed*1.6) { std::cerr<<"Sprite-local size did not change rendered extent"; passed=false; }
        const auto spriteEntity=runtime.getScene().findEntityByKey("entity");
        const auto& registry=runtime.getScene().getRegistry();
        if (registry.getComponent<sle::components::TransformComponent>(spriteEntity)->getScale()!=glm::vec2(1.0f) ||
            registry.getComponent<sle::components::BoxColliderComponent>(spriteEntity)->size!=glm::vec2(4,1)) {
            std::cerr<<"Sprite resize changed entity/collider geometry"; passed=false;
        }
        // Large, offset sprites must be culled by their own bounds, not origin.
        doc["entities"][0]["components"]["SpriteRenderer"]["size"]["x"]=100;
        doc["entities"][0]["components"]["SpriteRenderer"]["offset"]["x"]=-40;
        if (!runtime.loadEntityPreview(doc,true).ok() || !runtime.renderEntityPreview(480,270,false).ok() || redPixels()<originalRed*2) return 1;
        runtime.zoomEntityPreview(0.5f);
        const auto viewPosition=runtime.getPreviewPosition();
        const auto viewSize=runtime.getPreviewViewportSize();
        doc["entities"][0]["components"]["Transform"]["position"]["x"]=30;
        if (!runtime.loadEntityPreview(doc,true).ok() || !runtime.renderEntityPreview(480,270,false).ok()) return 1;
        if (runtime.getPreviewPosition()!=viewPosition || runtime.getPreviewViewportSize()!=viewSize) {
            std::cerr << "Preview edit moved the camera"; passed=false;
        }
        nlohmann::json sceneDoc={{"entities",doc["entities"]}};
        auto other=doc["entities"][0];
        other["key"]="other";
        other["components"]["Transform"]["position"]["x"]=-200;
        sceneDoc["entities"].push_back(other);
        if (!runtime.loadScenePreview(sceneDoc).ok() || !runtime.renderEntityPreview(480,270,true).ok()) return 1;
        const auto scenePosition=runtime.getPreviewPosition(), sceneSize=runtime.getPreviewViewportSize();
        if (scenePosition.x > -200 || scenePosition.x+sceneSize.x < 30) return 1;
        const auto retained=runtime.getScene().findEntityByKey("other");
        sceneDoc["entities"][1]["parent"]="missing";
        if (runtime.loadScenePreview(sceneDoc).ok() || runtime.getScene().findEntityByKey("other")!=retained) return 1;
        runtime.getGlobalBus().flushQueue();
        if (previewEvents != 0) { std::cerr << "Preview edits queued gameplay lifecycle events"; passed=false; }
        runtime.registerSceneFromFile("sandbox","assets/scenes/npc_zone_demo.json");
        if (!runtime.loadScene("sandbox").ok() || !runtime.tickEmbeddedFrame(480,270,1.0f/60.0f).ok()) passed=false;
        if (runtime.getWindowSize().x != 480 || glfwGetCurrentContext()!=host || glGetError()!=GL_NO_ERROR) passed=false;
        }
    }
    glfwDestroyWindow(host);
    glfwTerminate();
    return passed ? 0 : 1;
}
