#pragma once

#include <string>
#include <cstdint>
#include <glm/glm.hpp>

namespace sle::renderer {

enum class TextureWrap
{
    Default,
    Repeat,
    ClampToEdge
};

enum class TextureFilter
{
    Default,
    Linear,
    Nearest,
    LinearMipmapLinear
};

struct TextureLoadOptions
{
    bool generateMipmaps = false;
    TextureWrap wrapS = TextureWrap::Default;
    TextureWrap wrapT = TextureWrap::Default;
    TextureFilter minFilter = TextureFilter::Default;
    TextureFilter magFilter = TextureFilter::Default;
};

class Texture
{
public:
    Texture() = default;
    ~Texture();

    bool loadFromFiles(const std::string& imagePath);
    bool loadFromFiles(const std::string& imagePath, const TextureLoadOptions& options);
    bool loadFromRGBAData(int textureWidth, int textureHeight, const unsigned char* rgbaData);
    bool loadFromRGBAData(int textureWidth, int textureHeight, const unsigned char* rgbaData, const TextureLoadOptions& options);

    void bind(uint32_t slot = 0) const;
    void unbind() const;

    uint32_t getID() const { return textureID; }
    int getWidth() const { return width; }
    int getHeight() const { return height; }

    void setID(uint32_t id) { textureID = id; }

    glm::vec4 getUV() const { return uv; }
    void setUV(const glm::vec4& uvRect) { uv = uvRect; }

private:
    uint32_t textureID = 0;
    int width = 0;
    int height = 0;
    int channels = 0;
    glm::vec4 uv;
};

} // namespace sle::renderer
