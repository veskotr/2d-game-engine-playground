#include <sle/engine/ScriptApiImpl.hpp>

#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/platform/Input.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/Transform.hpp>
#include <vector>

namespace sle {

float ScriptApiImpl::getDeltaTime() const
{
    return runtime.getDeltaTime();
}

glm::vec2 ScriptApiImpl::getWindowSize() const
{
    return runtime.getWindowSize();
}

sle::scripting::ScriptEntityRef ScriptApiImpl::createEntity()
{
    auto entity = runtime.getScene().createEntity();
    runtime.getScene().getRegistry().addComponent<components::TransformComponent>(entity);
    return {entity.getID()};
}

bool ScriptApiImpl::isEntityAlive(sle::scripting::ScriptEntityRef entity) const
{
    return runtime.getScene().getRegistry().hasEntity(sle::entity::Entity(entity.id));
}

void ScriptApiImpl::destroyEntity(sle::scripting::ScriptEntityRef entity)
{
    runtime.getScene().destroyEntity(sle::entity::Entity(entity.id));
}

uint32_t ScriptApiImpl::getChildCount(sle::scripting::ScriptEntityRef parent) const
{
    if (!isEntityAlive(parent))
        return 0;

    return static_cast<uint32_t>(runtime.getScene().getChildren(sle::entity::Entity(parent.id)).size());
}

uint32_t ScriptApiImpl::destroyChildren(sle::scripting::ScriptEntityRef parent)
{
    if (!isEntityAlive(parent))
        return 0;

    const sle::entity::Entity parentEntity(parent.id);
    const auto& children = runtime.getScene().getChildren(parentEntity);
    std::vector<sle::entity::Entity> childrenCopy(children.begin(), children.end());

    for (sle::entity::Entity child : childrenCopy)
        runtime.getScene().destroyEntity(child);

    return static_cast<uint32_t>(childrenCopy.size());
}

bool ScriptApiImpl::setParent(
    sle::scripting::ScriptEntityRef child,
    sle::scripting::ScriptEntityRef parent)
{
    if (!isEntityAlive(child))
        return false;

    const sle::entity::Entity childEntity(child.id);
    const sle::entity::Entity parentEntity(parent.id);

    if (parentEntity.valid() && !isEntityAlive(parent))
        return false;

    runtime.getScene().setParent(childEntity, parentEntity);

    if (auto* transform = runtime.getScene().getRegistry().getComponent<components::TransformComponent>(childEntity))
        transform->setParent(parentEntity);

    return true;
}

sle::scripting::ScriptEntityRef ScriptApiImpl::getParent(sle::scripting::ScriptEntityRef entity) const
{
    if (!isEntityAlive(entity))
        return {};

    const auto parent = runtime.getScene().getParent(sle::entity::Entity(entity.id));
    return {parent.getID()};
}

bool ScriptApiImpl::getTransformPosition(
    sle::scripting::ScriptEntityRef entity,
    glm::vec2& outPosition) const
{
    if (!isEntityAlive(entity))
        return false;

    const auto* transform = runtime.getScene().getRegistry().getComponent<components::TransformComponent>(
        sle::entity::Entity(entity.id));
    if (!transform)
        return false;

    outPosition = transform->getPosition();
    return true;
}

bool ScriptApiImpl::setTransformPosition(
    sle::scripting::ScriptEntityRef entity,
    const glm::vec2& position)
{
    if (!isEntityAlive(entity))
        return false;

    auto* transform = runtime.getScene().getRegistry().getComponent<components::TransformComponent>(
        sle::entity::Entity(entity.id));
    if (!transform)
        return false;

    transform->setPosition(position);
    return true;
}

bool ScriptApiImpl::getTransformScale(
    sle::scripting::ScriptEntityRef entity,
    glm::vec2& outScale) const
{
    if (!isEntityAlive(entity))
        return false;

    const auto* transform = runtime.getScene().getRegistry().getComponent<components::TransformComponent>(
        sle::entity::Entity(entity.id));
    if (!transform)
        return false;

    outScale = transform->getScale();
    return true;
}

bool ScriptApiImpl::isKeyDown(int key) const
{
    return sle::input::Input::isKeyDown(key);
}

bool ScriptApiImpl::isKeyPressed(int key) const
{
    return sle::input::Input::isKeyPressed(key);
}

bool ScriptApiImpl::isKeyReleased(int key) const
{
    return sle::input::Input::isKeyReleased(key);
}

glm::dvec2 ScriptApiImpl::getMousePosition() const
{
    return sle::input::Input::getMouse().position;
}

glm::vec2 ScriptApiImpl::getCameraPosition() const
{
    return runtime.getCameraPosition();
}

void ScriptApiImpl::setCameraPosition(const glm::vec2& position)
{
    runtime.setCameraPosition(position);
}

void ScriptApiImpl::moveCamera(const glm::vec2& delta)
{
    runtime.moveCamera(delta);
}

float ScriptApiImpl::getCameraZoom() const
{
    return runtime.getCameraZoom();
}

void ScriptApiImpl::setCameraZoom(float zoom)
{
    runtime.setCameraZoom(zoom);
}

uint32_t ScriptApiImpl::loadTexture(const std::string& assetPath)
{
    auto texture = sle::core::Resources::create<sle::renderer::Texture>(assetPath, assetPath);
    if (!texture)
        return 0;

    return texture->getID();
}

bool ScriptApiImpl::setSpriteTexture(sle::scripting::ScriptEntityRef entity, const std::string& assetPath)
{
    if (!isEntityAlive(entity))
        return false;

    auto texture = sle::core::Resources::create<sle::renderer::Texture>(assetPath, assetPath);
    if (!texture)
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* sprite = registry.getComponent<components::SpriteRenderer>(rawEntity);
    if (!sprite)
        sprite = &registry.addComponent<components::SpriteRenderer>(rawEntity);

    sprite->region.texture = texture;
    sprite->region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    return true;
}

bool ScriptApiImpl::hasScene(const std::string& sceneName) const
{
    return runtime.hasScene(sceneName);
}

bool ScriptApiImpl::switchScene(const std::string& sceneName)
{
    return runtime.requestSceneSwitch(sceneName).ok();
}

std::string ScriptApiImpl::getCurrentSceneName() const
{
    return runtime.getCurrentSceneName();
}

void ScriptApiImpl::log(const std::string& message)
{
    sle::core::Log::info("[Script] {}", message);
}

void ScriptApiImpl::warn(const std::string& message)
{
    sle::core::Log::warn("[Script] {}", message);
}

void ScriptApiImpl::error(const std::string& message)
{
    sle::core::Log::error("[Script] {}", message);
}

} // namespace sle
