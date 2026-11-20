#pragma once

#include <stdexcept>

namespace sle::detail {

// The protected Lua boundary has already logged this error with its source and traceback.
class LuaSceneBuilderError : public std::runtime_error
{
public:
    using std::runtime_error::runtime_error;
};

} // namespace sle::detail
