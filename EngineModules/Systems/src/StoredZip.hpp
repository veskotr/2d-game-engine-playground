#pragma once
#include <cstddef>
#include <cstdint>
#include <filesystem>
#include <functional>
#include <map>
#include <string>

// Reader for the engine's uncompressed (STORED) ZIP containers: prefab/scene
// packages and game packages. Throws std::invalid_argument on any violation.
namespace sle::storedzip {
struct Limits {
    std::size_t minEntries;
    std::size_t maxEntries;
    std::size_t maxEntry;
    std::size_t maxTotal;
};
void require(bool condition, const std::string& message);
std::string readFile(const std::filesystem::path& path, std::size_t maxSize);
std::uint32_t crc32(const std::string& bytes);
// Returns every entry by name. Each name must satisfy acceptName.
std::map<std::string, std::string> unpack(const std::string& bytes, const Limits& limits,
    const std::function<bool(const std::string&)>& acceptName);
// Derived-cache identity only; archive data itself is independently CRC checked.
std::string cacheKey(const std::string& bytes);
}
