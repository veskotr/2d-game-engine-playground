#include <sle/engine/PrefabPackage.hpp>
#include "StoredZip.hpp"
#include <filesystem>
#include <fstream>
#include <functional>
#include <iomanip>
#include <map>
#include <set>
#include <sstream>
#include <stdexcept>
#include <cstdint>
#include <random>

namespace sle {
namespace {
using Json = nlohmann::json;
namespace fs = std::filesystem;
constexpr std::size_t maxEntry = 128ULL * 1024 * 1024;
constexpr std::size_t maxTotal = 512ULL * 1024 * 1024;
using storedzip::require;
std::string readFile(const fs::path& path) { return storedzip::readFile(path, maxTotal); }
bool validId(const std::string& id)
{
    if (id.size() != 64) return false;
    for (const char c : id) if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return false;
    return true;
}
bool validEntry(const std::string& name)
{
    if (!name.starts_with("assets/") || name.size() < 71) return false;
    if (!validId(name.substr(7,64))) return false;
    if (name.size() == 71) return true;
    if (name[71] != '.' || name.size() < 73 || name.size() > 84) return false;
    for (std::size_t i=72; i<name.size(); ++i)
        if (!(name[i]>='a' && name[i]<='z') && !(name[i]>='0' && name[i]<='9')) return false;
    return true;
}
}

static core::Result<Json> readPackage(const std::string& path, bool scene)
{
    try {
        const auto bytes=readFile(fs::u8path(path));
        const std::string documentName = scene ? "scene.json" : "entity.json";
        const auto entries=storedzip::unpack(bytes, {2, 4096, maxEntry, maxTotal}, [&](const std::string& name) {
            return name=="manifest.json" || name==documentName || validEntry(name);
        });
        auto metadata=[&](const std::string& name) {
            const auto found=entries.find(name);
            require(found!=entries.end() && found->second.size()<=4*1024*1024, "Missing or oversized "+name);
            return Json::parse(found->second);
        };
        const auto manifest=metadata("manifest.json");
        require(manifest.at("format")== (scene ? "sle-scene" : "sle-prefab") && manifest.at("version").is_number_integer() && manifest.at("version")==1,
            "Unsupported prefab package format/version");
        require(manifest.at("assets").is_array(), "Package assets must be an array");
        const auto base=fs::temp_directory_path()/"sle-prefab-cache";
        const auto cache=base/storedzip::cacheKey(bytes);
        std::map<std::string,fs::path> paths;
        std::map<fs::path,std::string> payloads;
        std::set<std::string> listed={documentName,"manifest.json"};
        for (const auto& asset : manifest["assets"]) {
            const auto id=asset.at("id").get<std::string>();
            const auto entry=asset.at("path").get<std::string>();
            require(asset.at("name").is_string() && validId(id) && validEntry(entry) && entry.substr(7,64)==id, "Invalid package asset descriptor");
            require(listed.insert(entry).second && entries.contains(entry), "Duplicate or missing package asset");
            const auto target=cache/entry.substr(7);
            require(paths.emplace(id,target).second, "Duplicate package asset ID");
            payloads[target]=entries.at(entry);
        }
        require(listed.size()==entries.size(), "Unlisted package entries");
        const std::set<std::string> assetFields={"texture","scriptAsset","assetPath","layoutAsset","fontAsset","behaviorAsset","definitionAsset","clipAsset"};
        std::function<void(Json&,const std::string&)> resolve=[&](Json& value,const std::string& field) {
            if (value.is_string() && assetFields.contains(field)) {
                const auto text=value.get<std::string>();
                if (text.starts_with("asset://")) {
                    const auto found=paths.find(text.substr(8));
                    require(found!=paths.end(), "Missing package asset reference: "+text);
                    value=found->second.generic_string();
                }
            } else if (value.is_object()) {
                for (auto& [key,child] : value.items()) resolve(child,field=="stateClipMap" ? "clipAsset" : key);
            } else if (value.is_array()) for (auto& child : value) resolve(child,field);
        };
        auto document=metadata(documentName);
        resolve(document,"");
        for (const auto& asset : manifest["assets"]) {
            auto& payload=payloads.at(paths.at(asset.at("id").get<std::string>()));
            auto name=asset.at("name").get<std::string>();
            for (auto& c : name) if (c>='A' && c<='Z') c=static_cast<char>(c-'A'+'a');
            if (name.ends_with(".json")) { auto data=Json::parse(payload); resolve(data,""); payload=data.dump(); }
        }
        // Complete validation above precedes all cache writes and scene mutation.
        require(!fs::is_symlink(base) && !fs::is_symlink(cache), "Unsafe prefab cache directory");
        fs::create_directories(base);
        if (!fs::exists(cache)) {
            // Publish a complete immutable cache directory, never partial files.
            std::random_device random;
            fs::path staging;
            for (int attempt=0; attempt<16 && staging.empty(); ++attempt) {
                auto candidate=base/(cache.filename().string()+".tmp-"+std::to_string(random()));
                if (fs::create_directory(candidate)) staging=std::move(candidate);
            }
            require(!staging.empty(), "Cannot create prefab cache staging directory");
            struct Cleanup { fs::path path; ~Cleanup() { std::error_code ignored; fs::remove_all(path,ignored); } } cleanup{staging};
            for (const auto& [target,payload] : payloads) {
                std::ofstream file(staging/target.filename(),std::ios::binary);
                file.write(payload.data(),static_cast<std::streamsize>(payload.size()));
                file.close();
                require(file.good(), "Cannot materialize prefab asset");
            }
            std::error_code error;
            fs::rename(staging,cache,error);
            require(!error || fs::is_directory(cache), "Cannot publish prefab cache");
        }
        require(!fs::is_symlink(cache), "Unsafe prefab cache directory");
        for (const auto& [target,payload] : payloads) {
            require(!fs::is_symlink(target), "Unsafe prefab cache entry");
            require(readFile(target)==payload, "Prefab cache content mismatch");
        }
        return core::Result<Json>::success(std::move(document));
    } catch (const std::exception& error) {
        return core::Result<Json>::error("Prefab package '"+path+"': "+error.what());
    }
}
core::Result<Json> PrefabPackage::read(const std::string& path) { return readPackage(path, false); }
core::Result<Json> PrefabPackage::readScene(const std::string& path) { return readPackage(path, true); }

}
