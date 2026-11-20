#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/renderer/Texture.hpp>
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
        const std::string prefabId="44444444-4444-4444-8444-444444444444";
        const auto prefabPath=original/"Assets/Prefabs/actor.prefab.json";
        const auto prefab=Json::parse(R"({"root":"root","entities":[
            {"key":"root","components":{"Transform":{"position":[1,2]}}},
            {"key":"child","parent":"root","components":{"Animator":{"targetEntities":{"root":1,"self":"child"}}}}
        ]})");
        write(prefabPath,{{"format","sle-project-prefab"},{"version",1},{"assets",Json::array()},{"document",prefab}});meta(prefabPath,prefabId);
        auto linked=scene("second");
        linked["document"]["prefabs"]=Json::parse(R"([
            {"file":"placeholder","key":"one","overrides":{"root":{"Transform":{"position":[100,50]}}}},
            {"file":"placeholder","key":"two"}
        ])");
        for(auto& instance:linked["document"]["prefabs"]) instance["file"]="project-asset://"+prefabId;
        write(original/"Assets/Scenes/second.scene.json",linked);
        const auto moved=root/"relocated";fs::rename(original,moved);fs::current_path(root);
        auto loaded=sle::ProjectRuntime::open(moved/"game.sleproject");check(loaded.ok(),loaded.error().c_str());
        const auto texture = moved/"Assets/inherited.png";
        std::ofstream{texture} << "fixture";
        using Filter = sle::renderer::TextureFilter;
        check(loaded.value().textureFilter(texture) == Filter::Default, "legacy sampler changed");
        for (const auto& [style, expected] : {std::pair{"pixelArt", Filter::Nearest}, {"smooth", Filter::Linear}}) {
            descriptor["settings"]["artStyle"] = style;
            write(moved/"game.sleproject", descriptor);
            const auto styled = sle::ProjectRuntime::open(moved/"game.sleproject");
            check(styled.ok(), styled.error().c_str());
            check(styled.value().textureFilter(texture) == expected, "project sampler was not inherited");
            check(styled.value().textureFilter(moved/"outside.png") == Filter::Default, "external texture inherited sampler");
        }
        for (const auto& invalid : {Json(nullptr), Json(1), Json("invalid")}) {
            descriptor["settings"]["artStyle"] = invalid;
            write(moved/"game.sleproject", descriptor);
            check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "invalid art style accepted");
        }
        descriptor["settings"] = Json::parse(R"({"collisionLayers":{"labels":["Player"],"blockedPairs":[[15,0],[3,3]]}})");
        write(moved/"game.sleproject", descriptor);
        const auto layered = sle::ProjectRuntime::open(moved/"game.sleproject");
        check(layered.ok(), layered.error().c_str());
        const auto& matrix = layered.value().collisionMatrix();
        check(matrix[0] == (1u << 15) && matrix[15] == 1 && matrix[3] == (1u << 3),
            "collision matrix did not preserve symmetric bit slots");
        descriptor["settings"]["collisionLayers"]["blockedPairs"] = Json::parse("[[-1,0]]");
        write(moved/"game.sleproject", descriptor);
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "invalid collision slot accepted");
        descriptor["settings"] = Json::parse(R"({"sortingLayers":[{"value":-3,"label":"Backdrop"},{"value":10,"label":"HUD"}]})");
        write(moved/"game.sleproject", descriptor);
        const auto sorted = sle::ProjectRuntime::open(moved/"game.sleproject");
        check(sorted.ok(), sorted.error().c_str());
        for (const auto& invalid : {R"([{"value":2,"label":"A"},{"value":2,"label":"B"}])",
                                    R"([{"value":2147483648,"label":"A"}])",
                                    R"([{"value":"2","label":"A"}])"}) {
            descriptor["settings"]["sortingLayers"] = Json::parse(invalid);
            write(moved/"game.sleproject", descriptor);
            check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "invalid sorting layers accepted");
        }
        descriptor["settings"] = Json::parse(R"({"tags":[{"id":"tag-player","name":"Player"}]})");
        write(moved/"game.sleproject", descriptor);
        check(sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "valid tag definition rejected");
        for (const auto* invalid : {R"([{"id":"a","name":"Player"},{"id":"b","name":"Player"}])",
                                    R"([{"id":"a","name":"Player"},{"id":"a","name":"Enemy"}])",
                                    R"([{"id":"","name":"Player"}])",
                                    R"([{"id":"a","name":""}])"}) {
            descriptor["settings"]["tags"] = Json::parse(invalid);
            write(moved/"game.sleproject", descriptor);
            check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "invalid tag definitions accepted");
        }
        descriptor["settings"] = Json::parse(R"({"inputActions":[
            {"name":"Jump","bindings":[{"kind":"key","key":32},{"kind":"key","key":87}]},
            {"name":"Confirm","bindings":[{"kind":"key","key":335}]},
            {"name":"Unbound","bindings":[]}
        ]})");
        write(moved/"game.sleproject", descriptor);
        check(sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "valid input actions rejected");
        for (const auto* invalid : {R"([{"name":"Jump","bindings":[]},{"name":"Jump","bindings":[]}])",
                                    R"([{"name":"","bindings":[]}])",
                                    R"([{"name":"Jump","bindings":[{"kind":"mouse","key":32}]}])",
                                    R"([{"name":"Jump","bindings":[{"kind":"key","key":2147483648}]}])",
                                    R"([{"name":"Jump","bindings":[{"kind":"key","key":"32"}]}])",
                                    R"([{"name":"Jump","bindings":[{"kind":"key","key":32},{"kind":"key","key":32}]}])"}) {
            descriptor["settings"]["inputActions"] = Json::parse(invalid);
            write(moved/"game.sleproject", descriptor);
            check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "invalid input actions accepted");
        }
        descriptor.erase("settings");
        write(moved/"game.sleproject", descriptor);
        const auto& project=loaded.value();
        check(project.engineConfig().width==640 && project.engineConfig().height==360 && project.engineConfig().targetFPS==30,"game settings lost");
        check(project.startupScene()==first,"startup UUID lost");
        const auto document=project.sceneDocument(first);
        const auto linkedDocument=project.sceneDocument(second);
        check(linkedDocument["entities"].size()==5,"linked subtree expansion lost entities");
        check(linkedDocument["entities"][2]["parent"]=="one","first subtree parent lost");
        check(linkedDocument["entities"][4]["components"]["Animator"]["targetEntities"]["root"]=="two","instance references not isolated");
        check(linkedDocument["entities"][1]["components"]["Transform"]["position"]["x"]==100,"placement override lost");
        check(project.resolveComponentAssets({{"variables",{{"texture","leave-me"}}}})["variables"]["texture"]=="leave-me","script data interpreted as asset");
        check(fs::equivalent(document.at("entities")[0]["components"]["Script"]["scriptAsset"].get<std::string>(),moved/"Assets/Scripts/main.lua"),"relocated script unresolved");
        sle::Runtime runtime(project.engineConfig());project.registerScenes(runtime);
        check(runtime.hasScene("second") && runtime.hasScene(second),"deferred scene aliases missing");
        check(runtime.loadScene(first).ok(),"startup scene failed");
        check(runtime.loadScene("second").ok(),"second scene failed");
        check(fs::equivalent(project.resolveAsset("Scripts/main.lua"),moved/"Assets/Scripts/main.lua"),"relative dynamic asset unresolved");
        const auto renamedPrefab=moved/"Assets/Prefabs/renamed.prefab.json";
        fs::rename(moved/"Assets/Prefabs/actor.prefab.json",renamedPrefab);
        fs::rename(moved/"Assets/Prefabs/actor.prefab.json.slemeta",fs::path(renamedPrefab.string()+".slemeta"));
        auto changedPrefab=prefab;changedPrefab["entities"][0]["components"]["Transform"]["position"]={7,8};
        write(renamedPrefab,{{"format","sle-project-prefab"},{"version",1},{"assets",Json::array()},{"document",changedPrefab}});
        auto refreshed=sle::ProjectRuntime::open(moved/"game.sleproject");check(refreshed.ok(),refreshed.error().c_str());
        const auto refreshedScene=refreshed.value().sceneDocument(second);
        check(refreshedScene["entities"][1]["components"]["Transform"]["position"]["x"]==100,"explicit placement changed after prefab update");
        check(refreshedScene["entities"][3]["components"]["Transform"]["position"]["x"]==7,"unoverridden prefab did not refresh after rename");
        auto invalidLink=linked;invalidLink["document"]["prefabs"][0]["overrides"]={{"missing",Json::object()}};
        write(moved/"Assets/Scenes/second.scene.json",invalidLink);
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"unknown override target accepted");
        write(moved/"Assets/Scenes/second.scene.json",linked);
        fs::rename(renamedPrefab,renamedPrefab.string()+".missing");
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(),"missing linked prefab accepted");
        fs::rename(renamedPrefab.string()+".missing",renamedPrefab);
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
        const auto image = moved/"Assets/sprite.png";
        const auto imageMeta = moved/"Assets/sprite.png.slemeta";
        std::ofstream(image) << "fixture";
        const std::string imageId = "55555555-5555-4555-8555-555555555555";
        meta(image, imageId);
        auto defaultFilter = sle::ProjectRuntime::open(moved/"game.sleproject");
        check(defaultFilter.ok(), defaultFilter.error().c_str());
        check(defaultFilter.value().textureFilter(image) == sle::renderer::TextureFilter::Default,
            "identity-only texture did not use default filtering");
        Json imageMetadata = {{"format","sle-asset"},{"version",1},{"id",imageId}};
        for (const auto* filter : {"nearest", "linear"}) {
            imageMetadata["texture"] = {{"filter",filter}};
            write(imageMeta, imageMetadata);
            auto filtered = sle::ProjectRuntime::open(moved/"game.sleproject");
            check(filtered.ok(), filtered.error().c_str());
            const auto indexedImage = fs::path(filtered.value().resolveAsset("project-asset://" + imageId));
            check(fs::equivalent(image, indexedImage), "indexed texture resolved to a different file");
            const auto expectedLookup = std::string(filter) == "nearest"
                ? sle::renderer::TextureFilter::Nearest : sle::renderer::TextureFilter::Linear;
            check(filtered.value().textureFilter(indexedImage) == expectedLookup, "indexed path lost texture filter");
            check(filtered.value().textureFilter(image) == expectedLookup, "texture path alias lost filter");
            check(sle::ProjectRuntime::previewTextureFilter(filtered.value().assetRoot(), image) == expectedLookup,
                "preview texture lookup lost filter");
        }
        imageMetadata["texture"]["filter"] = "invalid";
        write(imageMeta, imageMetadata);
        const auto rejected = sle::ProjectRuntime::open(moved/"game.sleproject");
        check(!rejected.ok() && rejected.error().find(imageMeta.filename().generic_string()) != std::string::npos,
            "invalid texture filter did not identify the sidecar");
        imageMetadata["texture"]["filter"] = "nearest";
        std::ofstream(imageMeta) << imageMetadata.dump() << " trailing data";
        check(!sle::ProjectRuntime::open(moved/"game.sleproject").ok(), "trailing asset metadata content accepted");
        meta(image, imageId);
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
