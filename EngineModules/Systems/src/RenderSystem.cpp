#include <sle/engine/RenderSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/RendererCommand.hpp>

#include <glm/gtc/matrix_transform.hpp>

#include <algorithm> // std::max
#include <cmath>

namespace sle {

namespace {

glm::mat4 buildModelMatrix(const components::WorldTransformComponent& world)
{
    const float c = std::cos(world.rotation);
    const float s = std::sin(world.rotation);

    glm::mat4 m(1.0f);
    m[0][0] = world.scale.x * c;
    m[0][1] = world.scale.x * s;
    m[1][0] = -world.scale.y * s;
    m[1][1] = world.scale.y * c;
    m[3][0] = world.position.x;
    m[3][1] = world.position.y;
    return m;
}

bool isSpriteVisible(
    const components::WorldTransformComponent& world,
    const core::Camera2D& camera)
{
    const glm::vec2 viewport = camera.getViewportSize();
    if (viewport.x <= 0.0f || viewport.y <= 0.0f)
        return true;

    const float zoom = std::max(camera.getZoom(), 0.0001f);
    const glm::vec2 cameraPos = camera.getPosition();

    const float viewLeft = cameraPos.x;
    const float viewRight = cameraPos.x + viewport.x / zoom;
    const float viewBottom = cameraPos.y;
    const float viewTop = cameraPos.y + viewport.y / zoom;

    const float sx = std::abs(world.scale.x);
    const float sy = std::abs(world.scale.y);
    const float c = std::abs(std::cos(world.rotation));
    const float s = std::abs(std::sin(world.rotation));

    const float halfWidth = 0.5f * (c * sx + s * sy);
    const float halfHeight = 0.5f * (s * sx + c * sy);

    const float spriteLeft = world.position.x - halfWidth;
    const float spriteRight = world.position.x + halfWidth;
    const float spriteBottom = world.position.y - halfHeight;
    const float spriteTop = world.position.y + halfHeight;

    if (spriteRight < viewLeft || spriteLeft > viewRight)
        return false;

    if (spriteTop < viewBottom || spriteBottom > viewTop)
        return false;

    return true;
}

} // anonymous namespace

void RenderSystem::update(Context& ctx)
{
    ctx.registry.view<components::WorldTransformComponent, components::SpriteRenderer>(
        [this, &renderer = ctx.renderer, &camera = ctx.camera](entity::Entity, components::WorldTransformComponent& world, components::SpriteRenderer& sprite)
        {
            if (!isSpriteVisible(world, camera))
                return;

            renderer::QuadCommand cmd;
            cmd.modelMatrix  = buildModelMatrix(world);
            cmd.color        = sprite.color;
            cmd.uvRect       = sprite.region.uv;
            cmd.texture_id   = sprite.region.texture ? sprite.region.texture->getID() : defaultTextureID;
            cmd.shader_id    = defaultShaderID;
            cmd.layer        = static_cast<uint32_t>(std::max(0, sprite.layer));
            renderer.submit(cmd);
        });
}

} // namespace sle
