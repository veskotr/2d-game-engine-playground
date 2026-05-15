# Rendering Current State

This document describes how the rendering path currently works.

## Responsibilities

Rendering is split into two layers:

1. `RenderSystem` converts world-space scene data into render commands.
2. `Renderer` batches and flushes those commands to OpenGL.

The renderer does not know about Lua or scene ownership. It only consumes render commands.

## RenderSystem

`RenderSystem` walks entities that have:
- `WorldTransformComponent`
- `SpriteRenderer`

For each visible entity it creates a `QuadCommand`.

Current behavior:
- culls sprites against the camera before submit
- builds a model matrix from world transform data
- copies sprite color, UV, layer, shader, and texture into the command
- submits the command to the renderer

## Visibility Culling

Culling is done before render submission.

The current test is conservative and uses:
- camera viewport size
- camera position
- sprite world bounds derived from scale and rotation

This reduces render submission cost when sprites are off screen.

## Renderer

`Renderer` owns the OpenGL objects and the batch upload path.

Current features:
- static quad vertex/index buffers
- per-frame batch collection
- sorting by layer, shader, and texture
- reusable batch staging vectors
- reusable instance staging buffers
- ping-pong instance VBOs
- dynamic instance buffer growth
- orphan + stream upload path

## Batch Model

Each draw batch is keyed by:
- layer
- shader ID
- texture ID

That allows the renderer to group similar sprites and reduce state changes.

## GPU Streaming Strategy

The current implementation uses a streaming approach because sprite counts can get very large in stress tests.

Key points:
- instance data is staged on the CPU in reusable memory
- uploads use stream/orphan behavior to reduce sync stalls
- double-buffered instance VBOs reduce GPU/CPU contention
- capacity grows automatically instead of hard-failing at a fixed instance count

## Current Performance Story

The engine is still CPU-driven, but the render path now avoids a number of common bottlenecks:
- off-screen sprites are culled early
- batch state is grouped more efficiently
- render data is staged without repeated allocations
- GPU uploads are streamed instead of forcing a stall-prone path

## Relation to the Recent Work

The render-related implementations currently in the repo are:
- render culling
- render submit CPU reductions
- GPU streaming improvements

Together they make large sprite stress tests possible without immediately hitting GL errors or obvious allocation overhead.
