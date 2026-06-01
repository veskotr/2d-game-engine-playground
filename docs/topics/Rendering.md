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

## Rules

- Renderer should not query Scene directly.
- Renderer should not know about Lua.
- GPU resources stay in Renderer.

## Update This File When

- Command shape, culling, batching, upload strategy, texture/shader handling, or camera behavior changes.

## Deep Reference

- `docs/RENDERING_CURRENT.md`
- `docs/OPTIMIZATIONS_CURRENT.md`
