#include <sle/renderer/Camera2D.hpp>

namespace sle::core {

Camera2D::Camera2D(float width, float height)
{
    setViewport(width, height);

    updateView();
}

void Camera2D::setViewport(float width, float height)
{
    projection = glm::ortho(
        0.0f, width,
        0.0f, height,
        -1.0f, 1.0f
    );
}

void Camera2D::setPosition(const glm::vec2& pos)
{
    position = pos;
    updateView();
}

void Camera2D::move(const glm::vec2& delta)
{
    position += delta;
    updateView();
}

void Camera2D::setZoom(float z)
{
    zoom = z;
    updateView();
}

void Camera2D::updateView()
{
    glm::mat4 transform = glm::translate(glm::mat4(1.0f), glm::vec3(position, 0.0f));
    transform = glm::scale(transform, glm::vec3(zoom, zoom, 1.0f));

    view = glm::inverse(transform);
}

const glm::mat4& Camera2D::getViewMatrix() const
{
    return view;
}

const glm::mat4& Camera2D::getProjectionMatrix() const
{
    return projection;
}

glm::mat4 Camera2D::getViewProjection() const
{
    return projection * view;
}

} // namespace sle::core
