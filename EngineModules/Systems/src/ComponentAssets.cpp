#include "ComponentRegistry.hpp"
#include <filesystem>

namespace sle::componentdata {
std::string resolveAssetPath(const std::string& path)
{
    namespace fs = std::filesystem;
    if (path.empty()) return {};
    if (fs::exists(path)) return fs::path(path).lexically_normal().generic_string();
    for (auto current=fs::current_path(); !current.empty(); current=current.parent_path())
    {
        const auto candidate=current/path;
        if (fs::exists(candidate)) return candidate.lexically_normal().generic_string();
        if (current==current.root_path()) break;
    }
    return path;
}

} // namespace sle::componentdata
