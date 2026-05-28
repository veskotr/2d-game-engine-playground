#pragma once

namespace sle::entity { class Scene; }
namespace sle::scripting { class ScriptEngine; }

namespace sle {

class StateMachineSystem
{
public:
    void update(sle::entity::Scene& scene, float dt, sle::scripting::ScriptEngine* scriptEngine = nullptr);
};

} // namespace sle
