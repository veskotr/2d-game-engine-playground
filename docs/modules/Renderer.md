# Renderer Module

## Responsibility

OpenGL rendering backend, GPU resources, render commands, batching, and camera math.

## Owns

- `Renderer`
- `Shader`
- `Texture`
- `TextureRegion`
- `RendererCommand`
- `Camera2D`
- OpenGL debug helpers

## Dependencies

- Core
- Platform/OpenGL dependencies

## Important Paths

- `EngineModules/Renderer/include/sle/renderer/`
- `EngineModules/Renderer/src/`
- `EngineModules/Renderer/CMakeLists.txt`
- `docs/topics/Rendering.md`

## Rules

- Renderer consumes commands; it does not query ECS directly.
- Renderer must not depend on Lua or entity lifecycle.
- Renderer owns its buffers/VAOs and borrows the externally supplied debug shader
  program ID. Shader resource owners delete programs; Renderer must not double-delete.

## Update This File When

- Renderer public APIs, command formats, batching, texture/shader behavior, or camera behavior changes.
