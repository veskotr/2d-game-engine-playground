#pragma once

#include <sle/events/ScopedSubscription.hpp>

#include <cstdint>
#include <unordered_set>

namespace sle::events { class EventBus; }

namespace sle::scripting { class ScriptRuntime; }

namespace sle {

struct Context;

// Placeholder for future Lua/scripting integration.
// Currently a no-op that can be extended to update script components.
class ScriptSystem
{
public:
    void setScriptRuntime(sle::scripting::ScriptRuntime* runtime) { scriptRuntime = runtime; }
    void update(Context& ctx);

private:
    void ensureStateMachineSubscription(sle::events::EventBus& eventBus);

    sle::scripting::ScriptRuntime* scriptRuntime = nullptr;
    std::unordered_set<uint32_t> activeScriptEntities;
    sle::events::EventBus* subscribedEventBus_ = nullptr;
    sle::events::ScopedSubscription stateMachineTransitionSubscription_;
};

} // namespace sle
