#pragma once

#include <cstdint>
#include <string>

namespace sle::ui {

struct UIClickEvent
{
    uint32_t ownerEntityId = 0;
    std::string elementId;
    std::string handler;
};

} // namespace sle::ui
