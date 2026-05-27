#pragma once

#include <sle/ui/UIValue.hpp>

#include <string>
#include <unordered_map>
#include <vector>

namespace sle::ui {

class UIBindingContext
{
public:
    bool set(const std::string& key, UIValue value);

    const UIValue* find(const std::string& key) const;
    bool has(const std::string& key) const;

    std::vector<std::string> consumeDirtyKeys();

private:
    std::unordered_map<std::string, UIValue> values;
    std::vector<std::string> dirtyKeys;
};

} // namespace sle::ui
