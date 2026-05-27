#include <sle/ui/UIDocument.hpp>

#include <sle/ui/UIValue.hpp>

#include <type_traits>
#include <string_view>
#include <utility>

namespace sle::ui {
namespace {

std::string valueToString(const UIValue& value)
{
    return std::visit([](const auto& current) -> std::string {
        using TValue = std::decay_t<decltype(current)>;

        if constexpr (std::is_same_v<TValue, std::monostate>)
        {
            return {};
        }
        else if constexpr (std::is_same_v<TValue, bool>)
        {
            return current ? "true" : "false";
        }
        else if constexpr (std::is_same_v<TValue, std::string>)
        {
            return current;
        }
        else
        {
            return std::to_string(current);
        }
    }, value);
}

std::string resolveTemplate(std::string_view source, const UIBindingContext& bindings)
{
    std::string resolved;
    resolved.reserve(source.size());

    std::size_t cursor = 0;
    while (cursor < source.size())
    {
        const std::size_t start = source.find("{{", cursor);
        if (start == std::string_view::npos)
        {
            resolved.append(source.substr(cursor));
            break;
        }

        resolved.append(source.substr(cursor, start - cursor));

        const std::size_t end = source.find("}}", start + 2);
        if (end == std::string_view::npos)
        {
            resolved.append(source.substr(start));
            break;
        }

        const std::string key(source.substr(start + 2, end - (start + 2)));
        if (const auto* value = bindings.find(key))
            resolved.append(valueToString(*value));

        cursor = end + 2;
    }

    return resolved;
}

void refreshElementBindings(UIElement& element, const UIBindingContext& bindings)
{
    element.resolvedText = resolveTemplate(element.text, bindings);

    for (auto& attribute : element.attributes)
        attribute.resolvedValue = resolveTemplate(attribute.value, bindings);

    for (auto& child : element.children)
        refreshElementBindings(child, bindings);
}

} // namespace

UIDocument::UIDocument(UIDocumentDescriptor descriptor)
    : descriptor(std::move(descriptor))
{
}

void UIDocument::setLayoutRoot(UIElement root)
{
    layoutRoot = std::move(root);
}

void UIDocument::setLastError(std::string error)
{
    lastError = std::move(error);
}

void UIDocument::clearLastError()
{
    lastError.clear();
}

void UIDocument::refreshBindings()
{
    if (!layoutRoot)
        return;

    refreshElementBindings(*layoutRoot, bindings);
}

} // namespace sle::ui
