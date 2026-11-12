#pragma once
namespace sle::entity { class Registry; }
namespace sle::core { class Camera2D; }
namespace sle {
class CameraSystem {
public:
    // Returns false without modifying the legacy camera when no primary exists.
    static bool update(entity::Registry& registry, core::Camera2D& camera);
};
}
