#pragma once

#include <sle/core/Log.hpp>
#include <nlohmann/json.hpp>
#include <deque>
#include <mutex>

namespace sle::editor {

class EditorLogQueue
{
public:
    void push(const core::LogEntry& entry)
    {
        std::lock_guard lock(mutex);
        entries.push_back(entry);
        if (entries.size() > capacity)
        {
            entries.pop_front();
            ++dropped;
        }
    }

    std::optional<nlohmann::json> drain()
    {
        std::deque<core::LogEntry> batch;
        uint64_t lost;
        {
            std::lock_guard lock(mutex);
            if (entries.empty()) return std::nullopt;
            lost = std::exchange(dropped, 0);
            while (!entries.empty() && batch.size() < batchSize)
            {
                batch.push_back(std::move(entries.front()));
                entries.pop_front();
            }
        }
        auto messages = nlohmann::json::array();
        for (const auto& entry : batch)
        {
            const char* level = "INFO";
            switch (entry.level)
            {
            case core::LogLevel::Info: break;
            case core::LogLevel::Warn: level = "WARN"; break;
            case core::LogLevel::Error: level = "ERROR"; break;
            case core::LogLevel::Debug: level = "DEBUG"; break;
            }
            nlohmann::json message = {{"level", level}, {"message", entry.message}};
            if (entry.lua)
            {
                const auto& lua = *entry.lua;
                message["lua"] = {{"file", lua.file}, {"line", lua.line}, {"entity", lua.entity},
                                  {"behaviour", lua.behaviour}, {"traceback", lua.traceback}};
            }
            messages.push_back(std::move(message));
        }
        return nlohmann::json{{"dropped", lost}, {"messages", std::move(messages)}};
    }

    void clear()
    {
        std::lock_guard lock(mutex);
        entries.clear();
        dropped = 0;
    }

private:
    static constexpr size_t capacity = 1024;
    static constexpr size_t batchSize = 64;
    std::mutex mutex;
    std::deque<core::LogEntry> entries;
    uint64_t dropped = 0;
};

} // namespace sle::editor
