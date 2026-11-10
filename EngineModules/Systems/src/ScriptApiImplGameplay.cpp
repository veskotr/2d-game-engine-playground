#include <sle/engine/ScriptApiImpl.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/platform/Input.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/Transform.hpp>

#include <cmath>
#include <unordered_set>
#include <vector>

namespace sle {

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

    // Scripts run before the transform pass. Compose current local values so a
    // mutation earlier in this callback is visible immediately, including ancestors.
    std::vector<sle::entity::Entity> ancestors;
    std::unordered_set<uint32_t> visited;
    for (sle::entity::Entity current(entity.id); current.valid(); current = scene.getParent(current))
    {
        if (!registry.hasEntity(current) || !visited.insert(current.getID()).second)
            return nullptr;
        ancestors.push_back(current);
    }
    glm::vec2 position{0};
    glm::vec2 scale{1};
    float rotation = 0;
    for (auto it = ancestors.rbegin(); it != ancestors.rend(); ++it)
    {
        const auto* local = registry.getComponent<components::TransformComponent>(*it);
        if (!local)
            continue;
        const auto offset = scale * local->getPosition();
        const float c = std::cos(rotation);
        const float s = std::sin(rotation);
        position += glm::vec2(offset.x * c - offset.y * s, offset.x * s + offset.y * c);
        scale *= local->getScale();
        rotation += local->getRotation();
    }
    return {{"position", {{"x", position.x}, {"y", position.y}}},
            {"rotation", rotation},
            {"scale", {{"x", scale.x}, {"y", scale.y}}}};
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
