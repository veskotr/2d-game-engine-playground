#include <sle/physics/PhysicsWorld.hpp>

#include <glm/vec2.hpp>
#include <box2d/b2_world.h>

#include <iostream>

namespace {
bool matrixFiltersSolidContacts()
{
    sle::physics::PhysicsWorld world({0.0f, 0.0f});
    sle::components::RigidBodyComponent fixed;
    fixed.bodyType = sle::components::BodyType::Static;
    sle::components::RigidBodyComponent moving;
    moving.bodyType = sle::components::BodyType::Dynamic;

    sle::components::CircleColliderComponent first;
    first.radius = 10;
    first.categoryBits = 1u << 15;
    sle::components::CircleColliderComponent second = first;
    second.categoryBits = 2;

    sle::physics::CollisionMatrix blocked{};
    blocked[15] = 2;
    blocked[1] = 1u << 15;
    world.setBlockedCollisionPairs(blocked);
    world.createCircleFixture(world.createBody({0, 0}, fixed), first);
    world.createCircleFixture(world.createBody({0, 0}, moving), second);
    world.step(1.0f / 60.0f);
    if (world.getRawWorld()->GetContactCount() != 0) return false;

    // Existing proxies must be reconsidered when a pair is enabled again.
    world.setBlockedCollisionPairs({});
    world.step(1.0f / 60.0f);
    world.step(1.0f / 60.0f);
    return world.getRawWorld()->GetContactCount() == 1;
}
}

int main() {
    if (!matrixFiltersSolidContacts()) {
        std::cerr << "Collision matrix did not block and then restore a solid contact\n";
        return 1;
    }
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
