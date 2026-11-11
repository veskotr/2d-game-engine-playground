#pragma once

#include <sle/events/ScopedSubscription.hpp>

#include <cstdint>
#include <unordered_set>
#include <unordered_map>
#include <string>

namespace sle::entity { class Registry; }

namespace sle::events { class EventBus; }

namespace sle::scripting { class ScriptRuntime; }

namespace sle {

struct Context;

// Manages per-entity script lifetime and updates. Chunk suspension pauses updates
// while preserving existing Lua instances.
class ScriptSystem
{
public:
    void setScriptRuntime(sle::scripting::ScriptRuntime* runtime) { scriptRuntime = runtime; }
    void update(Context& ctx);

private:
    void ensureStateMachineSubscription(sle::events::EventBus& eventBus, sle::entity::Registry& registry);

    sle::scripting::ScriptRuntime* scriptRuntime = nullptr;
    std::unordered_set<uint32_t> activeScriptEntities;
    std::unordered_map<uint32_t,std::unordered_map<std::string,std::string>> failedAssets;
    sle::events::EventBus* subscribedEventBus_ = nullptr;
    sle::events::ScopedSubscription stateMachineTransitionSubscription_;
};

} // namespace sle
