#pragma once

#include <cstdint>
#include <string>
#include <variant>

namespace sle::ui {

using UIValue = std::variant<std::monostate, bool, int64_t, double, std::string>;

} // namespace sle::ui
