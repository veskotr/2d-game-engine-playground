#include <sle/engine/ChunkManager.hpp>

#include <algorithm>
#include <iostream>
#include <vector>

int main()
{
    sle::ChunkManager manager({
        .chunkWidthTiles = 16,
        .chunkHeightTiles = 16,
        .tileWidth = 32,
        .tileHeight = 32,
        .activeRadiusX = 1,
        .activeRadiusY = 1
    });

    if (!manager.isValid())
    {
        std::cerr << "ChunkManager should be valid\n";
        return 1;
    }

    if (manager.getChunkWidthWorld() != 512 || manager.getChunkHeightWorld() != 512)
    {
        std::cerr << "Unexpected chunk world dimensions\n";
        return 1;
    }

    const sle::ChunkCoord originChunk = manager.worldToChunk({0.0f, 0.0f});
    const sle::ChunkCoord edgeChunk = manager.worldToChunk({511.0f, 511.0f});
    const sle::ChunkCoord nextChunk = manager.worldToChunk({512.0f, 512.0f});
    const sle::ChunkCoord negativeChunk = manager.worldToChunk({-1.0f, -1.0f});

    if (!(originChunk == sle::ChunkCoord{0, 0}) ||
        !(edgeChunk == sle::ChunkCoord{0, 0}) ||
        !(nextChunk == sle::ChunkCoord{1, 1}) ||
        !(negativeChunk == sle::ChunkCoord{-1, -1}))
    {
        std::cerr << "worldToChunk produced unexpected coordinates\n";
        return 1;
    }

    const sle::ChunkBounds bounds = manager.getChunkBounds({1, -1});
    if (bounds.min.x != 512.0f || bounds.min.y != -512.0f ||
        bounds.max.x != 1024.0f || bounds.max.y != 0.0f)
    {
        std::cerr << "Chunk bounds mismatch\n";
        return 1;
    }

    auto activeWindow = manager.computeActiveWindow({640.0f, 700.0f});
    std::sort(activeWindow.begin(), activeWindow.end());

    const std::vector<sle::ChunkCoord> expected{
        {0, 0}, {1, 0}, {2, 0},
        {0, 1}, {1, 1}, {2, 1},
        {0, 2}, {1, 2}, {2, 2}
    };

    if (activeWindow != expected)
    {
        std::cerr << "Active chunk window mismatch\n";
        return 1;
    }

    return 0;
}
