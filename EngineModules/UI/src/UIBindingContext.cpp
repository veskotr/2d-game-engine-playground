#include <sle/ui/UIBindingContext.hpp>

#include <utility>

namespace sle::ui {

bool UIBindingContext::set(const std::string& key, UIValue value)
{
    auto it = values.find(key);
    if (it != values.end() && it->second == value)
        return false;

    values[key] = std::move(value);
    dirtyKeys.push_back(key);
    return true;
}

const UIValue* UIBindingContext::find(const std::string& key) const
{
    auto it = values.find(key);
    if (it == values.end())
        return nullptr;
    return &it->second;
}

bool UIBindingContext::has(const std::string& key) const
{
    return values.find(key) != values.end();
}

std::vector<std::string> UIBindingContext::consumeDirtyKeys()
{
    std::vector<std::string> keys = std::move(dirtyKeys);
    dirtyKeys.clear();
    return keys;
}

} // namespace sle::ui
