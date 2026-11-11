#include <sle/engine/PrefabLoader.hpp>
#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/AudioComponent.hpp>
#include <nlohmann/json.hpp>
#include <iostream>
#include <fstream>
#include <filesystem>
#include <chrono>
#include <vector>

namespace {
bool nestedPrefabs()
{
    using Json=nlohmann::json;
    sle::entity::Scene scene;
    const auto anchor=scene.ensureSceneEntity();
    const auto existing=scene.createEntity();
    scene.setEntityKey(existing,"existing");
    const auto instance=sle::PrefabLoader::instantiate("tests/data/prefabs/nested/assembly.prefab.json",scene,"assembly",
        {{"left/leaf/child",{{"Transform",{{"position",{{"x",90}}}}},{"Audio",nullptr}}}});
    if (!instance.ok()) { std::cerr << instance.error(); return false; }
    const auto left=scene.findEntityByKey("assembly/left");
    const auto leftSocket=scene.findEntityByKey("assembly/left/socket");
    const auto leftLeaf=scene.findEntityByKey("assembly/left/leaf");
    const auto leftChild=scene.findEntityByKey("assembly/left/leaf/child");
    const auto right=scene.findEntityByKey("assembly/right");
    const auto rightLeaf=scene.findEntityByKey("assembly/right/leaf");
    const auto rightChild=scene.findEntityByKey("assembly/right/leaf/child");
    auto& registry=scene.getRegistry();
    const auto* leftTransform=registry.getComponent<sle::components::TransformComponent>(leftChild);
    const auto* rightTransform=registry.getComponent<sle::components::TransformComponent>(rightChild);
    const auto* rootAnimator=registry.getComponent<sle::components::AnimatorComponent>(instance.value());
    const auto* leftAnimator=registry.getComponent<sle::components::AnimatorComponent>(leftLeaf);
    const auto* rightAnimator=registry.getComponent<sle::components::AnimatorComponent>(rightLeaf);
    if (scene.getParent(instance.value())!=anchor || scene.getParent(left)!=instance.value() ||
        scene.getParent(right)!=leftSocket || scene.getParent(leftLeaf)!=leftSocket ||
        !leftTransform || !rightTransform || leftTransform->getPosition().x!=90 || leftTransform->getPosition().y!=11 ||
        rightTransform->getPosition().x!=5 || rightTransform->getPosition().y!=11 ||
        registry.hasComponent<sle::components::AudioComponent>(leftChild) || !registry.hasComponent<sle::components::AudioComponent>(rightChild) ||
        !rootAnimator || rootAnimator->targetEntities.at("nested")!=leftChild.getID() ||
        rootAnimator->targetEntities.at("numeric")!=scene.findEntityByKey("assembly/mount").getID() ||
        !leftAnimator || leftAnimator->targetEntities.at("child")!=leftChild.getID() || leftAnimator->targetEntities.at("numeric")!=leftChild.getID() ||
        !rightAnimator || rightAnimator->targetEntities.at("child")!=rightChild.getID() || rightAnimator->targetEntities.at("numeric")!=rightChild.getID()) return false;
    const auto children=scene.getChildren(anchor);
    for (const auto* file : {"cycle.prefab.json","invalid_parent.prefab.json","invalid_key.prefab.json","invalid_numeric.prefab.json"}) {
        const auto path=std::string("tests/data/prefabs/nested/")+file;
        const auto rejected=sle::PrefabLoader::instantiate(path,scene,"failed");
        if (rejected.ok() || rejected.error().find(file)==std::string::npos || scene.getChildren(anchor)!=children ||
            scene.findEntityByKey("failed").valid() || !registry.hasEntity(existing) || !registry.hasEntity(leftChild)) return false;
    }
    const auto scratch=std::filesystem::temp_directory_path()/
        ("sle-prefab-limits-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    std::filesystem::create_directories(scratch);
    struct RemoveDirectory { std::filesystem::path path; ~RemoveDirectory() { std::error_code ec; std::filesystem::remove_all(path,ec); } } cleanup{scratch};
    for (int i=0;i<34;++i) {
        Json source={{"root","root"},{"entities",Json::array({{{"key","root"}}})}};
        if (i<33) source["prefabs"]=Json::array({{{"file",std::to_string(i+1)+".json"},{"key","nested"}}});
        std::ofstream file(scratch/(std::to_string(i)+".json")); file << source.dump();
    }
    const auto tooDeep=sle::PrefabLoader::instantiate((scratch/"0.json").string(),scene,"deep");
    if (tooDeep.ok() || tooDeep.error().find("depth limit")==std::string::npos || scene.getChildren(anchor)!=children) return false;
    Json oversized={{"root","root"},{"entities",Json::array({{{"key","root"}}})}};
    for (int i=0;i<16384;++i) oversized["entities"].push_back({{"key","child"+std::to_string(i)},{"parent","root"}});
    { std::ofstream file(scratch/"oversized.json"); file << oversized.dump(); }
    const auto tooLarge=sle::PrefabLoader::instantiate((scratch/"oversized.json").string(),scene,"large");
    if (tooLarge.ok() || tooLarge.error().find("entity limit")==std::string::npos || scene.getChildren(anchor)!=children) return false;
    oversized["entities"].erase(oversized["entities"].begin()+8192,oversized["entities"].end());
    { std::ofstream file(scratch/"half.json"); file << oversized.dump(); }
    const Json fanout={{"root","root"},{"entities",Json::array({{{"key","root"}}})},
        {"prefabs",Json::array({{{"file","half.json"},{"key","left"}},{{"file","half.json"},{"key","right"}}})}};
    { std::ofstream file(scratch/"fanout.json"); file << fanout.dump(); }
    const auto tooWide=sle::PrefabLoader::instantiate((scratch/"fanout.json").string(),scene,"wide");
    if (tooWide.ok() || tooWide.error().find("entity limit")==std::string::npos || scene.getChildren(anchor)!=children) return false;
    sle::entity::Scene invalidScene;
    const Json invalidOrdinal={{"entities",Json::array({{{"key","world"},{"parent",2}}})},
        {"prefabs",Json::array({{{"file","leaf.prefab.json"},{"key","child"}}})}};
    const auto ordinalResult=sle::SceneLoader::loadData(invalidOrdinal,invalidScene,"tests/data/prefabs/nested/scene.json");
    if (ordinalResult.ok() || ordinalResult.error().find("parent")==std::string::npos) return false;
    sle::entity::Scene loaded;
    const auto load=sle::SceneLoader::loadData({{"prefabs",Json::array({{{"file","assembly.prefab.json"},{"key","loaded"}}})}},
        loaded,"tests/data/prefabs/nested/scene.json");
    return load.ok() && loaded.findEntityByKey("loaded/left/leaf/child").valid();
}
} // namespace

int main()
{
    if (!nestedPrefabs()) return 20;
    using Json=nlohmann::json;
    sle::entity::Scene scene;
    const auto outside=scene.createEntity();
    scene.setEntityKey(outside,"outside");
    const auto first=sle::PrefabLoader::instantiate("tests/data/prefabs/actor.prefab.json",scene,"a",
        {{"root",{{"Transform",{{"position",{100,50}}}}}},{"child",{{"Audio",nullptr}}}},outside);
    if (!first.ok()) { std::cerr << first.error(); return 1; }
    const auto second=sle::PrefabLoader::instantiate("tests/data/prefabs/actor.prefab.json",scene,"b");
    if (!second.ok()) { std::cerr << second.error(); return 1; }
    const auto child=scene.findEntityByKey("a/child");
    const auto other=scene.findEntityByKey("b/child");
    const auto* secondAnimator=scene.getRegistry().getComponent<sle::components::AnimatorComponent>(second.value());
    const auto* animator=scene.getRegistry().getComponent<sle::components::AnimatorComponent>(first.value());
    if (scene.getParent(first.value())!=outside || scene.getParent(child)!=first.value() ||
        scene.getParent(other)!=second.value() || !animator || !secondAnimator ||
        secondAnimator->targetEntities.at("child")!=other.getID() || secondAnimator->targetEntities.at("numeric")!=other.getID() ||
        animator->targetEntities.at("child")!=child.getID() ||
        animator->targetEntities.at("numeric")!=child.getID() ||
        scene.getRegistry().hasComponent<sle::components::AudioComponent>(child) ||
        !scene.getRegistry().hasComponent<sle::components::AudioComponent>(other) ||
        scene.getRegistry().getComponent<sle::components::TransformComponent>(first.value())->getPosition().x!=100 ||
        scene.getRegistry().getComponent<sle::components::TransformComponent>(second.value())->getPosition().x!=1) return 2;
    const auto roots=scene.getRoots();
    for (const Json bad : {Json{{"missing",Json::object()}},Json{{"root",{{"Transform",{{"position",{1}}}}}}}}) {
        const auto failed=sle::PrefabLoader::instantiate("tests/data/prefabs/actor.prefab.json",scene,"bad",bad);
        if (failed.ok() || scene.getRoots()!=roots || scene.findEntityByKey("bad").valid() ||
            !scene.getRegistry().hasEntity(child)) return 3;
    }
    if (sle::PrefabLoader::instantiate("tests/data/prefabs/actor.prefab.json",scene,"a").ok()) return 4;
    if (sle::PrefabLoader::instantiate("tests/data/prefabs/actor.prefab.json",scene,"dead-parent",Json::object(),
        sle::entity::Entity{999999}).ok() || scene.findEntityByKey("dead-parent").valid() || scene.getRoots()!=roots) return 11;
    const auto recursive=sle::PrefabLoader::instantiate("tests/data/prefabs/recursive.prefab.json",scene,"recursive");
    if (recursive.ok() || recursive.error().find("recursive.prefab.json")==std::string::npos || scene.getRoots()!=roots) return 7;
    const auto temporary=std::filesystem::temp_directory_path()/
        ("sle-prefab-invalid-"+std::to_string(std::chrono::steady_clock::now().time_since_epoch().count())+".json");
    struct Cleanup { std::filesystem::path path; ~Cleanup() { std::error_code ec; std::filesystem::remove(path,ec); } } cleanup{temporary};
    Json definition;
    { std::ifstream file("tests/data/prefabs/actor.prefab.json"); file >> definition; }
    std::vector<Json> invalid;
    auto bad=definition; bad["root"]="missing"; invalid.push_back(bad);
    bad=definition; bad["entities"][1]["key"]="root"; invalid.push_back(bad);
    bad=definition; bad["entities"][1].erase("key"); invalid.push_back(bad);
    bad=definition; bad["entities"][1]["parent"]="child"; invalid.push_back(bad);
    bad=definition; bad["entities"][1].erase("parent"); invalid.push_back(bad);
    bad=definition; bad["entities"][0]["components"]["Animator"]["targetEntities"]["child"]=3; invalid.push_back(bad);
    for (const auto& input : invalid) {
        { std::ofstream file(temporary); file << input.dump(); }
        const auto rejected=sle::PrefabLoader::instantiate(temporary.string(),scene,"invalid");
        if (rejected.ok() || rejected.error().find(temporary.string())==std::string::npos ||
            scene.getRoots()!=roots || scene.findEntityByKey("invalid").valid() || !scene.getRegistry().hasEntity(child)) return 10;
    }
    sle::entity::Scene loaded;
    const Json input={{"entities",Json::array({{{"key","parent"}},{{"key","legacy"},{"parent",1}}})},
        {"prefabs",Json::array({{{"file","actor.prefab.json"},{"key","x"},{"parent","parent"}},
                               {{"file","actor.prefab.json"},{"key","y"}}})}};
    const auto load=sle::SceneLoader::loadData(input,loaded,"tests/data/prefabs/scene.json");
    if (!load.ok()) { std::cerr << load.error(); return 5; }
    if (loaded.getParent(loaded.findEntityByKey("x"))!=loaded.findEntityByKey("parent") ||
        loaded.getParent(loaded.findEntityByKey("legacy"))!=loaded.findEntityByKey("parent")) return 6;
    sle::Runtime runtime(sle::core::EngineConfig{});
    runtime.registerScene("original",[](auto& rt) { rt.getScene().setEntityKey(rt.getScene().createEntity(),"original"); });
    if (!runtime.loadScene("original").ok()) return 8;
    const auto original=runtime.getScene().findEntityByKey("original");
    runtime.registerSceneFromFile("invalid","tests/data/prefabs/invalid_scene.json");
    const auto rejected=runtime.loadScene("invalid");
    if (rejected.ok() || runtime.getCurrentSceneName()!="original" ||
        runtime.getScene().findEntityByKey("original")!=original || !runtime.getScene().getRegistry().hasEntity(original)) return 9;
    return 0;
}
