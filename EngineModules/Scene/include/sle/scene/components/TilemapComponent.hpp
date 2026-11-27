#pragma once

#include <string>

namespace sle::components {
struct TilemapComponent
{
    std::string asset;
    int sortingOffset = 0;
};
}
