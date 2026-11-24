#pragma once
#include <sle/engine/Context.hpp>
#include <cstdint>

namespace sle {

// Submits visible tile chunks, ECS sprites and debug geometry to the renderer.
class RenderSystem
{
public:
    void setDefaultShaderID(uint32_t shaderID) { defaultShaderID = shaderID; }
    void setDefaultTextureID(uint32_t textureID) { defaultTextureID = textureID; }
    void setMissingTileTextureID(uint32_t textureID) { missingTileTextureID = textureID; }
    void setPhysicsDebugEnabled(bool enabled) { physicsDebugEnabled = enabled; }
    bool isPhysicsDebugEnabled() const { return physicsDebugEnabled; }
    void update(Context& ctx);

private:
    uint32_t defaultShaderID = 0;
    uint32_t defaultTextureID = 0;
    uint32_t missingTileTextureID = 0;
    bool physicsDebugEnabled = false;
};

} // namespace sle
