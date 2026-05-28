#define ENGINE_DEBUG

#include <sle/engine/Runtime.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/scene/components/UIComponent.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
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

std::string pickFontAsset()
{
  return "assets/fonts/Roboto-Regular.ttf";
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
    auto script = Resources::create<sle::scripting::ScriptResource>(
      "player_move_script",
      "assets/scripts/player_move.lua");
    if (!script)
    {
      Log::error("Failed to preload script resource: assets/scripts/player_move.lua");
      return;
    }

    auto texture = Resources::create<Texture>("tile2", "assets/textures/tile2.png");
    if (!texture)
    {
      Log::error("Failed to load sprite texture: assets/textures/tile2.png");
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

    if (!std::filesystem::exists(fontAsset))
    {
      Log::error("Roboto font is required for this demo but was not found: {}", fontAsset);
      return;
    }

    auto script = Resources::create<sle::scripting::ScriptResource>(
      "physics_debug_test_script",
      "assets/scripts/physics_debug_test.lua");
    if (!script)
    {
      Log::error("Failed to preload script resource: assets/scripts/physics_debug_test.lua");
      return;
    }

    auto texture = Resources::create<Texture>("tile2", "assets/textures/tile2.png");
    if (!texture)
    {
      Log::error("Failed to load sprite texture: assets/textures/tile2.png");
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
    playerStateMachine.definitionAsset = "assets/state_machines/player_state_machine.json";

    auto& playerUi = registry.addComponent<components::UIComponent>(player);
    playerUi.layoutAsset = "assets/ui/world_label.xml";
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
    hud.layoutAsset = "assets/ui/fps_hud.xml";
    hud.fontAsset = fontAsset;
    hud.spaceMode = components::UISpaceMode::Screen;
    hud.layer = 100;

    Log::info("UI demo font asset: {}", fontAsset);

    Log::info("Loaded physics_test scene. Use F3 or script key F to toggle physics debug.");
  });

  auto loadSceneResult = runtime.loadScene("physics_test");
  if (!loadSceneResult.ok())
  {
    Log::error("Failed to load startup scene: {}", loadSceneResult.error());
    return -1;
  }

  runtime.run();
}
