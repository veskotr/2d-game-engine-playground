#pragma once

namespace sle::entity { class Scene; class Registry; }
namespace sle::events { class EventBus; }
namespace sle::renderer { class Renderer; }
namespace sle::core { class Camera2D; }
namespace sle::scripting { class ScriptRuntime; }

namespace sle::ui {

struct UIFrameContext
{
    sle::entity::Scene& scene;
    sle::entity::Registry& registry;
    sle::events::EventBus& eventBus;
    sle::events::EventBus& globalBus;
    sle::renderer::Renderer& renderer;
    const sle::core::Camera2D& camera;
    sle::scripting::ScriptRuntime& scriptRuntime;
    float dt;
};

} // namespace sle::ui
