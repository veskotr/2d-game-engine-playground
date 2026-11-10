#include <sle/engine/Runtime.hpp>
#include <sle/events/ZoneEvents.hpp>
#include <sle/engine/ChunkActivationController.hpp>
#include <sle/engine/ChunkOwnershipIndex.hpp>
#include <sle/engine/PhysicsSystem.hpp>
#include <sle/engine/TransformSystem.hpp>
#include <sle/engine/StateMachineSystem.hpp>
#include <sle/engine/ScriptSystem.hpp>
#include <sle/engine/AnimationSystem.hpp>
#include <sle/renderer/Camera2D.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scene/components/ChunkActivationComponent.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/UIComponent.hpp>
#include <box2d/b2_world.h>
#include <box2d/b2_body.h>
#include <box2d/b2_fixture.h>
#include <cmath>
#include <iostream>

int main()
{
    sle::Runtime runtime(sle::core::EngineConfig{});
    auto& scene = runtime.getScene();
    auto& registry = scene.getRegistry();
    sle::ScriptApiImpl api(runtime);
    if (!runtime.getScriptEngine().init(&api))
        return 1;
    sle::physics::PhysicsWorld world({0.0f, 0.0f});
    world.setFixedTimestep(1.0f / 60.0f);
    sle::renderer::Renderer renderer;
    sle::core::Camera2D camera(320.0f, 180.0f);
    sle::Context ctx{scene, registry, scene.getEventBus(), runtime.getGlobalBus(), renderer, camera, &world, 1.0f / 60.0f};
    sle::PhysicsSystem physics;
    sle::ScriptSystem scripts;
    scripts.setScriptRuntime(&runtime.getScriptEngine());
    sle::AnimationSystem animation;
    sle::StateMachineSystem stateMachines;
    sle::ChunkActivationController controller({.mode = sle::core::ChunkActivationMode::AutoToggle});
    sle::ChunkOwnershipIndex index;
    bool passed = true;
    auto check = [&](bool condition, const char* message) {
        if (!condition) { std::cerr << message << "\n"; passed = false; }
    };
    auto near = [](float a, float b) { return std::fabs(a - b) < 0.001f; };

    const auto disabled = scene.createEntity();
    registry.addComponent<sle::components::ChunkActivationComponent>(disabled);
    auto& disabledScript = registry.addComponent<sle::components::ScriptComponent>(disabled);
    auto& disabledBody = registry.addComponent<sle::components::RigidBodyComponent>(disabled);
    auto& disabledAnimation = registry.addComponent<sle::components::AnimatorComponent>(disabled);
    auto& disabledState = registry.addComponent<sle::components::StateMachineComponent>(disabled);
    auto& disabledBox = registry.addComponent<sle::components::BoxColliderComponent>(disabled);
    auto& disabledCircle = registry.addComponent<sle::components::CircleColliderComponent>(disabled);
    auto& disabledZone = registry.addComponent<sle::components::BoxZoneComponent>(disabled);
    auto& disabledCircleZone = registry.addComponent<sle::components::CircleZoneComponent>(disabled);
    auto& hiddenSprite = registry.addComponent<sle::components::SpriteRenderer>(disabled);
    auto& hiddenUi = registry.addComponent<sle::components::UIComponent>(disabled);
    disabledScript.enabled = disabledBody.enabled = disabledAnimation.enabled = disabledState.enabled = false;
    disabledBox.enabled = disabledCircle.enabled = disabledZone.enabled = disabledCircleZone.enabled = false;
    hiddenSprite.visible = hiddenUi.visible = false;

    const auto moving = scene.createEntity();
    registry.addComponent<sle::components::ChunkActivationComponent>(moving);
    registry.addComponent<sle::components::TransformComponent>(moving);
    auto& body = registry.addComponent<sle::components::RigidBodyComponent>(moving);
    body.bodyType = sle::components::BodyType::Kinematic;
    body.velocity = {60.0f, 0.0f};
    auto& box = registry.addComponent<sle::components::BoxColliderComponent>(moving);
    registry.addComponent<sle::components::CircleColliderComponent>(moving);
    registry.addComponent<sle::components::BoxZoneComponent>(moving);
    registry.addComponent<sle::components::CircleZoneComponent>(moving);

    const auto scripted = scene.createEntity();
    registry.addComponent<sle::components::ChunkActivationComponent>(scripted);
    auto& scriptTransform = registry.addComponent<sle::components::TransformComponent>(scripted);
    registry.addComponent<sle::components::ScriptComponent>(scripted).scriptAsset = "tests/data/scripts/chunk_script_counter.lua";

    const auto animated = scene.createEntity();
    registry.addComponent<sle::components::ChunkActivationComponent>(animated);
    registry.addComponent<sle::components::TransformComponent>(animated);
    auto& animator = registry.addComponent<sle::components::AnimatorComponent>(animated);
    animator.clip = std::make_shared<sle::components::AnimationClipDefinition>();
    animator.clip->lengthSeconds = 1.0f;
    auto& state = registry.addComponent<sle::components::StateMachineComponent>(animated);
    state.definition = std::make_shared<sle::components::StateMachineDefinition>();
    state.definition->states.emplace("Idle", sle::components::StateDefinition{"Idle"});
    state.currentState = "Idle";
    state.initialized = true;
    index.rebuild(registry);
    controller.applyActiveChunks({{0, 0}}, index, registry);
    check(!disabledScript.enabled && !disabledBody.enabled && !disabledAnimation.enabled && !disabledState.enabled &&
          !disabledBox.enabled && !disabledCircle.enabled && !disabledZone.enabled && !disabledCircleZone.enabled &&
          !hiddenSprite.visible && !hiddenUi.visible, "Active chunks must not override gameplay-disabled components");
    physics.update(ctx);
    scripts.update(ctx);
    animation.update(scene, 0.1f);
    stateMachines.update(scene, 0.1f);
    check(near(scriptTransform.getPosition().x, 1.0f), "Script must update while its chunk is active");
    const auto bodyId = body.box2dBodyId;
    auto* native = world.getRawWorld()->GetBodyList();
    // The disabled entity has no transform and creates no body.
    check(native && native->GetFixtureList(), "Active body must have fixtures");
    glm::vec2 before;
    float angle;
    world.getBodyTransform(bodyId, before, angle);
    const float animationTime = animator.timeSeconds;
    const float stateTime = state.stateTimeSeconds;

    controller.applyActiveChunks({}, index, registry);
    physics.update(ctx);
    scripts.update(ctx);
    animation.update(scene, 0.1f);
    stateMachines.update(scene, 0.1f);
    for (int i = 0; i < 5; ++i) physics.update(ctx);
    glm::vec2 paused;
    world.getBodyTransform(bodyId, paused, angle);
    check(near(paused.x, before.x), "Inactive Box2D bodies must stop simulating, not just stop ECS readback");
    check(body.enabled && box.enabled, "Chunk suspension must not rewrite gameplay-enabled physics flags");
    check(near(state.stateTimeSeconds, stateTime), "Inactive state machine must pause");
    check(near(animator.timeSeconds, animationTime), "Inactive animation must pause");
    check(near(scriptTransform.getPosition().x, 1.0f) && runtime.getScriptEngine().hasScript(scripted.getID()),
          "Chunk suspension must pause scripts without destroying their Lua instances");

    // Gameplay can change its desired state even while the chunk is suspended.
    box.enabled = false;
    disabledScript.enabled = false;
    controller.applyActiveChunks({{0, 0}}, index, registry);
    physics.update(ctx);
    scripts.update(ctx);
    animation.update(scene, 0.1f);
    stateMachines.update(scene, 0.1f);
    glm::vec2 resumed;
    world.getBodyTransform(bodyId, resumed, angle);
    check(body.box2dBodyId == bodyId && near(resumed.x, before.x + 1.0f), "Reactivation must resume the same body without hidden catch-up movement");
    check(near(scriptTransform.getPosition().x, 2.0f), "Reactivation must resume the same Lua state, without rerunning initialization");
    check(near(state.stateTimeSeconds, stateTime + 0.1f), "State machine must resume from its paused time");
    check(near(animator.timeSeconds, animationTime + 0.1f), "Animation must resume from its paused time");
    check(!box.enabled && box.box2dFixtureId == 0, "Gameplay-disabled collider must remain disabled and have no active fixture");
    check(!disabledScript.enabled && !disabledBody.enabled && !disabledAnimation.enabled && !disabledState.enabled &&
          !disabledBox.enabled && !disabledCircle.enabled && !disabledZone.enabled && !disabledCircleZone.enabled &&
          !hiddenSprite.visible && !hiddenUi.visible, "Leaving and returning to a chunk must preserve gameplay-disabled state");
    // Fixture-only suspension must leave a body simulating while removing both
    // solid collision shapes and sensor shapes. Existing bodies must reconcile.
    sle::ChunkActivationController fixturesOnly({
        .mode = sle::core::ChunkActivationMode::AutoToggle,
        .affectPhysics = false
    });
    fixturesOnly.applyActiveChunks({}, index, registry);
    physics.update(ctx);
    check(native->IsEnabled() && !native->GetFixtureList(),
          "Collision/zone suspension must remove fixtures even when physics keeps running");
    fixturesOnly.applyActiveChunks({{0, 0}}, index, registry);
    physics.update(ctx);
    int fixtureCount = 0;
    for (auto* fixture = native->GetFixtureList(); fixture; fixture = fixture->GetNext()) ++fixtureCount;
    check(fixtureCount == 3 && box.box2dFixtureId == 0,
          "Reactivation must restore enabled fixtures without reviving a gameplay-disabled collider");

    // A policy opt-out must release a previous suspension rather than leave stale state.
    controller.applyActiveChunks({}, index, registry);
    sle::ChunkActivationController disabledController({.mode = sle::core::ChunkActivationMode::Disabled});
    disabledController.applyActiveChunks({}, index, registry);
    physics.update(ctx);
    scripts.update(ctx);
    check(native->IsEnabled() && near(scriptTransform.getPosition().x, 3.0f),
          "Disabling chunk activation must release old suspension without resetting scripts");
    // Rendering can consume dirty flags while physics is suspended. Resume
    // must still synchronize the latest local pose, including script/UI edits.
    controller.applyActiveChunks({}, index, registry);
    physics.update(ctx);
    registry.getComponent<sle::components::TransformComponent>(moving)->setPosition({300.0f, 0.0f});
    sle::TransformSystem transforms;
    transforms.update(ctx);
    controller.applyActiveChunks({{0, 0}}, index, registry);
    physics.update(ctx);
    glm::vec2 edited;
    world.getBodyTransform(bodyId, edited, angle);
    check(near(edited.x, 301.0f), "Reactivation must preserve local edits whose render dirty flag was already consumed");

    // Zone-only suspension must preserve the zone identity in EndContact events.
    const auto zoneEntity = scene.createEntity();
    registry.addComponent<sle::components::ChunkActivationComponent>(zoneEntity).homeChunkX = 9;
    registry.addComponent<sle::components::TransformComponent>(zoneEntity).setPosition({400.0f, 0.0f});
    registry.addComponent<sle::components::RigidBodyComponent>(zoneEntity).bodyType = sle::components::BodyType::Static;
    auto& namedZone = registry.addComponent<sle::components::BoxZoneComponent>(zoneEntity);
    namedZone.zoneId = "chunk-zone";
    namedZone.size = {20.0f, 20.0f};
    const auto visitor = scene.createEntity();
    registry.addComponent<sle::components::TransformComponent>(visitor).setPosition({400.0f, 0.0f});
    registry.addComponent<sle::components::RigidBodyComponent>(visitor);
    registry.addComponent<sle::components::CircleColliderComponent>(visitor);
    int enters = 0;
    int exits = 0;
    scene.getEventBus().subscribe<sle::events::ZoneEnterEvent>([&](const auto& event) {
        if (event.zoneEntity == zoneEntity && event.zoneId == "chunk-zone") ++enters;
    });
    scene.getEventBus().subscribe<sle::events::ZoneExitEvent>([&](const auto& event) {
        if (event.zoneEntity == zoneEntity && event.zoneId == "chunk-zone") ++exits;
    });
    physics.update(ctx);
    scene.getEventBus().flushQueue();
    check(enters == 1, "Named zone must first establish an overlapping contact");
    index.rebuild(registry);
    fixturesOnly.applyActiveChunks({{0, 0}}, index, registry);
    physics.update(ctx);
    scene.getEventBus().flushQueue();
    check(exits == 1, "Zone-only suspension must deliver the original zone ID in its exit event");
    runtime.getScriptEngine().shutdown();
    return passed ? 0 : 1;
}
