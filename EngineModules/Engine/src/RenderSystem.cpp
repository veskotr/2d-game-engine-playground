#include "RenderSystem.hpp"

void RenderSystem::render(Scene &scene, Registry &registry, Renderer &renderer)
{
    registry.view<Transform, SpriteRenderer>(
        [&](Entity e, Transform &t, SpriteRenderer &s)
        {
            if (!scene.isObjectActive(scene.getObject(e)))
                return;

            renderer.submitSprite({.transform = t.worldMatrix,
                                   .color = s.color,
                                   .size = s.size,
                                   .texture = s.texture.get(),
                                   .shader = s.shader.get()});
        });
}