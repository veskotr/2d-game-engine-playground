#pragma once
#include "Renderer.hpp"
#include "Scene.hpp"
#include "Registry.hpp"

namespace sle::renderer
{
    class RenderSystem
    {
    public:
        void render(Scene &scene, Registry &registry, Renderer &renderer);
    };
} // namespace sle::renderer