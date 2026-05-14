#pragma once
#include <glm/glm.hpp>
#include <sle/renderer/TextureRegion.hpp>

namespace sle::components {

// Pure rendering data component. Describes how an entity should be drawn.
// No rendering logic, no OpenGL calls. All fields are plain data read by
// RenderSystem each frame.
struct SpriteRenderer
{
    // RGBA tint applied to the sprite. White (1,1,1,1) = no tint.
    glm::vec4 color{1.0f, 1.0f, 1.0f, 1.0f};

    // Source texture region. region.texture == nullptr means untextured.
    // Use TextureRegion::fromPixels() for atlas slices and animation frames.
    // The default UV covers the full texture.
    renderer::TextureRegion region;

    // Render layer. Lower values are drawn first (further back).
    int layer = 0;
};

} // namespace sle::components
