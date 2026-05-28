#include <sle/engine/ScriptApiImpl.hpp>

#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/platform/Input.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/events/CollisionEvents.hpp>
#include <sle/events/ZoneEvents.hpp>
#include <box2d/b2_world.h>
#include <box2d/b2_contact.h>
#include <box2d/b2_fixture.h>
#include <algorithm>
#include <utility>
#include <vector>

namespace sle {

namespace {

class FirstHitRayCastCallback : public b2RayCastCallback
{
public:
    bool hasHit = false;
    sle::scripting::PhysicsRaycastHit hit{};

    float ReportFixture(b2Fixture* fixture, const b2Vec2& point, const b2Vec2& normal, float fraction) override
    {
        hasHit = true;
        hit.entityId = static_cast<uint32_t>(fixture->GetBody()->GetUserData().pointer);
        hit.point = {point.x, point.y};
        hit.normal = {normal.x, normal.y};
        hit.fraction = fraction;

        // Clip the ray to this hit so Box2D finds the closest intersection.
        return fraction;
    }
};

class AllHitsRayCastCallback : public b2RayCastCallback
{
public:
    std::vector<sle::scripting::PhysicsRaycastHit> hits;

    float ReportFixture(b2Fixture* fixture, const b2Vec2& point, const b2Vec2& normal, float fraction) override
    {
        sle::scripting::PhysicsRaycastHit hit;
        hit.entityId = static_cast<uint32_t>(fixture->GetBody()->GetUserData().pointer);
        hit.point = {point.x, point.y};
        hit.normal = {normal.x, normal.y};
        hit.fraction = fraction;
        hits.push_back(hit);

        // Continue raycast to collect all hits.
        return 1.0f;
    }
};

} // namespace

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
    // Auto-cleanup all event subscriptions for this entity
    entitySubscriptions_.erase(entity.id);
    
    // Also remove any subscription ID mappings that point to this entity
    auto it = subscriptionIdToLocation_.begin();
    while (it != subscriptionIdToLocation_.end())
    {
        if (it->second.first == entity.id)
            it = subscriptionIdToLocation_.erase(it);
        else
            ++it;
    }

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

bool ScriptApiImpl::setStateMachineBool(
    sle::scripting::ScriptEntityRef entity,
    const std::string& key,
    bool value)
{
    if (!isEntityAlive(entity) || key.empty())
        return false;

    auto& registry = runtime.getScene().getRegistry();
    auto* sm = registry.getComponent<components::StateMachineComponent>(sle::entity::Entity(entity.id));
    if (!sm)
        return false;

    sm->boolParameters[key] = value;
    return true;
}

bool ScriptApiImpl::setStateMachineTrigger(
    sle::scripting::ScriptEntityRef entity,
    const std::string& key)
{
    if (!isEntityAlive(entity) || key.empty())
        return false;

    auto& registry = runtime.getScene().getRegistry();
    auto* sm = registry.getComponent<components::StateMachineComponent>(sle::entity::Entity(entity.id));
    if (!sm)
        return false;

    sm->triggers.insert(key);
    return true;
}

bool ScriptApiImpl::getStateMachineCurrentState(
    sle::scripting::ScriptEntityRef entity,
    std::string& outState) const
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    auto* sm = registry.getComponent<components::StateMachineComponent>(sle::entity::Entity(entity.id));
    if (!sm || !sm->initialized)
        return false;

    outState = sm->currentState;
    return true;
}

bool ScriptApiImpl::forceStateMachineState(
    sle::scripting::ScriptEntityRef entity,
    const std::string& stateName)
{
    if (!isEntityAlive(entity) || stateName.empty())
        return false;

    auto& registry = runtime.getScene().getRegistry();
    auto* sm = registry.getComponent<components::StateMachineComponent>(sle::entity::Entity(entity.id));
    if (!sm || !sm->definition)
        return false;

    if (!sm->definition->findState(stateName))
        return false;

    sm->previousState = sm->currentState;
    sm->currentState = stateName;
    sm->stateTimeSeconds = 0.0f;
    sm->initialized = true;
    return true;
}

bool ScriptApiImpl::isStateMachineInState(
    sle::scripting::ScriptEntityRef entity,
    const std::string& stateName) const
{
    if (!isEntityAlive(entity) || stateName.empty())
        return false;

    auto& registry = runtime.getScene().getRegistry();
    auto* sm = registry.getComponent<components::StateMachineComponent>(sle::entity::Entity(entity.id));
    if (!sm || !sm->initialized)
        return false;

    return sm->currentState == stateName;
}

bool ScriptApiImpl::sendStateMachineEvent(
    sle::scripting::ScriptEntityRef entity,
    const std::string& eventName)
{
    // sendStateEvent is a named trigger — identical semantics to setStateMachineTrigger
    // but provides a cleaner intent-revealing API name at the Lua surface.
    return setStateMachineTrigger(entity, eventName);
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

bool ScriptApiImpl::addForce(sle::scripting::ScriptEntityRef entity, float forceX, float forceY)
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody || rigidBody->box2dBodyId == 0)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return false;

    physicsWorld->applyForce(rigidBody->box2dBodyId, {forceX, forceY});
    return true;
}

bool ScriptApiImpl::addImpulse(sle::scripting::ScriptEntityRef entity, float impulseX, float impulseY)
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody || rigidBody->box2dBodyId == 0)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return false;

    physicsWorld->applyImpulse(rigidBody->box2dBodyId, {impulseX, impulseY});
    return true;
}

bool ScriptApiImpl::setVelocity(sle::scripting::ScriptEntityRef entity, float velocityX, float velocityY)
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody || rigidBody->box2dBodyId == 0)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return false;

    rigidBody->velocity = {velocityX, velocityY};
    physicsWorld->setBodyVelocity(rigidBody->box2dBodyId, rigidBody->velocity);
    return true;
}

bool ScriptApiImpl::getVelocity(sle::scripting::ScriptEntityRef entity, glm::vec2& outVelocity) const
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody)
        return false;

    outVelocity = rigidBody->velocity;
    return true;
}

bool ScriptApiImpl::setAngularVelocity(sle::scripting::ScriptEntityRef entity, float angularVelocity)
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody || rigidBody->box2dBodyId == 0)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return false;

    rigidBody->angularVelocity = angularVelocity;
    physicsWorld->setBodyAngularVelocity(rigidBody->box2dBodyId, angularVelocity);
    return true;
}

float ScriptApiImpl::getAngularVelocity(sle::scripting::ScriptEntityRef entity) const
{
    if (!isEntityAlive(entity))
        return 0.0f;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody)
        return 0.0f;

    return rigidBody->angularVelocity;
}

bool ScriptApiImpl::setGravityScale(sle::scripting::ScriptEntityRef entity, float gravityScale)
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody || rigidBody->box2dBodyId == 0)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return false;

    rigidBody->gravityScale = gravityScale;
    physicsWorld->setBodyGravityScale(rigidBody->box2dBodyId, gravityScale);
    return true;
}

bool ScriptApiImpl::isTouching(sle::scripting::ScriptEntityRef entity) const
{
    if (!isEntityAlive(entity))
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);

    auto* rigidBody = registry.getComponent<components::RigidBodyComponent>(rawEntity);
    if (!rigidBody || rigidBody->box2dBodyId == 0)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return false;

    // Check if body has any active contacts
    b2World* world = physicsWorld->getRawWorld();
    if (!world)
        return false;

    // Iterate through all bodies and contacts
    for (b2Contact* contact = world->GetContactList(); contact; contact = contact->GetNext())
    {
        if (!contact->IsTouching())
            continue;

        b2Body* bodyA = contact->GetFixtureA()->GetBody();
        b2Body* bodyB = contact->GetFixtureB()->GetBody();

        uint32_t idA = static_cast<uint32_t>(bodyA->GetUserData().pointer);
        uint32_t idB = static_cast<uint32_t>(bodyB->GetUserData().pointer);

        if (idA == entity.id || idB == entity.id)
            return true;
    }

    return false;
}

bool ScriptApiImpl::raycastFirst(const glm::vec2& start, const glm::vec2& end, sle::scripting::PhysicsRaycastHit& outHit) const
{
    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return false;

    b2World* world = physicsWorld->getRawWorld();
    if (!world)
        return false;

    const glm::vec2 physicsStart = physicsWorld->worldToPhysicsVec2(start);
    const glm::vec2 physicsEnd = physicsWorld->worldToPhysicsVec2(end);

    FirstHitRayCastCallback callback;
    world->RayCast(&callback, b2Vec2(physicsStart.x, physicsStart.y), b2Vec2(physicsEnd.x, physicsEnd.y));

    if (!callback.hasHit)
        return false;

    outHit = callback.hit;
    outHit.point = physicsWorld->physicsToWorldVec2(outHit.point);
    return true;
}

uint32_t ScriptApiImpl::raycastAll(
    const glm::vec2& start,
    const glm::vec2& end,
    std::vector<sle::scripting::PhysicsRaycastHit>& outHits) const
{
    outHits.clear();

    auto* physicsWorld = runtime.getPhysicsWorld();
    if (!physicsWorld)
        return 0;

    b2World* world = physicsWorld->getRawWorld();
    if (!world)
        return 0;

    const glm::vec2 physicsStart = physicsWorld->worldToPhysicsVec2(start);
    const glm::vec2 physicsEnd = physicsWorld->worldToPhysicsVec2(end);

    AllHitsRayCastCallback callback;
    world->RayCast(&callback, b2Vec2(physicsStart.x, physicsStart.y), b2Vec2(physicsEnd.x, physicsEnd.y));

    for (auto& hit : callback.hits)
    {
        hit.point = physicsWorld->physicsToWorldVec2(hit.point);
    }

    std::sort(
        callback.hits.begin(),
        callback.hits.end(),
        [](const sle::scripting::PhysicsRaycastHit& a, const sle::scripting::PhysicsRaycastHit& b)
        {
            return a.fraction < b.fraction;
        });

    outHits = std::move(callback.hits);
    return static_cast<uint32_t>(outHits.size());
}

void ScriptApiImpl::setPhysicsDebugEnabled(bool enabled)
{
    runtime.setPhysicsDebugEnabled(enabled);
}

bool ScriptApiImpl::isPhysicsDebugEnabled() const
{
    return runtime.isPhysicsDebugEnabled();
}

int ScriptApiImpl::subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef)
{
    auto& eventBus = runtime.getScene().getEventBus();
    int subId = nextSubscriptionId_++;

    if (eventName == "collision.begin")
    {
        auto handle = eventBus.subscribe<sle::events::CollisionBeginEvent>(
            [this, luaRef](const sle::events::CollisionBeginEvent& evt) {
                // Call Lua handler with entity IDs
                // This would require access to Lua state (to be implemented with Lua integration)
                // For now, just log the event
                sle::core::Log::info("CollisionBegin event fired");
            });
        entitySubscriptions_[entityId].push_back(sle::events::ScopedSubscription(&eventBus, handle));
        subscriptionIdToLocation_[subId] = {entityId, entitySubscriptions_[entityId].size() - 1};
        return subId;
    }
    else if (eventName == "collision.end")
    {
        auto handle = eventBus.subscribe<sle::events::CollisionEndEvent>(
            [this, luaRef](const sle::events::CollisionEndEvent& evt) {
                sle::core::Log::info("CollisionEnd event fired");
            });
        entitySubscriptions_[entityId].push_back(sle::events::ScopedSubscription(&eventBus, handle));
        subscriptionIdToLocation_[subId] = {entityId, entitySubscriptions_[entityId].size() - 1};
        return subId;
    }
    else if (eventName == "zone.enter")
    {
        auto handle = eventBus.subscribe<sle::events::ZoneEnterEvent>(
            [this, luaRef](const sle::events::ZoneEnterEvent& evt) {
                sle::core::Log::info("ZoneEnter event fired");
            });
        entitySubscriptions_[entityId].push_back(sle::events::ScopedSubscription(&eventBus, handle));
        subscriptionIdToLocation_[subId] = {entityId, entitySubscriptions_[entityId].size() - 1};
        return subId;
    }
    else if (eventName == "zone.exit")
    {
        auto handle = eventBus.subscribe<sle::events::ZoneExitEvent>(
            [this, luaRef](const sle::events::ZoneExitEvent& evt) {
                sle::core::Log::info("ZoneExit event fired");
            });
        entitySubscriptions_[entityId].push_back(sle::events::ScopedSubscription(&eventBus, handle));
        subscriptionIdToLocation_[subId] = {entityId, entitySubscriptions_[entityId].size() - 1};
        return subId;
    }

    return -1;  // Unknown event name
}

void ScriptApiImpl::unsubscribeEvent(int subscriptionId)
{
    auto it = subscriptionIdToLocation_.find(subscriptionId);
    if (it == subscriptionIdToLocation_.end())
        return;

    uint32_t entityId = it->second.first;
    size_t index = it->second.second;

    auto entityIt = entitySubscriptions_.find(entityId);
    if (entityIt != entitySubscriptions_.end() && index < entityIt->second.size())
    {
        // Swap with last and pop for O(1) removal
        if (index != entityIt->second.size() - 1)
        {
            std::swap(entityIt->second[index], entityIt->second.back());
            
            // Update the location tracking for swapped subscription
            // This is a simplified version; a more robust implementation would track indices
        }
        entityIt->second.pop_back();

        if (entityIt->second.empty())
            entitySubscriptions_.erase(entityIt);
    }

    subscriptionIdToLocation_.erase(it);
}

} // namespace sle
