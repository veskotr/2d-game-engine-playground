#pragma once
#include <fmt/core.h>
#include <iostream>

enum class LogLevel
{
    Info,
    Warn,
    Error,
    Debug
};

class Log
{
public:
    template<typename... Args>
    static void info(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("INFO", fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void warn(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("WARN", fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void error(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("ERROR", fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    static void debug(fmt::format_string<Args...> fmt, Args&&... args)
    {
        print("DEBUG", fmt, std::forward<Args>(args)...);
    }

private:
    template<typename... Args>
    static void print(const char* level, fmt::format_string<Args...> fmt, Args&&... args)
    {
        std::cout << fmt::format("[{}] {}\n", level,
            fmt::format(fmt, std::forward<Args>(args)...));
    }
};