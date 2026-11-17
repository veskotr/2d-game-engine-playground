#include "GamePackage.hpp"
#include "StoredZip.hpp"
#include <nlohmann/json.hpp>
#include <algorithm>
#include <cctype>
#include <fstream>
#include <map>
#include <random>
#include <regex>
#include <set>

namespace sle::gamepackage {
namespace {
namespace fs = std::filesystem;
using Json = nlohmann::json;
using storedzip::require;
constexpr std::size_t maxEntries = 65000;
constexpr std::size_t maxEntry = 256ULL * 1024 * 1024;
constexpr std::size_t maxTotal = 1024ULL * 1024 * 1024;
// Payload limit plus generous room for ZIP headers and names.
constexpr std::size_t maxArchive = maxTotal + 64ULL * 1024 * 1024;
constexpr std::string_view assetPrefix = "assets/";

std::string lower(std::string value)
{
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
}

// Portable on Windows and POSIX: no traversal, drive, separator or reserved names.
bool validSegment(const std::string& segment)
{
    if (segment.empty() || segment == "." || segment == ".." || segment.size() > 255) return false;
    if (segment.back() == '.' || segment.back() == ' ') return false;
    for (const unsigned char c : segment)
        if (c < 32 || c == 127 || std::string_view("\\:*?\"<>|").find(static_cast<char>(c)) != std::string_view::npos) return false;
    static const std::regex reserved("(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");
    return !std::regex_match(lower(segment), reserved);
}

bool validAssetEntry(const std::string& name)
{
    if (!name.starts_with(assetPrefix) || name.size() == assetPrefix.size() || name.size() > 1024) return false;
    std::size_t start = assetPrefix.size();
    while (true) {
        const auto slash = name.find('/', start);
        if (!validSegment(name.substr(start, slash == std::string::npos ? std::string::npos : slash - start))) return false;
        if (slash == std::string::npos) return true;
        start = slash + 1;
    }
}

bool uuid(const Json& value)
{
    static const std::regex pattern("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    return value.is_string() && std::regex_match(value.get<std::string>(), pattern);
}

using Files = std::map<fs::path, const std::string*>;

// The tree must contain exactly the packaged files: an added file could otherwise
// become an indexed scene or asset of the mounted game.
bool matches(const fs::path& directory, const Files& files)
{
    std::error_code error;
    if (fs::is_symlink(fs::symlink_status(directory)) || !fs::is_directory(directory, error)) return false;
    std::size_t seen = 0;
    for (auto it = fs::recursive_directory_iterator(directory, error); !error && it != fs::recursive_directory_iterator(); it.increment(error)) {
        const auto status = it->symlink_status();
        if (fs::is_symlink(status)) return false;
        if (fs::is_directory(status)) continue;
        const auto found = files.find(it->path().lexically_relative(directory));
        if (!fs::is_regular_file(status) || found == files.end()) return false;
        try { if (storedzip::readFile(it->path(), maxEntry) != *found->second) return false; }
        catch (const std::exception&) { return false; }
        ++seen;
    }
    return !error && seen == files.size();
}

void extract(const fs::path& directory, const Files& files)
{
    for (const auto& [relative, payload] : files) {
        const auto target = directory / relative;
        fs::create_directories(target.parent_path());
        std::ofstream file(target, std::ios::binary);
        file.write(payload->data(), static_cast<std::streamsize>(payload->size()));
        file.close();
        require(file.good(), "Cannot unpack game package file: " + relative.generic_string());
    }
}

fs::path uniqueDirectory(const fs::path& parent, const std::string& stem)
{
    std::random_device random;
    for (int attempt = 0; attempt < 16; ++attempt) {
        auto candidate = parent / (stem + "-" + std::to_string(random()));
        if (fs::create_directory(candidate)) return candidate;
    }
    throw std::invalid_argument("Cannot create game package directory");
}

struct RemoveTree {
    fs::path path;
    ~RemoveTree() { std::error_code ignored; fs::remove_all(path, ignored); }
};
}

Mounted mount(const fs::path& package)
{
    const auto bytes = storedzip::readFile(package, maxArchive);
    std::set<std::string> folded;
    const auto entries = storedzip::unpack(bytes, {1, maxEntries, maxEntry, maxTotal}, [&](const std::string& name) {
        // Case-insensitive file systems must not merge two packaged files.
        return (name == "manifest.json" || validAssetEntry(name)) && folded.insert(lower(name)).second;
    });
    const auto found = entries.find("manifest.json");
    require(found != entries.end() && found->second.size() <= 4 * 1024 * 1024, "Missing or oversized manifest.json");
    const auto manifest = Json::parse(found->second);
    require(manifest.is_object(), "Game package manifest must be an object");
    for (const auto& [key, value] : manifest.items())
        require(key == "format" || key == "version" || key == "id" || key == "name" || key == "mainScene", "Unknown game package field: " + key);
    require(manifest.value("format", Json()) == "sle-package" && manifest.contains("version") &&
        manifest.at("version").is_number_integer() && manifest.at("version") == 1, "Unsupported game package format/version");
    require(manifest.contains("id") && uuid(manifest.at("id")), "Invalid game package id");
    require(manifest.contains("name") && manifest.at("name").is_string() && !manifest.at("name").get<std::string>().empty(), "Invalid game package name");
    require(manifest.contains("mainScene") && (manifest.at("mainScene").is_null() || uuid(manifest.at("mainScene"))), "Invalid game package mainScene");

    Files files;
    std::set<std::string> folders;
    for (const auto& [name, payload] : entries) {
        if (name == "manifest.json") continue;
        const auto relative = fs::u8path(name.substr(assetPrefix.size()));
        for (auto parent = relative.parent_path(); !parent.empty(); parent = parent.parent_path()) folders.insert(lower(parent.generic_string()));
        files.emplace(relative, &payload);
    }
    for (const auto& [relative, payload] : files)
        require(!folders.contains(lower(relative.generic_string())), "Game package path is both a file and a folder: " + relative.generic_string());

    Mounted mounted;
    mounted.id = manifest.at("id").get<std::string>();
    mounted.name = manifest.at("name").get<std::string>();
    if (!manifest.at("mainScene").is_null()) mounted.mainScene = manifest.at("mainScene").get<std::string>();

    // Complete validation above precedes all cache writes.
    const auto base = fs::temp_directory_path() / "sle-package-cache";
    require(!fs::is_symlink(fs::symlink_status(base)), "Unsafe game package cache directory");
    fs::create_directories(base);
    const auto cache = base / storedzip::cacheKey(bytes);
    if (!fs::exists(fs::symlink_status(cache))) {
        // Publish a complete immutable directory, never a partial tree.
        const auto staging = uniqueDirectory(base, cache.filename().string() + ".tmp");
        RemoveTree cleanup{staging};
        extract(staging, files);
        std::error_code error;
        fs::rename(staging, cache, error);
        require(!error || fs::is_directory(cache), "Cannot publish game package cache");
    }
    if (matches(cache, files)) {
        mounted.root = cache;
        return mounted;
    }
    // A damaged or colliding shared cache is never used or repaired in place
    // (another process may be reading it): unpack a private copy instead.
    const auto privateBase = fs::temp_directory_path() / "sle-package-runtime";
    require(!fs::is_symlink(fs::symlink_status(privateBase)), "Unsafe game package runtime directory");
    fs::create_directories(privateBase);
    auto owner = std::make_shared<RemoveTree>();
    owner->path = uniqueDirectory(privateBase, "package");
    extract(owner->path, files);
    require(matches(owner->path, files), "Cannot unpack game package");
    mounted.root = owner->path;
    mounted.owner = std::move(owner);
    return mounted;
}
}
