#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <glm/vec2.hpp>

namespace sle::scripting {

struct ScriptEntityRef
{
    uint32_t id = 0;
};

struct PhysicsRaycastHit
{
    uint32_t entityId = 0;
    glm::vec2 point{0.0f, 0.0f};
    glm::vec2 normal{0.0f, 0.0f};
    float fraction = 0.0f;
};

class ScriptApi
{
public:
    virtual ~ScriptApi() = default;

    // ====== ENGINE STATE ======
    virtual float getDeltaTime() const = 0;
    virtual glm::vec2 getWindowSize() const = 0;

    // ====== ENTITY LIFETIME ======
    virtual ScriptEntityRef createEntity() = 0;
    virtual bool isEntityAlive(ScriptEntityRef entity) const = 0;
    virtual void destroyEntity(ScriptEntityRef entity) = 0;
    virtual uint32_t getChildCount(ScriptEntityRef parent) const = 0;
    virtual uint32_t destroyChildren(ScriptEntityRef parent) = 0;
    virtual bool setParent(ScriptEntityRef child, ScriptEntityRef parent) = 0;
    virtual ScriptEntityRef getParent(ScriptEntityRef entity) const = 0;

    // ====== TRANSFORM ======
    virtual bool getTransformPosition(ScriptEntityRef entity, glm::vec2& outPosition) const = 0;
    virtual bool setTransformPosition(ScriptEntityRef entity, const glm::vec2& position) = 0;
    virtual bool getTransformScale(ScriptEntityRef entity, glm::vec2& outScale) const = 0;

    // ====== INPUT ======
    virtual bool isKeyDown(int key) const = 0;
    virtual bool isKeyPressed(int key) const = 0;
    virtual bool isKeyReleased(int key) const = 0;
    virtual glm::dvec2 getMousePosition() const = 0;

    // ====== CAMERA ======
    virtual glm::vec2 getCameraPosition() const = 0;
    virtual void setCameraPosition(const glm::vec2& position) = 0;
    virtual void moveCamera(const glm::vec2& delta) = 0;
    virtual float getCameraZoom() const = 0;
    virtual void setCameraZoom(float zoom) = 0;

    // ====== RESOURCES ======
    virtual uint32_t loadTexture(const std::string& assetPath) = 0;
    virtual bool setSpriteTexture(ScriptEntityRef entity, const std::string& assetPath) = 0;

    // ====== SCENES ======
    virtual bool hasScene(const std::string& sceneName) const = 0;
    virtual bool switchScene(const std::string& sceneName) = 0;
    virtual std::string getCurrentSceneName() const = 0;

    // ====== STATE MACHINE ======
    virtual bool setStateMachineBool(ScriptEntityRef entity, const std::string& key, bool value) = 0;
    virtual bool setStateMachineTrigger(ScriptEntityRef entity, const std::string& key) = 0;
    virtual bool getStateMachineCurrentState(ScriptEntityRef entity, std::string& outState) const = 0;
    virtual bool forceStateMachineState(ScriptEntityRef entity, const std::string& stateName) = 0;
    virtual bool isStateMachineInState(ScriptEntityRef entity, const std::string& stateName) const = 0;
    virtual bool sendStateMachineEvent(ScriptEntityRef entity, const std::string& eventName) = 0;

    // ====== LOGGING ======
    virtual void log(const std::string& message) = 0;
    virtual void warn(const std::string& message) = 0;
    virtual void error(const std::string& message) = 0;

    // ====== UI ======
    virtual bool setUIBinding(const std::string& key, const std::string& value) = 0;

    // ====== PHYSICS ======
    virtual bool addForce(ScriptEntityRef entity, float forceX, float forceY) = 0;
    virtual bool addImpulse(ScriptEntityRef entity, float impulseX, float impulseY) = 0;
    virtual bool setVelocity(ScriptEntityRef entity, float velocityX, float velocityY) = 0;
    virtual bool getVelocity(ScriptEntityRef entity, glm::vec2& outVelocity) const = 0;
    virtual bool setAngularVelocity(ScriptEntityRef entity, float angularVelocity) = 0;
    virtual float getAngularVelocity(ScriptEntityRef entity) const = 0;
    virtual bool setGravityScale(ScriptEntityRef entity, float gravityScale) = 0;
    virtual bool isTouching(ScriptEntityRef entity) const = 0;
    virtual bool raycastFirst(const glm::vec2& start, const glm::vec2& end, PhysicsRaycastHit& outHit) const = 0;
    virtual uint32_t raycastAll(const glm::vec2& start, const glm::vec2& end, std::vector<PhysicsRaycastHit>& outHits) const = 0;
    virtual void setPhysicsDebugEnabled(bool enabled) = 0;
    virtual bool isPhysicsDebugEnabled() const = 0;

    // ====== EVENTS ======
    // Subscribe to an engine event. Returns a subscription ID for later unsubscribe.
    // eventName: "collision.begin", "collision.end", "zone.enter", "zone.exit"
    // entityId: entity subscribing to the event (for auto-cleanup on destruction)
    // luaRef: Lua function ref (from luaL_ref) to call when event fires
    virtual int subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef) = 0;
    virtual void unsubscribeEvent(int subscriptionId) = 0;
    virtual bool emitEvent(const std::string& eventName, uint32_t sourceEntity, const std::string& payload) = 0;

    // ====== ANIMATOR ======
    virtual bool playAnimation(ScriptEntityRef entity, const std::string& clipAsset) = 0;
    virtual bool stopAnimation(ScriptEntityRef entity) = 0;
    virtual bool pauseAnimation(ScriptEntityRef entity) = 0;
    virtual bool resumeAnimation(ScriptEntityRef entity) = 0;
    virtual bool setAnimationSpeed(ScriptEntityRef entity, float speed) = 0;
    virtual bool setAnimationTime(ScriptEntityRef entity, float timeSeconds) = 0;
    virtual bool isAnimationPlaying(ScriptEntityRef entity) const = 0;
    virtual float getAnimationTime(ScriptEntityRef entity) const = 0;
    virtual bool setAnimationTarget(ScriptEntityRef entity, const std::string& targetName, ScriptEntityRef targetEntity) = 0;
    virtual bool setAnimatorFloat(ScriptEntityRef entity, const std::string& name, float value) = 0;
    virtual bool getAnimatorFloat(ScriptEntityRef entity, const std::string& name, float& outValue) const = 0;

    // ====== AUDIO ======
    virtual bool playSound(ScriptEntityRef entity, const std::string& assetPath, bool loop) = 0;
    virtual bool stopSound(ScriptEntityRef entity) = 0;
    virtual bool setSoundVolume(ScriptEntityRef entity, float volume) = 0;
    virtual bool setSoundPitch(ScriptEntityRef entity, float pitch) = 0;
    virtual bool isSoundPlaying(ScriptEntityRef entity) const = 0;
};

} // namespace sle::scripting
