#include <sle/renderer/Texture.hpp>
#include <sle/renderer/GLDebug.hpp>
#include <sle/core/Log.hpp>
#include <glad/gl.h>

#define STB_IMAGE_IMPLEMENTATION
#include <stb_image.h>

namespace sle::renderer {

using sle::core::Log;

Texture::~Texture()
{
    if (textureID != 0)
    {
        // During shutdown, the GL context might be invalid. Try to delete,
        // but don't spam errors if the context is gone.
        glDeleteTextures(1, &textureID);
        glGetError();  // Clear any error that might have occurred
        textureID = 0;
    }
}

bool Texture::loadFromFiles(const std::string& imagePath)
{
    return loadFromFiles(imagePath, TextureLoadOptions{});
}

bool Texture::loadFromFiles(const std::string& imagePath, const TextureLoadOptions& options)
{
    stbi_set_flip_vertically_on_load(true);
    unsigned char* data = stbi_load(imagePath.c_str(), &width, &height, &channels, 4);

    if (!data)
    {
        Log::error("Failed to load texture: {}", imagePath);
        return false;
    }

    GL_CALL(glGenTextures(1, &textureID));
    GL_CALL(glBindTexture(GL_TEXTURE_2D, textureID));

    const int wrapS = options.wrapS != 0 ? options.wrapS : GL_REPEAT;
    const int wrapT = options.wrapT != 0 ? options.wrapT : GL_REPEAT;
    const int minFilter = options.minFilter != 0
        ? options.minFilter
        : (options.generateMipmaps ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
    const int magFilter = options.magFilter != 0 ? options.magFilter : GL_LINEAR;

    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter));

    GL_CALL(glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data));
    if (options.generateMipmaps)
        GL_CALL(glGenerateMipmap(GL_TEXTURE_2D));

    stbi_image_free(data);

    return true;
}

bool Texture::loadFromRGBAData(int textureWidth, int textureHeight, const unsigned char* rgbaData)
{
    return loadFromRGBAData(textureWidth, textureHeight, rgbaData, TextureLoadOptions{});
}

bool Texture::loadFromRGBAData(int textureWidth, int textureHeight, const unsigned char* rgbaData, const TextureLoadOptions& options)
{
    if (!rgbaData || textureWidth <= 0 || textureHeight <= 0)
        return false;

    width = textureWidth;
    height = textureHeight;
    channels = 4;

    GL_CALL(glGenTextures(1, &textureID));
    GL_CALL(glBindTexture(GL_TEXTURE_2D, textureID));

    const int wrapS = options.wrapS != 0 ? options.wrapS : GL_CLAMP_TO_EDGE;
    const int wrapT = options.wrapT != 0 ? options.wrapT : GL_CLAMP_TO_EDGE;
    const int minFilter = options.minFilter != 0
        ? options.minFilter
        : (options.generateMipmaps ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
    const int magFilter = options.magFilter != 0 ? options.magFilter : GL_LINEAR;

    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrapS));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrapT));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter));

    GL_CALL(glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgbaData));
    if (options.generateMipmaps)
        GL_CALL(glGenerateMipmap(GL_TEXTURE_2D));

    return true;
}

void Texture::bind(uint32_t slot) const
{
    GL_CALL(glActiveTexture(GL_TEXTURE0 + slot));
    GL_CALL(glBindTexture(GL_TEXTURE_2D, textureID));
}

void Texture::unbind() const
{
    GL_CALL(glBindTexture(GL_TEXTURE_2D, 0));
}

} // namespace sle::renderer
