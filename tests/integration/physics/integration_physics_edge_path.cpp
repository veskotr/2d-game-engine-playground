#include <sle/physics/PhysicsWorld.hpp>

#include <glm/vec2.hpp>

#include <iostream>

int main() {
    sle::physics::PhysicsWorld world({0.0f, -9.81f});

    constexpr uint32_t invalidBodyId = 0;

    glm::vec2 outPos(123.0f, 456.0f);
    float outRot = 99.0f;
    world.getBodyTransform(invalidBodyId, outPos, outRot);

    if (outPos.x != 123.0f || outPos.y != 456.0f || outRot != 99.0f) {
        std::cerr << "Invalid body transform query should leave output unchanged\n";
        return 1;
    }

    const glm::vec2 velocity = world.getBodyVelocity(invalidBodyId);
    if (velocity.x != 0.0f || velocity.y != 0.0f) {
        std::cerr << "Expected zero velocity for invalid body\n";
        return 1;
    }

    if (world.getBodyAngularVelocity(invalidBodyId) != 0.0f) {
        std::cerr << "Expected zero angular velocity for invalid body\n";
        return 1;
    }

    if (world.getBodyGravityScale(invalidBodyId) != 1.0f) {
        std::cerr << "Expected default gravity scale for invalid body\n";
        return 1;
    }

    world.destroyBody(invalidBodyId);
    world.destroyFixture(invalidBodyId, 0);
    world.applyForce(invalidBodyId, {10.0f, 10.0f});
    world.applyImpulse(invalidBodyId, {5.0f, 5.0f});

    return 0;
}
