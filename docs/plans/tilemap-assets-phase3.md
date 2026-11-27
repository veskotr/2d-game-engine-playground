# Tilemap Assets and Placement Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task by task.

**Goal:** Author reusable tilemap assets and place them through ordinary scene entities, with matching rendering, collision, painting, save, undo, conversion, and export behavior.

**Architecture:** Scene components contain the map asset reference and sorting offset. Systems resolves asset data and owns placement runtime state; Renderer consumes commands. Each map has one grid and all layers start at map-local `(0, 0)`.

**Tech stack:** Existing C++20 ECS/Systems, nlohmann JSON, Box2D, Kotlin Compose editor and JUnit/CTest harnesses; no new dependencies.

**Spec:** [T1 and phase 3](polish-tiles-cameras-launcher.md#t1-tilemap-asset-and-placement), clarified by the user on 2026-11-27: only the tilemap moves; layers share its origin, or `(0, 0)` relative to it.

## Status and constraints

Checkpoint requested by the user on 2026-11-27. Phase 3 is partially implemented;
unit/native checks pass, but the desktop smoke fails and a confirmed shared-map
save issue remains. Read [the resume handoff](tilemap-assets-phase3-handoff.md)
before continuing. Do not treat the unchecked acceptance criteria as complete.
The user explicitly requested committing and pushing the current working tree,
including existing phase 0–2/editor and workflow changes.

- Use the existing T1 JSON shape: `sle-tilemap`, version 1; map-level grid, tileset key table, streaming settings, and layers whose cells contain `x`, `y`, `set`, `tile`.
- The `Tilemap` component contains `asset` and `sortingOffset`; Transform owns placement translation. No layer origin, rotation, scale, or independent entity placement.
- Multiple tilesets can contribute cells to one layer. Tile identity is the pair of map-local tileset key and tile UUID.
- Map translation affects render commands, culling, collision, selection, preview bounds, and inverse coordinates for painting. Rotation and scale are locked in the editor and ignored with a runtime warning, as specified in T1.
- Use live hierarchy-composed translation. Do not rotate or scale the map geometry through its own or inherited linear transform. Cover parented placement explicitly.
- Keep legacy inline `tileLayers` runtime loading and Tiled `.tmj` behavior intact.
- Shared map edits affect all placements. The scene workspace history owns map and scene edits; scene save includes dirty maps.
- Phase 4 owns the new palette window, focus streaming, and chunk geometry performance work. Phase 3 uses existing palette controls adapted to maps. Persist streaming configuration, default new maps to `always`, and explicitly diagnose unsupported focus streaming until phase 4.
- Preserve acyclic CMake dependencies; do not add Systems dependencies to Scene or Renderer.

## Current evidence

- `Scene.hpp` owns a flat `vector<TileLayer>`; `TileLayer.hpp` carries per-layer origin and single-tileset data.
- `TileLayerLoader.cpp` validates inline layers and builds render/collision chunks; `SceneLoader.cpp` stages them after successful entity linking.
- `RenderSystem.cpp` and `EntityPreviewSystem.cpp` consume the flat layers. `TileCollisionSystem.cpp` keys static bodies by layer ID and chunk and currently limits bodies around the camera.
- `SceneWorkspaceState.kt` stores inline JSON and records layer/cell changes in scene history. `TileTool.kt` and `TilePaintingControls.kt` target that representation.
- `PackagePlanner.kt` discovers inline tilesets and currently requires tileset version 1 in its saved inventory parser. Phase 2 v2 maps must export successfully as part of this work.

## Review focus

1. Repeated placement of the same map: chunk/body identity includes placement identity, and removal never removes another placement's bodies.
2. Asset or scene replacement: stale callbacks cannot mutate another project; failed staging preserves the old scene and unresolved drafts.
3. Negative coordinates, signed sorting offsets, and parented translation: render, collision, bounds, and painting agree without integer overflow.
4. Save conflict or partial file-write failure: preserve drafts and dirty flags; do not leave a saved scene referring to an absent new map.
5. Legacy origins and grids: conversion never silently shifts, rescales, drops cells, or creates independently movable layers.

## Task 1: Map asset contract and component registration

**Create:** `EngineModules/Scene/include/sle/scene/components/TilemapComponent.hpp`, `EngineModules/Systems/src/TilemapLoader.hpp`, `EngineModules/Systems/src/TilemapLoader.cpp`, `tools/editor/src/main/kotlin/sle/editor/model/TilemapDocument.kt`.

**Modify:** `ComponentRegistry.cpp`, `ComponentAssets.cpp`, `SceneLoader.cpp`, `SceneAuthoring.cpp`, affected Systems CMake sources, and editor `EntityDocument.kt` validation as required by its native catalog contract.

**Interfaces:** `TilemapComponent` exposes the authored asset string and signed sorting offset. `detail::canonicalTilemap(const nlohmann::json&)` validates/canonicalizes asset JSON independently of GPU loading. Kotlin `TilemapDocument.parse(String)` and `serialize(): String` preserve the equivalent authored contract.

- [ ] Add failing native/editor tests for two tilesets on one layer, repeated UUIDs in different sets, deterministic round trip, negative cell coordinates, missing/duplicate set keys, duplicate cells/layer IDs, nonfinite grid values and unsupported versions/fields.
- [ ] Implement strict bounded parsers and catalog asset metadata using existing validation conventions; use map grid for cell spacing and map pixels-per-unit for atlas geometry.
- [ ] Separate authored component data from resolved runtime resources. Resolve all dependencies during scene staging and retain strict runtime versus tolerant preview diagnostics.
- [ ] Run focused model/catalog/scene loader tests, including existing inline layer and tileset v1/v2 coverage.

## Task 2: Placement runtime, rendering, and collision

**Modify:** `Scene.hpp`, `TileLayer.hpp`, `RenderSystem.cpp`, `EntityPreviewSystem.cpp`, `PhysicsSystem.cpp`, `TileCollisionSystem.hpp/.cpp`, and runtime/preview initialization call sites as needed.

**Interfaces:** Runtime placement state associates registry entity identity with resolved local map layers. Render and physics consumers receive placement identity, live translation, and sorting offset. Collision keys include placement identity, layer identity, and chunk coordinate; legacy layers remain distinguishable.

- [ ] Add failing integration coverage for two scenes sharing one asset, two instances in one scene, translated and parented placements, live movement, one placement's destruction, scene teardown, and invalid assets leaving staging unchanged.
- [ ] Resolve native maps without flattening placement identity into authored layer IDs. Keep local geometry independent of translation and use composite tileset/tile identity for collision lookup.
- [ ] Apply live placement translation consistently to render/cull bounds, preview bounds, and bodies. Use the existing PhysicsWorld body-transform API for translation-only movement without rebuilding unchanged fixtures.
- [ ] New native maps in `always` mode retain collision outside the camera view; preserve existing legacy behavior. Test off-camera collision as well as visible collision and overlapping placements.
- [ ] Clamp or reject invalid effective draw orders at an explicit checked boundary; test signed offsets and extreme values.
- [ ] Verify runtime warning/ignored rotation and scale, and matching render/collision results. Never retain component references across structural mutations.
- [ ] Build changed native targets and run tilemap scene, collision, render and physics regression integration executables.

## Task 3: Editor creation, placement, and shared-map editing

**Create:** focused map asset session/control files alongside the existing tileset session and project asset controls; keep JSON/I/O out of widgets.

**Modify:** `SceneWorkspaceState.kt`, `ProjectDocumentControls.kt`, `ProjectAssetControls.kt`, `AssetBrowserPanel.kt`, `ToolCommands.kt`, `CommandMenus.kt`, hierarchy/viewport context-menu routes, `TilePaintingControls.kt`, `TileTool.kt`, and preview asset resolution.

**Interfaces:** One asset draft per project asset UUID, with source bytes for conflict detection. Selected placing entity identifies the active map and local paint coordinates. Existing scene history records map changes and placement changes together; all instances observe the same draft.

- [ ] Add failing model/session tests for creating a map, placing an existing map twice, multi-tileset cell editing, moved-placement painting, shared draft visibility, and undo/redo across map and entity changes.
- [ ] Route Create → Tilemap through the command system in the menu bar and hierarchy/viewport context menus. Pick new or existing asset, create/select its entity, and open existing tile controls.
- [ ] Show map layers in the palette only; expose no layer transform. Lock rotation/scale controls for map placements using existing property renderer patterns.
- [ ] Make tile picking, paint, erase, fill, stamps, selections and collision preview operate in selected map-local coordinates with a tileset-qualified brush.
- [ ] Save dirty maps alongside scene saves, checking source bytes and project/asset identity before writes. Write new maps before scenes that reference them; retain unsaved state and report exact failures if a later write fails.
- [ ] Test conflicting external edits, missing/deleted assets, scene/project switches, stale callbacks, cancelled dialogs, save/reopen, and existing tile-authoring workflows.

## Task 4: Explicit legacy conversion

**Create:** a focused conversion helper beside `TilemapDocument.kt`, with tests beside other model tests.

**Modify:** scene document opening/commands to offer conversion without forcing it.

**Interface:** A pure conversion result contains the new map document and placing entity, or an actionable incompatibility diagnostic. Conversion does not write files until validated and accepted.

- [ ] Test zero-origin layers and a shared nonzero origin: move that shared origin to the placing entity and write no per-layer origins.
- [ ] For different origins, rebase cell coordinates only when offsets are exactly representable on the single shared grid and geometry remains unchanged. Reject collisions introduced by rebasing and integer overflow.
- [ ] Reject incompatible cell sizes/scales or nonintegral offsets with a precise explanation; retain the original inline scene. Do not create separately movable layers or silently resample.
- [ ] Commit conversion to history and disk through the normal map/session save path, with cancellation/failure preserving the legacy scene. Verify undo and save/reopen.

## Task 5: Packaging, end-to-end verification, and docs

**Modify:** `PackagePlanner.kt`, project document asset traversal, native project asset resolution/package handling where required, and the standalone scene package guard in `files/PrefabPackages.kt`.

**Tests:** extend `integration_tilemap_scene_path.cpp`, `integration_tilemap_collision_path.cpp`, `integration_tilemap_render_path.cpp`, relevant package integration/editor tests; add a placement smoke using temporary project fixtures.

- [ ] Add failing export tests for scene → map → tilesets v1/v2 → atlas texture/sidecar dependencies; verify dependency closure and useful missing-reference errors.
- [ ] Export and relocate a project with two scenes sharing a map; run it without relying on original source paths. Ensure unsupported standalone scene packaging fails clearly rather than dropping map dependencies.
- [ ] Run focused Kotlin tests from `tools/editor` with `./gradlew test --no-daemon --console=plain --tests '*Tilemap*'`, then the full editor unit suite after integration changes.
- [ ] Build relevant native targets in the available configured build directory before anchored CTest tilemap/package runs. Use Windows `build/debug -C Debug` or the configured Linux build as appropriate; do not report commands as executed before running them.
- [ ] Run the placement/conversion desktop smoke plus existing fresh-tilemap and tile-painting smokes; check Dark/Light, scene switching, hierarchy selection, Inspector and translation gizmo behavior.
- [ ] Update `docs/topics/Scenes.md`, `docs/topics/EditorPropertySchema.md`, affected module docs, `tools/editor/README.md`, and the HTML tilemap guide using its skill. Record actual commands/results and unavailable checks here.

## Acceptance

- Two scenes and two entities in one scene reference one shared map; edits survive save/reopen and export.
- Moving only the placing entity moves every layer's rendering and collision together, including parented translation and runtime movement.
- Every layer has map-local origin `(0, 0)`; a cell may select any tileset in the map table.
- Compatible legacy scenes convert without visual/physics changes; incompatible ones remain usable in their original representation with an explanation.
- Undo, external conflict handling, project switching, missing assets, placement deletion and scene teardown preserve ownership and data integrity.
- Phase 4 streaming/palette redesign remains explicitly deferred; actual validation is recorded before completion is claimed.
