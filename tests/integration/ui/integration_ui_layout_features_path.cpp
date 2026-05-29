#include <sle/ui/UILayoutParser.hpp>
#include <sle/ui/UIResources.hpp>

#include <iostream>

// Verifies that the layout parser correctly reads the new layout feature attributes:
// textAlign, wrap, and anchor.  These are pure attribute presence checks — no rendering
// pipeline needed since UISystem rendering is an integration concern tested separately.

static const sle::ui::UIElement* findById(const sle::ui::UIElement& root, const std::string& id)
{
    if (root.id == id)
        return &root;
    for (const auto& child : root.children)
    {
        if (const auto* found = findById(child, id))
            return found;
    }
    return nullptr;
}

static std::string getAttribute(const sle::ui::UIElement& elem, const std::string& name)
{
    for (const auto& attr : elem.attributes)
        if (attr.name == name)
            return attr.value;
    return {};
}

int main()
{
    sle::ui::UILayoutResource layout;
    if (!layout.loadFromFiles("tests/data/ui/integration_layout_features.xml"))
    {
        std::cerr << "Failed to load integration_layout_features.xml\n";
        return 1;
    }

    auto parseResult = sle::ui::UILayoutParser::parse(layout);
    if (!parseResult.ok())
    {
        std::cerr << "UILayoutParser failed: " << parseResult.error() << "\n";
        return 1;
    }

    const sle::ui::UIElement& root = parseResult.value();

    // --- textAlign: center ---
    const auto* centeredLabel = findById(root, "centeredLabel");
    if (!centeredLabel)
    {
        std::cerr << "centeredLabel not found\n";
        return 1;
    }
    if (getAttribute(*centeredLabel, "textAlign") != "center")
    {
        std::cerr << "Expected textAlign=center on centeredLabel\n";
        return 1;
    }

    // --- textAlign: right ---
    const auto* rightLabel = findById(root, "rightLabel");
    if (!rightLabel)
    {
        std::cerr << "rightLabel not found\n";
        return 1;
    }
    if (getAttribute(*rightLabel, "textAlign") != "right")
    {
        std::cerr << "Expected textAlign=right on rightLabel\n";
        return 1;
    }

    // --- anchor: center ---
    const auto* anchoredPanel = findById(root, "anchoredPanel");
    if (!anchoredPanel)
    {
        std::cerr << "anchoredPanel not found\n";
        return 1;
    }
    if (getAttribute(*anchoredPanel, "anchor") != "center")
    {
        std::cerr << "Expected anchor=center on anchoredPanel\n";
        return 1;
    }

    // --- wrap: true ---
    const auto* wrapLabel = findById(root, "wrapLabel");
    if (!wrapLabel)
    {
        std::cerr << "wrapLabel not found\n";
        return 1;
    }
    if (getAttribute(*wrapLabel, "wrap") != "true")
    {
        std::cerr << "Expected wrap=true on wrapLabel\n";
        return 1;
    }
    if (wrapLabel->text.empty())
    {
        std::cerr << "Expected non-empty text on wrapLabel\n";
        return 1;
    }

    // --- child under anchor-shifted parent is parsed correctly ---
    const auto* anchoredChild = findById(root, "anchoredChild");
    if (!anchoredChild)
    {
        std::cerr << "anchoredChild not found under anchoredPanel\n";
        return 1;
    }
    if (getAttribute(*anchoredChild, "textAlign") != "center")
    {
        std::cerr << "Expected textAlign=center on anchoredChild\n";
        return 1;
    }

    return 0;
}
