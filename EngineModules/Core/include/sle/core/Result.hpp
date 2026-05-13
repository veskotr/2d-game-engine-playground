#pragma once
#include <string>
#include <optional>

namespace sle::core {

template<typename T>
class Result
{
public:
    static Result<T> success(const T& value)
    {
        return Result(value);
    }

    static Result<T> error(const std::string& message)
    {
        return Result(message);
    }

    bool ok() const { return m_hasValue; }

    const T& value() const { return m_value.value(); }
    const std::string& error() const { return m_error; }

private:
    std::optional<T> m_value;
    std::string m_error;
    bool m_hasValue = false;

    Result(const T& value)
        : m_value(value), m_hasValue(true) {}

    Result(const std::string& error)
        : m_error(error), m_hasValue(false) {}
};

} // namespace sle::core
