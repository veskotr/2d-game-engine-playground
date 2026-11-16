#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <chrono>
#include <stdexcept>
namespace fs=std::filesystem;
using Json=nlohmann::json;
void check(bool value,const std::string& message) { if(!value) throw std::runtime_error(message); }
void write(const fs::path& path,const Json& value) { fs::create_directories(path.parent_path());std::ofstream(path)<<value.dump(); }
const std::string a="11111111-1111-4111-8111-111111111111",b="22222222-2222-4222-8222-222222222222",s="33333333-3333-4333-8333-333333333333";
void meta(const fs::path& path,const std::string& id) { write(path.string()+".slemeta",{{"format","sle-asset"},{"version",1},{"id",id}}); }
int main() {
    const auto cwd=fs::current_path();const auto base=fs::temp_directory_path()/("sle-project-play-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    try {
        const auto root=base/"game";
        write(root/"game.sleproject",{{"format","sle-project"},{"version",1},{"id",a},{"name","Play"},{"assetRoot","Assets"},{"startupScene",a},
            {"game",{{"window",{{"title","Play project"},{"width",640},{"height",360},{"mode","Windowed"},{"vsync",false}}},{"targetFPS",30}}}});
        auto scene=[](const std::string& key,Json entities) { return Json{{"format","sle-project-scene"},{"version",1},{"assets",Json::array()}, {"document",{{"key",key},{"name",key},{"entities",entities}}}}; };
        write(root/"Assets/Scenes/a.scene.json",scene("first",Json::array({{{"key","first-actor"},{"components",{{"Script",{{"scriptAsset","project-asset://"+s}}}}}}})));meta(root/"Assets/Scenes/a.scene.json",a);
        write(root/"Assets/Scenes/b.scene.json",scene("second",Json::array({{{"key","second-actor"},{"components",{{"Transform",Json::object()},{"Script",{{"scriptAsset","project-asset://"+s}}}}}}})));meta(root/"Assets/Scenes/b.scene.json",b);
        write(root/"Assets/Prefabs/spawn.prefab.json",{{"format","sle-project-prefab"},{"version",1},{"assets",Json::array()},
            {"document",{{"root","root"},{"entities",Json::array({{{"key","root"},{"components",{{"Transform",Json::object()}}}}})}}}});
        meta(root/"Assets/Prefabs/spawn.prefab.json","44444444-4444-4444-8444-444444444444");
        fs::create_directories(root/"Assets/Scripts/modules");std::ofstream(root/"Assets/Scripts/modules/value.lua")<<"return 123";
        std::ofstream(root/"Assets/Scripts/main.lua")<<R"(local value=require('modules.value')
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
        const auto moved=base/"moved";fs::rename(root,moved);
        auto project=sle::ProjectRuntime::open(moved/"game.sleproject");check(project.ok(),project.error());
        auto config=project.value().engineConfig();config.engineAssetRoot=(cwd/"assets").generic_string();
        fs::current_path(base);
        {
            sle::Runtime runtime(config);const auto init=runtime.init();check(init.ok(),init.error());
            const auto start=runtime.startProject(project.value());check(start.ok(),start.error());
            check(runtime.runForFrames(8)==8,"bounded project run failed");
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
        }
        fs::current_path(cwd);fs::remove_all(base);std::cout<<"PROJECT_PLAY_PASS\n";return 0;
    } catch(const std::exception& error) {fs::current_path(cwd);fs::remove_all(base);std::cerr<<error.what()<<'\n';return 1;}
}
