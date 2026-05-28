#include "sle/test/LogCapture.hpp"

namespace sle::test {

ScopedStreamCapture::ScopedStreamCapture(std::ostream& stream)
    : m_stream(stream), m_originalBuffer(stream.rdbuf(m_capture.rdbuf())) {}

ScopedStreamCapture::~ScopedStreamCapture() {
    m_stream.rdbuf(m_originalBuffer);
}

std::string ScopedStreamCapture::str() const {
    return m_capture.str();
}

bool containsInOrder(std::string_view haystack, const std::vector<std::string>& markers) {
    std::size_t currentPos = 0;

    for (const std::string& marker : markers) {
        const std::size_t found = haystack.find(marker, currentPos);
        if (found == std::string_view::npos) {
            return false;
        }

        currentPos = found + marker.size();
    }

    return true;
}

} // namespace sle::test
