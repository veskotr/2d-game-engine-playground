#pragma once

#include <cstdint>
#include <fstream>
#include <iterator>
#include <memory>
#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include <glm/vec2.hpp>
#include <glm/vec4.hpp>
#include <sle/renderer/Texture.hpp>

#include <stb_truetype.h>

namespace sle::ui {

struct UIFontGlyphQuad
{
    glm::vec2 position{0.0f};
    glm::vec2 size{0.0f};
    glm::vec4 uvRect{0.0f};
};

struct UIFontGlyphInfo
{
    glm::vec2 size{0.0f};
    glm::vec2 bearing{0.0f};
    float advance = 0.0f;
    std::shared_ptr<sle::renderer::Texture> texture;
};

class UILayoutResource
{
public:
    bool loadFromFiles(const std::string& layoutPath)
    {
        std::ifstream file(layoutPath);
        if (!file.is_open())
            return false;

        std::stringstream buffer;
        buffer << file.rdbuf();

        path = layoutPath;
        xml = buffer.str();
        return true;
    }

    const std::string& getPath() const { return path; }
    const std::string& getXML() const { return xml; }

private:
    std::string path;
    std::string xml;
};

class UIFontResource
{
public:
    bool loadFromFiles(const std::string& fontPath);

    const std::string& getPath() const { return path; }
    const std::vector<std::uint8_t>& getData() const { return data; }
    float getPixelHeight() const { return pixelHeight; }
    float getLineHeight() const { return lineHeight; }
    float getAscent() const { return ascent; }
    float getDescent() const { return descent; }
    float getKerningAdvance(uint32_t previousCodepoint, uint32_t codepoint) const;
    bool getGlyphInfo(uint32_t codepoint, UIFontGlyphInfo& outInfo);

private:
    bool initializeFont();
    bool buildGlyph(uint32_t codepoint, UIFontGlyphInfo& outInfo);

    std::string path;
    std::vector<std::uint8_t> data;
    stbtt_fontinfo fontInfo{};
    float scale = 1.0f;
    float pixelHeight = 48.0f;
    float lineHeight = 48.0f;
    float ascent = 38.0f;
    float descent = -10.0f;
    std::unordered_map<uint32_t, UIFontGlyphInfo> glyphCache;
};

} // namespace sle::ui

