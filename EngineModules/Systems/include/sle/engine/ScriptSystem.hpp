#pragma once

#include <cstdint>
#include <unordered_set>

namespace sle::scripting { class ScriptEngine; }

namespace sle {

struct Context;

// Placeholder for future Lua/scripting integration.
// Currently a no-op that can be extended to update script components.
class ScriptSystem
{
public:
    void setScriptEngine(sle::scripting::ScriptEngine* engine) { scriptEngine = engine; }
    void update(Context& ctx);

private:
    sle::scripting::ScriptEngine* scriptEngine = nullptr;
    std::unordered_set<uint32_t> activeScriptEntities;
};

} // namespace sle
