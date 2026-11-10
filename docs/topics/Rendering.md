# Rendering

## Model

Rendering is split into scene-to-command conversion and backend rendering.

1. `RenderSystem` reads world transform and sprite components.
2. It creates render commands.
3. `Renderer` batches, sorts, uploads, and draws.

## Current Features

- Camera-based sprite culling before command submission.
- Sorting by layer, shader, and texture.
- Streaming instance uploads with reusable buffers.
- Ping-pong instance VBO strategy.

Sprite submission combines gameplay visibility with the chunk rendering gate.
Debug collider/zone submission respects physics and shape gates; see [Chunks](Chunks.md).

## Shutdown ownership

Runtime shuts down scripts, destroys scene data, clears UI caches, releases its
shader references and resource cache, then shuts down Renderer before destroying
the window/context. Renderer borrows the debug shader ID and never deletes it.
Client-held shared GPU resources must also be released while their context exists;
clearing the engine cache cannot revoke external shared ownership.

## Rules

- Renderer should not query Scene directly.
- Renderer should not know about Lua.
- GPU resources stay in Renderer.

## Update This File When

- Command shape, culling, batching, upload strategy, texture/shader handling, or camera behavior changes.

## Deep Reference

- `docs/RENDERING_CURRENT.md`
- `docs/OPTIMIZATIONS_CURRENT.md`
