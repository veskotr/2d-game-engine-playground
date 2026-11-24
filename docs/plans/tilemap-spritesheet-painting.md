# Tilemap and Spritesheet Painting Implementation Plan

**Status:** Sessions 1–7 implemented. Session 7 automated validation passed. The fresh-project walkthrough and the Session 4 visual comparison remain open.

**Goal:** Import a PNG, slice it into reusable sprites, generate tile colliders from visible pixels, and paint a layered map that plays the same in the editor and a relocated game export.

**Architecture:** A spritesheet references one project texture and owns named pixel rectangles. A tileset references those regions and stores shared collision per tile. A scene owns sparse tile layers. Systems converts visible chunks into render commands and manages tile fixtures through Physics. The editor owns slicing, collision generation, painting, and undo.

**Tech stack:** C++20, Box2D, OpenGL, Kotlin Compose Desktop, JNI, JSON, CTest and JUnit.

**Design basis:** The workflow above and the native tilemap direction in [Editor game-making workflows](editor-game-workflows.md#phase-5--2d-content-tools). This document carries the detailed design and the implementation sequence together.

## Resume across sessions

Read [repository instructions](../../AGENTS.md), [constraints](../CONSTRAINTS.md), and this plan. Check Git status and the handoff at the bottom. Implement the first unchecked session or finish its open checks. Use focused tests, then record commands, results, and the next starting point. Keep existing workspace edits intact. Each numbered session can span more than one conversation.

## Design and defaults

- **Spritesheet.** Grid slicing takes cell width and height, X/Y margins, and X/Y spacing in pixels. Preview over a checkerboard with zoom and pan. Ignore partial edge cells and optionally skip fully transparent cells, enabled by default. Draw and resize manual rectangles, rename regions, and reuse the same image for both sprites and maps. Keep existing IDs when changing a region's name or bounds. Reslicing shows which referenced regions would disappear before applying. Explicitly replacing or deleting a referenced region requires review and leaves an identifiable missing reference if the user proceeds.
- **Tileset.** Create one from selected regions. Use one positive tile width and height per map grid. A tile can use a differently sized source rectangle, anchored at the cell's lower-left with one project-consistent pixel-to-world conversion. Save stable tile IDs, a display name, region reference, and collision mode. Use project UUIDs for asset references so a rename or move keeps maps usable. Persist versioned formats and validate dimensions, IDs, and geometry on load.
- **Collision.** Tile modes are None (default), Full Cell, and Custom. **Generate from Alpha** creates Custom geometry and provides an overlay before Apply. Use source pixels with alpha at or above an adjustable threshold, default 128/255. At zero simplification, follow pixel boundaries and preserve holes and disconnected islands. Any simplification must preserve topology and stay within the shown tolerance. Corner-touching opaque islands stay separate. Save the approved polygons in tile-local pixel coordinates. Fully transparent input produces no fixtures. Regeneration is explicit so editing or replacing a texture cannot silently change gameplay. Batch apply to selected tiles in one undo step. Provide manual vertex editing and a reset to None or Full Cell.
- **Tile layers.** Store cells by signed grid coordinate in sparse 32×32 chunks. Use floor division for negative coordinates. A cell references a stable tile ID. Each layer has stable identity, name, visibility, lock, draw order and collision enable. Visibility controls drawing. Lock controls authoring. Collision enable controls fixtures independently. The first version uses an orthogonal, world-axis-aligned grid with a movable origin. Keep tile data on the layer and use reusable fixtures and render batches per chunk.
- **Paint workspace.** Dockable palette with tileset selection, thumbnail search, multi-tile selection and current stamp preview. Layer list supports create, rename, reorder, show/hide and lock. The viewport shows the grid, hovered cell, ghost stamp and optional collider overlay. Provide brush, eraser, rectangle, flood fill, eyedropper, and selection with copy, move and paste. Dragging interpolates crossed cells so fast strokes have no gaps. Escape cancels pending edits, and changing tools or documents clears previews. A completed stroke, fill, or move is one undo action. Keyboard shortcuts use the existing command registry and respect text focus.
- **Fill and stamps.** Four-way contiguous fill matches the starting tile. Filling empty space requires an explicit rectangular selection to serve as bounds. Preview edits before applying. Multi-tile stamps preserve layout and empty cells leave the destination alone. Erase is explicit. Clipboard and eyedropper use stable tile IDs. Show a clear error for a stamp whose tileset is unavailable on the active layer.
- **Scope.** Ship the authoring workflow above in stages. Terrain autotiling, animated tiles, non-orthogonal grids, and converting existing Tiled maps into native layers are follow-ups. Existing Tiled scenes must continue to load through TiledMapLoader.

## Files and ownership

| Area | Starting points |
| --- | --- |
| Project assets | Editor `project/assets/ProjectAssetOperations.kt` and `ProjectAssetControls.kt` for managed authoring. Systems `ProjectRuntime.cpp` and `GamePackage.cpp` for dependency resolution and export. |
| Scene and rendering | New tile data under `EngineModules/Scene/include/sle/scene/`. Extend Systems `SceneLoader.cpp`, `ComponentRegistry.cpp`, `EntityAuthoring.cpp`, `RenderSystem.cpp`, and `ChunkSceneRuntimeState.cpp` at the relevant boundaries. Reuse Renderer `TextureRegion.hpp`. |
| Physics | New tile fixture group support in `EngineModules/Physics`, called by Systems. Keep Box2D types out of saved scene and tileset data. |
| Editor | Add slicer, tileset tools and palette under `tools/editor/src/main/kotlin/sle/editor/`. Reuse `model/SceneDocument.kt`, `model/History.kt`, `viewport/tools/ViewportTool.kt`, and `ui/ViewportToolControls.kt`. Extend `tools/editor-native/EditorBridge.cpp` for tile edits and preview. |

## Session 1 — Slice and reuse spritesheet regions

- [x] Add an editor test for a grid with unequal margins and spacing, a partial edge cell, a transparent cell, and stable region IDs after rename and resize.
- [x] Add versioned spritesheet data and managed project asset creation. Persist source texture UUID, region ID, name, and top-left pixel rectangle. Convert to renderer coordinates at one boundary. Reject out-of-image rectangles.
- [x] Build the grid preview and manual rectangle editor, with a named region picker that can assign a region to a sprite. Keep existing numeric sprite regions readable. Surface broken references with the asset and region name or ID.
- [x] Run editor tests. Open a padded sheet, save it, reopen it, and use a named region on a sprite. Rename the region and confirm the sprite still points to it.

## Session 2 — Load and render native tile layers

- [x] Add `integration_tilemap_scene_path` for sparse save/load, negative coordinates crossing chunk boundaries, stable draw order, UUID asset references, and diagnostics for missing IDs. Include an existing Tiled fixture in the regression run.
- [x] Add versioned tileset data with texture dependencies through the spritesheet, and scene tile layers with deterministic serialization. Start with one tileset per layer. Add dependency traversal from map to tileset to spritesheet to image for runtime and package loading.
- [x] Feed visible tile chunks through RenderSystem using existing sprite render commands and texture batching. Build or update chunk render data after a committed edit. Render safe missing-tile placeholders in the editor and report missing assets during export. Add a windowed pixel test with layers, negative coordinates, and hide/show.
- [x] Load a hand-authored two-layer fixture in editor Play and standalone. Confirm both render the same tiles from a different working directory.

## Session 3 — Paint the first map

- [x] Add editor tests for coordinate conversion, sparse paint/erase deltas, locked layers, undo and redo, and cancellation with no document revision.
- [x] Wire tile layer creation and tile palette selection through normal editor commands. Add grid and stamp previews plus brush, erase, rectangle and eyedropper. Apply each gesture as a single before/after cell delta in scene history. Keep an in-progress preview separate from saved scene data.
- [x] Route viewport input through the existing tool dispatcher. Cancel pending edits on Escape, focus loss, or document change. Prevent tools from editing during Play. Undo and redo must update both the authoring viewport and saved scene.
- [x] Paint across a chunk boundary at two zoom levels, erase, pick a tile, undo and redo, save and reopen. Switch to another document during a drag and verify neither document receives a partial stroke.

## Session 4 — Generate and edit tile collision

Implementation order:

1. Exercise zero-tolerance generation in `TileCollisionTest` with source alpha
   arrays in image row order and convex fixture vertices in lower-left tile-local
   pixels. Verify coverage and total area as well as fixture count for opaque
   rectangles and disconnected components.
2. Add the generator and shared validation in the editor model. Validate
   geometry after conversion to physics units using the existing Box2D v2.4.1
   polygon checks as the reference. Check simplified contours for changes to
   topology before accepting their partition.
3. Extend the tileset reader and writer and the native tileset parser together.
   Existing tile records without collision data default to None. Stage changes
   in the tileset editor and commit each batch as one history entry.
4. Run the focused editor tests and full editor suite. Reopen a saved tileset
   with solid, sloped, perforated, and decorative tiles and compare the overlays.

- [x] Add editor tests for completely transparent and opaque tiles, a stair or slope silhouette, a ring with a hole, diagonal contact, separated islands, antialiased edges and deterministic generation. Check that default threshold includes alpha 128 and excludes 127.
- [x] Trace the alpha mask into outlines, validate simplification, and partition the solid area into convex pieces that satisfy the pinned Box2D vertex and size limits. Preserve holes and islands. On invalid output, leave prior geometry in place and show a diagnostic. Show the generated fixtures over the original pixels with a project-scale preview.
- [x] Add the collision editor with None, Full Cell, Generate from Alpha, manual vertex editing, and batch apply. Validate manually edited shapes with the same rules. Store geometry in the tileset, with asset-local undo. Verify regeneration replaces geometry only on Apply and undo restores the previous shape and mode.
- [ ] Reopen the tileset and confirm the overlay matches the saved geometry for solid, sloped, perforated and decorative tiles.

## Session 5 — Run tile collision in physics

- [x] Add `integration_tilemap_collision_path` covering a dynamic body on full tiles and a slope, passage through a transparent opening, no fixtures for decorative tiles, chunk suspension/resume and teardown. Test repeated rebuilds and continuous travel across adjacent solid tiles and chunk boundaries.
- [x] Build static fixtures from saved geometry, using the same cell and region transforms as rendering. Coalesce adjacent Full Cell tiles within a chunk. For custom geometry, use the convex pieces and verify internal seams with the movement regression. Replace only affected fixture groups outside a physics step. Keep fixture count and ownership bounded by active chunks and release groups on teardown.
- [x] Integrate with camera-driven chunk activity. Keep a physics margin around the active region for approaching bodies, and validate an approaching body can reach a chunk boundary without falling through the floor. Respect layer collision enable through suspend and resume. Expose the actual fixtures in the existing physics debug view.
- [x] Verify the painted map from Session 3 with a dynamic player body. Confirm physics debug geometry lines up with the editor preview at the configured pixel scale.

## Session 6 — Finish the paint workspace

- [x] Add editor tests for four-way bounded fill, a fill crossing chunk edges, multi-tile stamps, overlapping selection moves, undo of a paste, and layer lock changes. Reject a paste with a missing tile or incompatible tileset before touching cells.
- [x] Add searchable palette, multi-tile stamp preview, fill, and rectangle selection with copy, move and paste. Keep click and drag targets clear. Show fill bounds, selection and locked-layer status directly in the viewport. Preserve selections and active layer per document where appropriate.
- [x] Finish layer controls and shortcut bindings. Document each tool with a short tooltip. Make a typical workflow achievable immediately after creating a tile layer, with the palette focused on a compatible tileset.
- [x] Walk through making a two-layer room using every tool. Use fast diagonal strokes, fill a bounded background, move overlapping cells, and check independent history across two open scenes.

## Session 7 — Export, compatibility and onboarding

- [x] Extend package and project Play regressions with a map, spritesheet, tileset and texture. Verify scene reopening after an asset move and a clear export failure with a missing dependency. Verify compatibility with existing Tiled scenes and numeric sprite regions.
- [x] Add a small example room and a guide that starts from PNG import and ends with a paintable map and collision. Include solid, sloped, perforated and decoration tiles. Update affected scene, project and editor documentation and bump engine/editor feature versions per CONTRIBUTING.
- [x] Run the Windows Debug smoke and integration suites, including windowed rendering with graphics available. Build the Release bundle, run the editor distribution smoke, and play the exported room from an unrelated working directory after moving the output folder. Record what ran and any limitations.

## Validation commands

From the repository root in the supported Windows toolchain, configure with `cmake --preset debug`. Build each new test target before running it. Use the same pattern for subsequent native targets.

```powershell
cmake --build build/debug --config Debug --target integration_tilemap_scene_path
ctest --test-dir build/debug -C Debug -R '^integration_tilemap_scene_path$' --output-on-failure
cmake --build build/debug --config Debug --parallel
ctest --test-dir build/debug -C Debug --output-on-failure
cmake --preset release -DSLE_BUILD_EDITOR=ON
cmake --build build/release --config Release --target sle_runtime_bundle sle_editor_native --parallel
```

From `tools/editor` run the editor suite and then the distribution smoke after building the Release bundle.

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat run --args=--project-distribution-smoke
```

### Completion check

From a fresh project, import the sample sheet, slice it, generate and adjust collision, paint a room, undo and redo one stroke, then save and reopen. Walk the player across a solid seam, up a slope, and through transparent space in both editor Play and a relocated export. Compare the native collision overlay against the editor preview.

## Handoff

| Field | Current value |
| --- | --- |
| Remaining checks | Fresh-project walkthrough and manual comparison of generated collision against the native overlay. |
| Implementation | Spritesheet and tile collision editors, tile painting, native tile rendering and physics, export validation, and an onboarding room. |
| Validation | See the dated session records below. |
| Decisions to revisit with evidence | Chunk size, fixture count and seam behavior after measuring the sample and a larger map. |

At the end of each session, add a dated entry here with the change or commit, exact validation commands and outcomes, remaining checks, and the next task. Leave any unchecked acceptance criteria visible.

### 2026-11-23 — Session 1

- Added the spritesheet dialog for registered textures. Grid slicing previews complete cells with independent X/Y margins and spacing. It skips fully transparent cells by default. Manual mode supports drawing and entering rectangles, and it retains IDs when renaming or resizing saved regions.
- Used version 1 texture sidecars as the managed spritesheet representation. The sidecar UUID identifies the source texture. Each region stores a stable UUID, name, and top-left pixel bounds. The existing scene loader applies the renderer coordinate conversion. Reslicing only adds rectangles, so it has no implicit removals to review. Explicit delete still requires confirmation.
- Added regression coverage for partial and transparent cells, invalid bounds, and a saved sprite reference surviving a rename and resize through scene reopen and packaging.
- Ran `tools/editor/gradle.ps1 test --offline --no-daemon` on Windows. The editor suite reported 411 tests, 3 skipped, and zero failures.
- Ran `tools/editor/gradle.ps1 test run --args=--asset-browser-smoke --offline --no-daemon` on Windows. The spritesheet section passed with four padded regions, a reopened sheet, a fast drag resize, and a saved sprite reference that retained its region ID. The smoke assigned the ID through the existing sprite authoring command. Earlier runs hit the 30-second watchdog during Play and produced an Intel graphics driver access violation during shutdown. The final run used the existing 90-second watchdog tier and passed the full asset-browser smoke, including Play and project reopen.
- Ran `git -c core.whitespace=cr-at-eol diff --check`. No whitespace errors were reported.
- Next task is the Session 2 scene-path integration regression.

### 2026-11-23 — Session 2

- Added version 1 `*.tileset.json` assets and native project scene layers. The tileset's spritesheet UUID resolves to the image and its existing region sidecar. The layer stores a pixel scale and origin. Draw orders are unique and nonnegative within the tile layers. Saves sort layers by draw order and cells by Y then X.
- Scene loading builds bounds and tile visuals for occupied 32×32 chunks. RenderSystem culls by camera and submits quads to existing texture batches. A committed document reload rebuilds the chunk data. Missing references in the editor produce magenta placeholders and ID-specific diagnostics. Game package validation checks all tiles in each referenced tileset and decodes the source image before export. Native tile layers use project scenes and game packages.
- Built the Windows Debug targets `integration_tilemap_scene_path`, `integration_tilemap_render_path`, `integration_game_package_path`, `integration_scene_loader_roundtrip_path`, `integration_tiled_scene_loader_path`, `integration_tiled_tile_chunk_visibility_path`, `integration_chunk_render_visibility_path`, `integration_project_play_path`, `engine_app`, and `sle_editor_native` using `cmake --build build/debug --config Debug --target … --parallel 6`. Rebuilt the two new tests with `--parallel 4` after final fixture and test changes.
- Ran `ctest --test-dir build/debug -C Debug -R '^integration_(tilemap_(scene|render)|game_package|scene_loader_roundtrip|tiled_scene_loader|tiled_tile_chunk_visibility|chunk_render_visibility|project_play)_path$' --output-on-failure`. All eight passed. `ctest --test-dir build/debug -C Debug -R '^integration_tilemap_render_path$' --repeat until-fail:3 --output-on-failure` passed all three runs. It compared standalone and embedded Play pixels after changing the working directory, and checked hide/show and missing-tile rendering.
- Ran `tools/editor/gradle.ps1 test --rerun --offline --no-daemon -Pkotlin.incremental=false`. The Windows editor suite reported 412 tests, 3 skipped, and zero failures.
- Opened a temporary copy of the [two-layer fixture](../../tests/data/tilemap/Tiles.sleproject) with `tools/editor/gradle.ps1 run --offline --no-daemon "--args=--project <temporary copy>/Tiles.sleproject"`, used F5, and sampled the Windows editor viewport. The editor logged `Loaded scene: editor_play` and displayed both atlas colors. From `tests/data`, ran `build/debug/engine_app/Debug/engine_app.exe --project C:/projects/engine/tests/data/tilemap/Tiles.sleproject --frames 5` using the executable's absolute path. It completed five frames.
- The full `cmake --build build/debug --config Debug --parallel 6` build was blocked by pre-existing event integration test calls to the two-argument `SceneManager::processPendingSwitch`, which requires three arguments on the existing branch. The Linux scene and package tests passed. The WSL windowed attempt skipped because its graphics backend could not create an OpenGL context.
- Ran `git -c core.whitespace=cr-at-eol diff --check`. No whitespace errors were reported.
- Next task is Session 3's editor regression for coordinate conversion, sparse paint/erase deltas, locked layers, undo, redo and cancellation.


### 2026-11-23 — Session 3

- Added the Scene viewport's **Tiles…** control and **T** command. The palette creates layers from registered tilesets and selects the active layer, tile and paint mode. The grid and translucent stamp use the layer origin and scale. Brush and Erase interpolate the pointer's path through grid cells, Rectangle fills its bounds, and Eyedropper picks the cell's stable tile ID.
- Kept pending cells in the viewport tool. Release records one sparse before/after delta in scene history. Escape, focus loss, switching tools or documents, entering Play, and undo or redo during a gesture clear the preview without publishing it. Committed changes use the existing native Scene preview reload. Bumped the editor version to 0.31.0.
- Ran `tools/editor/gradle.ps1 test run --args=--tile-painting-smoke --offline --no-daemon -Pkotlin.incremental=false` on Windows. The suite reported 418 tests, 3 skipped, and zero failures. The desktop smoke reported `TILE_PAINTING_SMOKE_PASS`. It drove the viewport at two zoom levels across a negative chunk boundary, sampled rendered pixels after painting, erasing, undo and redo, picked a tile, saved and reopened, checked Play input, and replaced the document during a drag. The smoke used the editor model to set up its layer and select palette modes. It saved the painted copy under `tools/editor/build/reports/tile-painting-*/`. Run the same smoke to regenerate it for the collision check.
- Ran `git -c core.whitespace=cr-at-eol diff --check`. No whitespace errors were reported.
- Next task is Session 4's regression coverage for alpha-mask collision generation.

### Session 4 start

- Added `TileCollisionTest` and a zero-simplification partition that merges
  identical opaque runs across rows into rectangles. The resulting fixtures
  cover the original mask in lower-left tile-local pixels.
- Isolated compilation with the cached Kotlin 2.4.21 compiler and execution
  with JUnit 4.13.2 passed all seven focused tests. Continue with contour
  simplification and validation at project scale before wiring up persistence
  and the collision editor.

### Session 4 implementation and validation

- Added the tileset collision dialog under Asset Browser details. The dialog
  stages edits, previews polygons over source pixels, and writes one atomic
  asset update per Apply. Undo and redo operate within the open tileset.
  Unpreviewed vertex edits block Apply. The preview defaults to the largest
  matching layer scale in the open scene and validates against those layer
  scales as well as the entered preview scale.
- Positive tolerance simplifies a single simple contour when its convex hull
  fits the tolerance and fixture vertex count. Masks with holes, islands,
  or unsuitable hulls retain their exact pixel partition.
- The native reader accepts optional version 1 collision records, checks source
  region bounds, and validates fixture geometry at the enabled layer's scale.
  Fixture attachment and simulation are Session 5. Bumped the editor to 0.32.0.
- Ran `bash ./gradlew test --offline --no-daemon -Pkotlin.incremental=false`
  in `tools/editor`. All 431 tests passed. The collision tests cover batch
  save, undo and redo, stale files, malformed shapes, and reopening solid,
  sloped, perforated, and decorative collision data.
- Rebuilt the Linux scene and package integration targets and ran
  `ctest --test-dir /tmp/sle-tilemap-build -R '^integration_(tilemap_scene|game_package)_path$' --output-on-failure`.
  Both passed, including rejection of a fixture that would collapse at
  the authored layer scale. The windowed tilemap renderer test skipped
  because GLX failed to create a context.
- Opened the temporary fixture project and confirmed the collision action
  appeared in Asset Browser details. The desktop run did not yield a
  verified comparison of the four reopened overlays. The saved geometry
  for those four cases is covered by the automated round-trip test.

### Session 5 implementation sequence

1. Add the headless collision integration target using the existing project fixture
   for authored geometry and in-memory cells for traversal and chunk lifecycle.
2. Retain validated tile collision data in the scene and synchronize static bodies
   per active layer and chunk before simulation. Use the simulation camera bounds
   plus one chunk of padding in each direction. Merge full cells into rectangles.
3. Draw native fixture outlines through the existing runtime debug toggle. Run
   the focused collision, scene and package tests and check the editor viewport.

### Session 5 validation

- Rebuilt the relevant Linux targets in `/tmp/sle-tilemap-build`. Ran
  `ctest --test-dir /tmp/sle-tilemap-build -L physics -LE windowed --output-on-failure`
  and `ctest --test-dir /tmp/sle-tilemap-build -R '^integration_(tilemap_scene|camera_scene|game_package)_path$' --output-on-failure`.
  All ten tests passed.
- Ran `cmake --build build/debug --config Debug --target integration_tilemap_collision_path integration_tilemap_render_path --parallel 4`
  and `ctest --test-dir build/debug -C Debug -R '^integration_tilemap_(collision|render)_path$' --output-on-failure`
  on Windows. Both tests passed. The windowed test plays the painted fixture
  map in the embedded runtime, verifies terrain activation on the first
  physics frame after a camera jump, checks the dynamic player's slope
  response, and reads framebuffer pixels at the expected overlay location.
  Validation used the automated viewport test.

### Session 6 implementation sequence

1. Cover bounded fill, pattern stamping, selection moves, paste validation and
   independent histories in the existing editor tile test suite.
2. Keep selection and active layer on each scene workspace. Use the selection as
   fill bounds when present. Filling empty space requires a selection. Build
   moves and pastes as one sparse edit through the existing tile history.
3. Extend the tile tool, overlay and palette. Use viewport-scoped commands and
   run focused tests and the two-layer paint smoke.

### Session 6 validation

- Rebuilt the Windows JNI bridge with
  `cmake --build build/debug --config Debug --target sle_editor_native --parallel 4`.
  Ran `tools/editor/gradle.ps1 test run --args=--tile-painting-smoke --offline --no-daemon -Pkotlin.incremental=false`.
  The editor suite recorded 433 passing tests and three skips, with zero failures.
- The Windows desktop smoke passed. It used the existing brush, eraser,
  rectangle, and eyedropper, then selected and filled a room, moved the
  selection over itself, reused it as a stamp, pasted it, and checked undo
  and redo after switching scene files. It opened Tile Palette from the
  Scene toolbar. Unit coverage includes fast subcell diagonal input, missing
  and incompatible paste tiles, layer locking, and dock-layout migration.
- The editor restores recent saved scene timelines when the on-disk scene
  still matches the saved revision. Active layers and selections are
  workspace state and do not change the saved scene format.

### 2026-11-24 — Session 7

- Folder export now applies the package loader's tile dependency checks to the
  staged project. Package tests load the mounted tile scene and reopen the
  example after moving its PNG and sidecar together. Embedded Run Project
  compares tile pixels with standalone. The native suite also covers legacy
  Tiled scenes and numeric sprite UVs in preview and project Play.
- Added the [tilemap room and walkthrough](../../examples/tilemap_room/README.md).
  Built with engine 0.33.0 and editor 0.32.0, already set by the tilemap work.
  Updated five native event test files to call the current scene-switch API
  so the full Debug build could run.
- Ran `cmake --build build/debug --config Debug --parallel 4` and
  `ctest --test-dir build/debug -C Debug --output-on-failure` on Windows.
  All 82 tests passed, including 17 windowed tests.
- Ran `cmake --preset release -DSLE_BUILD_EDITOR=ON` and
  `cmake --build build/release --config Release --target sle_runtime_bundle sle_editor_native --parallel 4`.
  The Release bundle and bridge built successfully.
- Ran `tools/editor/gradle.ps1 test run --args=--project-distribution-smoke --offline --no-daemon -Pkotlin.incremental=false`.
  JUnit recorded 433 passes and three skips. The distribution smoke passed.
  The relocated room ran eight frames from the reports directory after its
  source PNG and sidecar were moved away. A second export reported the missing
  spritesheet UUID and published no delivery.
- Ran `git -c core.whitespace=cr-at-eol diff --check` with no errors.
- The manual fresh-project walkthrough, including walking a body across the
  sample terrain and comparing the generated collision preview with the native
  overlay, remains open. The automated windowed test exercises a dynamic body
  on a slope and samples the native overlay.
