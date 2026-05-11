#define ENGINE_DEBUG

#include "Engine.hpp"
#include "core/Log.hpp"
int main()
{
  EngineConfig config;

  config.width = 480;
  config.height = 270;
  config.vsync = true;
  config.title = "Sandbox Game";
  config.screenMode = ScreenMode::Windowed;

  Engine engine(config);

  auto initResult = engine.init();
  if (!initResult.ok())
  {
      Log::error("Failed to initialize engine: {}", initResult.error());
      return -1;
  }

  engine.run();
}