#include <sle/engine/SceneLoader.hpp>
#include <sle/engine/PrefabPackage.hpp>
#include <algorithm>
#include <cctype>
#include <sle/engine/TiledMapLoader.hpp>
#include <sle/core/Log.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include "ComponentRegistry.hpp"
#include "SceneComponentAdapter.hpp"
#include "SceneReferences.hpp"
#include "PrefabExpansion.hpp"

#include <nlohmann/json.hpp>
#include <filesystem>
#include <fstream>
#include <string>

namespace sle {
sle::core::Result<bool> SceneLoader::load(const std::string& jsonPath, sle::entity::Scene& scene)
{
    using Json = nlohmann::json;
    using Result = sle::core::Result<bool>;
    auto extension = std::filesystem::path(jsonPath).extension().string();
    std::transform(extension.begin(), extension.end(), extension.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    if (extension == ".slescene") {
        const auto package = PrefabPackage::readScene(jsonPath);
        if (!package.ok()) return Result::error(package.error());
        return loadData(package.value(), scene, jsonPath);
    }
    std::ifstream file(jsonPath);
    if (!file.is_open()) return Result::error("Failed to open scene JSON file: " + jsonPath);

    Json root;
    try { file >> root; }
    catch (const std::exception& ex) {
        return Result::error("Malformed scene JSON in '" + jsonPath + "': " + ex.what());
    }
    return loadData(root, scene, jsonPath);
}

sle::core::Result<bool> SceneLoader::loadData(const nlohmann::json& data, sle::entity::Scene& scene, const std::string& jsonPath)
{
    using Json = nlohmann::json;
    using Result = sle::core::Result<bool>;
    if (!data.is_object()) return Result::error("Scene JSON root must be an object: " + jsonPath);
    const auto expanded = detail::expandScenePrefabs(data, jsonPath);
    if (!expanded.ok()) return Result::error(expanded.error());
    const Json& root = expanded.value();
    const bool hasTiledMap = root.contains("tiledMap") && root["tiledMap"].is_string();
    const bool hasEntities = root.contains("entities") && root["entities"].is_array();
    const bool hasSceneComponents = root.contains("components");
    if (!hasTiledMap && !hasEntities && !hasSceneComponents)
        return Result::error("Scene JSON must contain a 'tiledMap' string and/or an 'entities' array: " + jsonPath);

    try
    {
        if (hasSceneComponents) {
            if (!root["components"].is_object()) return Result::error("Scene '"+jsonPath+"': components must be an object");
            scene.ensureSceneEntity();
        }
        if (hasTiledMap)
        {
            const auto mapPath = std::filesystem::path(jsonPath).parent_path() / root["tiledMap"].get<std::string>();
            const auto loaded = TiledMapLoader::load(componentdata::resolveAssetPath(mapPath.lexically_normal().generic_string()), scene);
            if (!loaded.ok()) return loaded;
        }
        Json entities = hasEntities ? root["entities"] : Json::array();
        const auto sceneIndex = hasSceneComponents ? std::optional<std::size_t>(entities.size()) : std::nullopt;
        if (hasSceneComponents) entities.push_back({{"key","@scene"},{"components",root["components"]}});
        const auto authoredCount=data.contains("entities") && data["entities"].is_array() ? data["entities"].size() : 0;
        // Imported prefab entries and the implicit scene root cannot turn an
        // invalid authored ordinal into a different, accidentally valid target.
        detail::SceneReferences references(entities, scene, jsonPath, sceneIndex, authoredCount);
        const auto created = references.createEntities();
        if (!created.ok()) return created;
        auto& registry = scene.getRegistry();
        const auto& codecs = componentdata::descriptors();
        for (std::size_t index = 0; index < entities.size(); ++index)
        {
            const auto& definition = entities[index];
            if (!definition.is_object()) {
                core::Log::warn("Scene '{}': entity[{}] is not an object, skipping", jsonPath, index);
                continue;
            }
            const std::string entityName = definition.value("name", "unnamed_" + std::to_string(index));
            const std::string context = "Scene '" + jsonPath + "', entity[" + std::to_string(index) + "] '" + entityName + "'";
            if (!definition.contains("components")) continue;
            if (!definition["components"].is_object())
                return Result::error(context + ": components must be an object");
            const auto entity = references.entityAt(index);
            for (const auto& [name, source] : definition["components"].items())
            {
                const auto codec = codecs.find(name);
                if (codec == codecs.end()) {
                    core::Log::warn("{}: unknown component type '{}', skipping", context, name);
                    continue;
                }
                try
                {
                    if (!codec->second.set) throw std::invalid_argument("Read-only runtime component");
                    auto patch = detail::adaptSceneComponent(name, source);
                    const auto resolved = references.resolveComponentReferences(index, name, patch.values);
                    if (!resolved.ok()) return resolved;
                    // Validate the complete copy before publishing component data or
                    // loading a texture. Both JSON and Lua invoke these same codecs.
                    (void)codec->second.validate(registry, entity, patch.values);
                    codec->second.set(registry, entity, patch.values);
                    if (patch.legacyMass)
                        registry.getComponent<components::RigidBodyComponent>(entity)->mass = *patch.legacyMass;
                    if (patch.texture) {
                        auto texture = core::Resources::create<renderer::Texture>(*patch.texture, *patch.texture);
                        if (texture)
                            registry.getComponent<components::SpriteRenderer>(entity)->region.texture = std::move(texture);
                        else
                            core::Log::warn("{}: failed to load texture '{}'", context, *patch.texture);
                    }
                }
                catch (const std::exception& ex) {
                    return Result::error(context + ", component '" + name + "': " + ex.what());
                }
            }
        }
        return references.resolveLinks();
    }
    catch (const std::exception& ex) {
        return Result::error("Failed to load scene '" + jsonPath + "': " + ex.what());
    }
}
} // namespace sle
