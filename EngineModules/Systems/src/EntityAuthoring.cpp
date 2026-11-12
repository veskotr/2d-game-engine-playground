#include <sle/engine/EntityAuthoring.hpp>
#include "ComponentRegistry.hpp"
#include "SceneComponentAdapter.hpp"
#include <stdexcept>

namespace sle {
namespace {
using Json = nlohmann::json;

void requireFields(const Json& object, std::initializer_list<const char*> allowed)
{
    if (!object.is_object()) throw std::invalid_argument("Expected JSON object");
    for (const auto& [field, _] : object.items())
    {
        bool known = false;
        for (const auto* name : allowed) known |= field == name;
        if (!known) throw std::invalid_argument("Unsupported field in single-entity prefab: " + field);
    }
}
}

nlohmann::json EntityAuthoring::componentCatalog()
{
    Json catalog = Json::object();
    // Enum values are probed through the real validators. Ordinary string fields
    // accept every probe and remain free text; enum fields accept only a subset.
    const Json candidates = {"Static", "Dynamic", "Kinematic", "Once", "Loop",
        "Screen", "World", "Inherit", "AlwaysActive", "EventsOnly", "AutoToggle"};
    for (const auto& [name, descriptor] : componentdata::descriptors())
    {
        if (!descriptor.set) continue;
        entity::Registry registry;
        const auto entity = registry.createEntity();
        descriptor.set(registry, entity, Json::object());
        const auto snapshot = descriptor.get(registry, entity);
        Json defaults = Json::object(), enums = Json::object();
        for (const auto& [field, value] : snapshot.items())
        {
            try { descriptor.validate(registry, entity, {{field, value}}); }
            catch (const std::invalid_argument&) { continue; } // Derived/read-only.
            defaults[field] = value;
            if (!value.is_string()) continue;
            Json choices = Json::array();
            for (const auto& choice : candidates)
            {
                try { descriptor.validate(registry, entity, {{field, choice}}); choices.push_back(choice); }
                catch (const std::invalid_argument&) {}
            }
            if (!choices.empty() && choices.size() < candidates.size()) enums[field] = choices;
        }
        if (name == "SpriteRenderer") defaults["texture"] = ""; // JSON resource selector.
        catalog[name] = {{"defaults", defaults}, {"enums", enums}};
    }
    return catalog;
}

core::Result<bool> EntityAuthoring::validatePrefab(const nlohmann::json& document)
{
    try
    {
        requireFields(document, {"root", "entities"});
        const auto& root = document.at("root");
        if (!root.is_string() || root.get<std::string>().empty() || root == "@scene")
            throw std::invalid_argument("Root must be a nonempty entity key other than @scene");
        const auto& entities = document.at("entities");
        if (!entities.is_array() || entities.size() != 1)
            throw std::invalid_argument("This editor supports exactly one entity per prefab");
        const auto& authored = entities[0];
        requireFields(authored, {"key", "name", "components"});
        if (authored.at("key") != root) throw std::invalid_argument("Entity key must match prefab root");
        if (authored.contains("name") && !authored["name"].is_string())
            throw std::invalid_argument("Entity name must be a string");
        const auto& components = authored.at("components");
        if (!components.is_object()) throw std::invalid_argument("Components must be an object");
        entity::Registry registry;
        const auto entity = registry.createEntity();
        for (const auto& [name, values] : components.items())
        {
            try
            {
                const auto found = componentdata::descriptors().find(name);
                if (found == componentdata::descriptors().end() || !found->second.set)
                    throw std::invalid_argument("Unknown or read-only component type");
                auto patch = detail::adaptSceneComponent(name, values).values;
                // Serialized references use authored keys/file positions, never
                // registry-local IDs. A single-entity prefab can reference itself.
                if (name == "Animator" && patch.contains("targetEntities"))
                {
                    auto& targets = patch["targetEntities"];
                    if (!targets.is_object()) throw std::invalid_argument("targetEntities must be an object");
                    for (auto& target : targets)
                    {
                        if (target != root && !(target.is_number_integer() && target == 1))
                            throw std::invalid_argument("Animator target must reference this entity's key");
                        target = entity.getID();
                    }
                }
                found->second.validate(registry, entity, patch);
            }
            catch (const std::exception& error) { throw std::invalid_argument(name + ": " + error.what()); }
        }
        return core::Result<bool>::success(true);
    }
    catch (const std::exception& error) { return core::Result<bool>::error(error.what()); }
}

nlohmann::json EntityAuthoring::canonicalPrefab(const nlohmann::json& document)
{
    return canonicalPrefab(document, componentCatalog());
}

nlohmann::json EntityAuthoring::canonicalPrefab(const nlohmann::json& document, const nlohmann::json& catalog)
{
    const auto valid = validatePrefab(document);
    if (!valid.ok()) throw std::invalid_argument(valid.error());
    auto result = document;
    for (auto& [name, source] : result["entities"][0]["components"].items())
    {
        const auto adapted = detail::adaptSceneComponent(name, source);
        Json values = catalog.at(name).at("defaults");
        for (const auto& [field, value] : adapted.values.items()) values[field] = value;
        if (adapted.texture) values["texture"] = source.at("texture");
        if (adapted.legacyMass) values["mass"] = source.at("mass");
        source = std::move(values);
    }
    return result;
}
}
