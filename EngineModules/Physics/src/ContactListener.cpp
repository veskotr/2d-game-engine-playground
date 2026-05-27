#include <sle/physics/ContactListener.hpp>
#include <sle/events/EventBus.hpp>
#include <sle/events/CollisionEvents.hpp>
#include <sle/events/ZoneEvents.hpp>
#include <box2d/b2_contact.h>
#include <box2d/b2_fixture.h>

namespace sle::physics {

ContactListener::ContactListener(sle::events::EventBus* eventBus, std::unordered_map<uintptr_t, std::string>* fixtureZoneIds)
    : eventBus_(eventBus), fixtureZoneIds_(fixtureZoneIds)
{
}

void ContactListener::BeginContact(b2Contact* contact)
{
    if (!eventBus_ || !contact)
        return;

    if (isSensorContact(contact))
    {
        // One or both fixtures are sensors (zones)
        // Dispatch zone enter events
        b2Fixture* fixtureA = contact->GetFixtureA();
        b2Fixture* fixtureB = contact->GetFixtureB();

        // Determine which is the zone and which is the other entity
        b2Fixture* zoneFix = fixtureA->IsSensor() ? fixtureA : fixtureB;
        b2Fixture* otherFix = fixtureA->IsSensor() ? fixtureB : fixtureA;

        // Extract entity IDs from user data (set during fixture creation)
        uint32_t zoneEntityId = static_cast<uint32_t>(zoneFix->GetBody()->GetUserData().pointer);
        uint32_t otherEntityId = static_cast<uint32_t>(otherFix->GetBody()->GetUserData().pointer);

        // Extract zone ID from fixture map
        std::string zoneId;
        if (fixtureZoneIds_)
        {
            auto it = fixtureZoneIds_->find(reinterpret_cast<uintptr_t>(zoneFix));
            if (it != fixtureZoneIds_->end())
                zoneId = it->second;
        }

        sle::events::ZoneEnterEvent event{
            sle::entity::Entity{zoneEntityId},
            zoneId,
            sle::entity::Entity{otherEntityId}
        };
        eventBus_->queue(event);
    }
    else
    {
        // Both fixtures are solid colliders
        // Dispatch collision begin event
        b2Body* bodyA = contact->GetFixtureA()->GetBody();
        b2Body* bodyB = contact->GetFixtureB()->GetBody();

        uint32_t entityIdA = static_cast<uint32_t>(bodyA->GetUserData().pointer);
        uint32_t entityIdB = static_cast<uint32_t>(bodyB->GetUserData().pointer);

        sle::events::CollisionBeginEvent event{
            sle::entity::Entity{entityIdA},
            sle::entity::Entity{entityIdB}
        };
        eventBus_->queue(event);
    }
}

void ContactListener::EndContact(b2Contact* contact)
{
    if (!eventBus_ || !contact)
        return;

    if (isSensorContact(contact))
    {
        // Zone exit event
        b2Fixture* fixtureA = contact->GetFixtureA();
        b2Fixture* fixtureB = contact->GetFixtureB();

        b2Fixture* zoneFix = fixtureA->IsSensor() ? fixtureA : fixtureB;
        b2Fixture* otherFix = fixtureA->IsSensor() ? fixtureB : fixtureA;

        uint32_t zoneEntityId = static_cast<uint32_t>(zoneFix->GetBody()->GetUserData().pointer);
        uint32_t otherEntityId = static_cast<uint32_t>(otherFix->GetBody()->GetUserData().pointer);

        // Extract zone ID from fixture map
        std::string zoneId;
        if (fixtureZoneIds_)
        {
            auto it = fixtureZoneIds_->find(reinterpret_cast<uintptr_t>(zoneFix));
            if (it != fixtureZoneIds_->end())
                zoneId = it->second;
        }

        sle::events::ZoneExitEvent event{
            sle::entity::Entity{zoneEntityId},
            zoneId,
            sle::entity::Entity{otherEntityId}
        };
        eventBus_->queue(event);
    }
    else
    {
        // Collision end event
        b2Body* bodyA = contact->GetFixtureA()->GetBody();
        b2Body* bodyB = contact->GetFixtureB()->GetBody();

        uint32_t entityIdA = static_cast<uint32_t>(bodyA->GetUserData().pointer);
        uint32_t entityIdB = static_cast<uint32_t>(bodyB->GetUserData().pointer);

        sle::events::CollisionEndEvent event{
            sle::entity::Entity{entityIdA},
            sle::entity::Entity{entityIdB}
        };
        eventBus_->queue(event);
    }
}

void ContactListener::PreSolve(b2Contact* contact, const b2Manifold* oldManifold)
{
    // Default: allow collision
    // Can be overridden to disable collisions for specific pairs
}

void ContactListener::PostSolve(b2Contact* contact, const b2ContactImpulse* impulse)
{
    // Called after collision resolution
    // Can be used for damage calculations, impact sounds, etc.
}

bool ContactListener::isSensorContact(b2Contact* contact) const
{
    if (!contact)
        return false;

    b2Fixture* fixtureA = contact->GetFixtureA();
    b2Fixture* fixtureB = contact->GetFixtureB();

    // If either fixture is a sensor, this is a sensor contact
    return (fixtureA && fixtureA->IsSensor()) || (fixtureB && fixtureB->IsSensor());
}

} // namespace sle::physics
