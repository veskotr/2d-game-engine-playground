#include <sle/engine/RenderSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/RendererCommand.hpp>

#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/constants.hpp>

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

glm::vec2 transformLocalPoint(const components::WorldTransformComponent& world, const glm::vec2& local)
{
    const glm::vec2 scaled = {local.x * world.scale.x, local.y * world.scale.y};
    const float c = std::cos(world.rotation);
    const float s = std::sin(world.rotation);

    return {
        world.position.x + (scaled.x * c - scaled.y * s),
        world.position.y + (scaled.x * s + scaled.y * c)
    };
}

void submitBoxOutline(
    renderer::Renderer& renderer,
    const components::WorldTransformComponent& world,
    const glm::vec2& offset,
    const glm::vec2& size,
    const glm::vec4& color,
    uint32_t layer)
{
    const glm::vec2 half = size * 0.5f;

    const glm::vec2 p0 = transformLocalPoint(world, {offset.x - half.x, offset.y - half.y});
    const glm::vec2 p1 = transformLocalPoint(world, {offset.x + half.x, offset.y - half.y});
    const glm::vec2 p2 = transformLocalPoint(world, {offset.x + half.x, offset.y + half.y});
    const glm::vec2 p3 = transformLocalPoint(world, {offset.x - half.x, offset.y + half.y});

    renderer.submit(renderer::LineCommand{p0, p1, color, 1.5f, 0.0f, layer});
    renderer.submit(renderer::LineCommand{p1, p2, color, 1.5f, 0.0f, layer});
    renderer.submit(renderer::LineCommand{p2, p3, color, 1.5f, 0.0f, layer});
    renderer.submit(renderer::LineCommand{p3, p0, color, 1.5f, 0.0f, layer});

    const glm::vec2 center = transformLocalPoint(world, offset);
    renderer.submit(renderer::PointCommand{center, color, 4.0f, 0.0f, layer});
}

void submitCircleOutline(
    renderer::Renderer& renderer,
    const components::WorldTransformComponent& world,
    const glm::vec2& offset,
    float radius,
    const glm::vec4& color,
    uint32_t layer)
{
    constexpr int kSegments = 24;
    const glm::vec2 center = transformLocalPoint(world, offset);
    const float scaleMax = std::max(std::abs(world.scale.x), std::abs(world.scale.y));
    const float r = radius * std::max(0.0001f, scaleMax);

    for (int i = 0; i < kSegments; ++i)
    {
        const float t0 = (static_cast<float>(i) / static_cast<float>(kSegments)) * glm::two_pi<float>();
        const float t1 = (static_cast<float>(i + 1) / static_cast<float>(kSegments)) * glm::two_pi<float>();

        const glm::vec2 a = {center.x + std::cos(t0) * r, center.y + std::sin(t0) * r};
        const glm::vec2 b = {center.x + std::cos(t1) * r, center.y + std::sin(t1) * r};
        renderer.submit(renderer::LineCommand{a, b, color, 1.5f, 0.0f, layer});
    }

    renderer.submit(renderer::PointCommand{center, color, 4.0f, 0.0f, layer});
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

    if (!physicsDebugEnabled)
        return;

    ctx.registry.view<components::WorldTransformComponent, components::BoxColliderComponent>(
        [&renderer = ctx.renderer](entity::Entity, components::WorldTransformComponent& world, components::BoxColliderComponent& box)
        {
            if (!box.enabled)
                return;
            submitBoxOutline(renderer, world, box.offset, box.size, {0.20f, 0.85f, 1.00f, 1.0f}, 100000);
        });

    ctx.registry.view<components::WorldTransformComponent, components::CircleColliderComponent>(
        [&renderer = ctx.renderer](entity::Entity, components::WorldTransformComponent& world, components::CircleColliderComponent& circle)
        {
            if (!circle.enabled)
                return;
            submitCircleOutline(renderer, world, circle.offset, circle.radius, {0.20f, 0.85f, 1.00f, 1.0f}, 100000);
        });

    ctx.registry.view<components::WorldTransformComponent, components::BoxZoneComponent>(
        [&renderer = ctx.renderer](entity::Entity, components::WorldTransformComponent& world, components::BoxZoneComponent& boxZone)
        {
            if (!boxZone.enabled)
                return;
            submitBoxOutline(renderer, world, boxZone.offset, boxZone.size, {1.00f, 0.80f, 0.20f, 1.0f}, 100001);
        });

    ctx.registry.view<components::WorldTransformComponent, components::CircleZoneComponent>(
        [&renderer = ctx.renderer](entity::Entity, components::WorldTransformComponent& world, components::CircleZoneComponent& circleZone)
        {
            if (!circleZone.enabled)
                return;
            submitCircleOutline(renderer, world, circleZone.offset, circleZone.radius, {1.00f, 0.80f, 0.20f, 1.0f}, 100001);
        });
}

} // namespace sle
