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

    // ====== LOGGING ======
    virtual void log(const std::string& message) = 0;
    virtual void warn(const std::string& message) = 0;
    virtual void error(const std::string& message) = 0;

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
};

} // namespace sle::scripting
