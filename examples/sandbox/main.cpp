#define ENGINE_DEBUG

#include <sle/engine/Runtime.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/scene/components/UIComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/AnimatorComponent.hpp>
#include <sle/scene/components/StateMachineComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scene/components/RigidBodyComponent.hpp>
#include <sle/scene/components/BoxColliderComponent.hpp>
#include <sle/scene/components/CircleColliderComponent.hpp>
#include <sle/scene/components/BoxZoneComponent.hpp>
#include <sle/scene/components/CircleZoneComponent.hpp>
#include <sle/scripting/ScriptResource.hpp>

#include <filesystem>

using namespace sle;
using namespace sle::core;
using namespace sle::renderer;

namespace {

std::string resolveAssetPath(const std::string& relativePath)
{
  namespace fs = std::filesystem;

  fs::path path(relativePath);
  if (fs::exists(path))
    return path.lexically_normal().generic_string();

  fs::path current = fs::current_path();
  while (!current.empty())
  {
    fs::path candidate = current / relativePath;
    if (fs::exists(candidate))
      return candidate.lexically_normal().generic_string();

    if (current == current.root_path())
      break;
    current = current.parent_path();
  }

  return relativePath;
}

std::string pickFontAsset()
{
  return resolveAssetPath("assets/fonts/Roboto-Regular.ttf");
}

} // namespace

int main()
{
  EngineConfig config;

  config.width = 480;
  config.height = 270;
  config.vsync = true;
  config.title = "Sandbox Game";
  config.screenMode = ScreenMode::Windowed;

  Runtime runtime(config);

  auto initResult = runtime.init();
  if (!initResult.ok())
  {
      Log::error("Failed to initialize engine: {}", initResult.error());
      return -1;
  }

  runtime.registerScene("main", [config](Runtime& runtime)
  {
    const std::string playerMoveScriptAsset = resolveAssetPath("assets/scripts/player_move.lua");
    const std::string tileTextureAsset = resolveAssetPath("assets/textures/tile2.png");

    auto script = Resources::create<sle::scripting::ScriptResource>(
      "player_move_script",
      playerMoveScriptAsset);
    if (!script)
    {
      Log::error("Failed to preload script resource: {}", playerMoveScriptAsset);
      return;
    }

    auto texture = Resources::create<Texture>("tile2", tileTextureAsset);
    if (!texture)
    {
      Log::error("Failed to load sprite texture: {}", tileTextureAsset);
      return;
    }

    auto entity = runtime.getScene().createEntity();
    auto& registry = runtime.getScene().getRegistry();

    auto& transform = registry.addComponent<components::TransformComponent>(entity);
    transform.setPosition({
      static_cast<float>(config.width) * 0.5f,
      static_cast<float>(config.height) * 0.5f});
    transform.setScale({32.0f, 32.0f});

    auto& sprite = registry.addComponent<components::SpriteRenderer>(entity);
    sprite.region.texture = texture;
    sprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};

    auto& scriptComponent = registry.addComponent<components::ScriptComponent>(entity);
    scriptComponent.scriptAsset = "player_move_script";
    scriptComponent.enabled = true;
  });

  runtime.registerScene("physics_test", [config](Runtime& runtime)
  {
    const std::string fontAsset = pickFontAsset();
    const std::string physicsScriptAsset = resolveAssetPath("assets/scripts/physics_debug_test.lua");
    const std::string tileTextureAsset = resolveAssetPath("assets/textures/tile2.png");
    const std::string stateMachineAsset = resolveAssetPath("assets/state_machines/player_state_machine.json");
    const std::string worldLabelLayoutAsset = resolveAssetPath("assets/ui/world_label.xml");
    const std::string fpsHudLayoutAsset = resolveAssetPath("assets/ui/fps_hud.xml");

    if (!std::filesystem::exists(fontAsset))
    {
      Log::error("Roboto font is required for this demo but was not found: {}", fontAsset);
      return;
    }

    auto script = Resources::create<sle::scripting::ScriptResource>(
      "physics_debug_test_script",
      physicsScriptAsset);
    if (!script)
    {
      Log::error("Failed to preload script resource: {}", physicsScriptAsset);
      return;
    }

    auto texture = Resources::create<Texture>("tile2", tileTextureAsset);
    if (!texture)
    {
      Log::error("Failed to load sprite texture: {}", tileTextureAsset);
      return;
    }

    auto& scene = runtime.getScene();
    auto& registry = scene.getRegistry();

    // Player: dynamic box with script controls.
    auto player = scene.createEntity();
    auto& playerTransform = registry.addComponent<components::TransformComponent>(player);
    playerTransform.setPosition({
      static_cast<float>(config.width) * 0.5f,
      static_cast<float>(config.height) * 0.72f});
    playerTransform.setScale({24.0f, 24.0f});

    auto& playerSprite = registry.addComponent<components::SpriteRenderer>(player);
    playerSprite.region.texture = texture;
    playerSprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    playerSprite.color = {0.95f, 0.95f, 1.0f, 1.0f};

    auto& playerBody = registry.addComponent<components::RigidBodyComponent>(player);
    playerBody.bodyType = components::BodyType::Dynamic;
    playerBody.linearDamping = 0.08f;
    playerBody.angularDamping = 0.35f;
    playerBody.gravityScale = 1.0f;

    auto& playerCollider = registry.addComponent<components::BoxColliderComponent>(player);
    playerCollider.size = {24.0f, 24.0f};
    playerCollider.friction = 0.5f;
    playerCollider.restitution = 0.05f;

    auto& playerScript = registry.addComponent<components::ScriptComponent>(player);
    playerScript.scriptAsset = "physics_debug_test_script";
    playerScript.enabled = true;

    auto& playerStateMachine = registry.addComponent<components::StateMachineComponent>(player);
    playerStateMachine.definitionAsset = stateMachineAsset;

    auto& playerUi = registry.addComponent<components::UIComponent>(player);
    playerUi.layoutAsset = worldLabelLayoutAsset;
    playerUi.fontAsset = fontAsset;
    playerUi.spaceMode = components::UISpaceMode::World;
    playerUi.layer = 20;

    // Ground: static wide collider.
    auto ground = scene.createEntity();
    auto& groundTransform = registry.addComponent<components::TransformComponent>(ground);
    groundTransform.setPosition({static_cast<float>(config.width) * 0.5f, 32.0f});
    groundTransform.setScale({400.0f, 24.0f});

    auto& groundSprite = registry.addComponent<components::SpriteRenderer>(ground);
    groundSprite.region.texture = texture;
    groundSprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    groundSprite.color = {0.40f, 0.45f, 0.55f, 1.0f};

    auto& groundBody = registry.addComponent<components::RigidBodyComponent>(ground);
    groundBody.bodyType = components::BodyType::Static;

    auto& groundCollider = registry.addComponent<components::BoxColliderComponent>(ground);
    groundCollider.size = {400.0f, 24.0f};
    groundCollider.friction = 0.9f;

    // Obstacle: dynamic circle to test circle collider debug and interactions.
    auto ball = scene.createEntity();
    auto& ballTransform = registry.addComponent<components::TransformComponent>(ball);
    ballTransform.setPosition({static_cast<float>(config.width) * 0.62f, 180.0f});
    ballTransform.setScale({22.0f, 22.0f});

    auto& ballSprite = registry.addComponent<components::SpriteRenderer>(ball);
    ballSprite.region.texture = texture;
    ballSprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    ballSprite.color = {1.0f, 0.75f, 0.50f, 1.0f};

    auto& ballBody = registry.addComponent<components::RigidBodyComponent>(ball);
    ballBody.bodyType = components::BodyType::Dynamic;
    ballBody.gravityScale = 1.0f;
    ballBody.linearDamping = 0.03f;

    auto& ballCollider = registry.addComponent<components::CircleColliderComponent>(ball);
    ballCollider.radius = 11.0f;
    ballCollider.restitution = 0.45f;
    ballCollider.friction = 0.4f;

    // Box zone: sensor area in the middle to validate zone debug geometry.
    auto boxZoneEntity = scene.createEntity();
    auto& boxZoneTransform = registry.addComponent<components::TransformComponent>(boxZoneEntity);
    boxZoneTransform.setPosition({static_cast<float>(config.width) * 0.35f, 90.0f});
    boxZoneTransform.setScale({120.0f, 70.0f});

    auto& boxZoneBody = registry.addComponent<components::RigidBodyComponent>(boxZoneEntity);
    boxZoneBody.bodyType = components::BodyType::Static;

    auto& boxZone = registry.addComponent<components::BoxZoneComponent>(boxZoneEntity);
    boxZone.zoneId = "test_box_zone";
    boxZone.size = {120.0f, 70.0f};

    // Circle zone: second sensor shape for circle debug checks.
    auto circleZoneEntity = scene.createEntity();
    auto& circleZoneTransform = registry.addComponent<components::TransformComponent>(circleZoneEntity);
    circleZoneTransform.setPosition({static_cast<float>(config.width) * 0.75f, 110.0f});
    circleZoneTransform.setScale({1.0f, 1.0f});

    auto& circleZoneBody = registry.addComponent<components::RigidBodyComponent>(circleZoneEntity);
    circleZoneBody.bodyType = components::BodyType::Static;

    auto& circleZone = registry.addComponent<components::CircleZoneComponent>(circleZoneEntity);
    circleZone.zoneId = "test_circle_zone";
    circleZone.radius = 36.0f;

    auto hudEntity = scene.createEntity();
    auto& hud = registry.addComponent<components::UIComponent>(hudEntity);
    hud.layoutAsset = fpsHudLayoutAsset;
    hud.fontAsset = fontAsset;
    hud.spaceMode = components::UISpaceMode::Screen;
    hud.layer = 100;

    Log::info("UI demo font asset: {}", fontAsset);

    Log::info("Loaded physics_test scene. Use F3 or script key F to toggle physics debug.");
  });

  runtime.registerScene("npc_zone_demo", [config](Runtime& runtime)
  {
    const std::string fontAsset = pickFontAsset();
    const std::string npcDemoScriptAsset = resolveAssetPath("assets/scripts/npc_zone_demo.lua");
    const std::string zoneEmitterScriptAsset = resolveAssetPath("assets/scripts/zone_distance_emitter.lua");
    const std::string tileTextureAsset = resolveAssetPath("assets/textures/tile2.png");
    const std::string playerColorClipAsset = resolveAssetPath("assets/animations/player_color_red_blue.clip.json");
    const std::string distanceLabelLayoutAsset = resolveAssetPath("assets/ui/player_distance_world_label.xml");

    if (!std::filesystem::exists(fontAsset))
    {
      Log::error("Roboto font is required for this demo but was not found: {}", fontAsset);
      return;
    }

    auto script = Resources::create<sle::scripting::ScriptResource>(
      "npc_zone_demo_script",
      npcDemoScriptAsset);
    if (!script)
    {
      Log::error("Failed to preload script resource: {}", npcDemoScriptAsset);
      return;
    }

    auto zoneScript = Resources::create<sle::scripting::ScriptResource>(
      "zone_distance_emitter_script",
      zoneEmitterScriptAsset);
    if (!zoneScript)
    {
      Log::error("Failed to preload script resource: {}", zoneEmitterScriptAsset);
      return;
    }

    auto texture = Resources::create<Texture>("tile2", tileTextureAsset);
    if (!texture)
    {
      Log::error("Failed to load sprite texture: {}", tileTextureAsset);
      return;
    }

    auto& scene = runtime.getScene();
    auto& registry = scene.getRegistry();

    // 1) Player (entity id 1): controllable, animated color, and world-space UI label.
    const auto player = scene.createEntity();

    auto& playerTransform = registry.addComponent<components::TransformComponent>(player);
    playerTransform.setPosition({static_cast<float>(config.width) * 0.35f, static_cast<float>(config.height) * 0.50f});
    playerTransform.setScale({28.0f, 28.0f});

    auto& playerSprite = registry.addComponent<components::SpriteRenderer>(player);
    playerSprite.region.texture = texture;
    playerSprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    playerSprite.color = {1.0f, 0.0f, 0.0f, 1.0f};

    auto& playerBody = registry.addComponent<components::RigidBodyComponent>(player);
    playerBody.bodyType = components::BodyType::Dynamic;
    playerBody.gravityScale = 0.0f;
    playerBody.linearDamping = 6.0f;

    auto& playerCollider = registry.addComponent<components::CircleColliderComponent>(player);
    playerCollider.radius = 14.0f;
    playerCollider.friction = 0.3f;
    playerCollider.restitution = 0.1f;

    auto& playerAnimator = registry.addComponent<components::AnimatorComponent>(player);
    playerAnimator.clipAsset = playerColorClipAsset;
    playerAnimator.enabled = true;
    playerAnimator.playing = true;

    auto& playerScript = registry.addComponent<components::ScriptComponent>(player);
    playerScript.scriptAsset = "npc_zone_demo_script";
    playerScript.enabled = true;

    auto& playerUi = registry.addComponent<components::UIComponent>(player);
    playerUi.layoutAsset = distanceLabelLayoutAsset;
    playerUi.fontAsset = fontAsset;
    playerUi.spaceMode = components::UISpaceMode::World;
    playerUi.layer = 50;

    // 2) NPC (entity id 2): static anchor for distance + event area demo.
    const auto npc = scene.createEntity();

    auto& npcTransform = registry.addComponent<components::TransformComponent>(npc);
    npcTransform.setPosition({static_cast<float>(config.width) * 0.70f, static_cast<float>(config.height) * 0.50f});
    npcTransform.setScale({30.0f, 30.0f});

    auto& npcSprite = registry.addComponent<components::SpriteRenderer>(npc);
    npcSprite.region.texture = texture;
    npcSprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    npcSprite.color = {0.20f, 0.95f, 0.30f, 1.0f};

    auto& npcBody = registry.addComponent<components::RigidBodyComponent>(npc);
    npcBody.bodyType = components::BodyType::Static;

    auto& npcCollider = registry.addComponent<components::CircleColliderComponent>(npc);
    npcCollider.radius = 15.0f;

    // Expose NPC as animator target for automatic UI bindings like
    // {{entity.target.npc.distance}} on the player's attached UI document.
    playerAnimator.targetEntities["npc"] = npc.getID();

    // Visible area 1 (inner ring) and active zone.
    const auto npcInnerArea = scene.createEntity();

    auto& innerTransform = registry.addComponent<components::TransformComponent>(npcInnerArea);
    innerTransform.setPosition(npcTransform.getPosition());
    innerTransform.setScale({120.0f, 120.0f});

    auto& innerSprite = registry.addComponent<components::SpriteRenderer>(npcInnerArea);
    innerSprite.region.texture = texture;
    innerSprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    innerSprite.color = {0.25f, 0.70f, 1.00f, 0.18f};

    auto& innerBody = registry.addComponent<components::RigidBodyComponent>(npcInnerArea);
    innerBody.bodyType = components::BodyType::Static;

    auto& innerZone = registry.addComponent<components::CircleZoneComponent>(npcInnerArea);
    innerZone.zoneId = "npc_inner";
    innerZone.radius = 60.0f;

    // Visible area 2 (outer ring) and secondary zone.
    const auto npcOuterArea = scene.createEntity();

    auto& outerTransform = registry.addComponent<components::TransformComponent>(npcOuterArea);
    outerTransform.setPosition(npcTransform.getPosition());
    outerTransform.setScale({180.0f, 180.0f});

    auto& outerSprite = registry.addComponent<components::SpriteRenderer>(npcOuterArea);
    outerSprite.region.texture = texture;
    outerSprite.region.uv = {0.0f, 0.0f, 1.0f, 1.0f};
    outerSprite.color = {0.25f, 0.70f, 1.00f, 0.08f};

    auto& outerBody = registry.addComponent<components::RigidBodyComponent>(npcOuterArea);
    outerBody.bodyType = components::BodyType::Static;

    auto& outerZone = registry.addComponent<components::CircleZoneComponent>(npcOuterArea);
    outerZone.zoneId = "npc_outer";
    outerZone.radius = 90.0f;

    auto& outerZoneScript = registry.addComponent<components::ScriptComponent>(npcOuterArea);
    outerZoneScript.scriptAsset = "zone_distance_emitter_script";
    outerZoneScript.enabled = true;

    Log::info("Loaded npc_zone_demo scene. Move with WASD toward the NPC to trigger event subscriptions.");
  });

  auto loadSceneResult = runtime.loadScene("npc_zone_demo");
  if (!loadSceneResult.ok())
  {
    Log::error("Failed to load startup scene: {}", loadSceneResult.error());
    return -1;
  }

  runtime.run();
}
