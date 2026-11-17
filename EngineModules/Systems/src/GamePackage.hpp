#pragma once
#include <filesystem>
#include <memory>
#include <optional>
#include <string>

// Mounts a versioned `.slepkg` game package as an unpacked, read-only asset root.
namespace sle::gamepackage {
struct Mounted {
    std::filesystem::path root;
    std::string id;
    std::string name;
    std::optional<std::string> mainScene;
    // Keeps a private extraction alive; null when the shared cache is used.
    std::shared_ptr<void> owner;
};
// Throws std::invalid_argument for invalid packages or extraction failures.
Mounted mount(const std::filesystem::path& package);
}
