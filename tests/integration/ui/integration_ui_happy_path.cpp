#include <sle/ui/UIBindingContext.hpp>
#include <sle/ui/UILayoutParser.hpp>
#include <sle/ui/UIResources.hpp>

#include <iostream>

int main() {
    sle::ui::UILayoutResource layout;
    if (!layout.loadFromFiles("tests/data/ui/integration_layout.xml")) {
        std::cerr << "Failed to load test UI layout asset\n";
        return 1;
    }

    auto parseResult = sle::ui::UILayoutParser::parse(layout);
    if (!parseResult.ok()) {
        std::cerr << "UILayoutParser failed: " << parseResult.error() << "\n";
        return 1;
    }

    const sle::ui::UIElement& root = parseResult.value();
    if (root.type != "panel") {
        std::cerr << "Expected root type panel, got: " << root.type << "\n";
        return 1;
    }

    if (root.children.empty()) {
        std::cerr << "Expected at least one child element in parsed layout\n";
        return 1;
    }

    const sle::ui::UIElement& label = root.children.front();
    if (label.textBindingKeys.empty() || label.textBindingKeys.front() != "playerName") {
        std::cerr << "Expected text binding key playerName on label\n";
        return 1;
    }

    sle::ui::UIBindingContext bindings;
    const bool changed = bindings.set("playerName", sle::ui::UIValue(std::string("IntegrationUser")));
    if (!changed || !bindings.has("playerName")) {
        std::cerr << "Expected binding set/has to succeed\n";
        return 1;
    }

    auto dirty = bindings.consumeDirtyKeys();
    if (dirty.empty() || dirty.front() != "playerName") {
        std::cerr << "Expected dirty key tracking for playerName\n";
        return 1;
    }

    return 0;
}
