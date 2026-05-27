#pragma once

#include <sle/core/Result.hpp>
#include <sle/ui/UIElement.hpp>
#include <sle/ui/UIResources.hpp>

namespace sle::ui {

class UILayoutParser
{
public:
    static sle::core::Result<UIElement> parse(const UILayoutResource& resource);
};

} // namespace sle::ui
