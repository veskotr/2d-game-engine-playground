#define ENGINE_DEBUG

#include <sle/engine/Runtime.hpp>
#include <sle/core/Log.hpp>
#include <sle/resources/Resources.hpp>
#include <sle/renderer/Texture.hpp>

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

  Runtime engine(config);

  auto initResult = engine.init();
  if (!initResult.ok())
  {
      Log::error("Failed to initialize engine: {}", initResult.error());
      return -1;
  }

  // Example: Load a texture and assign it to an object
  // auto texture = Resources::create<Texture>("player", "assets/textures/player.png");
  // if (texture) {
  //     auto player = engine.getScene().createObject();
  //     auto* sprite = player->addComponent<SpriteRenderer>();
  //     sprite->textureId = texture->getID();
  // }

  engine.run();
} 