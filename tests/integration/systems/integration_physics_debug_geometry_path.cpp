#include <sle/engine/RenderSystem.hpp>
#include <sle/engine/EntityPreviewSystem.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/scene/Scene.hpp>
#include <sle/scene/TransformMath.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/physics/PhysicsWorld.hpp>
#include <sle/renderer/Renderer.hpp>
#include <iostream>
#include <cmath>
#include <limits>

// Inspect queued commands without creating an OpenGL context or adding test hooks
// to Renderer. Explicit template instantiation may name private members.
struct LinesTag { using type = std::vector<sle::renderer::LineCommand> sle::renderer::Renderer::*; friend type linesMember(LinesTag); };
template <class Tag, typename Tag::type Member> struct Access { friend typename Tag::type linesMember(Tag) { return Member; } };
template struct Access<LinesTag, &sle::renderer::Renderer::lineCommands>;

int main()
{
    using namespace sle::components;
    sle::entity::Scene scene;
    auto& r=scene.getRegistry();
    sle::events::EventBus global;
    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(320,180);
    sle::physics::PhysicsWorld physics({0,0});
    sle::Context ctx{scene,r,scene.getEventBus(),global,renderer,camera,&physics,0};
    sle::TransformSystem transforms;
    sle::RenderSystem render; render.setPhysicsDebugEnabled(true);
    auto& lines=renderer.*linesMember(LinesTag{});
    bool ok=true;
    auto check=[&](bool value,const char* message) { if(!value) { std::cerr<<message<<'\n'; ok=false; } };
    auto near=[](glm::vec2 a,glm::vec2 b) { return glm::length(a-b)<0.002f; };
    auto draw=[&] { lines.clear(); transforms.update(ctx); render.update(ctx); };
    auto entity=scene.createEntity(); scene.setParent(entity,{});
    auto& local=r.addComponent<TransformComponent>(entity); local.setScale({32,32});
    r.addComponent<RigidBodyComponent>(entity);
    auto& box=r.addComponent<BoxColliderComponent>(entity); box.size={2,4}; box.offset={3,5};
    draw();
    check(lines.size()==4 && near(lines[0].a,{2,3}) && near(lines[0].b,{4,3}),"root outline must exclude own scale 32");
    local.setScale({0,-32}); draw();
    check(lines.size()==4 && near(lines[0].a,{2,3}),"zero or negative own visual scale must not collapse collider");
    auto parent=scene.createEntity(); scene.setParent(parent,{});
    auto& pt=r.addComponent<TransformComponent>(parent); pt.setScale({-2,3}); pt.setRotation(1.57079632679f); pt.setPosition({10,20});
    auto transparent=scene.createEntity(); scene.setParent(transparent,parent); scene.setParent(entity,transparent);
    draw();
    // Signed offset (-6,15), positive half extents (2,6), rotated by pi/2.
    check(lines.size()==4 && near(lines[0].a,{1,12}) && near(lines[0].b,{1,16}),"nested mirrored outline must use signed offset and absolute parent dimensions");
    r.removeComponent<BoxColliderComponent>(entity);
    auto& circle=r.addComponent<CircleColliderComponent>(entity); circle.radius=2; circle.offset={3,5};
    draw();
    check(lines.size()==8 && near(lines[0].a,{-5,18}),"nonuniform circle outline must match eight-vertex physics ellipse");
    circle.enabled=false; draw(); check(lines.empty(),"disabled colliders must not draw");
    auto& zone=r.addComponent<CircleZoneComponent>(entity); zone.radius=2; zone.offset={3,5};
    draw(); check(lines.size()==8,"circle zones must share ellipse geometry");
    zone.radius=0; draw(); check(lines.empty(),"invalid zone must not draw an active outline");
    r.removeComponent<CircleZoneComponent>(entity);
    auto& bz=r.addComponent<BoxZoneComponent>(entity); bz.size={2,4}; bz.offset={3,5};
    draw(); check(lines.size()==4 && near(lines[0].a,{1,12}),"box zones must share explicit collider units");
    circle.enabled=true; circle.radius=0; draw(); check(lines.empty(),"an invalid active fixture suspends all body outlines");
    auto& gate=r.addComponent<ChunkActivationComponent>(entity); gate.suspendedSubsystems=static_cast<std::uint8_t>(ChunkSubsystem::Collision);
    draw(); check(lines.size()==4,"invalid gated-off collider must not suppress active zone");
    pt.setScale({0,3}); draw(); check(lines.empty(),"singular ancestor must suspend outlines");
    pt.setScale({2,2}); draw(); check(lines.size()==4,"valid ancestor must restore zone outline");
    bz.enabled=false; circle.radius=2; gate.suspendedSubsystems=0; draw();
    check(lines.size()==24,"uniform circle must remain smooth circle outline");
    auto* body=r.getComponent<RigidBodyComponent>(entity);
    body->enabled=false; draw(); check(lines.empty(),"disabled body must hide active fixture outlines");
    body->enabled=true;
    local.setPosition({std::numeric_limits<float>::infinity(),0}); draw();
    check(lines.empty(),"nonfinite pose must suppress all outlines");
    local.setPosition({0,0}); scene.setParent(entity,{}); draw();
    check(lines.size()==24 && near(lines[0].a,{5,5}),"reparenting to root must restore explicit circle units");
    sle::entity::Scene preview;
    auto e=preview.createEntity(); preview.setParent(e,{});
    auto& t=preview.getRegistry().addComponent<TransformComponent>(e); t.setScale({32,32});
    preview.getRegistry().addComponent<BoxColliderComponent>(e).size={2,4};
    sle::Context previewCtx{preview,preview.getRegistry(),preview.getEventBus(),global,renderer,camera,&physics,0};
    transforms.update(previewCtx);
    sle::EntityPreviewSystem::frameCamera(preview,camera,1);
    check(camera.getViewportSize().y < 10,"editor framing must exclude own collider scale");
    return ok?0:1;
}
