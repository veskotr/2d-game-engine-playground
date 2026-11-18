#include <sle/engine/EntityPreviewSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/Camera2DComponent.hpp>
#include <sle/scene/components/WorldTransformComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <algorithm>
#include <cmath>
#include <limits>

namespace sle {
void EntityPreviewSystem::frameCamera(entity::Scene& scene, core::Camera2D& camera, float aspect)
{
    glm::dvec2 minimum{0}, maximum{0};
    bool any = false;
    auto& registry = scene.getRegistry();
    registry.view<components::WorldTransformComponent>([&](entity::Entity entity, const auto& world) {
        const glm::dvec2 center(world.position);
        double radius = 1.0;
        const auto scale = glm::abs(glm::dvec2(world.scale));
        auto include = [&](glm::vec2 offset, glm::vec2 halfSize) {
            const auto distance = glm::length(glm::dvec2(offset) * scale) + glm::length(glm::dvec2(halfSize) * scale);
            if (std::isfinite(distance)) radius = std::max(radius, static_cast<double>(distance));
        };
        if (const auto* cameraComponent=registry.getComponent<components::Camera2DComponent>(entity))
            radius=std::max(radius,static_cast<double>(glm::length(cameraComponent->viewSize/(2.0f*cameraComponent->zoom))));
        if (const auto* sprite = registry.getComponent<components::SpriteRenderer>(entity)) include(sprite->offset,sprite->size*0.5f);
        if (const auto* c = registry.getComponent<components::BoxColliderComponent>(entity)) include(c->offset,c->size*0.5f);
        if (const auto* c = registry.getComponent<components::CircleColliderComponent>(entity)) include(c->offset,{c->radius,c->radius});
        if (const auto* c = registry.getComponent<components::BoxZoneComponent>(entity)) include(c->offset,c->size*0.5f);
        if (const auto* c = registry.getComponent<components::CircleZoneComponent>(entity)) include(c->offset,{c->radius,c->radius});
        if (std::isfinite(center.x) && std::isfinite(center.y)) {
            const glm::dvec2 extent(radius);
            minimum = any ? glm::min(minimum,center-extent) : center-extent;
            maximum = any ? glm::max(maximum,center+extent) : center+extent;
            any = true;
        }
    });
    if (!std::isfinite(aspect) || aspect <= 0) aspect = 1.0f;
    const auto center = (minimum+maximum)*0.5;
    const auto extent = maximum-minimum;
    const double height = std::clamp(1.4*std::max(extent.y,extent.x/aspect),4.0,1.0e6);
    const double bound = std::numeric_limits<float>::max();
    const glm::vec2 size{static_cast<float>(std::clamp(height*aspect,0.01,bound)),static_cast<float>(height)};
    const auto position = center-glm::dvec2(size)*0.5;
    const glm::vec2 origin{static_cast<float>(std::clamp(position.x,-bound,bound)),
        static_cast<float>(std::clamp(position.y,-bound,bound))};
    camera.setRotation(0.0f);
    camera.setZoom(1.0f);
    camera.setViewport(size.x,size.y);
    camera.setPosition(origin);
}

bool EditorGrid::valid() const
{
    return std::isfinite(spacing) && spacing >= 0.0f && majorEvery >= 0 && majorEvery <= 1000;
}

std::vector<EditorGridLine> EntityPreviewSystem::gridLines(const core::Camera2D& camera, const EditorGrid& grid)
{
    std::vector<EditorGridLine> lines;
    const auto origin = camera.getPosition();
    const auto size = camera.getViewportSize();
    const float extent = std::max(size.x,size.y);
    if (!std::isfinite(extent) || extent <= 0.0f || !std::isfinite(origin.x) || !std::isfinite(origin.y)) return lines;
    double cell = std::pow(10.0, std::floor(std::log10(extent/8.0)));
    int major = 0;
    if (grid.valid() && grid.spacing > 0.0f)
    {
        cell = grid.spacing;
        major = grid.majorEvery > 1 ? grid.majorEvery : 0;
        // Too fine for the view: step up by the major interval (or 10) so lines stay readable.
        const double factor = major > 1 ? major : 10.0;
        for (int guard = 0; guard < 64 && extent/cell > 100.0; ++guard) cell *= factor;
    }
    if (!std::isfinite(cell) || cell <= 0.0) return lines;
    auto isMajor = [&](double index) { return major > 1 && std::fmod(index, static_cast<double>(major)) == 0.0; };
    const double firstX = std::floor(origin.x/cell), firstY = std::floor(origin.y/cell);
    // Fixed iteration bounds avoid loss-of-float-precision loops at extreme authored positions.
    for (int i=0;i<102;++i)
    {
        const float x = static_cast<float>((firstX+i)*cell);
        const float y = static_cast<float>((firstY+i)*cell);
        if (x <= origin.x+size.x) lines.push_back({{x,origin.y},{x,origin.y+size.y},isMajor(firstX+i)});
        if (y <= origin.y+size.y) lines.push_back({{origin.x,y},{origin.x+size.x,y},isMajor(firstY+i)});
    }
    return lines;
}

void EntityPreviewSystem::submitOverlay(entity::Scene& scene, renderer::Renderer& renderer, const core::Camera2D& camera,
    const EditorGrid& grid)
{
    const auto origin = camera.getPosition();
    const auto size = camera.getViewportSize();
    auto line = [&](glm::vec2 a, glm::vec2 b, glm::vec4 color, uint32_t layer = 90000) {
        if (std::isfinite(a.x) && std::isfinite(a.y) && std::isfinite(b.x) && std::isfinite(b.y))
            renderer.submit(renderer::LineCommand{a,b,color,1.0f,0.0f,layer});
    };
    // Grid lines sit below the axes and entity marks; major lines above minor ones.
    for (const auto& gridLine : gridLines(camera,grid))
        line(gridLine.from,gridLine.to,gridLine.major ? glm::vec4{0.27f,0.30f,0.34f,1} : glm::vec4{0.18f,0.20f,0.23f,1},
            gridLine.major ? 89999 : 89998);
    line({origin.x,0},{origin.x+size.x,0},{0.65f,0.25f,0.25f,1});
    line({0,origin.y},{0,origin.y+size.y},{0.25f,0.65f,0.35f,1});
    auto& registry=scene.getRegistry();
    registry.view<components::WorldTransformComponent>([&](entity::Entity entity,const auto& world) {
        const auto p=world.position;
        const float cross=std::min(size.x,size.y)*0.015f;
        line(p-glm::vec2(cross,0),p+glm::vec2(cross,0),{1.0f,0.65f,0.2f,1});
        line(p-glm::vec2(0,cross),p+glm::vec2(0,cross),{1.0f,0.65f,0.2f,1});
        renderer.submit(renderer::PointCommand{p,{1.0f,0.65f,0.2f,1},6.0f,0.0f,100002});
        if (const auto* cameraComponent=registry.getComponent<components::Camera2DComponent>(entity)) {
            const auto half=cameraComponent->viewSize/(2.0f*cameraComponent->zoom);
            const float c=std::cos(world.rotation),s=std::sin(world.rotation);
            auto corner=[&](float x,float y) { const auto q=half*glm::vec2(x,y); return p+glm::vec2(c*q.x-s*q.y,s*q.x+c*q.y); };
            const glm::vec4 color=cameraComponent->enabled ? glm::vec4(0.35f,0.8f,1,1) : glm::vec4(0.4f,0.4f,0.4f,1);
            line(corner(-1,-1),corner(1,-1),color); line(corner(1,-1),corner(1,1),color);
            line(corner(1,1),corner(-1,1),color); line(corner(-1,1),corner(-1,-1),color);
        }
        const auto* sprite=registry.getComponent<components::SpriteRenderer>(entity);
        if (!sprite) return;
        auto point=[&](float x,float y) {
            const glm::vec2 local=world.scale*(sprite->offset+sprite->size*glm::vec2(x,y));
            const float c=std::cos(world.rotation),s=std::sin(world.rotation);
            return p+glm::vec2(c*local.x-s*local.y,s*local.x+c*local.y);
        };
        const glm::vec4 color{0.8f,0.8f,0.85f,1};
        line(point(-0.5f,-0.5f),point(0.5f,-0.5f),color);
        line(point(0.5f,-0.5f),point(0.5f,0.5f),color);
        line(point(0.5f,0.5f),point(-0.5f,0.5f),color);
        line(point(-0.5f,0.5f),point(-0.5f,-0.5f),color);
    });
}
}
