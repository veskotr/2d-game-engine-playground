#pragma once

#include <sle/scripting/ScriptApi.hpp>

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

    void log(const std::string& message) override;
    void warn(const std::string& message) override;
    void error(const std::string& message) override;

private:
    Runtime& runtime;
};

} // namespace sle
