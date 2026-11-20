#pragma once

#include <nlohmann/json.hpp>
#include <sle/engine/InputActions.hpp>
#include <sle/platform/Input.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/renderer/Texture.hpp>
#include <limits>
#include <map>
#include <stdexcept>
#include <unordered_set>

namespace sle::detail {
inline void validateSortingLayers(const nlohmann::json& layers)
{
    if (!layers.is_array()) throw std::invalid_argument("Expected a sorting layer list");
    std::unordered_set<int> values;
    for (const auto& entry : layers)
    {
        if (!entry.is_object() || entry.size() != 2 || !entry.contains("value") || !entry.contains("label"))
            throw std::invalid_argument("Expected a sorting layer value and label");
        const auto& value = entry.at("value");
        const auto& label = entry.at("label");
        if (!value.is_number_integer() ||
            (value.is_number_unsigned()
                ? value.get<uint64_t>() > std::numeric_limits<int>::max()
                : value.get<int64_t>() < std::numeric_limits<int>::min() ||
                  value.get<int64_t>() > std::numeric_limits<int>::max()))
            throw std::invalid_argument("Expected a signed sorting layer integer");
        if (!label.is_string() || label.get_ref<const std::string&>().empty())
            throw std::invalid_argument("Expected a sorting layer label");
        if (!values.insert(value.get<int>()).second)
            throw std::invalid_argument("Duplicate sorting layer value");
    }
}

inline std::map<std::string, std::string> projectTags(const nlohmann::json& settings)
{
    std::map<std::string, std::string> tags;
    if (!settings.contains("tags")) return tags;
    const auto& definitions = settings.at("tags");
    if (!definitions.is_array()) throw std::invalid_argument("Expected a tag definition list");
    std::unordered_set<std::string> ids;
    for (const auto& definition : definitions) {
        if (!definition.is_object() || definition.size() != 2 ||
            !definition.contains("id") || !definition.at("id").is_string() ||
            !definition.contains("name") || !definition.at("name").is_string())
            throw std::invalid_argument("Expected a tag ID and name");
        const auto id = definition.at("id").get<std::string>();
        const auto name = definition.at("name").get<std::string>();
        if (id.empty() || name.empty() || !ids.insert(id).second || !tags.emplace(name, id).second)
            throw std::invalid_argument("Expected unique nonempty tag IDs and names");
    }
    return tags;
}

// This set matches the AWT-to-GLFW translation used for editor Play and capture.
inline bool supportedActionKey(int key)
{
    return (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) ||
           (key >= GLFW_KEY_0 && key <= GLFW_KEY_9) ||
           (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F12) ||
           (key >= GLFW_KEY_ESCAPE && key <= GLFW_KEY_END) ||
           (key >= GLFW_KEY_LEFT_SHIFT && key <= GLFW_KEY_RIGHT_SUPER) ||
           key == GLFW_KEY_SPACE || key == GLFW_KEY_APOSTROPHE || key == GLFW_KEY_COMMA ||
           key == GLFW_KEY_MINUS || key == GLFW_KEY_PERIOD || key == GLFW_KEY_SLASH ||
           key == GLFW_KEY_SEMICOLON || key == GLFW_KEY_EQUAL ||
           key == GLFW_KEY_LEFT_BRACKET || key == GLFW_KEY_BACKSLASH ||
           key == GLFW_KEY_RIGHT_BRACKET || key == GLFW_KEY_GRAVE_ACCENT || key == GLFW_KEY_KP_ENTER;
}

inline InputActionMap projectInputActions(const nlohmann::json& settings)
{
    InputActionMap actions;
    if (!settings.contains("inputActions")) return actions;
    const auto& entries = settings.at("inputActions");
    if (!entries.is_array()) throw std::invalid_argument("Expected an input action list");
    for (const auto& entry : entries)
    {
        if (!entry.is_object() || entry.size() != 2 ||
            !entry.contains("name") || !entry.at("name").is_string() ||
            !entry.contains("bindings") || !entry.at("bindings").is_array())
            throw std::invalid_argument("Expected an input action name and binding list");
        const auto& name = entry.at("name").get_ref<const std::string&>();
        if (name.empty()) throw std::invalid_argument("Expected a nonempty input action name");
        const auto [action, inserted] = actions.try_emplace(name);
        if (!inserted) throw std::invalid_argument("Duplicate input action: " + name);
        std::unordered_set<int> keys;
        for (const auto& binding : entry.at("bindings"))
        {
            if (!binding.is_object() || binding.size() != 2 ||
                !binding.contains("kind") || binding.at("kind") != "key" ||
                !binding.contains("key") || !binding.at("key").is_number_integer())
                throw std::invalid_argument("Expected a keyboard binding");
            const auto code = binding.at("key").get<uint64_t>();
            if (code > GLFW_KEY_LAST || !supportedActionKey(static_cast<int>(code)))
                throw std::invalid_argument("Unsupported input action key");
            const auto key = static_cast<int>(code);
            if (!keys.insert(key).second) throw std::invalid_argument("Duplicate input action key");
            action->second.push_back(key);
        }
    }
    return actions;
}

inline renderer::TextureFilter artStyleFilter(const nlohmann::json& settings)
{
    using Filter = renderer::TextureFilter;
    if (!settings.is_object()) throw std::invalid_argument("Expected a project settings object");
    for (const auto& [key, value] : settings.items()) {
        if (key == "collisionLayers" || key == "tags" || key == "inputActions") continue;
        if (key == "sortingLayers") {
            validateSortingLayers(value);
            continue;
        }
        if (key != "artStyle") throw std::invalid_argument("Unknown project setting: " + key);
        if (value != "pixelArt" && value != "smooth") throw std::invalid_argument("Invalid project art style");
    }
    if (!settings.contains("artStyle")) return Filter::Default;
    return settings.at("artStyle") == "pixelArt" ? Filter::Nearest : Filter::Linear;
}

inline physics::CollisionMatrix blockedCollisionPairs(const nlohmann::json& settings)
{
    physics::CollisionMatrix matrix{};
    if (!settings.contains("collisionLayers")) return matrix;
    const auto& layers = settings.at("collisionLayers");
    if (!layers.is_object()) throw std::invalid_argument("Expected collision layers object");
    for (const auto& [key, value] : layers.items())
    {
        if (key == "labels")
        {
            if (!value.is_array() || value.size() > matrix.size())
                throw std::invalid_argument("Expected up to 16 collision layer labels");
            for (const auto& label : value)
                if (!label.is_string()) throw std::invalid_argument("Expected collision layer label text");
        }
        else if (key == "blockedPairs")
        {
            const auto slot = [&](const nlohmann::json& input) {
                if (!input.is_number_integer() || input < 0 || input >= matrix.size())
                    throw std::invalid_argument("Collision layer slots must be integers from 0 to 15");
                return input.get<unsigned>();
            };
            if (!value.is_array()) throw std::invalid_argument("Expected blocked collision pair list");
            for (const auto& pair : value)
            {
                if (!pair.is_array() || pair.size() != 2)
                    throw std::invalid_argument("Expected two slots in blocked collision pair");
                const auto first = slot(pair[0]);
                const auto second = slot(pair[1]);
                matrix[first] |= static_cast<uint16_t>(1u << second);
                matrix[second] |= static_cast<uint16_t>(1u << first);
            }
        }
        else throw std::invalid_argument("Unknown collision layer setting: " + key);
    }
    return matrix;
}
}
