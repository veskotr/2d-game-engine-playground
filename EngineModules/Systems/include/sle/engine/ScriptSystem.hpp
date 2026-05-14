#pragma once

namespace sle {

struct Context;

// Placeholder for future Lua/scripting integration.
// Currently a no-op that can be extended to update script components.
class ScriptSystem
{
public:
    void update(Context& ctx);
};

} // namespace sle
