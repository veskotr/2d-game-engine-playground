#pragma once

namespace sle::entity { class Scene; class Registry; }
namespace sle::events { class EventBus; }
namespace sle::renderer { class Renderer; }
namespace sle::core { class Camera2D; }
namespace sle::physics { class PhysicsWorld; }

namespace sle {

// Unified per-frame execution context passed to all systems.
// Provides access to scene structure, components, events, rendering,
// and delta time. Eliminates global state and centralizes engine access.
struct Context
{
    sle::entity::Scene&      scene;
    sle::entity::Registry&   registry;
    sle::events::EventBus&     eventBus;        // Per-scene event bus
    sle::events::EventBus&     globalBus;       // Engine-wide event bus (scene load/unload)
    sle::renderer::Renderer& renderer;
    const sle::core::Camera2D& camera;
    sle::physics::PhysicsWorld* physicsWorld;  // Physics world for physics system
    float                    dt;
};

} // namespace sle
