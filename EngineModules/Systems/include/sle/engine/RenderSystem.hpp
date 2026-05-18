#pragma once
#include <sle/engine/Context.hpp>
#include <cstdint>

namespace sle {

// Iterates all entities that have both a WorldTransformComponent and a
// SpriteRenderer, builds a QuadCommand for each, and submits it to the
// Renderer. Stateless: no data is stored between frames.
class RenderSystem
{
public:
    void setDefaultShaderID(uint32_t shaderID) { defaultShaderID = shaderID; }
    void setDefaultTextureID(uint32_t textureID) { defaultTextureID = textureID; }
    void setPhysicsDebugEnabled(bool enabled) { physicsDebugEnabled = enabled; }
    bool isPhysicsDebugEnabled() const { return physicsDebugEnabled; }
    void update(Context& ctx);

private:
    uint32_t defaultShaderID = 0;
    uint32_t defaultTextureID = 0;
    bool physicsDebugEnabled = false;
};

} // namespace sle
