#include <glad/gl.h>
#include <GLFW/glfw3.h>
#include <box2d/b2_world.h>
#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/GameConfig.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <array>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <iterator>
#include <chrono>
#include <stdexcept>
#include <string_view>
namespace fs=std::filesystem;
using Json=nlohmann::json;
void check(bool value,const std::string& message) { if(!value) throw std::runtime_error(message); }
void write(const fs::path& path,const Json& value) { fs::create_directories(path.parent_path());std::ofstream(path)<<value.dump(); }
const std::string a="11111111-1111-4111-8111-111111111111",b="22222222-2222-4222-8222-222222222222",s="33333333-3333-4333-8333-333333333333";
void meta(const fs::path& path,const std::string& id) { write(path.string()+".slemeta",{{"format","sle-asset"},{"version",1},{"id",id}}); }
std::shared_ptr<sle::renderer::Texture> checkSampler(sle::Runtime& runtime, GLint expected, const char* key = "sprite") {
    auto& scene = runtime.getScene();
    const auto* sprite = scene.getRegistry().getComponent<sle::components::SpriteRenderer>(scene.findEntityByKey(key));
    check(sprite && sprite->region.texture, "project sprite texture missing");
    sprite->region.texture->bind();
    GLint minification = 0, magnification = 0;
    glGetTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, &minification);
    glGetTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, &magnification);
    sprite->region.texture->unbind();
    check(minification == expected && magnification == expected, "project sprite sampler mismatch");
    return sprite->region.texture;
}
void checkRegion(sle::Runtime& runtime, const glm::vec4& expected) {
    const auto& scene = runtime.getScene();
    const auto* sprite = scene.getRegistry().getComponent<sle::components::SpriteRenderer>(scene.findEntityByKey("sprite"));
    check(sprite && sprite->region.uv == expected, "project sprite region UV mismatch");
}
void checkCollisionPair(sle::Runtime& runtime, bool blocked) {
    auto& world = *runtime.getPhysicsWorld();
    sle::components::RigidBodyComponent ground;
    ground.bodyType = sle::components::BodyType::Static;
    sle::components::RigidBodyComponent moving;
    moving.bodyType = sle::components::BodyType::Dynamic;
    const auto groundId = world.createBody({0, 0}, ground);
    const auto movingId = world.createBody({0, 0}, moving);
    sle::components::BoxColliderComponent collider;
    collider.size = {10, 10};
    collider.categoryBits = 1;
    world.createBoxFixture(groundId, collider);
    collider.categoryBits = 2;
    world.createBoxFixture(movingId, collider);
    world.step(1.0f / 60.0f);
    check(world.getRawWorld()->GetContactCount() == (blocked ? 0 : 1), "runtime collision pair mismatch");
    world.destroyBody(movingId);
    world.destroyBody(groundId);
}
int main(int argc, char** argv) {
    if (argc == 4 && std::string_view(argv[1]) == "--check-package-sampler") {
        try {
            const auto game = sle::GameConfig::load(fs::u8path(argv[2]));
            check(game.ok(), game.error());
            const auto project = sle::ProjectRuntime::openPackages(game.value().packages, game.value().engine);
            check(project.ok(), project.error());
            sle::Runtime runtime(project.value().engineConfig());
            const auto init = runtime.init();
            check(init.ok(), init.error());
            const auto started = runtime.startProject(project.value());
            check(started.ok(), started.error());
            sle::input::Input::setKeyState(GLFW_KEY_ESCAPE, true);
            check(runtime.runForFrames(8) == 8, "bound Escape quit packaged project");
            check(runtime.sceneTransforms().at("second-actor").at("position").at("x") == 71,
                "Lua could not read the packaged action in standalone");
            const std::string_view style = argv[3];
            checkSampler(runtime, style == "nearest" || style == "pixelArt" ? GL_NEAREST : GL_LINEAR);
            checkRegion(runtime, {0, 0.5f, 0.5f, 1});
            checkCollisionPair(runtime, style == "pixelArt");
            return 0;
        } catch (const std::exception& error) {
            std::cerr << error.what() << '\n';
            return 1;
        }
    }
    const auto cwd=fs::current_path();const auto base=fs::temp_directory_path()/("sle-project-play-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    try {
        const auto root=base/"game";
        write(root/"game.sleproject",{{"format","sle-project"},{"version",1},{"id",a},{"name","Play"},{"assetRoot","Assets"},{"startupScene",a},{"settings",Json::parse(R"({"artStyle":"pixelArt","tags":[{"id":"actor-tag","name":"Actor"}],"inputActions":[{"name":"Jump","bindings":[{"kind":"key","key":32},{"kind":"key","key":87}]},{"name":"Unbound","bindings":[]}],"collisionLayers":{"blockedPairs":[[0,1]]}})")},
            {"game",{{"window",{{"title","Play project"},{"width",640},{"height",360},{"mode","Windowed"},{"vsync",false}}},{"targetFPS",30}}}});
        auto scene=[](const std::string& key,Json entities) { return Json{{"format","sle-project-scene"},{"version",1},{"assets",Json::array()}, {"document",{{"key",key},{"name",key},{"entities",entities}}}}; };
        write(root/"Assets/Scenes/b.scene.json",scene("second",Json::array({{{"key","second-actor"},{"components",{{"Transform",Json::object()},{"Script",{{"scriptAsset","project-asset://"+s}}}}}}})));meta(root/"Assets/Scenes/b.scene.json",b);
        const std::string textureId = "55555555-5555-4555-8555-555555555555";
        const auto textureRef = "project-asset://" + textureId;
        write(root/"Assets/Prefabs/spawn.prefab.json",{{"format","sle-project-prefab"},{"version",1},{"assets",Json::array()},
            {"document",{{"root","root"},{"entities",Json::array({{{"key","root"},{"components",{{"Transform",Json::object()},{"SpriteRenderer",{{"texture",textureRef}}},{"Tags",{{"ids",{"actor-tag","unresolved-tag"}}}}}}}})}}}});
        meta(root/"Assets/Prefabs/spawn.prefab.json","44444444-4444-4444-8444-444444444444");
        fs::create_directories(root/"Assets/Scripts/modules");std::ofstream(root/"Assets/Scripts/modules/value.lua")<<"return 123";
        std::ofstream(root/"Assets/Scripts/main.lua")<<R"(local value=require('modules.value')
function verifyActions(expected)
 local input = Engine.Input
 for _, name in ipairs({'Unknown', 'Unbound'}) do
   assert(not input.isActionDown(name) and not input.isActionPressed(name) and not input.isActionReleased(name))
 end
 local down, pressed, released = input.isActionDown('Jump'), input.isActionPressed('Jump'), input.isActionReleased('Jump')
 if expected == 'pressed' then return down and pressed and not released end
 if expected == 'held' then return down and not pressed and not released end
 if expected == 'released' then return not down and not pressed and released end
 return not down and not pressed and not released
end
function verifyTags(phase)
 local matches=Engine.findEntitiesByTag(phase == 'renamed' and 'Renamed' or 'Actor')
 assert(#Engine.findEntitiesByTag('unknown') == 0)
 if phase == 'startup' or phase == 'renamed' then
   assert(#matches == 1 and matches[1] == Engine.findEntityByKey('first-actor'))
   if phase == 'renamed' then assert(#Engine.findEntitiesByTag('Actor') == 0) end
 elseif phase == 'removed-definition' then
   assert(#matches == 0)
 elseif phase == 'switched' then
   assert(#matches == 2 and matches[1] == Engine.findEntityByKey('spawned'))
   assert(matches[2] == Engine.findEntityByKey('uuid-spawned') and matches[1] < matches[2])
 elseif phase == 'destroyed' then
   assert(#matches == 1 and matches[1] == Engine.findEntityByKey('uuid-spawned'))
 end
 return true
end
return { update=function(self,dt)
 assert(Engine.loadTexture('Textures/missing.png') == 0)
 local entity=Engine.findEntityByKey('second-actor')
 if entity then
   if not Engine.findEntityByKey('spawned') then
     assert(Engine.instantiatePrefab('Prefabs/spawn.prefab.json','spawned',{root={Script={scriptAsset='project-asset://33333333-3333-4333-8333-333333333333'}}}))
     assert(Engine.instantiatePrefab('project-asset://44444444-4444-4444-8444-444444444444','uuid-spawned',{root={Script={scriptAsset='Scripts/main.lua'}}}))
   end
   Engine.setTransformPosition(entity,value,0)
 else Engine.switchScene('second') end
end })";meta(root/"Assets/Scripts/main.lua",s);
        fs::create_directories(root/"Assets/Textures");
        fs::copy_file(cwd/"assets/textures/tile2.png", root/"Assets/Textures/sprite.png");
        auto textureMetadata = Json{{"format","sle-asset"},{"version",1},{"id",textureId},{"texture",{{"filter","nearest"}}}};
        const std::string regionId = "66666666-6666-4666-8666-666666666666";
        textureMetadata["texture"]["regions"] = Json::array({
            {{"id",regionId},{"name","Top left"},{"x",0},{"y",0},{"width",368},{"height",368}}
        });
        write(root/"Assets/Textures/sprite.png.slemeta", textureMetadata);
        auto startup = scene("first", Json::array({
            {{"key","first-actor"},{"components",{{"Tags",{{"ids",{"actor-tag"}}}},{"Script",{{"scriptAsset","project-asset://"+s}}}}}},
            {{"key","sprite"},{"components",{{"Transform",{{"scale",{4,4}}}},{"SpriteRenderer",{{"texture",textureRef},{"regionId",regionId}}}}}}
        }));
        write(root/"Assets/Scenes/a.scene.json", startup);
        meta(root/"Assets/Scenes/a.scene.json", a);
        const auto moved=base/"moved";fs::rename(root,moved);
        auto project=sle::ProjectRuntime::open(moved/"game.sleproject");check(project.ok(),project.error());
        auto config=project.value().engineConfig();config.engineAssetRoot=(cwd/"assets").generic_string();
        fs::current_path(base);
        check(glfwInit() == GLFW_TRUE, "failed to initialize preview host");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        std::unique_ptr<GLFWwindow, decltype(&glfwDestroyWindow)> host(
            glfwCreateWindow(480, 270, "Project sprite preview", nullptr, nullptr), glfwDestroyWindow);
        check(host != nullptr, "failed to create preview host");
        glfwMakeContextCurrent(host.get());
        check(gladLoadGL(glfwGetProcAddress) != 0, "failed to load preview host GL");
        {
            sle::Runtime preview(config);
            const auto init = preview.initEmbedded(false);
            check(init.ok(), init.error());
            auto layeredSprites = Json::parse(R"({"entities":[
                {"key":"top","components":{"Transform":{"position":[320,180]},
                    "SpriteRenderer":{"color":[0,1,0,1],"size":[1200,1200],"layer":10}}},
                {"key":"bottom","components":{"Transform":{"position":[320,180]},
                    "SpriteRenderer":{"color":[1,0,0,1],"size":[1200,1200],"layer":-3}}}
            ]})");
            const auto whiteTexture = moved/"Assets/Textures/white.ppm";
            std::ofstream(whiteTexture, std::ios::binary) << "P6\n1 1\n255\n" << "\xff\xff\xff";
            for (auto& sprite : layeredSprites["entities"])
                sprite["components"]["SpriteRenderer"]["texture"] = whiteTexture.generic_string();
            const auto pixel = [&](bool gameView) {
                check(preview.drawSceneView(480, 270, gameView, false).ok(), "failed to render sorting layer overlap");
                std::array<unsigned char, 4> color{};
                glReadPixels(220, 120, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, color.data());
                return color;
            };
            preview.setEditorProjectSettings(moved/"Assets", Json::parse(
                R"({"sortingLayers":[{"value":10,"label":"Foreground"},{"value":-3,"label":"Backdrop"}]})"));
            const auto sortingPreview = preview.loadScenePreview(layeredSprites);
            check(sortingPreview.ok(), sortingPreview.error());
            const auto before = pixel(false);
            check(before[0] < 10 && before[1] > 245 && before[2] < 10, "positive sprite layer should draw on top, read RGB " +
                std::to_string(before[0]) + "," + std::to_string(before[1]) + "," + std::to_string(before[2]));
            preview.setEditorProjectSettings(moved/"Assets", Json::parse(
                R"({"sortingLayers":[{"value":10,"label":"Renamed"}]})"));
            const auto sortingPlay = preview.startScenePlay(layeredSprites);
            check(sortingPlay.ok(), sortingPlay.error());
            check(pixel(true) == before, "label changes altered sprite overlap in Play Scene");
            const auto& draft = project.value().sceneDocument(a);
            preview.setEditorAssetRoot(moved/"Assets", "pixelArt");
            const auto firstPreview = preview.loadScenePreview(draft);
            check(firstPreview.ok(), firstPreview.error());
            const auto nearest = checkSampler(preview, GL_NEAREST);
            checkRegion(preview, {0, 0.5f, 0.5f, 1});
            auto& region = textureMetadata["texture"]["regions"][0];
            region["name"] = "Renamed";
            region["x"] = 368;
            textureMetadata["texture"]["filter"] = "linear";
            write(moved/"Assets/Textures/sprite.png.slemeta", textureMetadata);
            const auto refreshed = preview.loadScenePreview(draft, true);
            check(refreshed.ok(), refreshed.error());
            check(nearest != checkSampler(preview, GL_LINEAR), "preview reused texture after changing its filter");
            checkRegion(preview, {0.5f, 0.5f, 1, 1});
            region["x"] = 369;
            write(moved/"Assets/Textures/sprite.png.slemeta", textureMetadata);
            const auto outOfBounds = preview.loadScenePreview(draft, true);
            check(outOfBounds.ok(), outOfBounds.error());
            checkRegion(preview, {0, 0, 1, 1});
            region["x"] = 0;
            const auto image = moved/"Assets/Textures/sprite.png";
            write(moved/"Assets/Textures/sprite.png.slemeta", textureMetadata);
            std::ofstream(image, std::ios::binary) << "P6\n1 1\n255\n" << "abc";
            const auto resized = preview.loadScenePreview(draft, true);
            check(resized.ok(), resized.error());
            check(checkSampler(preview, GL_LINEAR)->getWidth() == 1, "preview reused resized texture");
            checkRegion(preview, {0, 0, 1, 1});
            fs::copy_file(cwd/"assets/textures/tile2.png", image, fs::copy_options::overwrite_existing);
            textureMetadata["texture"].erase("filter");
            write(moved/"Assets/Textures/sprite.png.slemeta", textureMetadata);
            preview.setEditorAssetRoot(moved/"Assets", "smooth");
            const auto inherited = preview.loadScenePreview(draft, true);
            check(inherited.ok(), inherited.error());
            checkSampler(preview, GL_LINEAR);
            preview.setEditorProjectSettings(moved/"Assets", Json::parse(
                R"({"artStyle":"pixelArt","collisionLayers":{"blockedPairs":[[1,0]]},
                    "inputActions":[{"name":"Draft action","bindings":[{"kind":"key","key":69}]}]})"));
            const auto draftPlay = preview.startScenePlay(draft);
            check(draftPlay.ok(), draftPlay.error());
            checkCollisionPair(preview, true);
            checkSampler(preview, GL_NEAREST);
            textureMetadata["texture"]["filter"] = "nearest";
            write(moved/"Assets/Textures/sprite.png.slemeta", textureMetadata);
            const auto instance = preview.liveHierarchy().at("sceneInstance").get<uint64_t>();
            const auto actor = preview.getScene().findEntityByKey("first-actor").getID();
            const auto attachment = preview.liveComponents(instance, actor).at("Script").at(0);
            check(attachment.at("canReload") == true, "Play Scene should resolve saved project sources");
            sle::input::Input::setKeyState(GLFW_KEY_E, true);
            check(preview.advanceEmbeddedFrame(0.016f, false).ok(), "draft action advance failed");
            check(preview.inputAction("Draft action").pressed, "Play Scene ignored editor action map");
            preview.setEditorAssetRoot({});
            check(preview.liveComponents(instance, actor).at("Script").at(0).at("canReload") == false,
                "Play Scene without a project should not offer reload");
        }
        {
            sle::Runtime runtime(config);
            const auto init = runtime.initEmbedded(false);
            check(init.ok(), init.error());
            const auto started = runtime.startProject(project.value());
            check(started.ok(), started.error());
            checkCollisionPair(runtime, true);
            auto& actionLua = runtime.getScriptEngine();
            const auto actionScript = (moved / "Assets/Scripts/main.lua").generic_string();
            const auto verifyAction = [&](const char* expected) {
                check(actionLua.callGlobalBoolFunction("verifyActions", 0, expected),
                    std::string("unexpected Lua action state: ") + expected);
            };
            const auto advance = [&] {
                check(runtime.advanceEmbeddedFrame(0.016f, false).ok(), "embedded action advance failed");
            };
            using Input = sle::input::Input;
            check(actionLua.executeScriptAsset(actionScript), "could not load action Lua probe");
            verifyAction("inactive");
            Input::setKeyState(GLFW_KEY_SPACE, true);
            advance();
            verifyAction("pressed");
            Input::setKeyState(GLFW_KEY_W, true);
            advance();
            verifyAction("held");
            Input::setKeyState(GLFW_KEY_SPACE, false);
            advance();
            verifyAction("held");
            runtime.setEmbeddedPaused(true);
            advance();
            verifyAction("held");
            check(runtime.requestEmbeddedStep(), "could not request action release step");
            advance();
            verifyAction("released");
            Input::setKeyState(GLFW_KEY_W, true);
            check(runtime.requestEmbeddedStep(), "could not request action press step");
            advance();
            verifyAction("pressed");
            Input::releaseAll();
            check(runtime.requestEmbeddedStep(), "could not request focus loss step");
            advance();
            verifyAction("released");
            check(runtime.requestEmbeddedStep(), "could not request edge expiry step");
            advance();
            verifyAction("inactive");
            Input::setKeyState(GLFW_KEY_SPACE, true);
            Input::setKeyState(GLFW_KEY_SPACE, false);
            check(runtime.requestEmbeddedStep(), "could not sample short tap");
            advance();
            verifyAction("inactive");
            Input::setKeyState(GLFW_KEY_SPACE, true);
            check(runtime.requestEmbeddedStep(), "could not request pre-restart step");
            advance();
            verifyAction("pressed");
            const auto restartedActions = runtime.startProject(project.value());
            check(restartedActions.ok(), restartedActions.error());
            check(actionLua.executeScriptAsset(actionScript), "could not reload the action Lua probe");
            verifyAction("inactive");
            advance();
            verifyAction("inactive");
            const auto staleScene = runtime.liveHierarchy().at("sceneInstance").get<uint64_t>();
            for (int frame = 0; frame < 4; ++frame)
                check(runtime.advanceEmbeddedFrame(0.016f, false).ok(), "embedded project advance failed");

            auto& scene = runtime.getScene();
            auto& lua = runtime.getScriptEngine();
            const auto actor = scene.findEntityByKey("second-actor").getID();
            const auto spawned = scene.findEntityByKey("uuid-spawned").getID();
            const auto currentScene = runtime.liveHierarchy().at("sceneInstance").get<uint64_t>();
            const auto attachment = runtime.liveComponents(currentScene, actor).at("Script").at(0);
            check(attachment.at("canReload") == true, "project behaviour should be reloadable");
            const auto asset = attachment.at("scriptAsset").get<std::string>();
            const auto oldActor = lua.getBehaviourGeneration(actor, "");
            const auto oldSpawn = lua.getBehaviourGeneration(spawned, "");
            check(oldActor && oldSpawn, "expected two loaded instances of the project script");
            const fs::path scriptPath = moved / "Assets/Scripts/main.lua";
            const auto original = [&] {
                std::ifstream script(scriptPath, std::ios::binary);
                return std::string(std::istreambuf_iterator<char>(script), {});
            }();
            const auto save = [&](std::string_view source) {
                check(static_cast<bool>(std::ofstream(scriptPath, std::ios::binary) << source), "could not save script");
            };
            runtime.setEmbeddedPaused(true);
            save("return {");
            runtime.reloadEmbeddedBehaviour(currentScene, actor, "", asset);
            check(lua.getBehaviourGeneration(actor, "") == oldActor &&
                  lua.getBehaviourGeneration(spawned, "") == oldSpawn, "syntax failure retired a live instance");
            save("return {init=function() error('reload init failure') end}");
            runtime.reloadEmbeddedBehaviour(staleScene, actor, "", asset);
            check(lua.getBehaviourGeneration(actor, "") == oldActor, "stale scene request retired an instance");
            runtime.reloadEmbeddedBehaviour(currentScene, actor, "", asset);
            check(!lua.hasBehaviour(actor, "") && !lua.hasBehaviour(spawned, ""), "failed init installed an instance");
            runtime.setEmbeddedPaused(false);
            check(runtime.advanceEmbeddedFrame(0.016f, false).ok() && !lua.hasBehaviour(actor, ""),
                "failed init was retried through the old cached script");
            runtime.setEmbeddedPaused(true);
            save(R"(return {init=function(self)
                if Engine.findEntityByKey('second-actor') == self then
                    Engine.setTransformPosition(self, 456, 0)
                end
            end})");
            runtime.reloadEmbeddedBehaviour(currentScene, actor, "", asset);
            check(lua.getBehaviourGeneration(actor, "") > oldActor &&
                  lua.getBehaviourGeneration(spawned, "") > oldSpawn, "corrected save did not restart both instances");
            check(runtime.sceneTransforms().at("second-actor").at("position").at("x") == 456,
                "reload did not run init while paused");
            save(original);
        }
        host.reset();
        glfwTerminate();
        {
            sle::Runtime runtime(config);const auto init=runtime.init();check(init.ok(),init.error());
            const auto start=runtime.startProject(project.value());check(start.ok(),start.error());
            checkCollisionPair(runtime, true);
            const auto nearest = checkSampler(runtime, GL_NEAREST);
            checkRegion(runtime, {0, 0.5f, 0.5f, 1});
            sle::ScriptApiImpl api(runtime);
            const auto sprite = api.findEntityByKey("sprite");
            check(api.setSpriteTexture(sprite, textureRef), "script sprite texture assignment failed");
            check(nearest == checkSampler(runtime, GL_NEAREST), "script assignment changed the sprite's sampler");
            std::string prefabError;
            const auto spawned = api.instantiatePrefab("Prefabs/spawn.prefab.json", "filtered-prefab",
                Json::object(), {}, prefabError);
            check(spawned.id != 0, prefabError);
            check(nearest == checkSampler(runtime, GL_NEAREST, "filtered-prefab"), "runtime prefab lost the project sampler");
            textureMetadata["texture"]["filter"] = "linear";
            write(moved/"Assets/Textures/sprite.png.slemeta", textureMetadata);
            auto edited = sle::ProjectRuntime::open(moved/"game.sleproject");
            check(edited.ok(), edited.error());
            const auto restarted = runtime.startProject(edited.value());
            check(restarted.ok(), restarted.error());
            check(nearest != checkSampler(runtime, GL_LINEAR), "changed filter reused the old texture");
            textureMetadata.erase("texture");
            write(moved/"Assets/Textures/sprite.png.slemeta", textureMetadata);
            auto cleared = sle::ProjectRuntime::open(moved/"game.sleproject");
            check(cleared.ok(), cleared.error());
            const auto startedDefault = runtime.startProject(cleared.value());
            check(startedDefault.ok(), startedDefault.error());
            checkSampler(runtime, GL_NEAREST);
            checkRegion(runtime, {0, 0, 1, 1});
            const auto tagTestScript = (moved / "Assets/Scripts/main.lua").generic_string();
            auto& tagLua = runtime.getScriptEngine();
            check(tagLua.executeScriptAsset(tagTestScript), "could not load tag Lua probe");
            check(tagLua.callGlobalBoolFunction("verifyTags", 0, "startup"), "startup tag query failed");

            const auto descriptorPath = moved / "game.sleproject";
            std::ifstream descriptorInput(descriptorPath);
            auto renamedDescriptor = Json::parse(descriptorInput);
            descriptorInput.close();
            const auto previousSettings = renamedDescriptor["settings"];
            renamedDescriptor["settings"]["tags"][0]["name"] = "Renamed";
            write(descriptorPath, renamedDescriptor);
            auto renamed = sle::ProjectRuntime::open(descriptorPath);
            check(renamed.ok(), renamed.error());
            const auto renamedStart = runtime.startProject(renamed.value());
            check(renamedStart.ok(), renamedStart.error());
            check(tagLua.executeScriptAsset(tagTestScript), "could not reload tag Lua probe");
            check(tagLua.callGlobalBoolFunction("verifyTags", 0, "renamed"), "tag rename changed membership");

            renamedDescriptor["settings"].erase("tags");
            write(descriptorPath, renamedDescriptor);
            auto removed = sle::ProjectRuntime::open(descriptorPath);
            check(removed.ok(), removed.error());
            const auto removedStart = runtime.startProject(removed.value());
            check(removedStart.ok(), removedStart.error());
            check(tagLua.executeScriptAsset(tagTestScript), "could not reload tag Lua probe");
            check(tagLua.callGlobalBoolFunction("verifyTags", 0, "removed-definition"), "removed tag still resolved");

            renamedDescriptor["settings"] = previousSettings;
            write(descriptorPath, renamedDescriptor);
            const auto restoredStart = runtime.startProject(cleared.value());
            check(restoredStart.ok(), restoredStart.error());
            sle::input::Input::setKeyState(GLFW_KEY_W, true);
            check(runtime.runForFrames(8)==8,"bounded project run failed");
            check(tagLua.executeScriptAsset(tagTestScript), "could not reload tag Lua probe after scene replacement");
            check(tagLua.callGlobalBoolFunction("verifyActions", 0, "held"), "standalone source action did not remain held");
            sle::input::Input::setKeyState(GLFW_KEY_W, false);
            check(tagLua.callGlobalBoolFunction("verifyTags", 0, "switched"), "tag query after scene replacement failed");
            check(runtime.getCurrentSceneName()=="second","Lua deferred scene switch failed");
            check(runtime.sceneTransforms().contains("second-actor"),"editor game view retained startup scene keys after switch");
            check(runtime.getScene().findEntityByKey("spawned").valid() && runtime.getScene().findEntityByKey("uuid-spawned").valid(),"deferred project prefab did not instantiate");
            for(const auto* key : {"spawned","uuid-spawned"}) {
                const auto* script=runtime.getScene().getRegistry().getComponent<sle::components::ScriptComponent>(runtime.getScene().findEntityByKey(key));
                check(script && fs::path(script->scriptAsset).is_absolute() && fs::equivalent(script->scriptAsset,moved/"Assets/Scripts/main.lua"),"project prefab override script remains unresolved");
            }
            auto entity=runtime.getScene().findEntityByKey("second-actor");check(entity.valid(),"second scene entity absent");
            const auto* transform=runtime.getScene().getRegistry().getComponent<sle::components::TransformComponent>(entity);
            check(transform && transform->getPosition().x==123,"relocated deferred Lua module did not execute");
            runtime.getScene().destroyEntity(runtime.getScene().findEntityByKey("spawned"));
            check(tagLua.callGlobalBoolFunction("verifyTags", 0, "destroyed"), "destroyed entity still matched its tag");
            sle::input::Input::setKeyState(GLFW_KEY_ESCAPE, true);
            check(runtime.runForFrames(1) == 0, "unbound Escape no longer exits standalone");
            sle::input::Input::setKeyState(GLFW_KEY_ESCAPE, false);
        }
        fs::current_path(cwd);fs::remove_all(base);std::cout<<"PROJECT_PLAY_PASS\n";return 0;
    } catch(const std::exception& error) {fs::current_path(cwd);fs::remove_all(base);std::cerr<<error.what()<<'\n';return 1;}
}
