#include <sle/engine/PrefabPackage.hpp>
#include <sle/engine/PrefabLoader.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <cstdint>
#include <vector>
#include <chrono>

namespace {
using Json=nlohmann::json;
using Entry=std::pair<std::string,std::string>;
std::uint32_t crc32(const std::string& value) {
    std::uint32_t result=~0U;
    for (unsigned char c : value) { result^=c; for(int i=0;i<8;++i) result=(result>>1)^(0xedb88320U & (0U-(result&1U))); }
    return ~result;
}
void zip(const std::filesystem::path& path, const std::vector<Entry>& entries, unsigned method=0) {
    std::ofstream file(path,std::ios::binary);
    auto u16=[&](unsigned v) { file.put(static_cast<char>(v)); file.put(static_cast<char>(v>>8)); };
    auto u32=[&](unsigned v) { u16(v&65535); u16(v>>16); };
    std::vector<unsigned> offsets;
    for (const auto& [name,data] : entries) {
        offsets.push_back(static_cast<unsigned>(file.tellp()));
        u32(0x04034b50); u16(20); u16(0); u16(method); u16(0); u16(0); u32(crc32(data));
        u32(static_cast<unsigned>(data.size())); u32(static_cast<unsigned>(data.size())); u16(static_cast<unsigned>(name.size())); u16(0);
        file<<name<<data;
    }
    const auto directory=static_cast<unsigned>(file.tellp());
    for (std::size_t i=0;i<entries.size();++i) {
        const auto& [name,data]=entries[i];
        u32(0x02014b50); u16(20); u16(20); u16(0); u16(method); u16(0); u16(0); u32(crc32(data));
        u32(static_cast<unsigned>(data.size())); u32(static_cast<unsigned>(data.size())); u16(static_cast<unsigned>(name.size()));
        u16(0);u16(0);u16(0);u16(0);u32(0);u32(offsets[i]);file<<name;
    }
    const auto end=static_cast<unsigned>(file.tellp());
    u32(0x06054b50);u16(0);u16(0);u16(static_cast<unsigned>(entries.size()));u16(static_cast<unsigned>(entries.size()));
    u32(end-directory);u32(directory);u16(0);
}
}
int main(int argc,char** argv) {
    if (argc==2 && std::filesystem::path(argv[1]).extension()==".slescene") {
        const auto result=sle::PrefabPackage::readScene(argv[1]);
        if (!result.ok()) { std::cerr << result.error(); return 1; }
        try { sle::SceneAuthoring::canonicalScene(result.value()); }
        catch (const std::exception& error) { std::cerr << error.what(); return 1; }
        std::cout << "EDITOR_SCENE_PACKAGE_ENGINE_READ_PASS\n";
        return 0;
    }
    if (argc==2) {
        const auto result=sle::PrefabPackage::read(argv[1]);
        if (!result.ok()) { std::cerr<<result.error(); return 1; }
        std::cout<<"EDITOR_PACKAGE_ENGINE_READ_PASS\n";
        return 0;
    }
    const auto folder=std::filesystem::temp_directory_path()/("sle-package-regression-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    std::filesystem::create_directories(folder);
    const auto path=folder/"actor.sleprefab";
    const std::string id(64,'a');
    Json manifest={{"format","sle-prefab"},{"version",1},{"assets",Json::array({{{"id",id},{"path","assets/"+id+".lua"},{"name","actor.lua"}}})}};
    Json document={{"root","actor"},{"entities",Json::array({{{"key","actor"},{"components",{{"Transform",Json::object()},{"Script",{{"scriptAsset","asset://"+id}}}}}}})}};
    document["entities"][0]["name"]="asset://literal-name";
    const std::string jsonId(64,'b');
    manifest["assets"].push_back({{"id",jsonId},{"path","assets/"+jsonId+".json"},{"name","definition.json"}});
    std::vector<Entry> entries={{"manifest.json",manifest.dump()},{"entity.json",document.dump()},{"assets/"+id+".lua","return {}"}};
    entries.emplace_back("assets/"+jsonId+".json",Json({{"scriptAsset","asset://"+id},{"initialState","asset://literal-state"}}).dump());
    zip(path,entries);
    const auto moved=folder/"moved.sleprefab";
    std::filesystem::rename(path,moved);
    const auto loaded=sle::PrefabPackage::read(moved.string());
    if (!loaded.ok()) { std::cerr<<loaded.error(); return 1; }
    if (loaded.value()["entities"][0]["name"]!="asset://literal-name") return 1;
    const auto script=loaded.value()["entities"][0]["components"]["Script"]["scriptAsset"].get<std::string>();
    if (!std::filesystem::exists(script)) return 1;
    const auto jsonFile=std::filesystem::path(script).parent_path()/(jsonId+".json");
    Json definition; { std::ifstream file(jsonFile); file>>definition; }
    if (definition["scriptAsset"]!=script || definition["initialState"]!="asset://literal-state") return 1;
    sle::entity::Scene scene;
    const auto instance=sle::PrefabLoader::instantiate(moved.string(),scene,"spawned");
    if (!instance.ok()) { std::cerr<<instance.error(); return 1; }
    const auto* component=scene.getRegistry().getComponent<sle::components::ScriptComponent>(instance.value());
    if (!component || component->scriptAsset!=script) return 1;
    auto sceneEntries=entries;
    auto sceneManifest=Json::parse(sceneEntries[0].second);
    sceneManifest["format"]="sle-scene";
    sceneEntries[0].second=sceneManifest.dump();
    sceneEntries[1]={"scene.json",Json({{"name","Portable"},{"entities",document["entities"]}}).dump()};
    const auto scenePath=folder/"portable.SLESCENE";
    zip(scenePath,sceneEntries);
    const auto movedScene=folder/"relocated.SLESCENE";
    std::filesystem::rename(scenePath,movedScene);
    const auto packagedScene=sle::PrefabPackage::readScene(movedScene.string());
    if (!packagedScene.ok()) { std::cerr << packagedScene.error(); return 1; }
    if (sle::PrefabPackage::read(movedScene.string()).ok() || sle::PrefabPackage::readScene(moved.string()).ok()) {
        std::cerr << "Wrong package format accepted"; return 1;
    }
    sle::entity::Scene packageScene;
    const auto sceneLoaded=sle::SceneLoader::load(movedScene.string(),packageScene);
    if (!sceneLoaded.ok()) { std::cerr << sceneLoaded.error(); return 1; }
    const auto sceneActor=packageScene.findEntityByKey("actor");
    const auto* sceneScript=packageScene.getRegistry().getComponent<sle::components::ScriptComponent>(sceneActor);
    if (!sceneScript || !std::filesystem::exists(sceneScript->scriptAsset)) return 1;
    auto reject=[&](const std::vector<Entry>& bad,unsigned method=0) {
        zip(path,bad,method);
        const auto result=sle::PrefabPackage::read(path.string());
        if (result.ok()) { std::cerr<<"Invalid package accepted\n"; return false; }
        return true;
    };
    auto invalid=entries; invalid.emplace_back("../escaped.txt","bad"); if(!reject(invalid)) return 1;
    invalid=entries; invalid.push_back(entries.back()); if(!reject(invalid)) return 1;
    invalid=entries; invalid.pop_back(); if(!reject(invalid)) return 1;
    invalid=entries; manifest["version"]=2; invalid[0].second=manifest.dump(); if(!reject(invalid)) return 1;
    if(!reject(entries,8)) return 1;
    invalid=entries; document["entities"][0]["components"]["Script"]["scriptAsset"]="asset://missing";
    invalid[1].second=document.dump(); if(!reject(invalid)) return 1;
    zip(path,entries);
    { std::fstream corrupt(path,std::ios::binary|std::ios::in|std::ios::out); corrupt.seekp(45); corrupt.put('!'); }
    if(sle::PrefabPackage::read(path.string()).ok()) return 1;
    std::filesystem::remove(path);std::filesystem::remove(moved);std::filesystem::remove(movedScene);std::filesystem::remove(folder);
    std::cout<<"PREFAB_PACKAGE_REGRESSION_PASS\n";
    return 0;
}
