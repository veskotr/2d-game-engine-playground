#pragma once

namespace sle::entity { class Scene; }

namespace sle {

class AnimationSystem
{
public:
    void update(sle::entity::Scene& scene, float dt);
};

} // namespace sle
