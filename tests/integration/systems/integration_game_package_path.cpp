#include <sle/engine/GameConfig.hpp>
#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

// Game packages (.slepkg) mounted through config.json: multiple packages share one
// UUID/scene namespace, the first declared main scene starts, and invalid archives fail.
namespace {
namespace fs=std::filesystem;
using Json=nlohmann::json;
using Entry=std::pair<std::string,std::string>;
void check(bool value,const std::string& message) { if(!value) throw std::runtime_error(message); }
std::uint32_t crc32(const std::string& value) {
    std::uint32_t result=~0U;
    for (unsigned char c : value) { result^=c; for(int i=0;i<8;++i) result=(result>>1)^(0xedb88320U & (0U-(result&1U))); }
    return ~result;
}
void zip(const fs::path& path,const std::vector<Entry>& entries) {
    std::ofstream file(path,std::ios::binary);
    auto u16=[&](unsigned v) { file.put(static_cast<char>(v)); file.put(static_cast<char>(v>>8)); };
    auto u32=[&](unsigned v) { u16(v&65535); u16(v>>16); };
    std::vector<unsigned> offsets;
    for (const auto& [name,data] : entries) {
        offsets.push_back(static_cast<unsigned>(file.tellp()));
        u32(0x04034b50); u16(20); u16(0); u16(0); u16(0); u16(0); u32(crc32(data));
        u32(static_cast<unsigned>(data.size())); u32(static_cast<unsigned>(data.size())); u16(static_cast<unsigned>(name.size())); u16(0);
        file<<name<<data;
    }
    const auto directory=static_cast<unsigned>(file.tellp());
    for (std::size_t i=0;i<entries.size();++i) {
        const auto& [name,data]=entries[i];
        u32(0x02014b50); u16(20); u16(20); u16(0); u16(0); u16(0); u16(0); u32(crc32(data));
        u32(static_cast<unsigned>(data.size())); u32(static_cast<unsigned>(data.size())); u16(static_cast<unsigned>(name.size()));
        u16(0);u16(0);u16(0);u16(0);u32(0);u32(offsets[i]);file<<name;
    }
    const auto end=static_cast<unsigned>(file.tellp());
    u32(0x06054b50);u16(0);u16(0);u16(static_cast<unsigned>(entries.size()));u16(static_cast<unsigned>(entries.size()));
    u32(end-directory);u32(directory);u16(0);
}
const std::string first="11111111-1111-4111-8111-111111111111",second="22222222-2222-4222-8222-222222222222";
const std::string script="33333333-3333-4333-8333-333333333333",prefab="44444444-4444-4444-8444-444444444444";
const std::string module="55555555-5555-4555-8555-555555555555";
Json manifest(const std::string& id,const Json& mainScene) {
    return {{"format","sle-package"},{"version",1},{"id",id},{"name","Package "+id.substr(0,1)},{"mainScene",mainScene}};
}
std::string meta(const std::string& id) { return Json{{"format","sle-asset"},{"version",1},{"id",id}}.dump(); }
std::string scene(const std::string& key) {
    return Json{{"format","sle-project-scene"},{"version",1},{"assets",Json::array()},{"document",{{"key",key},{"name",key},
        {"entities",Json::array({{{"key",key+"-actor"},{"components",{{"Transform",Json::object()},{"Script",{{"scriptAsset","project-asset://"+script}}}}}}})}}}}.dump();
}
// Base package: main scene, script and prefab. Its script needs a module from the level package.
std::vector<Entry> basePackage(const Json& mainScene) {
    return {
        {"manifest.json",manifest("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",mainScene).dump()},
        {"assets/Scenes/first.scene.json",scene("first")},{"assets/Scenes/first.scene.json.slemeta",meta(first)},
        {"assets/Scripts/main.lua",R"(local value=require('levels.value')
return { update=function(self,dt)
 if Engine.findEntityByKey('first-actor') then Engine.switchScene('second') end
 if value ~= 7 then error('cross-package module missing') end
end })"},{"assets/Scripts/main.lua.slemeta",meta(script)},
        {"assets/Prefabs/spawn.prefab.json",Json{{"format","sle-project-prefab"},{"version",1},{"assets",Json::array()},
            {"document",{{"root","root"},{"entities",Json::array({{{"key","root"},{"components",{{"Transform",Json::object()}}}}})}}}}.dump()},
        {"assets/Prefabs/spawn.prefab.json.slemeta",meta(prefab)},
    };
}
// Level package: a second scene (its own mainScene is ignored: base is listed first) and a Lua module.
std::vector<Entry> levelPackage() {
    return {
        {"manifest.json",manifest("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",second).dump()},
        {"assets/Levels/second.scene.json",scene("second")},{"assets/Levels/second.scene.json.slemeta",meta(second)},
        {"assets/Scripts/levels/value.lua","return 7"},{"assets/Scripts/levels/value.lua.slemeta",meta(module)},
    };
}
void writeConfig(const fs::path& path,const std::vector<std::string>& packages,const Json& extra=Json::object()) {
    Json config={{"window",{{"title","Packaged"},{"width",640},{"height",360},{"vsync",false},{"mode","Windowed"}}},{"targetFPS",30},{"packages",packages}};
    config.update(extra);
    std::ofstream(path)<<config.dump(2);
}
void fixture(const fs::path& directory,bool withMainScene) {
    fs::create_directories(directory);
    zip(directory/"game.slepkg",basePackage(withMainScene?Json(first):Json()));
    if (withMainScene) zip(directory/"levels.slepkg",levelPackage());
    writeConfig(directory/"config.json",withMainScene?std::vector<std::string>{"game.slepkg","levels.slepkg"}:std::vector<std::string>{"game.slepkg"});
}
bool opens(const fs::path& config) {
    const auto loaded=sle::GameConfig::load(config);
    return loaded.ok() && sle::ProjectRuntime::openPackages(loaded.value().packages,loaded.value().engine).ok();
}
}

int main(int argc,char** argv) {
    // Fixture mode for the engine_app launch script.
    if (argc==3 || argc==4) {
        try { fixture(fs::u8path(argv[2]),argc==3); return 0; }
        catch (const std::exception& error) { std::cerr<<error.what()<<'\n'; return 1; }
    }
    const auto root=fs::temp_directory_path()/("sle-game-package-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    const auto cwd=fs::current_path();
    try {
        fixture(root/"build",true);
        const auto elsewhere=root/"elsewhere";fs::create_directories(elsewhere);fs::current_path(elsewhere);
        const auto loaded=sle::GameConfig::load(root/"build/config.json");check(loaded.ok(),loaded.ok()?"":loaded.error());
        const auto& config=loaded.value();
        check(config.packages.size()==2 && config.packages[0].is_absolute() && config.scenes.empty(),"packages not resolved beside config");
        check(config.engine.width==640 && config.engine.targetFPS==30 && config.engine.title=="Packaged","engine settings lost");
        auto opened=sle::ProjectRuntime::openPackages(config.packages,config.engine);check(opened.ok(),opened.ok()?"":opened.error());
        const auto& project=opened.value();
        check(project.startupScene()==first,"first listed main scene must start");
        check(project.assetRoots().size()==2,"both packages mounted");
        check(project.engineConfig().height==360,"config not applied");
        const auto& level=project.sceneDocument(second);
        const auto resolved=level.at("entities")[0]["components"]["Script"]["scriptAsset"].get<std::string>();
        check(fs::equivalent(resolved,project.assetRoots()[0]/"Scripts/main.lua"),"cross-package UUID reference unresolved");
        check(fs::equivalent(project.resolveAsset("Scripts/levels/value.lua"),project.assetRoots()[1]/"Scripts/levels/value.lua"),"relative path in later package unresolved");
        check(fs::is_regular_file(project.resolveAsset("project-asset://"+prefab)),"derived prefab missing");
        for (const auto& bad : {std::string("../config.json"),(root/"build/config.json").generic_string()}) {
            bool rejected=false;try { project.resolveAsset(bad); } catch(const std::exception&) { rejected=true; }
            check(rejected,"escaping package path accepted");
        }
        {
            sle::Runtime runtime(project.engineConfig());project.registerScenes(runtime);
            check(runtime.hasScene("first") && runtime.hasScene("second") && runtime.hasScene(second),"package scene aliases missing");
            check(runtime.loadScene(first).ok() && runtime.loadScene("second").ok(),"package scenes failed to load");
        }
        // Remounting reuses the verified shared cache; an extra file there is never indexed.
        const auto cache=project.assetRoots()[0];
        {
            auto again=sle::ProjectRuntime::openPackages(config.packages,config.engine);check(again.ok(),"remount failed");
            check(fs::equivalent(again.value().assetRoots()[0],cache),"shared cache not reused");
        }
        std::ofstream(cache/"Scenes/injected.scene.json")<<scene("injected");
        std::ofstream(cache/"Scenes/injected.scene.json.slemeta")<<meta("66666666-6666-4666-8666-666666666666");
        fs::path privateRoot;
        {
            auto tampered=sle::ProjectRuntime::openPackages(config.packages,config.engine);check(tampered.ok(),"private fallback failed");
            privateRoot=tampered.value().assetRoots()[0];
            check(!fs::equivalent(privateRoot,cache) && !tampered.value().findSceneDocument("injected"),"tampered cache was used");
        }
        check(!fs::exists(privateRoot),"private extraction not removed on release");
        fs::remove(cache/"Scenes/injected.scene.json");fs::remove(cache/"Scenes/injected.scene.json.slemeta");

        // Config contract.
        const auto bad=root/"bad";fs::create_directories(bad);
        fs::copy_file(root/"build/game.slepkg",bad/"game.slepkg");
        writeConfig(bad/"config.json",{"game.slepkg"},{{"startScene","first"}});
        check(!sle::GameConfig::load(bad/"config.json").ok(),"packages with startScene accepted");
        writeConfig(bad/"config.json",{"missing.slepkg"});
        check(!sle::GameConfig::load(bad/"config.json").ok(),"missing package accepted");
        writeConfig(bad/"config.json",{});
        check(!sle::GameConfig::load(bad/"config.json").ok(),"empty package list accepted");
        writeConfig(bad/"config.json",{"game.slepkg"},{{"targetFPS",-1}});
        check(!sle::GameConfig::load(bad/"config.json").ok(),"negative FPS accepted");
        writeConfig(bad/"config.json",{"game.slepkg","game.slepkg"});
        check(!opens(bad/"config.json"),"duplicate UUIDs across packages accepted");

        // Package contract: each archive is rejected before anything is mounted.
        auto rejects=[&](const std::vector<Entry>& entries,const std::string& message) {
            zip(bad/"game.slepkg",entries);writeConfig(bad/"config.json",{"game.slepkg"});
            check(!opens(bad/"config.json"),message);
        };
        rejects(basePackage(Json()),"package set without a main scene accepted");
        auto entries=basePackage(first);entries.emplace_back("assets/../escape.lua","return 1");
        rejects(entries,"traversal entry accepted");
        entries=basePackage(first);entries.emplace_back("assets/scripts/MAIN.lua","return 1");
        rejects(entries,"case-insensitive duplicate accepted");
        entries=basePackage(first);entries.emplace_back("assets/Scripts/main.lua/inner.lua","return 1");
        rejects(entries,"file/folder collision accepted");
        entries=basePackage(first);entries.emplace_back("assets/CON.txt","x");
        rejects(entries,"reserved device name accepted");
        entries=basePackage(first);entries.emplace_back("loose.txt","x");
        rejects(entries,"entry outside assets accepted");
        { entries=basePackage(first);auto changed=Json::parse(entries[0].second);changed["version"]=2;entries[0].second=changed.dump(); }
        rejects(entries,"future package version accepted");
        { entries=basePackage(first);auto changed=Json::parse(entries[0].second);changed["extra"]=true;entries[0].second=changed.dump(); }
        rejects(entries,"unknown manifest field accepted");
        rejects({{"assets/Scenes/first.scene.json",scene("first")}},"package without manifest accepted");
        zip(bad/"game.slepkg",basePackage(first));
        { std::fstream file(bad/"game.slepkg",std::ios::in|std::ios::out|std::ios::binary);file.seekp(120);file.put('#'); }
        check(!opens(bad/"config.json"),"corrupted package accepted");

        fs::current_path(cwd);fs::remove_all(root);
        std::cout<<"GAME_PACKAGE_PASS\n";
        return 0;
    } catch (const std::exception& error) {
        fs::current_path(cwd);std::error_code ignored;fs::remove_all(root,ignored);
        std::cerr<<error.what()<<'\n';
        return 1;
    }
}
