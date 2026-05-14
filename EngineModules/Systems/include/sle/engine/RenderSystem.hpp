#pragma once
#include <sle/engine/Context.hpp>

namespace sle {

// Iterates all entities that have both a WorldTransformComponent and a
// SpriteRenderer, builds a QuadCommand for each, and submits it to the
// Renderer. Stateless: no data is stored between frames.
class RenderSystem
{
public:
    void update(Context& ctx);
};

} // namespace sle
