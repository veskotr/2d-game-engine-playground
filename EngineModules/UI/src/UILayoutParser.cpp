#include <sle/ui/UILayoutParser.hpp>

#include <pugixml.hpp>

#include <string_view>
#include <utility>

namespace sle::ui {
namespace {

std::vector<std::string> extractBindingKeys(std::string_view value)
{
    std::vector<std::string> keys;

    std::size_t cursor = 0;
    while (cursor < value.size())
    {
        const std::size_t start = value.find("{{", cursor);
        if (start == std::string_view::npos)
            break;

        const std::size_t end = value.find("}}", start + 2);
        if (end == std::string_view::npos)
            break;

        std::string key(value.substr(start + 2, end - (start + 2)));
        if (!key.empty())
            keys.push_back(std::move(key));

        cursor = end + 2;
    }

    return keys;
}

UIElement parseElement(const pugi::xml_node& node)
{
    UIElement element;
    element.type = node.name();
    element.text = node.text().as_string();
    element.resolvedText = element.text;
    element.textBindingKeys = extractBindingKeys(element.text);

    if (const auto idAttribute = node.attribute("id"))
        element.id = idAttribute.as_string();

    for (const auto& attribute : node.attributes())
    {
        UIAttribute parsedAttribute;
        parsedAttribute.name = attribute.name();
        parsedAttribute.value = attribute.as_string();
        parsedAttribute.resolvedValue = parsedAttribute.value;
        parsedAttribute.bindingKeys = extractBindingKeys(parsedAttribute.value);
        element.attributes.push_back(std::move(parsedAttribute));
    }

    for (const auto& child : node.children())
    {
        if (child.type() != pugi::node_element)
            continue;

        element.children.push_back(parseElement(child));
    }

    return element;
}

} // namespace

sle::core::Result<UIElement> UILayoutParser::parse(const UILayoutResource& resource)
{
    pugi::xml_document document;
    const auto result = document.load_string(resource.getXML().c_str());
    if (!result)
        return sle::core::Result<UIElement>::error(result.description());

    const auto root = document.document_element();
    if (!root)
        return sle::core::Result<UIElement>::error("UI layout has no root element");

    return sle::core::Result<UIElement>::success(parseElement(root));
}

} // namespace sle::ui
