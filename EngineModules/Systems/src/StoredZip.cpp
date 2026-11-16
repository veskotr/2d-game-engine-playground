#include "StoredZip.hpp"
#include <fstream>
#include <iomanip>
#include <set>
#include <sstream>
#include <stdexcept>

namespace sle::storedzip {
namespace fs = std::filesystem;

void require(bool condition, const std::string& message)
{
    if (!condition) throw std::invalid_argument(message);
}

std::string readFile(const fs::path& path, std::size_t maxSize)
{
    require(fs::is_regular_file(path) && fs::file_size(path) <= maxSize, "Missing or oversized package file");
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

std::map<std::string,std::string> unpack(const std::string& bytes, const Limits& limits,
    const std::function<bool(const std::string&)>& acceptName)
{
    auto u16 = [&](std::size_t offset) -> std::uint32_t {
        require(offset <= bytes.size() && bytes.size()-offset >= 2, "Truncated ZIP header");
        return static_cast<unsigned char>(bytes[offset]) | (static_cast<unsigned char>(bytes[offset+1]) << 8);
    };
    auto u32 = [&](std::size_t offset) -> std::uint32_t { return u16(offset) | (u16(offset+2) << 16); };
    require(bytes.size() >= 22, "Truncated ZIP archive");
    // Writers emit no archive comment; accepting comments does not alter entry semantics.
    std::size_t end = bytes.size()-22;
    while (u32(end) != 0x06054b50U || end+22+u16(end+20) != bytes.size()) {
        require(end > 0 && bytes.size()-end < 65557, "Missing ZIP directory");
        --end;
    }
    require(u16(end+4)==0 && u16(end+6)==0 && u16(end+8)==u16(end+10), "Split ZIP archives are unsupported");
    const auto count = u16(end+10);
    require(count >= limits.minEntries && count <= limits.maxEntries, "Invalid package entry count");
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
        require(acceptName(name), "Unsafe or unsupported package entry: "+name);
        require((flags & ~0x800U)==0 && method==0 && compressed==size, "Only unencrypted STORED ZIP entries are supported");
        require(size<=limits.maxEntry && total<=limits.maxTotal-size, "Package exceeds size limits");
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
    std::uint64_t first=14695981039346656037ULL, second=1099511628211ULL;
    for (unsigned char byte : bytes) { first=(first^byte)*1099511628211ULL; second=(second+byte)*0x9e3779b185ebca87ULL; }
    std::ostringstream key;
    key << std::hex << std::setfill('0') << std::setw(16) << first << std::setw(16) << second;
    return key.str();
}
}
