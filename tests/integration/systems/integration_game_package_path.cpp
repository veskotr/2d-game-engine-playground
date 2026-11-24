#include <sle/engine/GameConfig.hpp>
#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <chrono>
#include <iterator>
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
        {"entities",Json::array({{{"key",key+"-actor"},{"components",{{"Transform",Json::object()},{"Tags",{{"ids",{"packaged-actor","unresolved"}}}},{"Script",{{"scriptAsset","project-asset://"+script}}}}}}})}}}}.dump();
}
// Base package: main scene, script and prefab. Its script needs a module from the level package.
std::vector<Entry> basePackage(const Json& mainScene) {
    auto settings = manifest("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",mainScene);
    settings["settings"]["tags"] = Json::parse(R"([{"id":"packaged-actor","name":"Actor"}])");
    settings["settings"]["inputActions"] = Json::parse(
        R"([{"name":"Back","bindings":[{"kind":"key","key":256}]}])");
    return {
        {"manifest.json",settings.dump()},
        {"assets/Scenes/first.scene.json",scene("first")},{"assets/Scenes/first.scene.json.slemeta",meta(first)},
        {"assets/Scripts/main.lua",R"(local value=require('levels.value')
return { update=function(self,dt)
 local actor=Engine.findEntityByKey('first-actor') or Engine.findEntityByKey('second-actor')
 if Engine.Input.isActionDown('Back') and not Engine.Input.isActionPressed('Back') then
   Engine.setTransformPosition(actor, 71, 0)
 end
 local matches=Engine.findEntitiesByTag('Actor')
 if #matches ~= 1 or matches[1] ~= actor then error('packaged tag query failed') end
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
    auto settings = manifest("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",second);
    settings["settings"]["inputActions"] = Json::parse(R"([{"name":"WrongMap","bindings":[]}])");
    return {
        {"manifest.json",settings.dump()},
        {"assets/Levels/second.scene.json",scene("second")},{"assets/Levels/second.scene.json.slemeta",meta(second)},
        {"assets/Scripts/levels/value.lua","return 7"},{"assets/Scripts/levels/value.lua.slemeta",meta(module)},
    };
}
void writeConfig(const fs::path& path,const std::vector<std::string>& packages,const Json& extra=Json::object()) {
    Json config={{"window",{{"title","Packaged"},{"width",640},{"height",360},{"vsync",false},{"mode","Windowed"}}},{"targetFPS",30},{"packages",packages}};
    config.update(extra);
    std::ofstream(path)<<config.dump(2);
}
void fixture(const fs::path& directory,bool withMainScene, const std::string& filter = {}) {
    fs::create_directories(directory);
    auto base = basePackage(withMainScene ? Json(first) : Json());
    if (filter == "pixelArt") {
        auto settings = Json::parse(base.front().second);
        settings["settings"]["collisionLayers"] = Json::parse(R"({"blockedPairs":[[0,1]]})");
        base.front().second = settings.dump();
    }
    zip(directory/"game.slepkg", base);
    if (withMainScene) {
        auto level = levelPackage();
        if (!filter.empty()) {
            const std::string textureId = "77777777-7777-4777-8777-777777777777";
            const std::string regionId = "66666666-6666-4666-8666-666666666666";
            auto document = Json::parse(level[1].second);
            document["document"]["entities"].push_back({{"key","sprite"},{"components",{
                {"Transform",{{"scale",{32,32}}}},
                {"SpriteRenderer",{{"texture","project-asset://"+textureId},{"regionId",regionId}}}
            }}});
            level[1].second = document.dump();
            auto metadata = Json::parse(meta(textureId));
            metadata["texture"]["regions"] = Json::array({
                {{"id",regionId},{"name","Top left"},{"x",0},{"y",0},{"width",1},{"height",1}}
            });
            if (filter == "pixelArt" || filter == "smooth") {
                auto packageManifest = Json::parse(level.front().second);
                packageManifest["settings"]["artStyle"] = filter;
                level.front().second = packageManifest.dump();
            } else if (filter != "default") {
                metadata["texture"]["filter"] = filter;
            }
            const char pixels[] = "\xff\xff\xff\x00\x00\x00\x00\x00\x00\xff\xff\xff";
            level.emplace_back("assets/Textures/sprite.ppm", "P6\n2 2\n255\n" + std::string(pixels, sizeof(pixels)-1));
            level.emplace_back("assets/Textures/sprite.ppm.slemeta", metadata.dump());
        }
        zip(directory/"levels.slepkg",level);
    }
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
        try {
            const std::string option = argc == 4 ? argv[3] : "";
            fixture(fs::u8path(argv[2]), option != "--no-main", option);
            return 0;
        }
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
        check(project.tagsByName().at("Actor") == "packaged-actor", "package lost the tag definition");
        check(project.engineConfig().height==360,"config not applied");
        const auto& level=project.sceneDocument(second);
        const auto resolved=level.at("entities")[0]["components"]["Script"]["scriptAsset"].get<std::string>();
        check(fs::equivalent(resolved,project.assetRoots()[0]/"Scripts/main.lua"),"cross-package UUID reference unresolved");
        check(fs::equivalent(project.resolveAsset("Scripts/levels/value.lua"),project.assetRoots()[1]/"Scripts/levels/value.lua"),"relative path in later package unresolved");
        check(fs::is_regular_file(project.resolveAsset("project-asset://"+prefab)),"derived prefab missing");
        auto filteredPackage = levelPackage();
        auto styledManifest = Json::parse(filteredPackage.front().second);
        styledManifest["settings"]["artStyle"] = "smooth";
        styledManifest["settings"]["collisionLayers"] = Json::parse(R"({"blockedPairs":[[0,1]]})");
        styledManifest["settings"]["sortingLayers"] = Json::parse(R"([{"value":-3,"label":"Backdrop"},{"value":10,"label":"HUD"}])");
        filteredPackage.front().second = styledManifest.dump();
        filteredPackage.emplace_back("assets/Textures/inherited.png", "fixture");
        const std::string textureId = "77777777-7777-4777-8777-777777777777";
        auto textureMetadata = Json::parse(meta(textureId));
        textureMetadata["texture"] = {{"filter","nearest"}};
        filteredPackage.emplace_back("assets/Textures/sprite.png", "fixture");
        filteredPackage.emplace_back("assets/Textures/sprite.png.slemeta", textureMetadata.dump());
        const auto filteredPath = root/"filtered.slepkg";
        zip(filteredPath, filteredPackage);
        auto filtered = sle::ProjectRuntime::openPackages({config.packages[0], filteredPath}, config.engine);
        check(filtered.ok(), filtered.error());
        check(filtered.value().collisionMatrix() == sle::physics::CollisionMatrix{},
            "collision settings from the secondary package overrode the startup package");
        check(filtered.value().textureFilter(filtered.value().assetRoots()[1]/"Textures/sprite.png") ==
            sle::renderer::TextureFilter::Nearest, "mounted package lost the texture filter");
        check(filtered.value().textureFilter(filtered.value().assetRoots()[1]/"Textures/inherited.png") ==
            sle::renderer::TextureFilter::Linear, "mounted package lost its art-style default");
        check(filtered.value().textureFilter(filtered.value().assetRoots()[0]/"Scripts/main.lua") ==
            sle::renderer::TextureFilter::Default, "art style leaked into another package");
        auto invalidSettings = filteredPackage;
        styledManifest["settings"]["artStyle"] = true;
        invalidSettings.front().second = styledManifest.dump();
        const auto invalidStylePath = root/"invalid-style.slepkg";
        zip(invalidStylePath, invalidSettings);
        check(!sle::ProjectRuntime::openPackages({config.packages[0], invalidStylePath}, config.engine).ok(),
            "mounted package accepted an invalid art style");
        textureMetadata["texture"]["filter"] = nullptr;
        filteredPackage.back().second = textureMetadata.dump();
        const auto invalidFilterPath = root/"invalid-filter.slepkg";
        zip(invalidFilterPath, filteredPackage);
        const auto invalidFilter = sle::ProjectRuntime::openPackages({config.packages[0], invalidFilterPath}, config.engine);
        check(!invalidFilter.ok() && invalidFilter.error().find("sprite.png.slemeta") != std::string::npos,
            "mounted package accepted invalid texture metadata");
        for (const auto& bad : {std::string("../config.json"),(root/"build/config.json").generic_string()}) {
            bool rejected=false;try { project.resolveAsset(bad); } catch(const std::exception&) { rejected=true; }
            check(rejected,"escaping package path accepted");
        }
        {
            sle::Runtime runtime(project.engineConfig());project.registerScenes(runtime);
            check(runtime.hasScene("first") && runtime.hasScene("second") && runtime.hasScene(second),"package scene aliases missing");
            check(runtime.loadScene(first).ok() && runtime.loadScene("second").ok(),"package scenes failed to load");
        }
        const std::string tileScene = "00000002-1111-4111-8111-111111111111";
        const std::string sheet = "00000004-1111-4111-8111-111111111111";
        std::vector<Entry> tilePackage{{"manifest.json", manifest(first, tileScene).dump()}};
        for (const auto& entry : fs::directory_iterator(cwd / "tests/data/tilemap/Assets")) {
            std::ifstream file(entry.path(), std::ios::binary);
            check(file.is_open(), "Could not open tilemap package fixture");
            tilePackage.emplace_back("assets/" + entry.path().filename().generic_string(),
                std::string(std::istreambuf_iterator<char>(file), {}));
        }
        const auto tilePackagePath = root / "tiles.slepkg";
        zip(tilePackagePath, tilePackage);
        const auto packedTiles = sle::ProjectRuntime::openPackages({tilePackagePath}, config.engine);
        check(packedTiles.ok(), packedTiles.error());
        check(packedTiles.value().sceneDocument(tileScene).at("tileLayers").size() == 2,
            "Packaged tile layers were lost");
        const auto loadTileScene = [](const sle::ProjectRuntime& project, const fs::path& source) {
            sle::entity::Scene scene;
            const auto loaded = sle::SceneLoader::loadData(project.sceneDocument(project.startupScene()),
                scene, source.generic_string(), {}, [&project](const auto& ref) { return project.resolveAsset(ref); });
            check(loaded.ok(), loaded.error());
            check(scene.getTileLayers().size() == 2, "Runtime did not load both tile layers");
        };
        loadTileScene(packedTiles.value(), tilePackagePath);

        const auto room = root / "room";
        fs::copy(cwd / "examples/tilemap_room", room, fs::copy_options::recursive);
        const auto roomProject = room / "Room.sleproject";
        const auto originalRoom = sle::ProjectRuntime::open(roomProject);
        check(originalRoom.ok(), originalRoom.error());
        const auto roomDocument = originalRoom.value().sceneDocument(originalRoom.value().startupScene());
        const auto art = room / "Assets/Art";
        fs::create_directory(art);
        fs::rename(room / "Assets/Room.png", art / "Renamed.png");
        fs::rename(room / "Assets/Room.png.slemeta", art / "Renamed.png.slemeta");
        const auto reopenedRoom = sle::ProjectRuntime::open(roomProject);
        check(reopenedRoom.ok(), reopenedRoom.error());
        check(reopenedRoom.value().sceneDocument(reopenedRoom.value().startupScene()) == roomDocument,
            "Moving the spritesheet changed the reopened room document");
        loadTileScene(reopenedRoom.value(), roomProject);
        auto corruptTiles = tilePackage;
        for (auto& [path, bytes] : corruptTiles)
            if (path == "assets/Colors.png") bytes.resize(33); // Keep PNG signature and IHDR only.
        const auto corruptTilesPath = root / "corrupt-sheet.slepkg";
        zip(corruptTilesPath, corruptTiles);
        const auto corruptSheet = sle::ProjectRuntime::openPackages({corruptTilesPath}, config.engine);
        check(!corruptSheet.ok() && corruptSheet.error().find(sheet) != std::string::npos,
            "Package validation accepted a truncated spritesheet image");
        std::erase_if(tilePackage, [](const Entry& entry) { return entry.first == "assets/Colors.png"; });
        const auto brokenTilesPath = root / "missing-sheet.slepkg";
        zip(brokenTilesPath, tilePackage);
        const auto brokenTiles = sle::ProjectRuntime::openPackages({brokenTilesPath}, config.engine);
        check(!brokenTiles.ok() && brokenTiles.error().find(sheet) != std::string::npos,
            "Package validation did not identify the missing spritesheet");

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
