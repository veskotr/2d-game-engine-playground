#define ENGINE_DEBUG

#include <sle/engine/Runtime.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/renderer/Texture.hpp>
#include <sle/scene/components/SpriteRenderer.hpp>
#include <sle/scene/components/ScriptComponent.hpp>
#include <sle/scene/components/Transform.hpp>
#include <sle/scripting/ScriptResource.hpp>

using namespace sle;
using namespace sle::core;
using namespace sle::renderer;

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

  auto loadSceneResult = runtime.loadScene("main");
  if (!loadSceneResult.ok())
  {
    Log::error("Failed to load startup scene: {}", loadSceneResult.error());
    return -1;
  }

  runtime.run();
} 