#include <sle/engine/PrefabLoader.hpp>
#include <sle/engine/PrefabPackage.hpp>
#include <sle/engine/SceneLoader.hpp>
#include "PrefabExpansion.hpp"
#include <algorithm>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <functional>
#include <stdexcept>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace sle {
namespace {
using Json = nlohmann::json;

void require(bool condition, const std::string& message)
{
    if (!condition) throw std::invalid_argument(message);
}

void merge(Json& destination, const Json& patch)
{
    if (!destination.is_object() || !patch.is_object()) { destination=patch; return; }
    for (const auto& [key,value] : patch.items()) {
        if (destination.contains(key)) merge(destination[key],value);
        else destination[key]=value;
    }
}

// Limits apply to one dynamic instance or one scene prefab expansion operation.
constexpr std::size_t maximumDepth=32;
constexpr std::size_t maximumEntities=16384;
struct ExpansionContext
{
    std::vector<std::filesystem::path> stack;
    std::size_t entityCount=0;
};

struct StackEntry
{
    ExpansionContext& context;
    StackEntry(ExpansionContext& context, const std::filesystem::path& path) : context(context)
    {
        require(context.stack.size()<maximumDepth,"prefab depth limit (32) exceeded");
        require(std::find(context.stack.begin(),context.stack.end(),path)==context.stack.end(),"recursive prefab import cycle");
        context.stack.push_back(path);
    }
    ~StackEntry() { context.stack.pop_back(); }
};

Json expand(const std::string& path, const std::string& instanceKey, const Json& overrides, ExpansionContext& context);

void appendInstances(Json& entities, const Json& instances, const std::string& path,
    const std::string& defaultParent, ExpansionContext& context)
{
    require(instances.is_array(),"prefabs must be an array");
    for (const auto& instance : instances) {
        require(instance.is_object(),"prefab instance must be an object");
        require(instance.contains("file") && instance["file"].is_string() && !instance["file"].get_ref<const std::string&>().empty(),
            "prefab file must be a nonempty string");
        require(instance.contains("key") && instance["key"].is_string(),"prefab instance key must be a string");
        if (instance.contains("parent")) require(instance["parent"].is_string() && !instance["parent"].get_ref<const std::string&>().empty(),
            "prefab instance parent must be a nonempty enclosing entity key");
        const auto file=(std::filesystem::path(path).parent_path()/instance["file"].get<std::string>()).lexically_normal();
        const auto key=instance["key"].get<std::string>();
        auto children=expand(file.generic_string(),key,instance.value("overrides",Json::object()),context);
        for (auto& entry : children) {
            if (entry["key"]==key) {
                if (instance.contains("parent")) entry["parent"]=instance["parent"];
                else if (!defaultParent.empty()) entry["parent"]=defaultParent;
            }
            entities.push_back(std::move(entry));
        }
    }
}

Json expand(const std::string& path, const std::string& instanceKey, const Json& overrides, ExpansionContext& context)
{
    try {
        require(!instanceKey.empty() && instanceKey!="@scene","instance key must be nonempty and must not be reserved @scene");
        const StackEntry stackEntry(context,std::filesystem::canonical(std::filesystem::path(path)));
        require(overrides.is_object(),"overrides must be an object");
        Json source;
        auto extension=std::filesystem::path(path).extension().string();
        std::transform(extension.begin(),extension.end(),extension.begin(),[](unsigned char c) { return static_cast<char>(std::tolower(c)); });
        if (extension==".sleprefab") {
            auto package=PrefabPackage::read(path);
            require(package.ok(),package.error());
            source=std::move(package.value());
        } else {
            std::ifstream file(path);
            require(file.is_open(),"cannot open file");
            file >> source;
        }
        require(source.is_object(),"root must be an object");
        require(!source.contains("tiledMap"),"Tiled map imports are unsupported in prefabs");
        require(source.contains("root") && source["root"].is_string() && !source["root"].get_ref<const std::string&>().empty(),
            "root must name a nonempty entity key");
        require(source.contains("entities") && source["entities"].is_array() && !source["entities"].empty(),
            "entities must be a nonempty array");
        auto entities=source["entities"];
        const auto authoredCount=entities.size();
        require(authoredCount<=maximumEntities-context.entityCount,"prefab entity limit (16384) exceeded");
        context.entityCount+=authoredCount;
        const auto rootKey=source["root"].get<std::string>();
        if (source.contains("prefabs")) appendInstances(entities,source["prefabs"],path,rootKey,context);
        std::unordered_map<std::string,std::size_t> indices;
        for (std::size_t i=0;i<entities.size();++i) {
            const auto& entry=entities[i];
            require(entry.is_object() && entry.contains("key") && entry["key"].is_string() &&
                !entry["key"].get_ref<const std::string&>().empty() && entry["key"]!="@scene","entity["+std::to_string(i)+"] needs a nonempty key");
            require(indices.emplace(entry["key"].get<std::string>(),i).second,"duplicate entity key");
            require(!entry.contains("components") || entry["components"].is_object(),"entity components must be an object");
        }
        require(indices.contains(rootKey),"root names an unknown entity key '"+rootKey+"'");
        const auto rootIndex=indices.at(rootKey);
        auto localKey=[&](const Json& reference) -> std::string {
            if (reference.is_string()) {
                const auto key=reference.get<std::string>();
                require(indices.contains(key),"unknown internal reference '"+key+"'");
                return key;
            }
            if (reference.is_number_unsigned() || (reference.is_number_integer() && reference.get<int64_t>()>0)) {
                const auto position=reference.get<uint64_t>();
                require(position>0 && position<=authoredCount,"internal numeric reference is outside authored entities array");
                return entities[position-1]["key"].get<std::string>();
            }
            throw std::invalid_argument("internal reference must be a key or positive 1-based entity position");
        };
        auto qualified=[&](const std::string& key) { return key==rootKey ? instanceKey : instanceKey+"/"+key; };
        require(!entities[rootIndex].contains("parent") || entities[rootIndex]["parent"].is_null(),"declared root must not have a parent");
        // Every entity must belong to the declared subtree, and parent chains must terminate.
        for (std::size_t i=0;i<entities.size();++i) {
            std::unordered_set<std::size_t> visited;
            auto cursor=i;
            while (cursor!=rootIndex) {
                require(visited.insert(cursor).second,"parent hierarchy cycle");
                const auto& entry=entities[cursor];
                require(entry.contains("parent") && !entry["parent"].is_null(),"entity outside declared root subtree");
                cursor=indices.at(localKey(entry["parent"]));
            }
        }
        for (const auto& [key,patch] : overrides.items()) {
            require(indices.contains(key),"override names unknown entity '"+key+"'");
            require(patch.is_object(),"entity override must be a component object: "+key);
            auto& components=entities[indices.at(key)]["components"];
            if (components.is_null()) components=Json::object();
            for (const auto& [component,value] : patch.items()) {
                require(component!="key" && component!="parent" && component!="name" && component!="components",
                    "overrides may only change components");
                require(value.is_null() || value.is_object(),"component override must be an object or null: "+component);
                if (value.is_null()) components.erase(component);
                else { if (!components.contains(component)) components[component]=Json::object(); merge(components[component],value); }
            }
        }
        for (auto& entry : entities) {
            if (entry.contains("parent") && !entry["parent"].is_null()) entry["parent"]=qualified(localKey(entry["parent"]));
            if (entry.contains("components") && entry["components"].contains("Animator")) {
                auto& animator=entry["components"]["Animator"];
                if (animator.is_object() && animator.contains("targetEntities")) {
                    auto& targets=animator["targetEntities"];
                    require(targets.is_object(),"Animator.targetEntities must be an object");
                    for (auto& [alias,target] : targets.items()) target=qualified(localKey(target));
                }
            }
        }
        // Preserve local keys until every numeric reference has been translated.
        for (auto& entry : entities) entry["key"]=qualified(entry["key"].get<std::string>());
        return entities;
    }
    catch (const std::exception& ex) { throw std::invalid_argument("Prefab '"+path+"': "+ex.what()); }
}
Json expand(const std::string& path, const std::string& instanceKey, const Json& overrides)
{
    ExpansionContext context;
    return expand(path,instanceKey,overrides,context);
}
} // namespace

namespace detail {
core::Result<Json> expandScenePrefabs(const Json& source, const std::string& path)
{
    using Result=core::Result<Json>;
    try {
        Json result=source;
        if (!source.contains("prefabs")) return Result::success(result);
        require(source["prefabs"].is_array(),"prefabs must be an array");
        if (!result.contains("entities")) result["entities"]=Json::array();
        require(result["entities"].is_array(),"entities must be an array");
        ExpansionContext context;
        appendInstances(result["entities"],source["prefabs"],path,"",context);
        result.erase("prefabs");
        return Result::success(result);
    }
    catch (const std::exception& ex) { return Result::error("Scene '"+path+"': "+ex.what()); }
}
} // namespace detail

core::Result<entity::Entity> PrefabLoader::instantiate(const std::string& path, entity::Scene& scene,
    const std::string& key, const Json& overrides, entity::Entity parent)
{
    using Result=core::Result<entity::Entity>;
    // Snapshot complete hierarchy, including entities under an optional scene anchor.
    std::unordered_set<uint32_t> old;
    std::function<void(entity::Entity)> remember=[&](entity::Entity entity) {
        old.insert(entity.getID());
        for (auto child : scene.getChildren(entity)) remember(child);
    };
    for (auto root : scene.getRoots()) remember(root);
    auto rollback=[&] {
        std::function<void(entity::Entity)> remove=[&](entity::Entity entity) {
            if (!old.contains(entity.getID())) { scene.destroyEntity(entity); return; }
            const auto children=scene.getChildren(entity);
            for (auto child : children) remove(child);
        };
        const auto roots=scene.getRoots();
        for (auto root : roots) remove(root);
    };
    try {
        require(!parent.valid() || scene.getRegistry().hasEntity(parent),"external parent must be live");
        auto entities=expand(path,key,overrides);
        for (const auto& entry : entities)
            require(!scene.findEntityByKey(entry["key"].get<std::string>()).valid(),"instance key already exists: "+entry["key"].get<std::string>());
        const Json data={{"entities",entities}};
        entity::Scene staged;
        const auto validation=SceneLoader::loadData(data,staged,path);
        if (!validation.ok()) return Result::error(validation.error());
        const auto loaded=SceneLoader::loadData(data,scene,path);
        if (!loaded.ok()) { rollback(); return Result::error(loaded.error()); }
        const auto root=scene.findEntityByKey(key);
        require(root.valid(),"instance root was not created");
        if (parent.valid()) require(scene.setParent(root,parent),"cannot attach instance root to external parent");
        return Result::success(root);
    }
    catch (const std::exception& ex) { rollback(); return Result::error("Prefab '"+path+"': "+ex.what()); }
}
} // namespace sle
