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
    stbi_set_flip_vertically_on_load(true);
    unsigned char* data = stbi_load(imagePath.c_str(), &width, &height, &channels, 4);

    if (!data)
    {
        Log::error("Failed to load texture: {}", imagePath);
        return false;
    }

    GL_CALL(glGenTextures(1, &textureID));
    GL_CALL(glBindTexture(GL_TEXTURE_2D, textureID));

    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR));
    GL_CALL(glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR));

    GL_CALL(glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data));

    stbi_image_free(data);

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
