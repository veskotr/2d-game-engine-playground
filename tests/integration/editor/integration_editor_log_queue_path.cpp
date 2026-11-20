#include <EditorLogQueue.hpp>
#include <iostream>

int main()
{
    sle::editor::EditorLogQueue queue;
    for (int i = 0; i < 1100; ++i)
        queue.push({sle::core::LogLevel::Info, std::to_string(i), {}});

    const auto first = queue.drain();
    const auto second = queue.drain();
    if (!first || !second || first->at("dropped") != 76 || first->at("messages").size() != 64 ||
        first->at("messages").front().at("message") != "76" ||
        second->at("dropped") != 0 || second->at("messages").front().at("message") != "140")
    {
        std::cerr << "Expected bounded, FIFO console batches and a one-time overflow count\n";
        return 1;
    }
    for (int i = 0; i < 1100; ++i)
        queue.push({sle::core::LogLevel::Info, "queued", {}});
    queue.clear();
    const auto empty = queue.drain();
    if (empty)
    {
        std::cerr << "Clearing must remove pending messages and reset the overflow count\n";
        return 1;
    }
    queue.push({sle::core::LogLevel::Info, "new run", {}});
    const auto restarted = queue.drain();
    if (!restarted || restarted->at("dropped") != 0 || restarted->at("messages").size() != 1)
        return 1;
    return 0;
}
