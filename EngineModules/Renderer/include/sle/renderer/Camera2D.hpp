#pragma once
#include <glm/glm.hpp>
#include <glm/gtc/matrix_transform.hpp>

namespace sle::core {

class Camera2D
{
public:
    Camera2D(float width, float height);

    void setViewport(float width, float height);

    void setPosition(const glm::vec2& pos);
    void move(const glm::vec2& delta);

    void setZoom(float zoom);

    const glm::mat4& getViewMatrix() const;
    const glm::mat4& getProjectionMatrix() const;
    glm::mat4 getViewProjection() const;

private:
    glm::vec2 position{0.0f, 0.0f};
    float zoom = 1.0f;

    glm::mat4 view{1.0f};
    glm::mat4 projection{1.0f};

    void updateView();
};

} // namespace sle::core
