#include <sle/engine/PhysicsSystem.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/renderer/Renderer.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <box2d/box2d.h>
#include <cmath>
#include <iostream>

int main()
{
    using namespace sle::components;
    sle::entity::Scene scene;
    auto& r = scene.getRegistry();
    sle::events::EventBus global;
    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(320, 180);
    sle::physics::PhysicsWorld world({0, 0});
    sle::Context ctx{scene, r, scene.getEventBus(), global, renderer, camera, &world, 0};
    sle::PhysicsSystem physics;
    sle::TransformSystem transforms;
    bool ok = true;
    auto check = [&](bool value, const char* message) { if (!value) { std::cerr << message << "\n"; ok = false; } };
    auto near = [](float a, float b) { return std::fabs(a-b) < 0.002f; };
    auto parent = scene.ensureSceneEntity();
    auto& pt = r.addComponent<TransformComponent>(parent);
    pt.setPosition({100, 20}); pt.setRotation(1.57079632679f); pt.setScale({2, 3});
    auto transparent = scene.createEntity();
    auto child = scene.createEntity(); scene.setParent(child, transparent);
    auto& ct = r.addComponent<TransformComponent>(child); ct.setPosition({4, 5}); ct.setScale({100, 100});
    auto& rb = r.addComponent<RigidBodyComponent>(child); rb.bodyType = BodyType::Kinematic;
    auto& box = r.addComponent<BoxColliderComponent>(child); box.size = {10, 20}; box.offset = {2, 3};
    transforms.update(ctx); physics.update(ctx);
    const auto id = rb.box2dBodyId;
    auto pose = [&](float x, float y, float angle) { glm::vec2 p; float a; world.getBodyTransform(id, p, a); return near(p.x,x) && near(p.y,y) && near(a,angle); };
    check(pose(85,28,1.57079632679f), "initial body must compose current scene/transparent ancestors");
    auto* native = world.getRawWorld()->GetBodyList();
    auto* shape = static_cast<const b2PolygonShape*>(native->GetFixtureList()->GetShape());
    float minX=1000, maxX=-1000, minY=1000, maxY=-1000;
    for(int i=0;i<shape->m_count;++i) { auto v=world.physicsToWorldVec2({shape->m_vertices[i].x,shape->m_vertices[i].y}); minX=std::min(minX,v.x); maxX=std::max(maxX,v.x); minY=std::min(minY,v.y); maxY=std::max(maxY,v.y); }
    check(near(maxX-minX,20)&&near(maxY-minY,60)&&near((maxX+minX)/2,4)&&near((maxY+minY)/2,9), "fixtures inherit ancestor scale, excluding own visual dimensions");
    pt.setPosition({110, 20}); transforms.update(ctx); physics.update(ctx);
    check(pose(95,28,1.57079632679f), "parent edit must reach existing body even with clean child/cache");
    world.setBodyVelocity(id,{60,0}); ctx.dt=1.0f/60; physics.update(ctx); ctx.dt=0;
    check(near(ct.getPosition().x,4)&&near(ct.getPosition().y,5-1.0f/3), "world motion must write inverse-parent local coordinates");
    auto p2 = scene.createEntity(); scene.setParent(p2,{}); auto& t2=r.addComponent<TransformComponent>(p2); t2.setPosition({200,0});
    ct.setPosition({4,5}); scene.setParent(child,p2); physics.update(ctx);
    check(pose(204,5,0)&&rb.box2dBodyId==id&&near(world.getBodyVelocity(id).x,60), "reparent must retain body identity and world velocity");
    t2.setScale({0,1}); physics.update(ctx);
    check(!native->IsEnabled()&&rb.enabled, "singular parent must suspend native body without changing authored enabled");
    t2.setPosition({300,0}); t2.setScale({2,2}); physics.update(ctx);
    check(native->IsEnabled()&&pose(308,10,0)&&rb.box2dBodyId==id, "invertible parent must resume at current hierarchy pose");
    auto& gate=r.addComponent<ChunkActivationComponent>(child); gate.suspendedSubsystems=static_cast<std::uint8_t>(ChunkSubsystem::Physics); physics.update(ctx);
    t2.setPosition({400,0}); transforms.update(ctx); gate.suspendedSubsystems=0; physics.update(ctx);
    check(pose(408,10,0)&&rb.box2dBodyId==id, "chunk resume must use current hierarchy and retain native identity");
    // Independent world bodies: the child must not inherit the parent's simulated
    // displacement a second time, regardless of registry iteration order.
    auto movingChild = scene.createEntity(); scene.setParent(movingChild,p2);
    auto& mt=r.addComponent<TransformComponent>(movingChild); mt.setPosition({20,0});
    auto& mb=r.addComponent<RigidBodyComponent>(movingChild); mb.bodyType=BodyType::Kinematic; mb.velocity={0,60};
    auto& pb=r.addComponent<RigidBodyComponent>(p2); pb.bodyType=BodyType::Kinematic; pb.velocity={60,0};
    physics.update(ctx); ctx.dt=1.0f/60; physics.update(ctx); ctx.dt=0;
    transforms.update(ctx);
    glm::vec2 cp; float ca; world.getBodyTransform(mb.box2dBodyId,cp,ca);
    check(near(cp.x,440)&&near(cp.y,1)&&near(mt.getPosition().x,19.5f)&&near(mt.getPosition().y,0.5f), "simulated ancestor and child must resolve independently parent-first");
    auto circle=scene.createEntity(); scene.setParent(circle,p2);
    r.addComponent<TransformComponent>(circle);
    r.addComponent<RigidBodyComponent>(circle).bodyType=BodyType::Static;
    auto& cc=r.addComponent<CircleColliderComponent>(circle); cc.radius=5;
    physics.update(ctx);
    check(reinterpret_cast<b2Fixture*>(cc.box2dFixtureId)->GetType()==b2Shape::e_circle,"uniform ancestor scale must retain native circle");
    t2.setScale({2,3}); physics.update(ctx);
    auto* ellipse=reinterpret_cast<b2Fixture*>(cc.box2dFixtureId);
    check(ellipse->GetType()==b2Shape::e_polygon && static_cast<const b2PolygonShape*>(ellipse->GetShape())->m_count==8,"nonuniform circle must use convex eight-vertex ellipse");
    auto root=scene.createEntity(); scene.setParent(root,{});
    r.addComponent<TransformComponent>(root).setScale({100,200});
    r.addComponent<RigidBodyComponent>(root).bodyType=BodyType::Static;
    auto& rootCircle=r.addComponent<CircleColliderComponent>(root); rootCircle.radius=7;
    physics.update(ctx);
    check(near(world.physicsToWorldLength(static_cast<const b2CircleShape*>(reinterpret_cast<b2Fixture*>(rootCircle.box2dFixtureId)->GetShape())->m_radius),7),"root visual scale must not change explicit fixture units");
    r.removeComponent<TransformComponent>(child); physics.update(ctx);
    check(rb.box2dBodyId==0 || !native->IsEnabled(), "removing Transform must stop orphan body simulation");
    check(box.box2dFixtureId==0,"removing Transform must clear native fixture handles");
    r.addComponent<TransformComponent>(child).setPosition({4,5}); physics.update(ctx);
    check(rb.box2dBodyId!=0 && box.box2dFixtureId!=0 &&
        reinterpret_cast<b2Fixture*>(box.box2dFixtureId)->GetBody()->IsEnabled(),
        "restoring Transform must recreate valid body and fixture");
    auto& invalidZone=r.addComponent<CircleZoneComponent>(child); invalidZone.radius=0;
    gate.suspendedSubsystems=static_cast<std::uint8_t>(ChunkSubsystem::Zones);
    physics.update(ctx);
    check(reinterpret_cast<b2Fixture*>(box.box2dFixtureId)->GetBody()->IsEnabled(),
        "invalid gated-off zone must not suspend active collision body");
    r.removeComponent<CircleZoneComponent>(child); gate.suspendedSubsystems=0;
    // Actual converted vertices can collapse at huge offsets despite finite
    // radii and positions. This must suspend before native polygon construction.
    const auto circleBody=r.getComponent<RigidBodyComponent>(circle)->box2dBodyId;
    cc.radius=1; cc.offset={1.0e10f,1.0e10f}; physics.update(ctx);
    auto* circleNative=reinterpret_cast<b2Fixture*>(cc.box2dFixtureId)->GetBody();
    check(!circleNative->IsEnabled(),"unrepresentable ellipse vertices must suspend without native assertion");
    check(world.createCircleFixture(circleBody,cc,{2,3})==0,
        "direct circle fixture creation must reject unrepresentable native vertices");
    cc.offset={0,0}; physics.update(ctx);
    check(circleNative->IsEnabled()&&r.getComponent<RigidBodyComponent>(circle)->box2dBodyId==circleBody,
        "valid ellipse geometry must resume same native body");
    return ok ? 0 : 1;
}
