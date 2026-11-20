#pragma once

#include <functional>
#include <map>
#include <string>
#include <vector>

namespace sle {
using InputActionMap = std::map<std::string, std::vector<int>, std::less<>>;
}
