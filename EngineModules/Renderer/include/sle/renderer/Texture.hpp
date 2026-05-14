#pragma once

#include <string>
#include <cstdint>
#include <glm/glm.hpp>

namespace sle::renderer {

class Texture
{
public:
    Texture() = default;
    ~Texture();

    bool loadFromFiles(const std::string& imagePath);

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
