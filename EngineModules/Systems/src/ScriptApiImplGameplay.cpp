#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/PrefabLoader.hpp>
#include <sle/platform/Input.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/TransformMath.hpp>

#include <cmath>
#include <vector>

namespace sle {

scripting::ScriptEntityRef ScriptApiImpl::getSceneEntity()
{
    return {runtime.getScene().ensureSceneEntity().getID()};
}

scripting::ScriptEntityRef ScriptApiImpl::instantiatePrefab(const std::string& path,const std::string& key,
    const nlohmann::json& overrides,scripting::ScriptEntityRef parent,std::string& error)
{
    error.clear();
    const auto result=PrefabLoader::instantiate(path,runtime.getScene(),key,overrides,entity::Entity(parent.id));
    if(!result.ok()) {error=result.error();return {};}
    return {result.value().getID()};
}


scripting::ScriptEntityRef ScriptApiImpl::findEntityByKey(const std::string& key) const
{
    return {runtime.getScene().findEntityByKey(key).getID()};
}

bool ScriptApiImpl::setTransformScale(scripting::ScriptEntityRef entity, const glm::vec2& scale)
{
    if (!isEntityAlive(entity) || !std::isfinite(scale.x) || !std::isfinite(scale.y))
        return false;
    auto* transform = runtime.getScene().getRegistry().getComponent<components::TransformComponent>(
        sle::entity::Entity(entity.id));
    if (!transform)
        return false;
    transform->setScale(scale);
    return true;
}

bool ScriptApiImpl::getTransformRotation(scripting::ScriptEntityRef entity, float& out) const
{
    if (!isEntityAlive(entity))
        return false;
    const auto* transform = runtime.getScene().getRegistry().getComponent<components::TransformComponent>(
        sle::entity::Entity(entity.id));
    if (!transform)
        return false;
    out = transform->getRotation();
    return true;
}

bool ScriptApiImpl::setTransformRotation(scripting::ScriptEntityRef entity, float rotation)
{
    if (!isEntityAlive(entity) || !std::isfinite(rotation))
        return false;
    auto* transform = runtime.getScene().getRegistry().getComponent<components::TransformComponent>(
        sle::entity::Entity(entity.id));
    if (!transform)
        return false;
    transform->setRotation(rotation);
    return true;
}

nlohmann::json ScriptApiImpl::getWorldTransform(scripting::ScriptEntityRef entity) const
{
    if (!isEntityAlive(entity))
        return nullptr;
    auto& scene = runtime.getScene();
    const auto& registry = scene.getRegistry();
    if (!registry.getComponent<components::TransformComponent>(sle::entity::Entity(entity.id)))
        return nullptr;

    // Share the live hierarchy contract with physics and TransformSystem.
    const auto world=sle::entity::transformMath::currentWorld(scene,sle::entity::Entity(entity.id));
    return {{"position", {{"x", world.position.x}, {"y", world.position.y}}},
            {"rotation", world.rotation},
            {"scale", {{"x", world.scale.x}, {"y", world.scale.y}}}};
}

std::vector<uint32_t> ScriptApiImpl::getChildren(scripting::ScriptEntityRef entity) const
{
    std::vector<uint32_t> result;
    if (!isEntityAlive(entity))
        return result;
    for (const auto child : runtime.getScene().getChildren(sle::entity::Entity(entity.id)))
        if (isEntityAlive({child.getID()}))
            result.push_back(child.getID());
    return result;
}

glm::dvec2 ScriptApiImpl::getMouseDelta() const
{
    return input::Input::getMouse().delta;
}

bool ScriptApiImpl::isMouseButtonDown(int button) const
{
    const auto& mouse = input::Input::getMouse();
    switch (button)
    {
    case GLFW_MOUSE_BUTTON_LEFT: return mouse.left;
    case GLFW_MOUSE_BUTTON_RIGHT: return mouse.right;
    case GLFW_MOUSE_BUTTON_MIDDLE: return mouse.middle;
    default: return false;
    }
}

bool ScriptApiImpl::isMouseButtonPressed(int button) const
{
    const auto& mouse = input::Input::getMouse();
    switch (button)
    {
    case GLFW_MOUSE_BUTTON_LEFT: return mouse.leftPressed;
    case GLFW_MOUSE_BUTTON_RIGHT: return mouse.rightPressed;
    case GLFW_MOUSE_BUTTON_MIDDLE: return mouse.middlePressed;
    default: return false;
    }
}

bool ScriptApiImpl::isMouseButtonReleased(int button) const
{
    const auto& mouse = input::Input::getMouse();
    switch (button)
    {
    case GLFW_MOUSE_BUTTON_LEFT: return mouse.leftReleased;
    case GLFW_MOUSE_BUTTON_RIGHT: return mouse.rightReleased;
    case GLFW_MOUSE_BUTTON_MIDDLE: return mouse.middleReleased;
    default: return false;
    }
}

bool ScriptApiImpl::getGravityScale(scripting::ScriptEntityRef entity, float& out) const
{
    if (!isEntityAlive(entity))
        return false;
    const auto* body = runtime.getScene().getRegistry().getComponent<components::RigidBodyComponent>(
        sle::entity::Entity(entity.id));
    if (!body)
        return false;
    out = body->gravityScale;
    return true;
}

} // namespace sle
