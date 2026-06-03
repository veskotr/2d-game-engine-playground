#pragma once

#include <string>

namespace sle::events {

struct ChunkLoadedEvent
{
    std::string mapId;
    int chunkX = 0;
    int chunkY = 0;
    float minX = 0.0f;
    float minY = 0.0f;
    float maxX = 0.0f;
    float maxY = 0.0f;
};

struct ChunkActivatedEvent
{
    std::string mapId;
    int chunkX = 0;
    int chunkY = 0;
    float minX = 0.0f;
    float minY = 0.0f;
    float maxX = 0.0f;
    float maxY = 0.0f;
};

struct ChunkDeactivatedEvent
{
    std::string mapId;
    int chunkX = 0;
    int chunkY = 0;
    float minX = 0.0f;
    float minY = 0.0f;
    float maxX = 0.0f;
    float maxY = 0.0f;
};

struct ChunkUnloadedEvent
{
    std::string mapId;
    int chunkX = 0;
    int chunkY = 0;
    float minX = 0.0f;
    float minY = 0.0f;
    float maxX = 0.0f;
    float maxY = 0.0f;
};

} // namespace sle::events
