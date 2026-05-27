#pragma once

namespace sle::entity { class Scene; class Registry; }
namespace sle::events { class EventBus; }
namespace sle::renderer { class Renderer; }
namespace sle::core { class Camera2D; }
namespace sle::scripting { class ScriptEngine; }

namespace sle::ui {

struct UIFrameContext
{
    sle::entity::Scene& scene;
    sle::entity::Registry& registry;
    sle::events::EventBus& eventBus;
    sle::events::EventBus& globalBus;
    sle::renderer::Renderer& renderer;
    const sle::core::Camera2D& camera;
    sle::scripting::ScriptEngine& scriptEngine;
    float dt;
};

} // namespace sle::ui
