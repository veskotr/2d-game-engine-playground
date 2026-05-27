#define STB_TRUETYPE_IMPLEMENTATION
#include <sle/ui/UIResources.hpp>

namespace sle::ui {

bool UIFontResource::loadFromFiles(const std::string& fontPath)
{
    std::ifstream file(fontPath, std::ios::binary);
    if (!file.is_open())
        return false;

    std::vector<std::uint8_t> buffer((std::istreambuf_iterator<char>(file)), std::istreambuf_iterator<char>());

    path = fontPath;
    data = std::move(buffer);
    return initializeFont();
}

bool UIFontResource::initializeFont()
{
    if (data.empty())
        return false;

    if (!stbtt_InitFont(&fontInfo, data.data(), 0))
        return false;

    scale = stbtt_ScaleForPixelHeight(&fontInfo, pixelHeight);

    int rawAscent = 0;
    int rawDescent = 0;
    int rawLineGap = 0;
    stbtt_GetFontVMetrics(&fontInfo, &rawAscent, &rawDescent, &rawLineGap);

    ascent = static_cast<float>(rawAscent) * scale;
    descent = static_cast<float>(rawDescent) * scale;
    lineHeight = static_cast<float>(rawAscent - rawDescent + rawLineGap) * scale;
    return true;
}

bool UIFontResource::buildGlyph(uint32_t codepoint, UIFontGlyphInfo& outInfo)
{
    int advanceWidth = 0;
    int leftBearing = 0;
    stbtt_GetCodepointHMetrics(&fontInfo, static_cast<int>(codepoint), &advanceWidth, &leftBearing);

    int x0 = 0;
    int y0 = 0;
    int x1 = 0;
    int y1 = 0;
    stbtt_GetCodepointBitmapBox(&fontInfo, static_cast<int>(codepoint), scale, scale, &x0, &y0, &x1, &y1);

    const int width = std::max(0, x1 - x0);
    const int height = std::max(0, y1 - y0);
    std::vector<unsigned char> bitmap(static_cast<std::size_t>(width) * static_cast<std::size_t>(height), 0);

    if (width > 0 && height > 0)
    {
        stbtt_MakeCodepointBitmap(
            &fontInfo,
            bitmap.data(),
            width,
            height,
            width,
            scale,
            scale,
            static_cast<int>(codepoint));
    }

    std::vector<unsigned char> rgba(static_cast<std::size_t>(std::max(1, width)) * static_cast<std::size_t>(std::max(1, height)) * 4, 255);
    for (int y = 0; y < height; ++y)
    {
        for (int x = 0; x < width; ++x)
        {
            const std::size_t srcIndex = static_cast<std::size_t>(y) * static_cast<std::size_t>(width) + static_cast<std::size_t>(x);
            const std::size_t dstIndex = static_cast<std::size_t>(y) * static_cast<std::size_t>(std::max(1, width)) + static_cast<std::size_t>(x);
            rgba[dstIndex * 4 + 3] = bitmap[srcIndex];
        }
    }

    auto texture = std::make_shared<sle::renderer::Texture>();
    sle::renderer::TextureLoadOptions textureOptions;
    textureOptions.generateMipmaps = true;
    if (!texture->loadFromRGBAData(std::max(1, width), std::max(1, height), rgba.data(), textureOptions))
        return false;

    outInfo.size = {static_cast<float>(width), static_cast<float>(height)};
    outInfo.bearing = {static_cast<float>(x0), static_cast<float>(y0)};
    outInfo.advance = static_cast<float>(advanceWidth) * scale;
    outInfo.texture = std::move(texture);
    return true;
}

float UIFontResource::getKerningAdvance(uint32_t previousCodepoint, uint32_t codepoint) const
{
    if (previousCodepoint == 0 || codepoint == 0)
        return 0.0f;

    return static_cast<float>(stbtt_GetCodepointKernAdvance(
        &fontInfo,
        static_cast<int>(previousCodepoint),
        static_cast<int>(codepoint))) * scale;
}

bool UIFontResource::getGlyphInfo(uint32_t codepoint, UIFontGlyphInfo& outInfo)
{
    if (codepoint == 0)
        return false;

    auto it = glyphCache.find(codepoint);
    if (it != glyphCache.end())
    {
        outInfo = it->second;
        return true;
    }

    UIFontGlyphInfo glyphInfo;
    if (!buildGlyph(codepoint, glyphInfo))
    {
        if (codepoint != static_cast<uint32_t>('?'))
            return getGlyphInfo(static_cast<uint32_t>('?'), outInfo);
        return false;
    }

    auto inserted = glyphCache.emplace(codepoint, std::move(glyphInfo));
    outInfo = inserted.first->second;
    return true;
}

} // namespace sle::ui
