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

    virtual float getDeltaTime() const = 0;
    virtual glm::vec2 getWindowSize() const = 0;

    virtual ScriptEntityRef createEntity() = 0;
    virtual bool isEntityAlive(ScriptEntityRef entity) const = 0;
    virtual void destroyEntity(ScriptEntityRef entity) = 0;

    virtual bool getTransformPosition(ScriptEntityRef entity, glm::vec2& outPosition) const = 0;
    virtual bool setTransformPosition(ScriptEntityRef entity, const glm::vec2& position) = 0;

    virtual uint32_t loadTexture(const std::string& assetPath) = 0;
};

} // namespace sle::scripting
