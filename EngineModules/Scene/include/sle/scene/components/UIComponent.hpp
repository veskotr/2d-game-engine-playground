#pragma once

#include <cstdint>
#include <string>

namespace sle::components {

enum class UISpaceMode : uint8_t
{
    Screen = 0,
    World = 1,
};

struct UIComponent
{
    std::string layoutAsset;
    std::string fontAsset;
    std::string behaviorAsset;
    UISpaceMode spaceMode = UISpaceMode::Screen;
    bool visible = true;
    uint32_t layer = 0;
    std::string bindingScope;
};

} // namespace sle::components
