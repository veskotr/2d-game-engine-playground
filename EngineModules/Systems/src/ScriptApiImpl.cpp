#include <sle/engine/ScriptApiImpl.hpp>

#include <sle/core/Log.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/platform/Input.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/AudioComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/events/CollisionEvents.hpp>
#include <sle/events/ScriptEvents.hpp>
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
    if (!isEntityAlive(entity))
        return;

    auto& scene = runtime.getScene();
    std::vector<sle::entity::Entity> pending{sle::entity::Entity(entity.id)};
    while (!pending.empty())
    {
        const auto current = pending.back();
        pending.pop_back();
        const auto& children = scene.getChildren(current);
        pending.insert(pending.end(), children.begin(), children.end());
        clearEventSubscriptions(current.getID());
    }
    scene.destroyEntity(sle::entity::Entity(entity.id));
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
        destroyEntity({child.getID()});

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

    return runtime.getScene().setParent(childEntity, parentEntity);
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

bool ScriptApiImpl::setUIBinding(const std::string& key, const std::string& value)
{
    return runtime.setUIBinding(key, value);
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
    if (!rigidBody)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    rigidBody->velocity = {velocityX, velocityY};
    if (physicsWorld && rigidBody->box2dBodyId != 0)
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
    if (!rigidBody)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    rigidBody->angularVelocity = angularVelocity;
    if (physicsWorld && rigidBody->box2dBodyId != 0)
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
    if (!rigidBody)
        return false;

    auto* physicsWorld = runtime.getPhysicsWorld();
    rigidBody->gravityScale = gravityScale;
    if (physicsWorld && rigidBody->box2dBodyId != 0)
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

uint32_t ScriptApiImpl::getExecutingEntity() const
{
    return runtime.getScriptEngine().getExecutingEntity();
}

bool ScriptApiImpl::isExecutingEntityScript() const
{
    return runtime.getScriptEngine().isExecutingEntityScript();
}

bool ScriptApiImpl::canDispatchEvent(int subscriptionId) const
{
    const auto it = subscriptions_.find(subscriptionId);
    if (it == subscriptions_.end() || !*it->second.active)
        return false;
    const auto& subscription = it->second;
    if (subscription.owner == 0)
        return true; // Explicit scene-global subscription.
    const auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity owner(subscription.owner);
    if (!registry.hasEntity(owner) ||
        !sle::entity::isChunkActive(registry, owner, components::ChunkSubsystem::Scripts))
        return false;
    const auto* script = registry.getComponent<components::ScriptComponent>(owner);
    if (subscription.scriptOwned)
        return script && script->enabled && runtime.getScriptEngine().hasScript(subscription.owner);
    return true; // UI/global behavior ownership is independent of ScriptComponent.
}

int ScriptApiImpl::subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef)
{
    if (eventName.empty() || (entityId != 0 && !isEntityAlive({entityId})))
        return 0;
    const int id = nextSubscriptionId_++;
    auto active = std::make_shared<bool>(true);
    auto& bus = runtime.getScene().getEventBus();
    const bool scriptOwned = isExecutingEntityScript();
    auto dispatch = [this, active, id, luaRef, entityId, scriptOwned](const std::string& name,
        uint32_t a, uint32_t b, const std::string& zone, uint32_t source, const std::string& payload)
    {
        // unsubscribe/scene teardown may happen during snapshot dispatch. A stale
        // closure must not call a released (possibly reused) Lua registry ref.
        if (*active && canDispatchEvent(id))
            (void)runtime.getScriptEngine().callEventCallback(luaRef, name, a, b, zone, source, payload, entityId, scriptOwned);
    };
    sle::events::SubscriptionHandle handle;
    if (eventName == "collision.begin")
        handle = bus.subscribe<sle::events::CollisionBeginEvent>([dispatch](const auto& e) { dispatch("collision.begin", e.entityA.getID(), e.entityB.getID(), {}, 0, {}); });
    else if (eventName == "collision.end")
        handle = bus.subscribe<sle::events::CollisionEndEvent>([dispatch](const auto& e) { dispatch("collision.end", e.entityA.getID(), e.entityB.getID(), {}, 0, {}); });
    else if (eventName == "zone.enter")
        handle = bus.subscribe<sle::events::ZoneEnterEvent>([dispatch](const auto& e) { dispatch("zone.enter", e.zoneEntity.getID(), e.otherEntity.getID(), e.zoneId, 0, {}); });
    else if (eventName == "zone.exit")
        handle = bus.subscribe<sle::events::ZoneExitEvent>([dispatch](const auto& e) { dispatch("zone.exit", e.zoneEntity.getID(), e.otherEntity.getID(), e.zoneId, 0, {}); });
    else
        handle = bus.subscribe<sle::events::ScriptEvent>([dispatch](const auto& e) { dispatch(e.name, 0, 0, {}, e.sourceEntity.getID(), e.payload); },
            [eventName](const auto& e) { return e.name == eventName; });
    subscriptions_.emplace(id, EventSubscription{entityId, luaRef, scriptOwned, active,
        sle::events::ScopedSubscription(&bus, handle)});
    return id;
}

void ScriptApiImpl::unsubscribeEvent(int subscriptionId)
{
    const auto it = subscriptions_.find(subscriptionId);
    if (it == subscriptions_.end())
        return;
    *it->second.active = false;
    it->second.handle.reset();
    runtime.getScriptEngine().releaseEventCallback(it->second.luaRef);
    subscriptions_.erase(it);
}

void ScriptApiImpl::clearEventSubscriptions(uint32_t entityId)
{
    std::vector<int> ids;
    for (const auto& [id, subscription] : subscriptions_)
        if (subscription.owner == entityId)
            ids.push_back(id);
    for (int id : ids)
        unsubscribeEvent(id);
}

void ScriptApiImpl::clearScriptEventSubscriptions(uint32_t entityId)
{
    std::vector<int> ids;
    for (const auto& [id, subscription] : subscriptions_)
        if (subscription.owner == entityId && subscription.scriptOwned)
            ids.push_back(id);
    for (int id : ids)
        unsubscribeEvent(id);
}

void ScriptApiImpl::pruneEventSubscriptions()
{
    std::vector<int> ids;
    const auto& registry = runtime.getScene().getRegistry();
    for (const auto& [id, subscription] : subscriptions_)
    {
        if (subscription.owner == 0) continue;
        const sle::entity::Entity owner(subscription.owner);
        const auto* script = registry.getComponent<components::ScriptComponent>(owner);
        if (!registry.hasEntity(owner) || (subscription.scriptOwned &&
            (!script || !script->enabled || !runtime.getScriptEngine().hasScript(subscription.owner))))
            ids.push_back(id);
    }
    for (int id : ids)
        unsubscribeEvent(id);
}

void ScriptApiImpl::rollbackEventSubscriptions(uint32_t owner, int checkpoint, bool scriptOwned)
{
    std::vector<int> ids;
    for (const auto& [id, subscription] : subscriptions_)
        if (id >= checkpoint && subscription.owner == owner && subscription.scriptOwned == scriptOwned)
            ids.push_back(id);
    for (int id : ids)
        unsubscribeEvent(id);
}

void ScriptApiImpl::clearAllEventSubscriptions()
{
    while (!subscriptions_.empty())
        unsubscribeEvent(subscriptions_.begin()->first);
}

bool ScriptApiImpl::emitEvent(const std::string& eventName, uint32_t sourceEntity, const std::string& payload)
{
    return runtime.emitScriptEvent(eventName, sourceEntity, payload);
}

// ============================================================
// Animator API
// ============================================================

bool ScriptApiImpl::playAnimation(sle::scripting::ScriptEntityRef entity, const std::string& clipAsset)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    if (!clipAsset.empty() && animator->clipAsset != clipAsset)
    {
        animator->clipAsset = clipAsset;
        animator->clip.reset();
        animator->clipLoadAttempted = false;
    }

    animator->playing = true;
    animator->timeSeconds = 0.0f;
    return true;
}

bool ScriptApiImpl::stopAnimation(sle::scripting::ScriptEntityRef entity)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    animator->playing = false;
    animator->timeSeconds = 0.0f;
    return true;
}

bool ScriptApiImpl::pauseAnimation(sle::scripting::ScriptEntityRef entity)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    animator->playing = false;
    return true;
}

bool ScriptApiImpl::resumeAnimation(sle::scripting::ScriptEntityRef entity)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    animator->playing = true;
    return true;
}

bool ScriptApiImpl::setAnimationSpeed(sle::scripting::ScriptEntityRef entity, float speed)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    animator->speed = speed;
    return true;
}

bool ScriptApiImpl::setAnimationTime(sle::scripting::ScriptEntityRef entity, float timeSeconds)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    animator->timeSeconds = timeSeconds;
    return true;
}

bool ScriptApiImpl::isAnimationPlaying(sle::scripting::ScriptEntityRef entity) const
{
    auto& registry = const_cast<ScriptApiImpl*>(this)->runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    const auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    return animator && animator->playing;
}

float ScriptApiImpl::getAnimationTime(sle::scripting::ScriptEntityRef entity) const
{
    auto& registry = const_cast<ScriptApiImpl*>(this)->runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    const auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    return animator ? animator->timeSeconds : 0.0f;
}

bool ScriptApiImpl::setAnimationTarget(
    sle::scripting::ScriptEntityRef entity,
    const std::string& targetName,
    sle::scripting::ScriptEntityRef targetEntity)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    if (targetEntity.id == 0)
        animator->targetEntities.erase(targetName);
    else
        animator->targetEntities[targetName] = targetEntity.id;

    return true;
}

bool ScriptApiImpl::setAnimatorFloat(sle::scripting::ScriptEntityRef entity, const std::string& name, float value)
{
    if (name.empty())
        return false;

    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    animator->floatParameters[name] = value;
    return true;
}

bool ScriptApiImpl::getAnimatorFloat(
    sle::scripting::ScriptEntityRef entity,
    const std::string& name,
    float& outValue) const
{
    if (name.empty())
        return false;

    auto& registry = const_cast<ScriptApiImpl*>(this)->runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    const auto* animator = registry.getComponent<sle::components::AnimatorComponent>(rawEntity);
    if (!animator)
        return false;

    auto it = animator->floatParameters.find(name);
    if (it == animator->floatParameters.end())
        return false;

    outValue = it->second;
    return true;
}

bool ScriptApiImpl::playSound(
    sle::scripting::ScriptEntityRef entity,
    const std::string& assetPath,
    bool loop)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* audio = registry.getComponent<sle::components::AudioComponent>(rawEntity);
    if (!audio)
        return false;

    // Only override the stored path when the caller provides one.
    if (!assetPath.empty())
        audio->assetPath = assetPath;

    if (audio->assetPath.empty())
        return false;

    audio->loop = loop;
    audio->playRequested = true;
    audio->stopRequested = false;
    return true;
}

bool ScriptApiImpl::stopSound(sle::scripting::ScriptEntityRef entity)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* audio = registry.getComponent<sle::components::AudioComponent>(rawEntity);
    if (!audio)
        return false;

    audio->stopRequested = true;
    audio->playRequested = false;
    return true;
}

bool ScriptApiImpl::setSoundVolume(sle::scripting::ScriptEntityRef entity, float volume)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* audio = registry.getComponent<sle::components::AudioComponent>(rawEntity);
    if (!audio)
        return false;

    audio->volume = volume;
    return true;
}

bool ScriptApiImpl::setSoundPitch(sle::scripting::ScriptEntityRef entity, float pitch)
{
    auto& registry = runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    auto* audio = registry.getComponent<sle::components::AudioComponent>(rawEntity);
    if (!audio)
        return false;

    audio->pitch = pitch;
    return true;
}

bool ScriptApiImpl::isSoundPlaying(sle::scripting::ScriptEntityRef entity) const
{
    auto& registry = const_cast<ScriptApiImpl*>(this)->runtime.getScene().getRegistry();
    const sle::entity::Entity rawEntity(entity.id);
    const auto* audio = registry.getComponent<sle::components::AudioComponent>(rawEntity);
    if (!audio)
        return false;

    return audio->state == sle::components::AudioPlaybackState::Playing;
}

} // namespace sle
