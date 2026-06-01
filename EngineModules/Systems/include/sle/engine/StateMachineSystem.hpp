#pragma once

namespace sle::entity { class Scene; }
namespace sle::scripting { class ScriptRuntime; }

namespace sle {

class StateMachineSystem
{
public:
    void update(sle::entity::Scene& scene, float dt, sle::scripting::ScriptRuntime* scriptRuntime = nullptr);
};

} // namespace sle
