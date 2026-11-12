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

void EntityPreviewSystem::submitOverlay(entity::Scene& scene, renderer::Renderer& renderer, const core::Camera2D& camera)
{
    const auto origin = camera.getPosition();
    const auto size = camera.getViewportSize();
    const float cell = std::pow(10.0f, std::floor(std::log10(std::max(size.x,size.y)/8.0f)));
    auto line = [&](glm::vec2 a, glm::vec2 b, glm::vec4 color) {
        if (std::isfinite(a.x) && std::isfinite(a.y) && std::isfinite(b.x) && std::isfinite(b.y))
            renderer.submit(renderer::LineCommand{a,b,color,1.0f,0.0f,90000});
    };
    // Fixed iteration bounds avoid loss-of-float-precision loops at extreme authored positions.
    for (int i=0;i<100;++i)
    {
        const float x = std::floor(origin.x/cell)*cell+i*cell;
        const float y = std::floor(origin.y/cell)*cell+i*cell;
        if (x <= origin.x+size.x) line({x,origin.y},{x,origin.y+size.y},{0.18f,0.20f,0.23f,1});
        if (y <= origin.y+size.y) line({origin.x,y},{origin.x+size.x,y},{0.18f,0.20f,0.23f,1});
    }
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
