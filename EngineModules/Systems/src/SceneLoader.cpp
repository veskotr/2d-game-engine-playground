#include <sle/engine/SceneLoader.hpp>

#include <sle/core/Log.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/AudioComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/UIComponent.hpp>

#include <nlohmann/json.hpp>

#include <algorithm>
#include <cctype>
#include <filesystem>
#include <fstream>
#include <string>

namespace sle {
namespace {

using Json = nlohmann::json;

std::string trim(std::string value)
{
    value.erase(
        value.begin(),
        std::find_if(value.begin(), value.end(), [](unsigned char ch) { return !std::isspace(ch); }));
    value.erase(
        std::find_if(value.rbegin(), value.rend(), [](unsigned char ch) { return !std::isspace(ch); }).base(),
        value.end());
    return value;
}

std::string resolveAssetPath(const std::string& relativePath)
{
    namespace fs = std::filesystem;

    fs::path path(relativePath);
    if (fs::exists(path))
        return path.lexically_normal().generic_string();

    fs::path current = fs::current_path();
    while (!current.empty())
    {
        fs::path candidate = current / relativePath;
        if (fs::exists(candidate))
            return candidate.lexically_normal().generic_string();

        if (current == current.root_path())
            break;
        current = current.parent_path();
    }

    return relativePath;
}

glm::vec2 readVec2OrWarn(const Json& obj, const char* key, const glm::vec2& fallback, const std::string& context)
{
    if (!obj.contains(key))
    {
        sle::core::Log::warn("{}: missing key '{}', using default", context, key);
        return fallback;
    }

    const auto& value = obj[key];
    if (!value.is_array() || value.size() < 2)
    {
        sle::core::Log::warn("{}: key '{}' must be a 2-element array, using default", context, key);
        return fallback;
    }

    return glm::vec2{value[0].get<float>(), value[1].get<float>()};
}

glm::vec4 readVec4OrWarn(const Json& obj, const char* key, const glm::vec4& fallback, const std::string& context)
{
    if (!obj.contains(key))
    {
        sle::core::Log::warn("{}: missing key '{}', using default", context, key);
        return fallback;
    }

    const auto& value = obj[key];
    if (!value.is_array() || value.size() < 4)
    {
        sle::core::Log::warn("{}: key '{}' must be a 4-element array, using default", context, key);
        return fallback;
    }

    return glm::vec4{value[0].get<float>(), value[1].get<float>(), value[2].get<float>(), value[3].get<float>()};
}

template <typename T>
T readValueOrWarn(const Json& obj, const char* key, const T& fallback, const std::string& context)
{
    if (!obj.contains(key))
    {
        sle::core::Log::warn("{}: missing key '{}', using default", context, key);
        return fallback;
    }

    return obj[key].get<T>();
}

sle::components::BodyType readBodyTypeOrWarn(const Json& obj, const std::string& context)
{
    if (!obj.contains("bodyType"))
    {
        sle::core::Log::warn("{}: missing key 'bodyType', using default", context);
        return sle::components::BodyType::Dynamic;
    }

    const std::string bodyType = obj["bodyType"].get<std::string>();
    if (bodyType == "Static")
        return sle::components::BodyType::Static;
    if (bodyType == "Dynamic")
        return sle::components::BodyType::Dynamic;
    if (bodyType == "Kinematic")
        return sle::components::BodyType::Kinematic;

    sle::core::Log::warn("{}: invalid bodyType '{}' (expected Static, Dynamic, or Kinematic), using default", context, bodyType);
    return sle::components::BodyType::Dynamic;
}

sle::components::UISpaceMode readSpaceModeOrWarn(const Json& obj, const std::string& context)
{
    if (!obj.contains("spaceMode"))
    {
        sle::core::Log::warn("{}: missing key 'spaceMode', using default", context);
        return sle::components::UISpaceMode::Screen;
    }

    const std::string spaceMode = obj["spaceMode"].get<std::string>();
    if (spaceMode == "Screen")
        return sle::components::UISpaceMode::Screen;
    if (spaceMode == "World")
        return sle::components::UISpaceMode::World;

    sle::core::Log::warn("{}: invalid spaceMode '{}' (expected Screen or World), using default", context, spaceMode);
    return sle::components::UISpaceMode::Screen;
}

void loadTransform(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::TransformComponent>(entity);
    component.setPosition(readVec2OrWarn(componentJson, "position", component.getPosition(), context));
    component.setRotation(readValueOrWarn<float>(componentJson, "rotation", component.getRotation(), context));
    component.setScale(readVec2OrWarn(componentJson, "scale", component.getScale(), context));
}

void loadSpriteRenderer(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::SpriteRenderer>(entity);

    if (componentJson.contains("texture"))
    {
        const std::string texturePath = resolveAssetPath(componentJson["texture"].get<std::string>());
        auto texture = sle::core::Resources::create<sle::renderer::Texture>(texturePath, texturePath);
        if (!texture)
        {
            sle::core::Log::warn("{}: failed to load texture '{}'", context, texturePath);
        }
        else
        {
            component.region.texture = texture;
            component.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
        }
    }

    component.color = readVec4OrWarn(componentJson, "color", component.color, context);
    component.layer = readValueOrWarn<int>(componentJson, "layer", component.layer, context);
}

void loadRigidBody(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::RigidBodyComponent>(entity);

    component.bodyType = readBodyTypeOrWarn(componentJson, context);
    component.mass = readValueOrWarn<float>(componentJson, "mass", component.mass, context);
    component.linearDamping = readValueOrWarn<float>(componentJson, "linearDamping", component.linearDamping, context);
    component.angularDamping = readValueOrWarn<float>(componentJson, "angularDamping", component.angularDamping, context);
    component.gravityScale = readValueOrWarn<float>(componentJson, "gravityScale", component.gravityScale, context);
    component.velocity = readVec2OrWarn(componentJson, "velocity", component.velocity, context);
    component.angularVelocity = readValueOrWarn<float>(componentJson, "angularVelocity", component.angularVelocity, context);
    component.fixedRotation = readValueOrWarn<bool>(componentJson, "fixedRotation", component.fixedRotation, context);
    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
}

void loadBoxCollider(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::BoxColliderComponent>(entity);

    component.offset = readVec2OrWarn(componentJson, "offset", component.offset, context);
    component.size = readVec2OrWarn(componentJson, "size", component.size, context);
    component.friction = readValueOrWarn<float>(componentJson, "friction", component.friction, context);
    component.restitution = readValueOrWarn<float>(componentJson, "restitution", component.restitution, context);
    component.density = readValueOrWarn<float>(componentJson, "density", component.density, context);
    component.categoryBits = readValueOrWarn<uint16_t>(componentJson, "categoryBits", component.categoryBits, context);
    component.maskBits = readValueOrWarn<uint16_t>(componentJson, "maskBits", component.maskBits, context);
    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
}

void loadCircleCollider(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::CircleColliderComponent>(entity);

    component.offset = readVec2OrWarn(componentJson, "offset", component.offset, context);
    component.radius = readValueOrWarn<float>(componentJson, "radius", component.radius, context);
    component.friction = readValueOrWarn<float>(componentJson, "friction", component.friction, context);
    component.restitution = readValueOrWarn<float>(componentJson, "restitution", component.restitution, context);
    component.density = readValueOrWarn<float>(componentJson, "density", component.density, context);
    component.categoryBits = readValueOrWarn<uint16_t>(componentJson, "categoryBits", component.categoryBits, context);
    component.maskBits = readValueOrWarn<uint16_t>(componentJson, "maskBits", component.maskBits, context);
    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
}

void loadBoxZone(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::BoxZoneComponent>(entity);

    component.offset = readVec2OrWarn(componentJson, "offset", component.offset, context);
    component.size = readVec2OrWarn(componentJson, "size", component.size, context);
    component.zoneId = readValueOrWarn<std::string>(componentJson, "zoneId", component.zoneId, context);
    component.categoryBits = readValueOrWarn<uint16_t>(componentJson, "categoryBits", component.categoryBits, context);
    component.maskBits = readValueOrWarn<uint16_t>(componentJson, "maskBits", component.maskBits, context);
    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
}

void loadCircleZone(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::CircleZoneComponent>(entity);

    component.offset = readVec2OrWarn(componentJson, "offset", component.offset, context);
    component.radius = readValueOrWarn<float>(componentJson, "radius", component.radius, context);
    component.zoneId = readValueOrWarn<std::string>(componentJson, "zoneId", component.zoneId, context);
    component.categoryBits = readValueOrWarn<uint16_t>(componentJson, "categoryBits", component.categoryBits, context);
    component.maskBits = readValueOrWarn<uint16_t>(componentJson, "maskBits", component.maskBits, context);
    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
}

void loadScript(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::ScriptComponent>(entity);

    if (!componentJson.contains("asset"))
    {
        sle::core::Log::warn("{}: missing key 'asset', using default", context);
    }
    else
    {
        component.scriptAsset = resolveAssetPath(componentJson["asset"].get<std::string>());
    }

    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
}

void loadAudio(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::AudioComponent>(entity);

    if (!componentJson.contains("asset"))
    {
        sle::core::Log::warn("{}: missing key 'asset', using default", context);
    }
    else
    {
        component.assetPath = resolveAssetPath(componentJson["asset"].get<std::string>());
    }

    component.loop = readValueOrWarn<bool>(componentJson, "loop", component.loop, context);
    component.volume = readValueOrWarn<float>(componentJson, "volume", component.volume, context);
    component.pitch = readValueOrWarn<float>(componentJson, "pitch", component.pitch, context);
    component.playRequested = readValueOrWarn<bool>(componentJson, "playRequested", component.playRequested, context);
    component.stopRequested = readValueOrWarn<bool>(componentJson, "stopRequested", component.stopRequested, context);
}

void loadUI(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::UIComponent>(entity);

    if (!componentJson.contains("layout"))
    {
        sle::core::Log::warn("{}: missing key 'layout', using default", context);
    }
    else
    {
        const std::string raw = trim(componentJson["layout"].get<std::string>());
        component.layoutAsset = raw.empty() ? std::string() : resolveAssetPath(raw);
    }

    if (!componentJson.contains("font"))
    {
        sle::core::Log::warn("{}: missing key 'font', using default", context);
    }
    else
    {
        const std::string raw = trim(componentJson["font"].get<std::string>());
        component.fontAsset = raw.empty() ? std::string() : resolveAssetPath(raw);
    }

    if (!componentJson.contains("behavior"))
    {
        sle::core::Log::warn("{}: missing key 'behavior', using default", context);
    }
    else
    {
        const std::string raw = trim(componentJson["behavior"].get<std::string>());
        component.behaviorAsset = raw.empty() ? std::string() : resolveAssetPath(raw);
    }

    component.spaceMode = readSpaceModeOrWarn(componentJson, context);
    component.visible = readValueOrWarn<bool>(componentJson, "visible", component.visible, context);
    component.layer = readValueOrWarn<uint32_t>(componentJson, "layer", component.layer, context);
    component.bindingScope = readValueOrWarn<std::string>(componentJson, "bindingScope", component.bindingScope, context);
}

void loadStateMachine(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::StateMachineComponent>(entity);

    if (!componentJson.contains("definition"))
    {
        sle::core::Log::warn("{}: missing key 'definition', using default", context);
    }
    else
    {
        component.definitionAsset = resolveAssetPath(componentJson["definition"].get<std::string>());
    }

    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
}

void loadAnimator(const Json& componentJson, sle::entity::Entity entity, sle::entity::Registry& registry, const std::string& context)
{
    auto& component = registry.addComponent<sle::components::AnimatorComponent>(entity);

    if (!componentJson.contains("clip"))
    {
        sle::core::Log::warn("{}: missing key 'clip', using default", context);
    }
    else
    {
        component.clipAsset = resolveAssetPath(componentJson["clip"].get<std::string>());
    }

    component.enabled = readValueOrWarn<bool>(componentJson, "enabled", component.enabled, context);
    component.playing = readValueOrWarn<bool>(componentJson, "playing", component.playing, context);
    component.speed = readValueOrWarn<float>(componentJson, "speed", component.speed, context);

    if (componentJson.contains("targetEntities") && componentJson["targetEntities"].is_object())
    {
        for (const auto& [targetName, targetValue] : componentJson["targetEntities"].items())
        {
            if (targetValue.is_number_unsigned())
            {
                component.targetEntities[targetName] = targetValue.get<uint32_t>();
            }
            else if (targetValue.is_number_integer())
            {
                const int64_t id = targetValue.get<int64_t>();
                if (id > 0)
                    component.targetEntities[targetName] = static_cast<uint32_t>(id);
            }
        }
    }

    if (componentJson.contains("stateClipMap") && componentJson["stateClipMap"].is_object())
    {
        for (const auto& [stateName, clipPathJson] : componentJson["stateClipMap"].items())
        {
            if (!clipPathJson.is_string())
                continue;
            component.stateClipMap[stateName] = resolveAssetPath(clipPathJson.get<std::string>());
        }
    }
}

} // namespace

sle::core::Result<bool> SceneLoader::load(const std::string& jsonPath, sle::entity::Scene& scene)
{
    std::ifstream file(jsonPath);
    if (!file.is_open())
        return sle::core::Result<bool>::error("Failed to open scene JSON file: " + jsonPath);

    Json root;
    try
    {
        file >> root;
    }
    catch (const std::exception& ex)
    {
        return sle::core::Result<bool>::error("Malformed scene JSON in '" + jsonPath + "': " + ex.what());
    }

    if (!root.is_object())
        return sle::core::Result<bool>::error("Scene JSON root must be an object: " + jsonPath);

    if (!root.contains("entities") || !root["entities"].is_array())
        return sle::core::Result<bool>::error("Scene JSON must contain an 'entities' array: " + jsonPath);

    auto& registry = scene.getRegistry();

    std::size_t entityIndex = 0;
    for (const auto& entityJson : root["entities"])
    {
        if (!entityJson.is_object())
        {
            ++entityIndex;
            sle::core::Log::warn("Scene '{}': entity[{}] is not an object, skipping", jsonPath, entityIndex - 1);
            continue;
        }

        const std::string entityName = entityJson.value("name", std::string("unnamed_") + std::to_string(entityIndex));
        const std::string entityContext = "Scene '" + jsonPath + "', entity '" + entityName + "'";

        auto entity = scene.createEntity();

        if (!entityJson.contains("components") || !entityJson["components"].is_object())
        {
            sle::core::Log::warn("{}: missing 'components' object, leaving entity empty", entityContext);
            ++entityIndex;
            continue;
        }

        for (const auto& [componentKey, componentJson] : entityJson["components"].items())
        {
            if (!componentJson.is_object())
            {
                sle::core::Log::warn("{}: component '{}' must be an object, skipping", entityContext, componentKey);
                continue;
            }

            const std::string componentContext = entityContext + ", component '" + componentKey + "'";

            if (componentKey == "Transform")
            {
                loadTransform(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "SpriteRenderer")
            {
                loadSpriteRenderer(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "RigidBody")
            {
                loadRigidBody(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "BoxCollider")
            {
                loadBoxCollider(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "CircleCollider")
            {
                loadCircleCollider(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "BoxZone")
            {
                loadBoxZone(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "CircleZone")
            {
                loadCircleZone(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "Script")
            {
                loadScript(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "Audio")
            {
                loadAudio(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "UI")
            {
                loadUI(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "StateMachine")
            {
                loadStateMachine(componentJson, entity, registry, componentContext);
            }
            else if (componentKey == "Animator")
            {
                loadAnimator(componentJson, entity, registry, componentContext);
            }
            else
            {
                sle::core::Log::warn("{}: unknown component type '{}', skipping", entityContext, componentKey);
            }
        }

        ++entityIndex;
    }

    return sle::core::Result<bool>::success(true);
}

} // namespace sle
