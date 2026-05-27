#pragma once

#include <string>
#include <vector>

namespace sle::ui {

struct UIAttribute
{
    std::string name;
    std::string value;
    std::string resolvedValue;
    std::vector<std::string> bindingKeys;
};

struct UIElement
{
    std::string type;
    std::string id;
    std::string text;
    std::string resolvedText;
    std::vector<std::string> textBindingKeys;
    std::vector<UIAttribute> attributes;
    std::vector<UIElement> children;
};

} // namespace sle::ui
