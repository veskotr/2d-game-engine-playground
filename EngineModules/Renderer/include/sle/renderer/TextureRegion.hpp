#pragma once
#include "Texture.hpp"
#include <glm/glm.hpp>
#include <memory>
namespace sle::renderer
{
    struct TextureRegion
    {
        std::shared_ptr<Texture> texture;
        glm::vec4 uv{0.0f, 0.0f, 1.0f, 1.0f}; // (u0, v0, u1, v1)

        static TextureRegion fromPixels(
            std::shared_ptr<Texture> tex,
            int x, int y, int w, int h)
        {
            TextureRegion r;
            r.texture = tex;

            float u0 = x / (float)tex->getWidth();
            float v0 = y / (float)tex->getHeight();
            float u1 = (x + w) / (float)tex->getWidth();
            float v1 = (y + h) / (float)tex->getHeight();

            r.uv = {u0, v0, u1, v1};
            return r;
        }
    };
}