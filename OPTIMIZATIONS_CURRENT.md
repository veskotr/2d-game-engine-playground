# Optimization Work Completed

This document summarizes the optimization work that has been implemented so far.

## 1. Render Culling

What changed:
- off-screen sprites are skipped before render submission
- visibility uses camera viewport bounds and conservative world-space sprite bounds

Why:
- prevents wasted CPU work on invisible sprites

Files:
- `EngineModules/Systems/src/RenderSystem.cpp`
- `EngineModules/Systems/include/sle/engine/Context.hpp`
- `EngineModules/Systems/src/Runtime.cpp`
- `EngineModules/Renderer/include/sle/renderer/Camera2D.hpp`
- `EngineModules/Renderer/src/Camera2D.cpp`

## 2. Spawn and Death Controls for Stress Tests

What changed:
- script-side active child cap
- script-side spawn budget per second
- one-key clear using `C`
- script-side child counting
- child destruction API

Why:
- keeps stress tests bounded and reproducible

Files:
- `EngineModules/Scripting/include/sle/scripting/ScriptApi.hpp`
- `EngineModules/Systems/include/sle/engine/ScriptApiImpl.hpp`
- `EngineModules/Systems/src/ScriptApiImpl.cpp`
- `EngineModules/Scripting/src/LuaBindings.cpp`
- `assets/scripts/player_move.lua`

## 3. Logging Overhead Reduction

What changed:
- status logging is aggregated once per second
- script supports log levels: debug, info, warn, silent
- per-burst spam was removed

Why:
- reduces IO overhead during stress runs

Files:
- `assets/scripts/player_move.lua`

## 4. Transform Update Optimization

What changed:
- recursive transform traversal was replaced by iterative DFS
- dirty-aware recompute is preserved
- clean leaf branches short-circuit

Why:
- reduces hierarchy traversal overhead at large entity counts

Files:
- `EngineModules/Systems/include/sle/engine/TransformSystem.hpp`
- `EngineModules/Systems/src/TransformSystem.cpp`

## 5. Render Submit CPU Optimization

What changed:
- direct 2D matrix construction in the render submit path
- persistent staging vectors for batch assembly
- deterministic batch ordering by layer, shader, texture
- reduced per-frame allocations

Why:
- lowers CPU time spent preparing render commands

Files:
- `EngineModules/Systems/src/RenderSystem.cpp`
- `EngineModules/Renderer/include/sle/renderer/Renderer.hpp`
- `EngineModules/Renderer/src/Renderer.cpp`

## 6. GPU-Focused Streaming Improvements

What changed:
- ping-pong instance VBOs
- dynamic instance buffer growth
- orphan + stream upload path

Why:
- reduces driver stalls and removes fixed-capacity bottlenecks

Files:
- `EngineModules/Renderer/include/sle/renderer/Renderer.hpp`
- `EngineModules/Renderer/src/Renderer.cpp`

## 7. Measurement Harness

Current state:
- per-phase timing logs are already in `Runtime`
- this is the foundation for comparing future changes

Files:
- `EngineModules/Systems/src/Runtime.cpp`
