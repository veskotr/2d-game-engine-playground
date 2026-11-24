#pragma once

#include <sle/renderer/Camera2D.hpp>

#include <algorithm>
#include <cmath>
#include <limits>

namespace sle {

struct ViewBounds
{
    glm::vec2 minimum;
    glm::vec2 maximum;

    bool intersects(glm::vec2 lowerLeft, glm::vec2 upperRight) const
    {
        return upperRight.x >= minimum.x && lowerLeft.x <= maximum.x &&
            upperRight.y >= minimum.y && lowerLeft.y <= maximum.y;
    }
};

inline ViewBounds viewBounds(const core::Camera2D& camera)
{
    const glm::vec2 viewport = camera.getViewportSize();
    if (viewport.x <= 0.0f || viewport.y <= 0.0f)
        return {glm::vec2(-std::numeric_limits<float>::infinity()), glm::vec2(std::numeric_limits<float>::infinity())};

    const glm::vec2 cameraPos = camera.getPosition();
    const glm::vec2 extent = viewport * std::max(camera.getZoom(), 0.0001f);
    const float rc=std::cos(camera.getRotation()), rs=std::sin(camera.getRotation());
    const glm::vec2 ex{rc*extent.x,rs*extent.x}, ey{-rs*extent.y,rc*extent.y};
    const auto minimum=glm::min(glm::min(cameraPos,cameraPos+ex),glm::min(cameraPos+ey,cameraPos+ex+ey));
    const auto maximum=glm::max(glm::max(cameraPos,cameraPos+ex),glm::max(cameraPos+ey,cameraPos+ex+ey));
    return {minimum, maximum};
}

} // namespace sle
