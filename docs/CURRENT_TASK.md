# Current Task

## Active Task

Continue the chunked-world implementation after the first `Tiled` scene-integration slice landed.

## Status

In Progress.

## Scope

- Keep the planning docs in sync as each implementation slice lands.
- Build on the new `tiledMap` scene hook and `TiledMapLoader`.
- Move toward chunk-backed world runtime ownership without overcommitting to final streaming behavior too early.
- Preserve the existing scene-loader and integration-test behavior while expanding the map pipeline.
- Add small reusable chunk runtime primitives before wiring them into higher-level systems.

## Decisions

- Every completed implementation slice should update the relevant docs immediately.
- Treat the current `Tiled` work as `Stream C1` progress, not as completion of the full Tiled/chunk/pathfinding feature block.
- Keep chunking camera-driven and optional to map-backed scenes only.
- Land chunk runtime in small vertical slices: math/window primitives first, events/activation integration second.
- Keep chunk-driven activation configurable: global engine mode first, per-entity override second.

## Files Changed For This Task

- `docs/CURRENT_TASK.md`
- `docs/TILED_CHUNKED_WORLD_PLAN.md`
- `EngineModules/Systems/include/sle/engine/TiledMapLoader.hpp`
- `EngineModules/Systems/src/TiledMapLoader.cpp`
- `EngineModules/Systems/src/SceneLoader.cpp`
- `EngineModules/Systems/CMakeLists.txt`
- `tests/data/maps/test_object_layer.tmj`
- `tests/data/scenes/test_tiled_scene.json`
- `EngineModules/Systems/include/sle/engine/ChunkManager.hpp`
- `EngineModules/Systems/include/sle/engine/ChunkActivityTracker.hpp`
- `EngineModules/Systems/include/sle/engine/ChunkActivationController.hpp`
- `EngineModules/Systems/include/sle/engine/ChunkMapComponent.hpp`
- `EngineModules/Systems/include/sle/engine/ChunkOwnershipIndex.hpp`
- `EngineModules/Systems/src/ChunkManager.cpp`
- `EngineModules/Systems/src/ChunkActivityTracker.cpp`
- `EngineModules/Systems/src/ChunkActivationController.cpp`
- `EngineModules/Systems/src/ChunkOwnershipIndex.cpp`
- `EngineModules/Events/include/sle/events/ChunkEvents.hpp`
- `EngineModules/Core/include/sle/core/ChunkActivationConfig.hpp`
- `EngineModules/Scene/include/sle/scene/components/ChunkActivationComponent.hpp`
- `EngineModules/Core/include/sle/core/EngineConfig.hpp`
- `EngineModules/Renderer/include/sle/renderer/Renderer.hpp`
- `EngineModules/Renderer/src/Renderer.cpp`
- `EngineModules/UI/include/sle/ui/UIDocument.hpp`
- `EngineModules/UI/include/sle/ui/UISystem.hpp`
- `EngineModules/UI/src/UISystem.cpp`
- `EngineModules/Systems/include/sle/engine/Runtime.hpp`
- `EngineModules/Systems/src/Runtime.cpp`
- `assets/ui/player_distance_world_label.xml`
- `tests/integration/systems/integration_chunk_manager_window_path.cpp`
- `tests/integration/systems/integration_chunk_activity_tracker_events_path.cpp`
- `tests/integration/systems/integration_chunk_activation_controller_path.cpp`
- `tests/integration/systems/integration_tiled_scene_loader_path.cpp`
- `tests/integration/systems/integration_tiled_chunk_assignment_path.cpp`
- `tests/integration/systems/integration_tiled_chunk_map_component_path.cpp`
- `tests/integration/systems/integration_chunk_ownership_index_path.cpp`
- `tests/integration/CMakeLists.txt`

## Validation

- `cmake --build build/debug --config Debug --target integration_tiled_scene_loader_path` succeeded on June 2, 2026.
- `cmake --build build/debug --config Debug --target integration_chunk_manager_window_path` succeeded on June 2, 2026.
- `cmake --build build/debug --config Debug --target integration_chunk_activity_tracker_events_path` succeeded on June 2, 2026.
- `cmake --build build/debug --config Debug --target integration_chunk_activation_controller_path` succeeded on June 2, 2026.
- `cmake --build build/debug --config Debug --target integration_tiled_chunk_map_component_path` succeeded on June 2, 2026.
- `cmake --build build/debug --config Debug --target integration_tiled_chunk_assignment_path` succeeded on June 2, 2026.
- `cmake --build build/debug --config Debug --target integration_chunk_ownership_index_path` succeeded on June 2, 2026.
- `cmake --build build/debug --config Debug --target example_sandbox` succeeded on June 2, 2026 after the UI alignment and F3 FPS overlay slice.
- `ctest -C Debug -R integration_tiled_scene_loader_path --output-on-failure` passed on June 2, 2026.
- `ctest -C Debug -R integration_chunk_manager_window_path --output-on-failure` passed on June 2, 2026.
- `ctest -C Debug -R integration_chunk_activity_tracker_events_path --output-on-failure` passed on June 2, 2026.
- `ctest -C Debug -R integration_chunk_activation_controller_path --output-on-failure` passed on June 2, 2026.
- `ctest -C Debug -R integration_tiled_chunk_map_component_path --output-on-failure` passed on June 2, 2026.
- `ctest -C Debug -R integration_tiled_chunk_assignment_path --output-on-failure` passed on June 2, 2026.
- `ctest -C Debug -R integration_chunk_ownership_index_path --output-on-failure` passed on June 2, 2026.
- Regression check: `integration_tiled_scene_loader_path`, `integration_tiled_chunk_assignment_path`, `integration_tiled_chunk_map_component_path`, `integration_chunk_ownership_index_path`, and `integration_chunk_activation_controller_path` all passed on June 2, 2026 after the chunk metadata slice.
- Regression check: `integration_tiled_scene_loader_path`, `integration_tiled_chunk_assignment_path`, `integration_chunk_ownership_index_path`, `integration_chunk_manager_window_path`, and `integration_chunk_activity_tracker_events_path` all passed on June 2, 2026 after the chunk activation controller slice.
- Regression check: `integration_scene_loader_roundtrip_path` and `integration_scene_loader_negative_path` both passed on June 2, 2026.

## Next Steps

1. Record `Stream C1` as partially complete in the chunked-world plan.
2. Re-run the sandbox interactively to validate the world-space UI fix and the new F3 FPS overlay behavior.
3. Wire the new chunk map metadata and activation controller into the runtime/update flow for map-backed scenes.
4. Promote chunk state from test-time/controller-only logic into live scene-owned runtime state transitions.
5. Extend activation policy to chunked tile/runtime content before tilemap `A*`.
6. Keep documenting each completed slice as it lands.
