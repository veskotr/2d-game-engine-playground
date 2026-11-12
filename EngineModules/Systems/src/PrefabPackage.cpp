#include <sle/engine/PrefabPackage.hpp>
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
void require(bool condition, const std::string& message)
{
    if (!condition) throw std::invalid_argument(message);
}
std::string readFile(const fs::path& path)
{
    require(fs::is_regular_file(path) && fs::file_size(path) <= maxTotal, "Missing or oversized package file");
    std::ifstream stream(path, std::ios::binary);
    require(stream.is_open(), "Cannot open package file");
    const auto size = fs::file_size(path);
    std::string bytes(static_cast<std::size_t>(size), '\0');
    stream.read(bytes.data(), static_cast<std::streamsize>(bytes.size()));
    require(static_cast<std::size_t>(stream.gcount()) == bytes.size(), "Truncated package file");
    return bytes;
}
std::uint32_t crc32(const std::string& bytes)
{
    std::uint32_t crc = ~0U;
    for (unsigned char byte : bytes) {
        crc ^= byte;
        for (int i = 0; i < 8; ++i) crc = (crc >> 1) ^ (0xedb88320U & (0U - (crc & 1U)));
    }
    return ~crc;
}
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
std::map<std::string,std::string> unpack(const std::string& bytes, const std::string& documentName)
{
    auto u16 = [&](std::size_t offset) -> std::uint32_t {
        require(offset <= bytes.size() && bytes.size()-offset >= 2, "Truncated ZIP header");
        return static_cast<unsigned char>(bytes[offset]) | (static_cast<unsigned char>(bytes[offset+1]) << 8);
    };
    auto u32 = [&](std::size_t offset) -> std::uint32_t { return u16(offset) | (u16(offset+2) << 16); };
    require(bytes.size() >= 22, "Truncated ZIP archive");
    // Writer emits no archive comment; accepting comments does not alter entry semantics.
    std::size_t end = bytes.size()-22;
    while (u32(end) != 0x06054b50U || end+22+u16(end+20) != bytes.size()) {
        require(end > 0 && bytes.size()-end < 65557, "Missing ZIP directory");
        --end;
    }
    require(u16(end+4)==0 && u16(end+6)==0 && u16(end+8)==u16(end+10), "Split ZIP archives are unsupported");
    const auto count = u16(end+10);
    require(count >= 2 && count <= 4096, "Invalid package entry count");
    std::size_t cursor = u32(end+16);
    require(cursor <= end && u32(end+12)==end-cursor, "Invalid ZIP directory bounds");
    const auto directoryStart = cursor;
    std::size_t total = 0;
    std::map<std::string,std::string> entries;
    std::set<std::size_t> offsets;
    for (std::uint32_t i=0; i<count; ++i) {
        require(cursor<=end && end-cursor>=46 && u32(cursor)==0x02014b50U, "Invalid ZIP directory entry");
        const auto flags=u16(cursor+8), method=u16(cursor+10);
        const auto crc=u32(cursor+16), compressed=u32(cursor+20), size=u32(cursor+24);
        const auto nameSize=u16(cursor+28), extra=u16(cursor+30), comment=u16(cursor+32);
        const std::size_t offset=u32(cursor+42);
        const std::size_t next=cursor+46+nameSize+extra+comment;
        require(next<=end && u16(cursor+34)==0, "Invalid ZIP directory entry bounds");
        const auto name=bytes.substr(cursor+46,nameSize);
        require(name=="manifest.json" || name==documentName || validEntry(name), "Unsafe or unsupported package entry: "+name);
        require((flags & ~0x800U)==0 && method==0 && compressed==size, "Only unencrypted STORED ZIP entries are supported");
        require(size<=maxEntry && total<=maxTotal-size, "Package exceeds size limits");
        total+=size;
        require(offset<directoryStart && directoryStart-offset>=30 && offsets.insert(offset).second && u32(offset)==0x04034b50U, "Invalid ZIP local entry");
        const auto localName=u16(offset+26), localExtra=u16(offset+28);
        const std::size_t start=offset+30+localName+localExtra;
        require(start<=directoryStart && size<=directoryStart-start &&
            u16(offset+6)==flags && u16(offset+8)==method && u32(offset+14)==crc &&
            u32(offset+18)==size && u32(offset+22)==size && localName==nameSize && bytes.substr(offset+30,localName)==name,
            "Mismatched ZIP local entry");
        auto payload=bytes.substr(start,size);
        require(crc32(payload)==crc, "Corrupt package entry: "+name);
        require(entries.emplace(name,std::move(payload)).second, "Duplicate package entry");
        cursor=next;
    }
    require(cursor==end, "Unexpected ZIP directory data");
    return entries;
}
std::string cacheKey(const std::string& bytes)
{
    // Cache identity only: archive data itself is independently CRC checked.
    std::uint64_t first=14695981039346656037ULL, second=1099511628211ULL;
    for (unsigned char byte : bytes) { first=(first^byte)*1099511628211ULL; second=(second+byte)*0x9e3779b185ebca87ULL; }
    std::ostringstream key;
    key << std::hex << std::setfill('0') << std::setw(16) << first << std::setw(16) << second;
    return key.str();
}
}

static core::Result<Json> readPackage(const std::string& path, bool scene)
{
    try {
        const auto bytes=readFile(fs::u8path(path));
        const std::string documentName = scene ? "scene.json" : "entity.json";
        const auto entries=unpack(bytes, documentName);
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
        const auto cache=base/cacheKey(bytes);
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
