#pragma once

#include <ostream>
#include <sstream>
#include <string>
#include <string_view>
#include <vector>

namespace sle::test {

class ScopedStreamCapture {
public:
    explicit ScopedStreamCapture(std::ostream& stream);
    ~ScopedStreamCapture();

    ScopedStreamCapture(const ScopedStreamCapture&) = delete;
    ScopedStreamCapture& operator=(const ScopedStreamCapture&) = delete;

    [[nodiscard]] std::string str() const;

private:
    std::ostream& m_stream;
    std::streambuf* m_originalBuffer = nullptr;
    std::ostringstream m_capture;
};

bool containsInOrder(std::string_view haystack, const std::vector<std::string>& markers);

} // namespace sle::test
