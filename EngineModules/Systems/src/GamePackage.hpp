#pragma once
#include <filesystem>
#include <memory>
#include <map>
#include <optional>
#include <string>
#include <sle/renderer/Texture.hpp>
#include <sle/engine/InputActions.hpp>
#include <sle/physics/PhysicsWorld.hpp>

// Mounts a versioned `.slepkg` game package as an unpacked, read-only asset root.
namespace sle::gamepackage {
struct Mounted {
    std::filesystem::path root;
    std::string id;
    std::string name;
    std::optional<std::string> mainScene;
    renderer::TextureFilter textureDefault = renderer::TextureFilter::Default;
    physics::CollisionMatrix blockedPairs{};
    std::map<std::string, std::string> tags;
    InputActionMap actions;
    // Keeps a private extraction alive; null when the shared cache is used.
    std::shared_ptr<void> owner;
};
// Throws std::invalid_argument for invalid packages or extraction failures.
Mounted mount(const std::filesystem::path& package);
}
