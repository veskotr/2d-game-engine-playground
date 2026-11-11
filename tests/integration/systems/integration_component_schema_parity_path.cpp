#include <sle/engine/Runtime.hpp>
#include <nlohmann/json.hpp>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <vector>

namespace {
using Json = nlohmann::json;
struct Case { const char* name; const char* component; Json patch; bool valid=true; bool luaTest=true; };
}

int main()
{
    const auto file=std::filesystem::temp_directory_path()/
        ("sle-component-schema-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count())+".json");
    struct Cleanup { std::filesystem::path file; ~Cleanup(){std::error_code ec;std::filesystem::remove(file,ec);} } cleanup{file};
    std::vector<Case> cases={
        {"transform","Transform",{{"position",{3,4}},{"rotation",0.5},{"scale",{2,3}}}},
        {"sprite","SpriteRenderer",{{"color",{0.2,0.4,0.6,1}},{"visible",false},{"layer",-2},{"region",{{"uv",{0,0,0.5,0.5}}}}}},
        {"body","RigidBody",{{"bodyType","Kinematic"},{"velocity",{3,4}},{"linearDamping",0.2}}},
        {"box","BoxCollider",{{"size",{3,4}},{"maskBits",4}}},
        {"circle","CircleCollider",{{"radius",2},{"density",0.4}}},
        {"box_zone","BoxZone",{{"size",{3,4}},{"zoneId","entry"}}},
        {"circle_zone","CircleZone",{{"radius",2},{"zoneId","exit"}}},
        {"script","Script",{{"asset","assets/scripts/player_move.lua"},{"enabled",false}}},
        {"audio","Audio",{{"asset","assets/sounds/gravel.ogg"},{"volume",0.4},{"pitch",1.2}}},
        {"ui","UI",{{"layout","  "},{"font",""},{"behavior",""},{"spaceMode","World"}}},
        {"animator","Animator",{{"clip",""},{"timeSeconds",0.5},{"overrideLoopMode",true},{"loopMode","Once"},{"floatParameters",{{"speed",2}}}}},
        {"state","StateMachine",{{"definition",""},{"boolParameters",{{"moving",true}}},{"triggers",{"jump"}}}},
        {"chunk","ChunkActivation",{{"homeChunkX",-2},{"mode","AlwaysActive"},{"keepScriptsWhenInactive",true}}},
        {"volume_high","Audio",{{"volume",2}},false},
        {"pitch_zero","Audio",{{"pitch",0}},false},
        {"wrong_bool","Audio",{{"loop",1}},false},
        {"wrong_number","Audio",{{"volume","0.5"}},false},
        {"negative_size","BoxCollider",{{"size",{-1,2}}},false},
        {"bad_mask","BoxCollider",{{"maskBits",65536}},false},
        {"fractional_mask","BoxCollider",{{"maskBits",1.5}},false},
        {"bad_enum","RigidBody",{{"bodyType","Flying"}},false},
        {"negative_damping","RigidBody",{{"linearDamping",-1}},false},
        {"negative_layer","UI",{{"layer",-1}},false},
        {"bad_vector","Transform",{{"position",{1}}},false},
        {"long_vector","Transform",{{"position",{1,2,3}}},false},
        {"unknown_field","Audio",{{"volume",0.2},{"typo",true}},false},
        {"internal_field","Audio",{{"volume",0.2},{"runtimeHandle",1}},false},
        {"bad_map","Animator",{{"floatParameters",{{"speed","fast"}}}},false},
        {"bad_trigger","StateMachine",{{"triggers",{3}}},false},
        {"bad_asset","Script",{{"asset",false}},false},
        {"read_only","WorldTransform",{{"rotation",1}},false},
        {"overflow_float","Transform",{{"rotation",1e100}},false},
        {"not_object","Audio",3,false},
        {"asset_maps","Animator",{{"clipAsset",""},{"stateClipMap",{{"Idle","assets/animations/player_color_red_blue.clip.json"}}}}},
        {"transform_canonical","Transform",{{"position",{{"x",3},{"y",4}}},{"rotation",0.5},{"scale",{{"x",2},{"y",3}}}}},
        {"script_canonical","Script",{{"scriptAsset","assets/scripts/player_move.lua"},{"enabled",false}}},
        {"audio_canonical","Audio",{{"assetPath","assets/sounds/gravel.ogg"},{"volume",0.4},{"pitch",1.2}}},
        {"alias_conflict","Script",{{"asset","a.lua"},{"scriptAsset","b.lua"}},false,false},
        {"negative_legacy_mass","RigidBody",{{"mass",-1}},false,false}
    };
    const char* components[]={"Transform","SpriteRenderer","RigidBody","BoxCollider","CircleCollider",
        "BoxZone","CircleZone","Script","Audio","UI","Animator","StateMachine","ChunkActivation"};
    std::vector<std::string> names;
    names.reserve(std::size(components));
    for(const auto* name:components) names.push_back(std::string("default_")+name);
    for(std::size_t i=0;i<names.size();++i) cases.push_back({names[i].c_str(),components[i],Json::object()});

    sle::Runtime runtime(sle::core::EngineConfig{});
    sle::ScriptApiImpl api(runtime);
    auto& scene=runtime.getScene();
    auto& lua=runtime.getScriptEngine();
    if(!lua.init(&api)) return 1;
    struct LuaCleanup { sle::scripting::ScriptEngine& engine; ~LuaCleanup(){engine.shutdown();} } luaCleanup{lua};
    if(!lua.executeScriptAsset("tests/data/scripts/component_schema_parity.lua")) return 1;
    runtime.registerScene("initial",[](auto& rt){rt.getScene().createEntity();});
    if(!runtime.loadScene("initial").ok()) return 1;
    runtime.registerSceneFromFile("parity",file.string());
    bool passed=true;
    for(const auto& test:cases) {
        const auto previousRoots=scene.getRoots();
        const auto previousName=runtime.getCurrentSceneName();
        const auto previousKey=scene.findEntityByKey("specimen");
        { std::ofstream out(file); out << Json{{"entities",Json::array({
            {{"key","specimen"},{"components",{{test.component,test.patch}}}}
        })}}.dump(); }
        const auto loaded=runtime.loadScene("parity");
        if(loaded.ok()!=test.valid) {
            std::cerr<<test.name<<": JSON acceptance mismatch: "<<(loaded.ok()?"accepted":loaded.error())<<'\n'; passed=false;
        }
        if(!test.valid && (scene.getRoots()!=previousRoots || runtime.getCurrentSceneName()!=previousName || scene.findEntityByKey("specimen")!=previousKey)) {
            std::cerr<<test.name<<": rejected JSON changed active scene\n";passed=false;
        }
        if(!loaded.ok() && (loaded.error().find(test.component)==std::string::npos || loaded.error().find(file.string())==std::string::npos)) {
            std::cerr<<test.name<<": missing component/file error context\n";passed=false;
        }
        if(!test.luaTest) continue;
        const auto entity=scene.createEntity();
        std::string error;
        if(!test.valid && std::string(test.component)!="WorldTransform")
            if(!api.setComponent({entity.getID()},test.component,Json::object(),error)) return 1;
        const auto before=api.getComponent({entity.getID()},test.component);
        const bool applied=lua.callGlobalBoolFunction("apply_schema_case",entity.getID(),test.name);
        if(applied!=test.valid) {std::cerr<<test.name<<": Lua acceptance mismatch\n";passed=false;}
        const auto after=api.getComponent({entity.getID()},test.component);
        if(!test.valid && after!=before) {std::cerr<<test.name<<": rejected Lua patch changed component\n";passed=false;}
        if(test.valid && loaded.ok()) {
            const auto authored=api.getComponent({scene.findEntityByKey("specimen").getID()},test.component);
            if(authored!=after || authored.is_null()) {
                std::cerr<<test.name<<": snapshots differ\nJSON: "<<authored.dump()<<"\nLua: "<<after.dump()<<'\n';passed=false;
            }
        }
        scene.destroyEntity(entity);
    }
    return passed?0:1;
}
