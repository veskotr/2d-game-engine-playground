#include <sle/ui/UILayoutParser.hpp>
#include <sle/ui/UIResources.hpp>

#include <iostream>

int main() {
    sle::ui::UILayoutResource layout;
    if (!layout.loadFromFiles("tests/data/ui/integration_layout_invalid.xml")) {
        std::cerr << "Failed to load invalid test UI layout asset\n";
        return 1;
    }

    auto parseResult = sle::ui::UILayoutParser::parse(layout);
    if (parseResult.ok()) {
        std::cerr << "Expected invalid XML parse to fail\n";
        return 1;
    }

    if (parseResult.error().empty()) {
        std::cerr << "Expected parser to return an error message\n";
        return 1;
    }

    return 0;
}
