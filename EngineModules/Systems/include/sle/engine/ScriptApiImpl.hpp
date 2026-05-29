#pragma once

#include <sle/scripting/ScriptApi.hpp>
#include <sle/events/ScopedSubscription.hpp>
#include <unordered_map>
#include <vector>

namespace sle {

class Runtime;

class ScriptApiImpl : public sle::scripting::ScriptApi
{
public:
    explicit ScriptApiImpl(Runtime& runtime) : runtime(runtime) {}

    float getDeltaTime() const override;
    glm::vec2 getWindowSize() const override;

    sle::scripting::ScriptEntityRef createEntity() override;
    bool isEntityAlive(sle::scripting::ScriptEntityRef entity) const override;
    void destroyEntity(sle::scripting::ScriptEntityRef entity) override;
    uint32_t getChildCount(sle::scripting::ScriptEntityRef parent) const override;
    uint32_t destroyChildren(sle::scripting::ScriptEntityRef parent) override;
    bool setParent(sle::scripting::ScriptEntityRef child, sle::scripting::ScriptEntityRef parent) override;
    sle::scripting::ScriptEntityRef getParent(sle::scripting::ScriptEntityRef entity) const override;

    bool getTransformPosition(sle::scripting::ScriptEntityRef entity, glm::vec2& outPosition) const override;
    bool setTransformPosition(sle::scripting::ScriptEntityRef entity, const glm::vec2& position) override;
    bool getTransformScale(sle::scripting::ScriptEntityRef entity, glm::vec2& outScale) const override;

    bool isKeyDown(int key) const override;
    bool isKeyPressed(int key) const override;
    bool isKeyReleased(int key) const override;
    glm::dvec2 getMousePosition() const override;

    glm::vec2 getCameraPosition() const override;
    void setCameraPosition(const glm::vec2& position) override;
    void moveCamera(const glm::vec2& delta) override;
    float getCameraZoom() const override;
    void setCameraZoom(float zoom) override;

    uint32_t loadTexture(const std::string& assetPath) override;
    bool setSpriteTexture(sle::scripting::ScriptEntityRef entity, const std::string& assetPath) override;

    bool hasScene(const std::string& sceneName) const override;
    bool switchScene(const std::string& sceneName) override;
    std::string getCurrentSceneName() const override;

    bool setStateMachineBool(sle::scripting::ScriptEntityRef entity, const std::string& key, bool value) override;
    bool setStateMachineTrigger(sle::scripting::ScriptEntityRef entity, const std::string& key) override;
    bool getStateMachineCurrentState(sle::scripting::ScriptEntityRef entity, std::string& outState) const override;
    bool forceStateMachineState(sle::scripting::ScriptEntityRef entity, const std::string& stateName) override;
    bool isStateMachineInState(sle::scripting::ScriptEntityRef entity, const std::string& stateName) const override;
    bool sendStateMachineEvent(sle::scripting::ScriptEntityRef entity, const std::string& eventName) override;

    void log(const std::string& message) override;
    void warn(const std::string& message) override;
    void error(const std::string& message) override;
    bool setUIBinding(const std::string& key, const std::string& value) override;

    bool addForce(sle::scripting::ScriptEntityRef entity, float forceX, float forceY) override;
    bool addImpulse(sle::scripting::ScriptEntityRef entity, float impulseX, float impulseY) override;
    bool setVelocity(sle::scripting::ScriptEntityRef entity, float velocityX, float velocityY) override;
    bool getVelocity(sle::scripting::ScriptEntityRef entity, glm::vec2& outVelocity) const override;
    bool setAngularVelocity(sle::scripting::ScriptEntityRef entity, float angularVelocity) override;
    float getAngularVelocity(sle::scripting::ScriptEntityRef entity) const override;
    bool setGravityScale(sle::scripting::ScriptEntityRef entity, float gravityScale) override;
    bool isTouching(sle::scripting::ScriptEntityRef entity) const override;
    bool raycastFirst(const glm::vec2& start, const glm::vec2& end, sle::scripting::PhysicsRaycastHit& outHit) const override;
    uint32_t raycastAll(const glm::vec2& start, const glm::vec2& end, std::vector<sle::scripting::PhysicsRaycastHit>& outHits) const override;
    void setPhysicsDebugEnabled(bool enabled) override;
    bool isPhysicsDebugEnabled() const override;

    int subscribeEvent(const std::string& eventName, uint32_t entityId, int luaRef) override;
    void unsubscribeEvent(int subscriptionId) override;
    bool emitEvent(const std::string& eventName, uint32_t sourceEntity, const std::string& payload) override;

    bool playAnimation(sle::scripting::ScriptEntityRef entity, const std::string& clipAsset) override;
    bool stopAnimation(sle::scripting::ScriptEntityRef entity) override;
    bool pauseAnimation(sle::scripting::ScriptEntityRef entity) override;
    bool resumeAnimation(sle::scripting::ScriptEntityRef entity) override;
    bool setAnimationSpeed(sle::scripting::ScriptEntityRef entity, float speed) override;
    bool setAnimationTime(sle::scripting::ScriptEntityRef entity, float timeSeconds) override;
    bool isAnimationPlaying(sle::scripting::ScriptEntityRef entity) const override;
    float getAnimationTime(sle::scripting::ScriptEntityRef entity) const override;
    bool setAnimationTarget(sle::scripting::ScriptEntityRef entity, const std::string& targetName, sle::scripting::ScriptEntityRef targetEntity) override;
    bool setAnimatorFloat(sle::scripting::ScriptEntityRef entity, const std::string& name, float value) override;
    bool getAnimatorFloat(sle::scripting::ScriptEntityRef entity, const std::string& name, float& outValue) const override;

    bool playSound(sle::scripting::ScriptEntityRef entity, const std::string& assetPath, bool loop) override;
    bool stopSound(sle::scripting::ScriptEntityRef entity) override;
    bool setSoundVolume(sle::scripting::ScriptEntityRef entity, float volume) override;
    bool setSoundPitch(sle::scripting::ScriptEntityRef entity, float pitch) override;
    bool isSoundPlaying(sle::scripting::ScriptEntityRef entity) const override;

private:
    Runtime& runtime;
    // Track subscriptions per entity for auto-cleanup
    std::unordered_map<uint32_t, std::vector<sle::events::ScopedSubscription>> entitySubscriptions_;
    // Track subscription ID to index mapping for unsubscribe
    std::unordered_map<int, std::pair<uint32_t, size_t>> subscriptionIdToLocation_;
    int nextSubscriptionId_ = 1;
};
};// namespace sle
