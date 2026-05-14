#pragma once

namespace sle {

struct Context;

// Placeholder for future physics integration (Box2D).
// Currently a no-op that can be extended to step physics world.
class PhysicsSystem
{
public:
    void update(Context& ctx);
};

} // namespace sle
