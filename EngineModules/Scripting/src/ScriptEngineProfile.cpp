#include "LuaDiagnostics.hpp"
#include <sle/scripting/ScriptEngine.hpp>

#include <chrono>
#include <cstring>
#include <numeric>

namespace sle::scripting {

namespace {
using Clock = std::chrono::steady_clock;

uint64_t elapsedNs(Clock::time_point start)
{
    return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - start).count();
}
}

void ScriptEngine::setProfiling(bool enabled)
{
    if (!L || enabled == isProfiling()) return;
    if (enabled)
    {
        profileAllocator = lua_getallocf(L, &profileAllocatorData);
        lua_setallocf(L, allocateProfiled, this);
    }
    else
    {
        lua_setallocf(L, profileAllocator, profileAllocatorData);
        profileAllocator = nullptr;
        clearProfileFrame();
    }
}

void ScriptEngine::clearProfileFrame()
{
    profileRows.clear();
    profileSourcePaths.clear();
    profileElapsedNs = 0;
    profileGcNs = 0;
}

void* ScriptEngine::allocateProfiled(void* user, void* pointer, size_t oldSize, size_t newSize)
{
    auto& engine = *static_cast<ScriptEngine*>(user);
    // When pointer is null, Lua uses oldSize to identify the object type.
    const size_t previous = pointer ? oldSize : 0;
    void* result = engine.profileAllocator(engine.profileAllocatorData, pointer, oldSize, newSize);
    if (auto* scope = engine.profileScope; scope && (result || newSize == 0))
    {
        if (newSize > previous) scope->allocatedBytes += newSize - previous;
        else scope->freedBytes += previous - newSize;
    }
    return result;
}

int ScriptEngine::callLua(int arguments, int results, ProfileCall kind)
{
    if (!isProfiling()) return detail::protectedCall(L, arguments, results);

    const auto scopeStart = Clock::now();
    ProfileScope scope;
    auto* parent = std::exchange(profileScope, &scope);

    // Capture identity before callbacks can replace the attachment.
    auto [entry, inserted] = profileRows.try_emplace(
        std::make_tuple(executingEntity, executingBehaviourKey, executingBehaviourGeneration));
    auto& row = entry->second;
    if (inserted)
    {
        row.entity = executingEntity;
        row.behaviour = executingBehaviourKey;
    }
    if (row.source.empty())
    {
        lua_Debug info{};
        lua_pushvalue(L, -1 - arguments);
        if (lua_getinfo(L, ">S", &info) && info.source && info.source[0] == '@')
        {
            const std::string_view source(info.source + 1, info.srclen - 1);
            if (const auto found = profileSourcePaths.find(source); found != profileSourcePaths.end())
                row.source = found->second;
            else
            {
                row.source = detail::absoluteLuaSource(std::string(source));
                if (profileSourcePaths.size() < 1024) profileSourcePaths.emplace(source, row.source);
            }
        }
    }

    const auto start = Clock::now();
    const int status = detail::protectedCall(L, arguments, results);
    const auto duration = elapsedNs(start);
    const auto exclusive = duration - scope.childNs;
    profileElapsedNs += exclusive;
    const auto index = static_cast<size_t>(kind);
    ++row.calls[index];
    row.elapsedNs[index] += exclusive;
    row.allocatedBytes += scope.allocatedBytes;
    row.freedBytes += scope.freedBytes;
    profileScope = parent;
    if (parent) parent->childNs += elapsedNs(scopeStart);
    return status;
}

LuaProfileFrame ScriptEngine::finishProfileFrame()
{
    if (!isProfiling()) return {};
    LuaProfileFrame frame;
    frame.elapsedNs = std::exchange(profileElapsedNs, 0);
    frame.explicitGcNs = std::exchange(profileGcNs, 0);
    frame.memoryBytes = 1024ULL * lua_gc(L, LUA_GCCOUNT) + lua_gc(L, LUA_GCCOUNTB);
    frame.rows.reserve(profileRows.size());
    for (auto& [owner, row] : profileRows) frame.rows.push_back(std::move(row));
    profileRows.clear();

    constexpr size_t rowLimit = 512;
    const auto total = [](const LuaProfileRow& row) {
        return std::accumulate(row.elapsedNs.begin(), row.elapsedNs.end(), uint64_t{0});
    };
    const auto retained = std::min(rowLimit, frame.rows.size());
    std::partial_sort(frame.rows.begin(), frame.rows.begin() + retained, frame.rows.end(),
        [&](const auto& left, const auto& right) { return total(left) > total(right); });
    frame.omittedRows = frame.rows.size() - retained;
    frame.rows.resize(retained);
    return frame;
}

void ScriptEngine::installProfileGc()
{
    lua_getglobal(L, "collectgarbage");
    collectGarbage = lua_tocfunction(L, -1);
    lua_pop(L, 1);
    *static_cast<ScriptEngine**>(lua_getextraspace(L)) = this;
    lua_pushcfunction(L, profileGc);
    lua_setglobal(L, "collectgarbage");
}

int ScriptEngine::profileGc(lua_State* state)
{
    // Lua copies the main state's extra space into new coroutines.
    auto& engine = **static_cast<ScriptEngine**>(lua_getextraspace(state));
    if (!engine.isProfiling()) return engine.collectGarbage(state);
    const char* operation = lua_type(state, 1) == LUA_TSTRING ? lua_tostring(state, 1) : nullptr;
    const bool timed = lua_isnoneornil(state, 1) ||
        (operation && (std::strcmp(operation, "collect") == 0 || std::strcmp(operation, "step") == 0));
    if (!timed) return engine.collectGarbage(state);
    const auto start = Clock::now();
    const int results = engine.collectGarbage(state);
    engine.profileGcNs += elapsedNs(start);
    return results;
}

} // namespace sle::scripting
