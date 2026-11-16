#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <chrono>
#include <stdexcept>

namespace fs=std::filesystem;
using Json=nlohmann::json;
void check(bool value,const char* message) { if(!value) throw std::runtime_error(message); }
void write(const fs::path& file,const Json& value) { fs::create_directories(file.parent_path());std::ofstream(file)<<value.dump(2); }
const std::string first="11111111-1111-4111-8111-111111111111";
const std::string second="22222222-2222-4222-8222-222222222222";
const std::string script="33333333-3333-4333-8333-333333333333";
void meta(const fs::path& path,const std::string& id) { write(path.string()+".slemeta",{{"format","sle-asset"},{"version",1},{"id",id}}); }
int main() {
    const auto root=fs::temp_directory_path()/("sle-project-runtime-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    const auto cwd=fs::current_path();
    try {
        const auto original=root/"original";
        Json descriptor={{"format","sle-project"},{"version",1},{"id",first},{"name","Test Game"},{"assetRoot","Assets"},{"startupScene",first},
            {"game",{{"window",{{"title","Project Window"},{"width",640},{"height",360},{"vsync",false},{"mode","Windowed"}}},{"targetFPS",30}}}};
        write(original/"game.sleproject",descriptor);
        auto scene=[](const std::string& key) {return Json{{"format","sle-project-scene"},{"version",1},{"assets",Json::array()},
            {"document",{{"key",key},{"name",key},{"entities",Json::array({{{"key","actor"},{"components",{{"Script",{{"scriptAsset","project-asset://"+script}}}}}}})}}}};};
        write(original/"Assets/Scenes/first.scene.json",scene("first"));meta(original/"Assets/Scenes/first.scene.json",first);
        write(original/"Assets/Scenes/second.scene.json",scene("second"));meta(original/"Assets/Scenes/second.scene.json",second);
        fs::create_directories(original/"Assets/Scripts");std::ofstream(original/"Assets/Scripts/main.lua")<<"return {}";
        meta(original/"Assets/Scripts/main.lua",script);
        const auto moved=root/"relocated";fs::rename(original,moved);fs::current_path(root);
        auto loaded=sle::ProjectRuntime::open(moved/"game.sleproject");check(loaded.ok(),loaded.error().c_str());
        const auto& project=loaded.value();
        check(project.engineConfig().width==640 && project.engineConfig().height==360 && project.engineConfig().targetFPS==30,"game settings lost");
        check(project.startupScene()==first,"startup UUID lost");
        const auto document=project.sceneDocument(first);
        check(project.resolveComponentAssets({{"variables",{{"texture","leave-me"}}}})["variables"]["texture"]=="leave-me","script data interpreted as asset");
        check(fs::equivalent(document.at("entities")[0]["components"]["Script"]["scriptAsset"].get<std::string>(),moved/"Assets/Scripts/main.lua"),"relocated script unresolved");
        sle::Runtime runtime(project.engineConfig());project.registerScenes(runtime);
        check(runtime.hasScene("second") && runtime.hasScene(second),"deferred scene aliases missing");
        check(runtime.loadScene(first).ok(),"startup scene failed");
        check(runtime.loadScene("second").ok(),"second scene failed");
        check(fs::equivalent(project.resolveAsset("Scripts/main.lua"),moved/"Assets/Scripts/main.lua"),"relative dynamic asset unresolved");
        for (const auto& bad : {std::string("../game.sleproject"),(moved/"game.sleproject").generic_string(),std::string("project-asset://bad")}) {
            bool rejected=false;try { project.resolveAsset(bad); } catch(const std::exception&) { rejected=true; }
            check(rejected,"escaping or malformed dynamic path accepted");
        }
        for (const auto& dimensions : {Json(0),Json(16385),Json("640"),Json(true)}) {
            auto invalid=descriptor;invalid["game"]["window"]["width"]=dimensions;write(moved/"game.sleproject",invalid);
            check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"invalid dimensions accepted");
        }
        auto invalid=descriptor;invalid["startupScene"]=nullptr;write(moved/"game.sleproject",invalid);
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"missing startup accepted");
        invalid=descriptor;invalid["assetRoot"]="../Assets";write(moved/"game.sleproject",invalid);
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"escaping root accepted");
        invalid=descriptor;invalid["game"]["window"]["mode"]="Other";write(moved/"game.sleproject",invalid);
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"invalid window mode accepted");
        invalid=descriptor;invalid["game"]["targetFPS"]=-1;write(moved/"game.sleproject",invalid);
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"negative FPS accepted");
        write(moved/"game.sleproject",descriptor);
        std::ofstream(moved/"Assets/Scripts/main.lua.slemeta")<<"bad json";
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"malformed metadata accepted");
        meta(moved/"Assets/Scripts/main.lua",script);
        meta(moved/"Assets/Scripts/duplicate.lua",script);std::ofstream(moved/"Assets/Scripts/duplicate.lua")<<"return {}";
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"duplicate UUID accepted");
        fs::remove(moved/"Assets/Scripts/duplicate.lua");fs::remove(moved/"Assets/Scripts/duplicate.lua.slemeta");
        fs::remove(moved/"Assets/Scripts/main.lua");
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"missing deferred asset accepted");
        fs::current_path(cwd);fs::remove_all(root);std::cout<<"PROJECT_RUNTIME_PASS\n";return 0;
    } catch(const std::exception& error) { fs::current_path(cwd);fs::remove_all(root);std::cerr<<error.what()<<'\n';return 1; }
}
