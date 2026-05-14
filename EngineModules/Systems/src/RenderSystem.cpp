#include <sle/engine/RenderSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/RendererCommand.hpp>

#include <glm/gtc/matrix_transform.hpp>

#include <algorithm> // std::max

namespace sle {

namespace {

glm::mat4 buildModelMatrix(const components::WorldTransformComponent& world)
{
    glm::mat4 m(1.0f);
    m = glm::translate(m, glm::vec3(world.position, 0.0f));
    m = glm::rotate(m, world.rotation, glm::vec3(0.0f, 0.0f, 1.0f));
    m = glm::scale(m, glm::vec3(world.scale, 1.0f));
    return m;
}

} // anonymous namespace

void RenderSystem::update(Context& ctx)
{
    ctx.registry.view<components::WorldTransformComponent, components::SpriteRenderer>(
        [&renderer = ctx.renderer](entity::Entity, components::WorldTransformComponent& world, components::SpriteRenderer& sprite)
        {
            renderer::QuadCommand cmd;
            cmd.modelMatrix  = buildModelMatrix(world);
            cmd.color        = sprite.color;
            cmd.uvRect       = sprite.region.uv;
            cmd.texture_id   = sprite.region.texture ? sprite.region.texture->getID() : 0;
            cmd.shader_id    = 0; // default shader
            cmd.layer        = static_cast<uint32_t>(std::max(0, sprite.layer));
            renderer.submit(cmd);
        });
}

} // namespace sle
