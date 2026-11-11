#pragma once

#include <sle/core/Result.hpp>
#include <sle/scene/Scene.hpp>
#include <nlohmann/json.hpp>
#include <string>
#include <unordered_map>
#include <vector>
#include <optional>

namespace sle::detail {

// Loader-local reference table: authored positions/keys never become runtime IDs.
// Non-object slots stay empty so skipping an entry cannot silently retarget a link.
class SceneReferences
{
public:
    SceneReferences(const nlohmann::json& definitions, entity::Scene& scene, const std::string& path, std::optional<std::size_t> sceneIndex = {},
        std::optional<std::size_t> authoredCount = {})
        : definitions(definitions), scene(scene), path(path), entities(definitions.size()), sceneIndex(sceneIndex),
          authoredCount(authoredCount.value_or(definitions.size())) {}

    core::Result<bool> createEntities()
    {
        for (std::size_t i = 0; i < definitions.size(); ++i)
        {
            const auto& definition = definitions[i];
            if (!definition.is_object()) continue;
            entities[i] = sceneIndex && i == *sceneIndex ? scene.ensureSceneEntity() : scene.createEntity();
            if (!definition.contains("key")) continue;
            const auto& key = definition["key"];
            if (!key.is_string() || key.get_ref<const std::string&>().empty())
                return core::Result<bool>::error(context(i, "key") + " must be a nonempty string");
            const auto& value = key.get_ref<const std::string&>();
            if (!scene.setEntityKey(entities[i], value))
                return core::Result<bool>::error(context(i, "key") + ": duplicate key '" + value + "'");
            keys.emplace(value, entities[i]);
        }
        return core::Result<bool>::success(true);
    }

    entity::Entity entityAt(std::size_t index) const { return entities.at(index); }

    core::Result<bool> resolveComponentReferences(std::size_t index, const std::string& name,
        nlohmann::json& patch) const
    {
        if (name != "Animator" || !patch.contains("targetEntities"))
            return core::Result<bool>::success(true);
        auto& targets = patch["targetEntities"];
        if (!targets.is_object())
            return core::Result<bool>::error(context(index, "Animator.targetEntities") + " must be an object");
        for (auto& [alias, reference] : targets.items())
        {
            const auto target = resolve(reference, context(index, "Animator.targetEntities." + alias));
            if (!target.ok()) return core::Result<bool>::error(target.error());
            reference = target.value().getID();
        }
        return core::Result<bool>::success(true);
    }

    core::Result<bool> resolveLinks()
    {
        for (std::size_t i = 0; i < entities.size(); ++i)
        {
            const auto entity = entities[i];
            if (!entity.valid()) continue;
            const auto& definition = definitions[i];
            if (definition.contains("parent") && !definition["parent"].is_null())
            {
                const auto parent = resolve(definition["parent"], context(i, "parent"));
                if (!parent.ok()) return core::Result<bool>::error(parent.error());
                if (!scene.setParent(entity, parent.value()))
                    return core::Result<bool>::error(context(i, "parent") + ": self-parenting or hierarchy cycle");
            }
        }
        return core::Result<bool>::success(true);
    }

private:
    core::Result<entity::Entity> resolve(const nlohmann::json& reference, const std::string& where) const
    {
        using Result = core::Result<entity::Entity>;
        if (reference.is_string())
        {
            const auto& key = reference.get_ref<const std::string&>();
            const auto it = keys.find(key);
            if (it != keys.end()) return Result::success(it->second);
            return Result::error(where + ": unknown entity key '" + key + "'");
        }
        // Old files used first-load runtime IDs, which coincided with 1-based
        // file positions. Remap those positions for every load, including reloads.
        if (reference.is_number_unsigned() || (reference.is_number_integer() && reference.get<int64_t>() > 0))
        {
            const auto position = reference.get<uint64_t>();
            if (position > 0 && position <= authoredCount && position <= entities.size() && entities[position - 1].valid())
                return Result::success(entities[position - 1]);
        }
        return Result::error(where + ": expected an entity key or a positive 1-based JSON entity position within this file");
    }

    std::string context(std::size_t index, const std::string& field) const
    {
        return "Scene '" + path + "', entity[" + std::to_string(index) + "]." + field;
    }

    const nlohmann::json& definitions;
    entity::Scene& scene;
    const std::string& path;
    std::vector<entity::Entity> entities;
    std::unordered_map<std::string, entity::Entity> keys;
    std::optional<std::size_t> sceneIndex;
    std::size_t authoredCount;
};
} // namespace sle::detail
