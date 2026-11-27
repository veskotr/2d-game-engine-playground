#include <sle/engine/EntityAuthoring.hpp>
#include "ComponentRegistry.hpp"
#include "SceneComponentAdapter.hpp"
#include <filesystem>
#include <stdexcept>
#include <utility>

namespace sle {
namespace {
using Json = nlohmann::json;

void addCatalogMetadata(const std::string& component, Json& entry)
{
    namespace constraints = lua::components;
    using constraints::bitMaskMaximum;
    using constraints::enumChoices;
    using constraints::nonnegativeMinimum;
    using constraints::positiveMinimum;
    using constraints::signedIntegerMaximum;
    using constraints::signedIntegerMinimum;
    using constraints::unitMaximum;
    using constraints::unitMinimum;
    using constraints::unsignedIntegerMinimum;
    Json fields = Json::array();
    const auto add = [&](Json path, const char* type) -> Json& {
        fields.push_back({{"path", std::move(path)}, {"type", type}});
        return fields.back();
    };
    const auto field = [&](const char* name, const char* type) -> Json& { return add({name}, type); };
    const auto setEnum = [&](const char* name, std::string_view kind) {
        Json choices = Json::array();
        for (const auto value : enumChoices(kind)) choices.push_back(std::string(value));
        entry["enums"][name] = choices;
        field(name, "enum")["choices"] = std::move(choices);
    };
    const Json positive = {{"min", positiveMinimum}, {"minExclusive", true}};
    const Json nonnegative = {{"min", nonnegativeMinimum}};
    const auto axes = [&](const char* name, const Json& bounds = nullptr) {
        for (const auto* axis : {"x", "y"})
        {
            auto& child = add({name, axis}, "number");
            if (!bounds.is_null()) child["hard"] = bounds;
        }
    };
    const auto integer = [&](const char* name, int min, int max) -> Json& {
        auto& result = field(name, "integer");
        result["hard"] = {{"min", min}, {"max", max}};
        result["ui"] = {{"step", 1}};
        return result;
    };

    if (component == "Tilemap")
    {
        field("asset", "resource");
        integer("sortingOffset", signedIntegerMinimum, signedIntegerMaximum);
    }
    else if (component == "Animator") setEnum("loopMode", "loop");
    else if (component == "UI")
    {
        setEnum("spaceMode", "space");
        field("bindingScope", "string");
    }
    else if (component == "ChunkActivation") setEnum("mode", "mode");
    else if (component == "Transform")
    {
        field("position", "vector")["order"] = 10;
        auto& rotation = field("rotation", "number");
        rotation["order"] = 20;
        rotation["label"] = "Rotation (rad)";
        rotation["ui"] = {{"unit", "rad"}};
        field("scale", "vector")["order"] = 30;
        axes("position");
        axes("scale");
    }
    else if (component == "RigidBody")
    {
        setEnum("bodyType", "body");
        field("linearDamping", "number")["hard"] = nonnegative;
        field("angularDamping", "number")["hard"] = nonnegative;
        field("gravityScale", "number");
        field("velocity", "vector");
        axes("velocity");
        field("angularVelocity", "number");
        field("fixedRotation", "boolean");
        field("enabled", "boolean");
    }
    else if (component == "Camera2D")
    {
        entry["groups"] = {{"view", {{"label", "View"}, {"order", 10}}}};
        field("enabled", "boolean")["order"] = 10;
        field("primary", "boolean")["order"] = 20;
        fields.push_back({{"path", {"zoom"}}, {"type", "number"}, {"hard", positive},
            {"ui", {{"min", 0.25}, {"max", 4}, {"step", 0.25}, {"precision", 2}, {"unit", "\xC3\x97"}, {"slider", true}}},
            {"group", "view"}, {"order", 10}, {"help", "Sets the camera zoom multiplier."},
            {"visibleWhen", {{"path", {"enabled"}}, {"equals", true}}}});
        auto& viewSize = field("viewSize", "vector");
        viewSize["group"] = "view";
        viewSize["order"] = 20;
        axes("viewSize", positive);
    }
    else if (component == "SpriteRenderer")
    {
        fields.push_back({{"path", {"texture"}}, {"type", "resource"}, {"drawer", "texture"}, {"order", 10}});
        fields.push_back({{"path", {"color"}}, {"type", "vector"}, {"drawer", "color"}, {"order", 20},
            {"axisLabels", {{"x", "R"}, {"y", "G"}, {"z", "B"}, {"w", "A"}}}});
        field("size", "vector")["order"] = 30;
        field("offset", "vector")["order"] = 40;
        integer("layer", signedIntegerMinimum, signedIntegerMaximum)["order"] = 50;
        field("visible", "boolean")["order"] = 60;
        fields.push_back({{"path", {"region"}}, {"type", "object"}, {"drawer", "structured"}, {"order", 70}});
        fields.push_back({{"path", {"region", "uv"}}, {"type", "vector"}, {"label", "UV rect"}, {"drawer", "uv"},
            {"axisLabels", {{"x", "U0"}, {"y", "V0"}, {"z", "U1"}, {"w", "V1"}}}});
        axes("size", positive);
        axes("offset");
        for (const auto* axis : {"x", "y", "z", "w"})
        {
            add({"color", axis}, "number");
            add({"region", "uv", axis}, "number");
        }
    }
    else if (component == "BoxCollider" || component == "CircleCollider")
    {
        if (component == "BoxCollider")
        {
            field("size", "vector")["order"] = 10;
            axes("size", positive);
        }
        else
        {
            auto& radius = field("radius", "number");
            radius["order"] = 10;
            radius["hard"] = positive;
        }
        field("offset", "vector")["order"] = 20;
        axes("offset");
        field("friction", "number")["hard"] = nonnegative;
        field("density", "number")["hard"] = nonnegative;
        field("restitution", "number")["hard"] = {{"min", unitMinimum}, {"max", unitMaximum}};
        integer("categoryBits", unsignedIntegerMinimum, bitMaskMaximum);
        integer("maskBits", unsignedIntegerMinimum, bitMaskMaximum);
        field("enabled", "boolean");
    }
    if (!fields.empty()) entry["fields"] = std::move(fields);
}

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

std::string EntityAuthoring::resolveStandaloneAsset(const std::string& path)
{
    const auto absolute = std::filesystem::absolute(componentdata::resolveAssetPath(path)).generic_u8string();
    return {absolute.begin(), absolute.end()};
}

nlohmann::json EntityAuthoring::componentCatalog()
{
    Json catalog = Json::object();
    for (const auto& [name, descriptor] : componentdata::descriptors())
    {
        if (!descriptor.set) continue;
        entity::Registry registry;
        const auto entity = registry.createEntity();
        descriptor.set(registry, entity, Json::object());
        const auto snapshot = descriptor.get(registry, entity);
        Json defaults = Json::object();
        for (const auto& [field, value] : snapshot.items())
        {
            try { descriptor.validate(registry, entity, {{field, value}}); }
            catch (const std::invalid_argument&) { continue; } // Derived/read-only.
            defaults[field] = value;
        }
        if (name == "SpriteRenderer") defaults["texture"] = ""; // JSON resource selector.
        auto& entry = catalog[name] = {{"defaults", std::move(defaults)}, {"enums", Json::object()}};
        addCatalogMetadata(name, entry);
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
        if (!adapted.regionId.empty()) values["regionId"] = adapted.regionId;
        if (adapted.legacyMass) values["mass"] = source.at("mass");
        source = std::move(values);
    }
    return result;
}
}
