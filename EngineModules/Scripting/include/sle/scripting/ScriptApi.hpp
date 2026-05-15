#pragma once

#include <cstdint>
#include <string>
#include <glm/vec2.hpp>

namespace sle::scripting {

struct ScriptEntityRef
{
    uint32_t id = 0;
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
};

} // namespace sle::scripting
