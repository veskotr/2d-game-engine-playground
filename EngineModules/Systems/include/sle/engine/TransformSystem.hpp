#pragma once
#include <sle/engine/Context.hpp>
#include <sle/scene/Entity.hpp>
#include <sle/scene/Registry.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>

namespace sle {

// Traverses the Scene hierarchy once per frame (depth-first from every root)
// and resolves each entity's local TransformComponent into a world-space
// WorldTransformComponent that is stored back into the Registry.
//
// Entities without a TransformComponent are transparent: they do not
// contribute a transform of their own but still propagate the accumulated
// parent world transform to their children.
//
// Run after animation, scripts, and physics have finished writing local transforms,
// before rendering/UI read WorldTransformComponent. This pass clears local dirty
// flags, so physics must consume local transform edits before it runs.
class TransformSystem
{
public:
    void update(Context& ctx);
};

} // namespace sle
