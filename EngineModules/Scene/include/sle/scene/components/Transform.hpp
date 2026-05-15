#pragma once
#include <glm/glm.hpp>
#include <sle/scene/Entity.hpp>

namespace sle::components {

struct TransformComponent
{
public:
    const glm::vec2& getPosition() const { return position; }
    float getRotation() const { return rotation; }
    const glm::vec2& getScale() const { return scale; }

    void setPosition(const glm::vec2& value)
    {
        position = value;
        dirty = true;
    }

    void setRotation(float value)
    {
        rotation = value;
        dirty = true;
    }

    void setScale(const glm::vec2& value)
    {
        scale = value;
        dirty = true;
    }

    bool isDirty() const { return dirty; }
    void markDirty() { dirty = true; }
    void clearDirty() { dirty = false; }

    // Hierarchy: invalid (default) entity means no parent
    sle::entity::Entity getParent() const { return parent; }
    void setParent(sle::entity::Entity value)
    {
        parent = value;
        dirty = true;
    }

private:
    glm::vec2 position{0.0f};
    float rotation = 0.0f;
    glm::vec2 scale{1.0f, 1.0f};
    bool dirty = true;
    sle::entity::Entity parent{};
};

} // namespace sle::components
