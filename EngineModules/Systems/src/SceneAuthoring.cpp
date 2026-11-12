#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include <set>
#include <map>
#include <stdexcept>

namespace sle {
nlohmann::json SceneAuthoring::canonicalScene(const nlohmann::json& document)
{
    using Json = nlohmann::json;
    if (!document.is_object()) throw std::invalid_argument("Scene must be an object");
    for (const auto& [field, value] : document.items()) {
        if (field != "entities" && field != "key" && field != "name")
            throw std::invalid_argument("Unsupported field in editable scene: " + field);
        if (field != "entities" && !value.is_string()) throw std::invalid_argument("Scene key/name must be strings");
    }
    if (document.contains("key") && document["key"].get<std::string>().empty())
        throw std::invalid_argument("Scene key must be nonempty");
    const auto& entities = document.at("entities");
    if (!entities.is_array() || entities.size() > 4096 || document.dump().size() > 4*1024*1024)
        throw std::invalid_argument("Scene exceeds authoring limits or entities is not an array");
    std::set<std::string> keys;
    for (const auto& entity : entities) {
        if (!entity.is_object() || !entity.contains("key") || !entity["key"].is_string())
            throw std::invalid_argument("Every scene entity must have a string key");
        const auto key = entity.at("key").get<std::string>();
        if (key.empty() || key == "@scene" || !keys.insert(key).second)
            throw std::invalid_argument("Scene entity keys must be unique and nonempty (not @scene)");
    }
    auto result = document;
    std::map<std::string,std::string> parents;
    for (auto& entity : result["entities"]) {
        if (!entity.contains("parent")) continue;
        auto& parent=entity["parent"];
        if (parent.is_number_integer()) {
            const double ordinal=parent.get<double>();
            if (ordinal<1 || ordinal>entities.size()) throw std::invalid_argument("Parent ordinal is outside scene");
            parent=entities[static_cast<std::size_t>(ordinal-1)].at("key");
        }
        if (!parent.is_string() || !keys.contains(parent.get<std::string>()))
            throw std::invalid_argument("Parent must reference a scene entity key");
        parents[entity.at("key").get<std::string>()]=parent.get<std::string>();
    }
    for (const auto& [child,parent] : parents) {
        std::set<std::string> seen{child};
        auto current=parent;
        while (true) {
            if (!seen.insert(current).second) throw std::invalid_argument("Scene hierarchy contains a cycle");
            const auto next=parents.find(current);
            if (next==parents.end()) break;
            current=next->second;
        }
    }
    const auto catalog = EntityAuthoring::componentCatalog();
    for (auto& entity : result["entities"]) {
        const auto key = entity.at("key").get<std::string>();
        Json targets;
        auto& components = entity.at("components");
        if (!components.is_object()) throw std::invalid_argument("Entity components must be an object");
        if (components.contains("Animator") && components["Animator"].contains("targetEntities")) {
            targets = components["Animator"]["targetEntities"];
            if (!targets.is_object()) throw std::invalid_argument("Animator targetEntities must be an object");
            for (auto& target : targets) {
                if (target.is_number_integer()) {
                    if (target.is_number_unsigned() && target.get<std::uint64_t>() > entities.size())
                        throw std::invalid_argument("Animator target ordinal is outside scene");
                    const auto ordinal = target.get<std::int64_t>();
                    if (ordinal < 1 || ordinal > static_cast<std::int64_t>(entities.size()))
                        throw std::invalid_argument("Animator target ordinal is outside scene");
                    target = entities[static_cast<std::size_t>(ordinal-1)].at("key");
                }
                if (!target.is_string() || !keys.contains(target.get<std::string>()))
                    throw std::invalid_argument("Animator target must reference a scene entity key");
            }
            for (auto& target : components["Animator"]["targetEntities"]) target = key;
        }
        entity.erase("parent");
        entity = EntityAuthoring::canonicalPrefab({{"root",key},{"entities",Json::array({entity})}}, catalog)["entities"][0];
        if (parents.contains(key)) entity["parent"]=parents.at(key);
        if (!targets.is_null()) entity["components"]["Animator"]["targetEntities"] = targets;
    }
    if (result.dump().size() > 4*1024*1024)
        throw std::invalid_argument("Canonical scene exceeds authoring size limit");
    return result;
}
}
