#pragma once

#include <string>
#include <cstdint>

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

private:
    uint32_t textureID = 0;
    int width = 0;
    int height = 0;
    int channels = 0;
};

} // namespace sle::renderer
