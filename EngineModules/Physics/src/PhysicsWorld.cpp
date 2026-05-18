#include <sle/physics/PhysicsWorld.hpp>
#include <sle/physics/ContactListener.hpp>
#include <sle/physics/PhysicsTypes.hpp>
#include <box2d/b2_world.h>
#include <box2d/b2_body.h>
#include <box2d/b2_fixture.h>
#include <box2d/b2_polygon_shape.h>
#include <box2d/b2_circle_shape.h>
#include <glm/glm.hpp>
#include <algorithm>

namespace sle::physics {

PhysicsWorld::PhysicsWorld(const glm::vec2& gravity)
{
    // Create Box2D world with the given gravity
    b2Vec2 b2Gravity(gravity.x, gravity.y);
    world_ = std::make_unique<b2World>(b2Gravity);

    // Create and set contact listener
    contactListener_ = std::make_unique<ContactListener>(nullptr, &fixtureZoneIds_);
    world_->SetContactListener(contactListener_.get());

    // Store initial gravity
    world_->SetAutoClearForces(true);
}

PhysicsWorld::~PhysicsWorld()
{
    // Destroy all bodies
    b2Body* body = world_->GetBodyList();
    while (body)
    {
        b2Body* nextBody = body->GetNext();
        world_->DestroyBody(body);
        body = nextBody;
    }

    // world_ and contactListener_ are cleaned up automatically via unique_ptr
}

void PhysicsWorld::step(float deltaTime)
{
    // Clamp unusually large frame gaps to avoid excessive catch-up bursts.
    deltaTime = std::min(deltaTime, 0.1f);

    // Accumulate time for fixed timestep
    accumulator_ += deltaTime;

    // Step physics at fixed timestep
    while (accumulator_ >= fixedTimestep_)
    {
        world_->Step(fixedTimestep_, DEFAULT_VELOCITY_ITERATIONS, DEFAULT_POSITION_ITERATIONS);
        accumulator_ -= fixedTimestep_;
    }
}

void PhysicsWorld::setGravity(const glm::vec2& gravity)
{
    world_->SetGravity(b2Vec2(gravity.x, gravity.y));
}

glm::vec2 PhysicsWorld::getGravity() const
{
    b2Vec2 gravity = world_->GetGravity();
    return glm::vec2(gravity.x, gravity.y);
}

float PhysicsWorld::worldToPhysicsLength(float worldValue) const
{
    return worldValue / pixelsPerMeter_;
}

float PhysicsWorld::physicsToWorldLength(float physicsValue) const
{
    return physicsValue * pixelsPerMeter_;
}

glm::vec2 PhysicsWorld::worldToPhysicsVec2(const glm::vec2& worldValue) const
{
    return {
        worldToPhysicsLength(worldValue.x),
        worldToPhysicsLength(worldValue.y)
    };
}

glm::vec2 PhysicsWorld::physicsToWorldVec2(const glm::vec2& physicsValue) const
{
    return {
        physicsToWorldLength(physicsValue.x),
        physicsToWorldLength(physicsValue.y)
    };
}

uint32_t PhysicsWorld::createBody(const glm::vec2& position, const sle::components::RigidBodyComponent& rigidBody)
{
    b2BodyDef bodyDef;

    // Set body type
    switch (rigidBody.bodyType)
    {
        case sle::components::BodyType::Static:
            bodyDef.type = b2_staticBody;
            break;
        case sle::components::BodyType::Dynamic:
            bodyDef.type = b2_dynamicBody;
            break;
        case sle::components::BodyType::Kinematic:
            bodyDef.type = b2_kinematicBody;
            break;
    }

    // Set transform and velocity
    const glm::vec2 physicsPosition = worldToPhysicsVec2(position);
    const glm::vec2 physicsVelocity = worldToPhysicsVec2(rigidBody.velocity);
    bodyDef.position.Set(physicsPosition.x, physicsPosition.y);
    bodyDef.linearVelocity.Set(physicsVelocity.x, physicsVelocity.y);
    bodyDef.angularVelocity = rigidBody.angularVelocity;

    // Set damping
    bodyDef.linearDamping = rigidBody.linearDamping;
    bodyDef.angularDamping = rigidBody.angularDamping;

    // Set gravity scale
    bodyDef.gravityScale = rigidBody.gravityScale;

    // Set rotation constraint
    bodyDef.fixedRotation = rigidBody.fixedRotation;

    // Create body and store mapping
    b2Body* body = world_->CreateBody(&bodyDef);

    // Store entity ID in user data for later retrieval in callbacks
    // Note: The entity ID is passed through the component creation
    // This will be set properly when integrating with the Scene

    // Cast b2Body pointer to uint32_t for storage
    uint32_t bodyId = reinterpret_cast<uintptr_t>(body);
    bodyMap_[bodyId] = body;

    return bodyId;
}

void PhysicsWorld::setBodyEntityId(uint32_t bodyId, uint32_t entityId)
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        b2BodyUserData userData;
        userData.pointer = static_cast<uintptr_t>(entityId);
        body->GetUserData() = userData;
    }
}

void PhysicsWorld::setEventBus(sle::core::EventBus* eventBus)
{
    if (contactListener_)
        contactListener_->setEventBus(eventBus);
}

void PhysicsWorld::destroyBody(uint32_t bodyId)
{
    b2Body* body = getBodyPtr(bodyId);
    if (!body)
        return;

    // Destroy all fixtures first
    b2Fixture* fixture = body->GetFixtureList();
    while (fixture)
    {
        b2Fixture* nextFixture = fixture->GetNext();
        fixtureZoneIds_.erase(reinterpret_cast<uintptr_t>(fixture));
        body->DestroyFixture(fixture);
        fixture = nextFixture;
    }

    // Destroy the body
    world_->DestroyBody(body);
    bodyMap_.erase(bodyId);
}

void PhysicsWorld::setBodyTransform(uint32_t bodyId, const glm::vec2& position, float rotation)
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        const glm::vec2 physicsPosition = worldToPhysicsVec2(position);
        body->SetTransform(b2Vec2(physicsPosition.x, physicsPosition.y), rotation);
    }
}

void PhysicsWorld::getBodyTransform(uint32_t bodyId, glm::vec2& outPosition, float& outRotation) const
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        const b2Transform& transform = body->GetTransform();
        outPosition = physicsToWorldVec2({transform.p.x, transform.p.y});
        outRotation = body->GetAngle();
    }
}

void PhysicsWorld::setBodyVelocity(uint32_t bodyId, const glm::vec2& velocity)
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        const glm::vec2 physicsVelocity = worldToPhysicsVec2(velocity);
        body->SetLinearVelocity(b2Vec2(physicsVelocity.x, physicsVelocity.y));
    }
}

glm::vec2 PhysicsWorld::getBodyVelocity(uint32_t bodyId) const
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        b2Vec2 vel = body->GetLinearVelocity();
        return physicsToWorldVec2({vel.x, vel.y});
    }
    return glm::vec2(0.0f);
}

void PhysicsWorld::setBodyAngularVelocity(uint32_t bodyId, float angularVelocity)
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        body->SetAngularVelocity(angularVelocity);
    }
}

float PhysicsWorld::getBodyAngularVelocity(uint32_t bodyId) const
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        return body->GetAngularVelocity();
    }
    return 0.0f;
}

void PhysicsWorld::setBodyGravityScale(uint32_t bodyId, float gravityScale)
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        body->SetGravityScale(gravityScale);
    }
}

float PhysicsWorld::getBodyGravityScale(uint32_t bodyId) const
{
    b2Body* body = getBodyPtr(bodyId);
    if (body)
    {
        return body->GetGravityScale();
    }
    return 1.0f;
}

void PhysicsWorld::applyForce(uint32_t bodyId, const glm::vec2& force)
{
    b2Body* body = getBodyPtr(bodyId);
    if (body && body->GetType() == b2_dynamicBody)
    {
        const glm::vec2 physicsForce = worldToPhysicsVec2(force);
        body->ApplyForceToCenter(b2Vec2(physicsForce.x, physicsForce.y), true);
    }
}

void PhysicsWorld::applyImpulse(uint32_t bodyId, const glm::vec2& impulse)
{
    b2Body* body = getBodyPtr(bodyId);
    if (body && body->GetType() == b2_dynamicBody)
    {
        const glm::vec2 physicsImpulse = worldToPhysicsVec2(impulse);
        body->ApplyLinearImpulseToCenter(b2Vec2(physicsImpulse.x, physicsImpulse.y), true);
    }
}

uintptr_t PhysicsWorld::createBoxFixture(uint32_t bodyId, const sle::components::BoxColliderComponent& collider)
{
    b2Body* body = getBodyPtr(bodyId);
    if (!body)
        return 0;

    // Create box shape
    b2PolygonShape shape;
    const glm::vec2 halfSize = worldToPhysicsVec2(collider.size * 0.5f);
    const glm::vec2 offset = worldToPhysicsVec2(collider.offset);
    shape.SetAsBox(halfSize.x, halfSize.y, b2Vec2(offset.x, offset.y), 0.0f);

    // Create fixture definition
    b2FixtureDef fixtureDef;
    fixtureDef.shape = &shape;
    fixtureDef.friction = collider.friction;
    fixtureDef.restitution = collider.restitution;
    fixtureDef.density = collider.density;
    fixtureDef.filter.categoryBits = collider.categoryBits;
    fixtureDef.filter.maskBits = collider.maskBits;
    fixtureDef.isSensor = false;

    // Create and return fixture
    b2Fixture* fixture = body->CreateFixture(&fixtureDef);
    return reinterpret_cast<uintptr_t>(fixture);
}

uintptr_t PhysicsWorld::createCircleFixture(uint32_t bodyId, const sle::components::CircleColliderComponent& collider)
{
    b2Body* body = getBodyPtr(bodyId);
    if (!body)
        return 0;

    // Create circle shape
    b2CircleShape shape;
    shape.m_radius = worldToPhysicsLength(collider.radius);
    const glm::vec2 offset = worldToPhysicsVec2(collider.offset);
    shape.m_p.Set(offset.x, offset.y);

    // Create fixture definition
    b2FixtureDef fixtureDef;
    fixtureDef.shape = &shape;
    fixtureDef.friction = collider.friction;
    fixtureDef.restitution = collider.restitution;
    fixtureDef.density = collider.density;
    fixtureDef.filter.categoryBits = collider.categoryBits;
    fixtureDef.filter.maskBits = collider.maskBits;
    fixtureDef.isSensor = false;

    // Create and return fixture
    b2Fixture* fixture = body->CreateFixture(&fixtureDef);
    return reinterpret_cast<uintptr_t>(fixture);
}

uintptr_t PhysicsWorld::createBoxZone(uint32_t bodyId, const sle::components::BoxZoneComponent& zone)
{
    b2Body* body = getBodyPtr(bodyId);
    if (!body)
        return 0;

    // Create box shape
    b2PolygonShape shape;
    const glm::vec2 halfSize = worldToPhysicsVec2(zone.size * 0.5f);
    const glm::vec2 offset = worldToPhysicsVec2(zone.offset);
    shape.SetAsBox(halfSize.x, halfSize.y, b2Vec2(offset.x, offset.y), 0.0f);

    // Create fixture definition for sensor
    b2FixtureDef fixtureDef;
    fixtureDef.shape = &shape;
    fixtureDef.filter.categoryBits = zone.categoryBits;
    fixtureDef.filter.maskBits = zone.maskBits;
    fixtureDef.isSensor = true;  // Important: this is a sensor, not a collider

    // Create and return fixture
    b2Fixture* fixture = body->CreateFixture(&fixtureDef);
        uintptr_t id = reinterpret_cast<uintptr_t>(fixture);
        if (!zone.zoneId.empty())
            fixtureZoneIds_[id] = zone.zoneId;
        return id;
}

uintptr_t PhysicsWorld::createCircleZone(uint32_t bodyId, const sle::components::CircleZoneComponent& zone)
{
    b2Body* body = getBodyPtr(bodyId);
    if (!body)
        return 0;

    // Create circle shape
    b2CircleShape shape;
    shape.m_radius = worldToPhysicsLength(zone.radius);
    const glm::vec2 offset = worldToPhysicsVec2(zone.offset);
    shape.m_p.Set(offset.x, offset.y);

    // Create fixture definition for sensor
    b2FixtureDef fixtureDef;
    fixtureDef.shape = &shape;
    fixtureDef.filter.categoryBits = zone.categoryBits;
    fixtureDef.filter.maskBits = zone.maskBits;
    fixtureDef.isSensor = true;  // Important: this is a sensor

    // Create and return fixture
    b2Fixture* fixture = body->CreateFixture(&fixtureDef);
        uintptr_t id = reinterpret_cast<uintptr_t>(fixture);
        if (!zone.zoneId.empty())
            fixtureZoneIds_[id] = zone.zoneId;
        return id;
}

void PhysicsWorld::destroyFixture(uint32_t bodyId, uintptr_t fixtureId)
{
    b2Body* body = getBodyPtr(bodyId);
    if (!body)
        return;

    b2Fixture* fixture = reinterpret_cast<b2Fixture*>(fixtureId);
    if (fixture && fixture->GetBody() == body)
    {
        fixtureZoneIds_.erase(fixtureId);
        body->DestroyFixture(fixture);
    }
}

b2Body* PhysicsWorld::getBodyPtr(uint32_t bodyId) const
{
    auto it = bodyMap_.find(bodyId);
    if (it != bodyMap_.end())
    {
        return it->second;
    }
    return nullptr;
}

const std::string& PhysicsWorld::getFixtureZoneId(uintptr_t fixtureId) const
{
    static const std::string empty;
    auto it = fixtureZoneIds_.find(fixtureId);
    return it != fixtureZoneIds_.end() ? it->second : empty;
}

} // namespace sle::physics
