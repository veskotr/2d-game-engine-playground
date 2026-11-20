#pragma once
#include <fmt/core.h>
#include <iostream>
#include <cstdint>
#include <functional>
#include <mutex>
#include <optional>
#include <string>
#include <utility>

namespace sle::core {

enum class LogLevel
{
    Info,
    Warn,
    Error,
    Debug
};

struct LuaDiagnostic
{
    std::string file;
    int line = 0;
    uint32_t entity = 0;
    std::string behaviour;
    std::string traceback;
};

struct LogEntry
{
    LogLevel level;
    std::string message;
    std::optional<LuaDiagnostic> lua;
};

class Log
{
public:
    using Sink = std::function<void(const LogEntry&)>;

    // The sink runs synchronously under the log lock. It must not call Log.
    // Replacing it waits for any in-flight call to finish.
    static void setSink(Sink sink)
    {
        std::lock_guard lock(mutex);
        observer = std::move(sink);
    }

    static void luaError(std::string message, LuaDiagnostic diagnostic)
    {
        print("ERROR", {LogLevel::Error, std::move(message), std::move(diagnostic)});
    }

    template<typename... Args>
    static void info(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("INFO", {LogLevel::Info, fmt::format(fmt, std::forward<Args>(args)...), {}});
    }

    template<typename... Args>
    static void warn(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("WARN", {LogLevel::Warn, fmt::format(fmt, std::forward<Args>(args)...), {}});
    }

    template<typename... Args>
    static void error(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("ERROR", {LogLevel::Error, fmt::format(fmt, std::forward<Args>(args)...), {}});
    }

    template<typename... Args>
    static void debug(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("DEBUG", {LogLevel::Debug, fmt::format(fmt, std::forward<Args>(args)...), {}});
    }

private:
    static void print(const char* level, const LogEntry& entry)
    {
        std::lock_guard lock(mutex);
        std::cout << fmt::format("[{}] {}\n", level, entry.message);
        if (entry.lua && !entry.lua->traceback.empty())
            std::cout << entry.lua->traceback << '\n';
        if (observer)
        {
            try { observer(entry); }
            catch (...) { /* Optional observers must not interrupt the engine. */ }
        }
    }

    static inline std::mutex mutex;
    static inline Sink observer;
};

} // namespace sle::core
