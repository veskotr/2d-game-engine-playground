#pragma once

namespace sle::entity { class Scene; class Registry; }
namespace sle::core { class EventBus; }
namespace sle::renderer { class Renderer; }
namespace sle::core { class Camera2D; }

namespace sle {

// Unified per-frame execution context passed to all systems.
// Provides access to scene structure, components, events, rendering,
// and delta time. Eliminates global state and centralizes engine access.
struct Context
{
    sle::entity::Scene&      scene;
    sle::entity::Registry&   registry;
    sle::core::EventBus&     eventBus;
    sle::renderer::Renderer& renderer;
    const sle::core::Camera2D& camera;
    float                    dt;
};

} // namespace sle
