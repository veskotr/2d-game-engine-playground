#include "CameraViewBounds.hpp"
#include <sle/engine/RenderSystem.hpp>
#include <sle/scene/ChunkActivity.hpp>
#include <sle/physics/PhysicsWorld.hpp>
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
#include <limits>

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

bool isSpriteVisible(const components::WorldTransformComponent& world, const ViewBounds& view)
{
    const float sx = std::abs(world.scale.x);
    const float sy = std::abs(world.scale.y);
    const float c = std::abs(std::cos(world.rotation));
    const float s = std::abs(std::sin(world.rotation));

    const float halfWidth = 0.5f * (c * sx + s * sy);
    const float halfHeight = 0.5f * (s * sx + c * sy);

    const glm::vec2 half{halfWidth, halfHeight};
    return view.intersects(world.position - half, world.position + half);
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
    const auto view = viewBounds(ctx.camera);
    for (const auto& layer : ctx.scene.getTileLayers()) {
        if (!layer.visible) continue;
        for (const auto& [coordinate, chunk] : layer.chunks) {
            if (!view.intersects(chunk.lowerLeft, chunk.upperRight)) continue;
            for (const auto& cell : chunk.cells) {
                if (!view.intersects(cell.lowerLeft, cell.lowerLeft + cell.size)) continue;
                const auto texture = cell.missing ? missingTileTextureID :
                    (cell.region.texture ? cell.region.texture->getID() : 0);
                if (!texture) continue;
                renderer::QuadCommand cmd;
                components::WorldTransformComponent world;
                world.position = cell.lowerLeft + cell.size * 0.5f;
                world.scale = cell.size;
                cmd.modelMatrix = buildModelMatrix(world);
                cmd.color = glm::vec4(1);
                cmd.uvRect = cell.region.uv;
                cmd.texture_id = texture;
                cmd.shader_id = defaultShaderID;
                cmd.layer = layer.drawOrder;
                ctx.renderer.submit(cmd);
            }
        }
    }

    ctx.registry.view<components::WorldTransformComponent, components::SpriteRenderer>(
        [this, &renderer = ctx.renderer, &view, &registry = ctx.registry](entity::Entity entity, components::WorldTransformComponent& world, components::SpriteRenderer& sprite)
        {
            if (!sprite.visible || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Rendering))
                return;

            auto visualWorld = world;
            visualWorld.position = transformLocalPoint(world, sprite.offset);
            visualWorld.scale *= sprite.size;
            if (!isSpriteVisible(visualWorld, view))
                return;

            renderer::QuadCommand cmd;
            cmd.modelMatrix  = buildModelMatrix(visualWorld);
            cmd.color        = sprite.color;
            cmd.uvRect       = sprite.region.uv;
            cmd.texture_id   = sprite.region.texture ? sprite.region.texture->getID() : defaultTextureID;
            cmd.shader_id    = defaultShaderID;
            cmd.layer        = static_cast<uint32_t>(std::max(0, sprite.layer));
            renderer.submit(cmd);
        });

    if (!physicsDebugEnabled)
        return;

    if (ctx.physicsWorld) {
        ctx.physicsWorld->visitTerrainEdges([&](glm::vec2 a, glm::vec2 b) {
            ctx.renderer.submit(renderer::LineCommand{a, b, {0.20f, 0.85f, 1.00f, 1.0f}, 1.5f, 0.0f, 100000});
        });
    }

    ctx.registry.view<components::WorldTransformComponent, components::BoxColliderComponent>(
        [&renderer = ctx.renderer, &registry = ctx.registry](entity::Entity entity, components::WorldTransformComponent& world, components::BoxColliderComponent& box)
        {
            if (!box.enabled || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Collision) || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Physics))
                return;
            submitBoxOutline(renderer, world, box.offset, box.size, {0.20f, 0.85f, 1.00f, 1.0f}, 100000);
        });

    ctx.registry.view<components::WorldTransformComponent, components::CircleColliderComponent>(
        [&renderer = ctx.renderer, &registry = ctx.registry](entity::Entity entity, components::WorldTransformComponent& world, components::CircleColliderComponent& circle)
        {
            if (!circle.enabled || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Collision) || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Physics))
                return;
            submitCircleOutline(renderer, world, circle.offset, circle.radius, {0.20f, 0.85f, 1.00f, 1.0f}, 100000);
        });

    ctx.registry.view<components::WorldTransformComponent, components::BoxZoneComponent>(
        [&renderer = ctx.renderer, &registry = ctx.registry](entity::Entity entity, components::WorldTransformComponent& world, components::BoxZoneComponent& boxZone)
        {
            if (!boxZone.enabled || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Zones) || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Physics))
                return;
            submitBoxOutline(renderer, world, boxZone.offset, boxZone.size, {1.00f, 0.80f, 0.20f, 1.0f}, 100001);
        });

    ctx.registry.view<components::WorldTransformComponent, components::CircleZoneComponent>(
        [&renderer = ctx.renderer, &registry = ctx.registry](entity::Entity entity, components::WorldTransformComponent& world, components::CircleZoneComponent& circleZone)
        {
            if (!circleZone.enabled || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Zones) || !entity::isChunkActive(registry, entity, components::ChunkSubsystem::Physics))
                return;
            submitCircleOutline(renderer, world, circleZone.offset, circleZone.radius, {1.00f, 0.80f, 0.20f, 1.0f}, 100001);
        });
}

} // namespace sle
