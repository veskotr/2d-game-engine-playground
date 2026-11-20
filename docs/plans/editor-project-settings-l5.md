# L5 project settings session plan

**Status:** Sessions 1–5 implemented. Each session ends with a usable workflow and focused validation. Scope comes from the [editor roadmap](editor-game-workflows.md#5-build-order-shared-base-layers-then-feature-tracks). Follow the [repository validation workflow](../../CONTRIBUTING.md#validation).

## 1. Project Settings sections and art style

**Done when** a new project can choose Pixel Art or Smooth, an existing project can change the preset, and inherited defaults agree in authoring, Play and export.

1. Extend the typed Kotlin project document and the native project loader with an optional `settings.artStyle` enum (`pixelArt`, `smooth`). Keep the version-1 document readable when settings are absent. Test absence, both values, invalid values, and save/reopen in `ProjectDocumentsTest.kt` and `integration_project_runtime_path`. Keep parser validation equivalent across both readers. Extend the same settings object in later sessions.
2. Split the existing dialog in `ProjectControls.kt` into Startup, Game and Art Style sections. Use existing L2 drawers for ordinary fields. Stage edits until Save, using the existing project save path. Add the preset to New Project. Cancel leaves the file and effective defaults untouched.
3. Use nearest filtering for Pixel Art and linear for Smooth when a project texture has no explicit L3 sampler. Absent style uses the legacy sampler. Thread the effective sampler through the existing texture resolution/cache path. Persist enough data through `GameBuild.kt`, `GamePackage.cpp` and the package loader for exported games to resolve the same sampler. Use [L3's inheritance contract](editor-asset-metadata-l3.md#2-project-filter-defaults-coordinated-with-l5).
4. In `SnapService.kt` and `SnapSettingsStore.kt`, derive pixel snapping and grid defaults from the project preset while preserving explicit overrides. Before wiring pixels-per-unit, identify the existing sprite/world conversion and choose preset values with that convention. Apply new defaults to inherited values only and preserve authored transforms and sprite sizes. Resolve texture defaults at the next scene load or Play restart.
5. Test both presets, legacy projects, explicit sampler and snap overrides, Cancel, a project switch, and relocated export. Build and run `integration_project_runtime_path`, `integration_project_play_path` and `integration_game_package_path`. Run the editor unit suite and exercise New Project and Project Settings manually.

### Session 1 implementation

- Version 1 project descriptors and package manifests carry optional art style.
  Package defaults follow the package providing the texture and explicit
  sidecar filters retain precedence.
- Sprite rendering multiplies authored world scale by authored sprite size.
  Pixel Art uses a one-world-unit grid with snapping enabled. Smooth uses a
  ten-world-unit grid with snapping disabled. Local snap fields retain their
  overrides independently. Sprite sizing and transform data are preserved.
- Project Settings stages edits until Save. Filtering updates at preview load
  or Play start. Snap defaults update immediately after Save.
- Styled source and package exports require an engine 0.26 or newer runtime
  bundle. Legacy projects can still use older bundles.

### Session 1 validation

- Built `integration_project_runtime_path`, `integration_project_play_path`,
  `integration_game_package_path`, `sle_editor_native`, and `engine_app` with
  `cmake --build /tmp/sle-l2-linux --target <targets> -j 5`.
- Ran `ctest --test-dir /tmp/sle-l2-linux -R
  '^integration_(project_runtime|project_play|game_package|package_discovery|package_launch|distribution_launch)_path$'
  --output-on-failure` with Mesa software rendering and OpenGL 4.6 overrides.
  All six tests passed, including relocated packages with both presets.
- Gradle 9.5.1 `-p tools/editor --offline --no-daemon --console=plain
  -PsleBuildDir=/tmp/sle-l5-editor-build test` passed 376 tests.
- The desktop `run --args=--project-smoke` reached the first viewport frame,
  but the File menu click timed out under WSLg. Interactive New Project and
  Project Settings checks remain open.

## 2. Collision layer names and matrix

**Done when** the editor can label the 16 existing bit positions, display named category and mask checkboxes, and enable or disable collisions for a pair of layers.

1. Add optional `settings.collisionLayers` with labels by bit slot and a symmetric 16-by-16 interaction matrix. Default missing entries to existing numeric labels and allowed pairs. Keep scene and prefab category/mask values in their existing representation. Renaming a layer changes its label while keeping its bit position.
2. Add a Collision section and use the bit labels in the existing property drawers for collision masks. Editing either half of the matrix updates the symmetric pair. Preserve unnamed bits in the Inspector and when saving.
3. In Systems, apply the project matrix as an additional filter. A pair qualifies when at least one category pair passes the matrix and both corresponding per-component mask bits. Keep authored masks intact. Audit `PhysicsSystem.cpp` and sensor/zone paths for existing filter decisions and preserve current event ordering and activity gates. Load the matrix before creating physics objects, including in project-backed Play Scene and exported games.
4. Extend `integration_physics_edge_path` and `integration_physics_zone_enter_exit_path` with legacy parity, symmetric blocking, multi-bit categories, explicit mask exclusion and allowed-pair cases. Check the existing contact dispatch regression. Verify labels and matrix persistence after save/reopen and behavior parity in package playback.

### Session 2 implementation

- Persist optional names by bit slot and store only blocked pairs. The editor
  shows the full symmetric matrix and keeps the numeric mask entry alongside
  the named checkboxes.
- Use the Box2D contact filter for solids and sensors. The runtime sets the
  matrix after scene load succeeds and before physics creates scene fixtures.
- The package that selects the main scene provides the matrix for the whole
  run. Projects with collision settings require a 0.27 runtime bundle.
- Regression coverage includes multi-bit categories, component mask exclusion,
  legacy behavior, the high bit, reopening, and both export formats.

### Session 2 validation

- Built the editor bridge, `engine_app`, and the six changed or directly affected
  CTest executables with `cmake --build /tmp/sle-l2-linux --target <targets> -j 5`.
- Ran `ctest --test-dir /tmp/sle-l2-linux -R
  '^integration_(physics_(edge|zone_enter_exit|contact_deferred_dispatch)|project_(runtime|play)|game_package|package_(discovery|launch)|distribution_launch)_path$'
  --output-on-failure` with Mesa software rendering and OpenGL 4.6 overrides.
  All nine passed, including windowed Play and relocated package playback.
- Gradle 9.5.1 `-p tools/editor --offline --no-daemon --console=plain
  -PsleBuildDir=/tmp/sle-l5-editor-build test` passed 378 tests.
- The desktop `run --args=--console-smoke` passed with the rebuilt Linux
  bridge and Mesa overrides, exercising the editor settings JNI call during
  embedded Play. A manual walkthrough of the collision settings and Inspector
  controls remains open.

## 3. Sorting layer labels

**Done when** the SpriteRenderer Inspector selects a named layer and existing sprite ordering survives label edits and project format round-trips.

1. Add optional `settings.sortingLayers` as label entries keyed by existing integer layer value. Reject duplicate values. Display an unlabeled integer when a legacy layer has no label or a label is deleted. Keep the numeric value available for assets moved between projects.
2. Add a Sorting Layers section and feed the choices into the L2 SpriteRenderer layer drawer. Use the existing property transaction for selection and undo. Keep sort priority tied to the integer in `RenderSystem.cpp`; UI list order has no effect on drawing. Defer order-in-layer until there is a demonstrated need.
3. Test duplicate rejection, rename and deletion, selecting and undoing a layer, and save/reopen for values without labels. Visually compare overlapping sprites in the editor and Play after sorting a scene with both positive and negative layer values.

### Session 3 implementation and validation

- Project Settings saves signed-value labels, and the SpriteRenderer Inspector
  uses the existing undoable property transaction for its named choices.
  Scenes and prefabs continue to store integers. Negative values render at
  layer zero, consistent with existing sprite ordering.
- Both export formats carry labels. Projects with sorting layer labels require
  an SLE 0.28 runtime bundle.
- Built `integration_project_runtime_path`, `integration_project_play_path`,
  `integration_game_package_path`, `sle_editor_native`, and `engine_app` with
  `cmake --build /tmp/sle-l2-linux --target <targets> -j 5`.
- All five CTests selected by
  `ctest --test-dir /tmp/sle-l2-linux -R '^integration_(project_runtime|project_play|game_package|package_launch|distribution_launch)_path$' --output-on-failure`
  passed with Mesa software rendering and OpenGL 4.6 overrides. The Play test
  compares pixels from overlapping sprites in the editor and Play after a
  label rename and removal.
- Gradle 9.5.1 `-p tools/editor --offline --no-daemon --console=plain
  -PsleBuildDir=/tmp/sle-l5-editor-build test` passed all 381 tests. A manual
  walkthrough of the desktop controls remains open.

## 4. Entity tags and Lua queries

**Done when** content authors can assign a project tag in the Inspector and Lua can query current members in Play and standalone.

1. Add optional `settings.tags` with stable IDs and unique display names. Store membership IDs in scene/entity data, and add round-trip coverage for scene, prefab and package paths. Renaming keeps membership IDs stable. Preserve unknown IDs on load and show them as unresolved. Removing a definition leaves membership IDs recoverable.
2. Add a Tags settings section and membership editing through the existing Inspector transaction path. Keep display name lookup in the runtime project data. Reuse the scene loaders and component registry for entity data. Document how portable scenes and prefabs retain unresolved memberships across projects.
3. Expose an initial Lua query through Scripting bindings and the ScriptApi bridge. Resolve the name against the active project and query live entities in the current scene. Specify ordering, unknown-name behavior and teardown in the API docs. A rename preserves entity membership and scripts that use the old name need updating. Track unique-name lookup independently from tag membership.
4. Extend `integration_scene_loader_roundtrip_path`, `integration_prefabs_path`, and the gameplay Lua integration coverage for multiple members, rename, unknown definitions, entity destruction and scene replacement. Regenerate the Lua API stub through the existing CMake target. Verify save/reopen and package playback.

### Session 4 implementation plan

- Use a list of `{id, name}` definitions in project and package settings. Generate UUIDs for new definitions in the editor and keep IDs stable across renames.
- Store membership as a Tags component so the shared scene, prefab, and authoring paths round-trip IDs. Use the Inspector transaction to update membership and display unresolved IDs.
- Add `Engine.findEntitiesByTag(name)` as a case-sensitive lookup returning a dense array sorted by entity ID. Return an empty array for unknown names. Query the current scene through the runtime bridge on each call.
- Exercise project validation, scene and prefab round-trips, runtime lifecycle, package playback, and editor save/reopen. Regenerate and check the Lua API stub.

### Session 4 implementation

- Project Settings stores stable tag IDs with editable names. The scene and
  prefab Inspector assign memberships through the undoable authoring path.
  Unmatched IDs survive save and reopen and appear as unresolved.
- The shared Tags codec feeds authored scenes, prefab instances and game
  packages. Both export formats carry saved definitions and require a tag-capable
  runtime when the project contains definitions or authored Tags components.
- `Engine.findEntitiesByTag` resolves the active project's exact display name
  and scans the current scene for live members. Results are sorted by entity ID.

### Session 4 validation

- Built the stub, native tests, editor bridge and app with
  `cmake --build /tmp/sle-l2-linux --target generate_lua_api_stub integration_scene_loader_roundtrip_path integration_prefabs_path integration_game_package_path integration_project_play_path integration_project_runtime_path sle_editor_native engine_app -j 5`.
- Ran `ctest --test-dir /tmp/sle-l2-linux -R '^integration_(scene_loader_roundtrip|prefabs|game_package|project_runtime|package_discovery)_path$' --output-on-failure`.
  All five passed.
- Ran `ctest --test-dir /tmp/sle-l2-linux -R '^integration_(project_play|package_launch|distribution_launch)_path$' --output-on-failure`
  with Mesa software rendering and OpenGL 4.6 overrides. All three passed.
- Ran `gradle -p tools/editor --offline --no-daemon --console=plain -PsleBuildDir=/tmp/sle-l5-editor-build test`.
  All 384 tests passed.
- Ran `python3 -m unittest discover -s EngineModules/Scripting/tools/tests`.
  All 21 passed. The Lua stub generator also passed in `--check` mode.
- A manual Inspector walkthrough and the Windows Release distribution smoke
  remain open.

## 5. Keyboard input actions

**Done when** a project author can bind a named action to multiple keys and a Lua behaviour can consume held, pressed and released state in Play and standalone.

1. Add optional `settings.inputActions` and the Input Actions settings section. Use unique action names and a validated list of typed bindings. Start with keys and keep binding kind explicit so gamepad entries can follow. Provide add, remove and capture controls using existing key names.
2. Inspect `Platform/Input.hpp`, `ViewportInput.kt` and the runtime frame path. Resolve actions in Systems using the existing input snapshot. An action is held if any binding is held. Derive the edges from the aggregate state so changing between two held bindings does not create a second press. Define sampling on simulation frames and verify Pause, Step and focus loss. Apply map edits on the next run.
3. Extend `LuaBindingsInput.cpp` and the ScriptApi bridge with action queries. Unknown actions are inactive. Preserve existing raw input APIs. Include the action map in project and packaged launches. Document and generate the Lua API additions.
4. Add deterministic tests for unbound and unknown actions, overlapping keys, edge lifetime, focus loss and restart. Run the editor unit suite and gameplay, project Play and package integration tests. Verify the play viewport receives the game binding without accidentally triggering editor shortcuts while it has game input focus.

### Session 5 implementation plan

- Use the same typed action map in source projects and package manifests.
  `ProjectDocument.kt` and `ProjectSettings.hpp` should accept empty binding
  lists and validate unique nonempty names, binding kinds and supported key
  codes. Capture in `ProjectControls.kt` should reuse `ViewportInput.kt`
  translation so every captured key can reach embedded gameplay.
- Sample the aggregate held state at the start of `Runtime::advanceSimulation`,
  before queued events and Lua updates. Both standalone and embedded Play
  already pass through this function. A paused render takes no sample. Step
  takes one sample. Focus loss clears held keys, and the next simulation sample
  reports a release if that action was held on the prior sample. A press and
  release between samples leave the sampled state unchanged. Start each run
  with a fresh map and no prior action state.
- Expose `Engine.Input.isActionDown`, `isActionPressed` and `isActionReleased`
  through the existing Lua input bridge. Unknown and unbound actions return
  false for all three queries.
- Verify key routing before fixing the supported key set. The standalone loop
  currently exits on Escape before simulation runs. The editor shortcut
  dispatcher reserves enabled Play controls even when Game has focus. Approved
  behavior is for explicitly bound gameplay keys to reach gameplay while Game
  has focus, and for standalone Escape to retain its exit behavior when unbound.
- Cover parser parity and save/reopen in the editor and project runtime tests.
  Inject key transitions in project Play, including Pause, Step and focus loss.
  Exercise manifest loading and relocated launch with a bound action and check
  the generated Lua stub. Build the affected targets before CTest and run the
  editor unit suite and focused desktop routing check.

### Session 5 implementation

- Project Settings stages named keyboard actions and captures supported keys
  using the Game viewport's key mapping. Source and package launches carry the
  same typed bindings, and either export format requires an SLE 0.30 runtime
  bundle for projects with actions.
- Systems samples key-down state once per simulation frame. Lua exposes
  `Engine.Input.isActionDown`, `isActionPressed`, and `isActionReleased`.
  Play Scene snapshots saved settings, Run Project uses its prepared snapshot,
  and the startup package supplies the map for the whole run.
- Bound keys take priority with native Game canvas focus. Play controls still
  work from the toolbar, and standalone Escape exits when unbound. Keypad
  Enter uses its distinct GLFW code in both launch paths.

### Session 5 validation

- Built with `cmake --build /tmp/sle-l2-linux --target generate_lua_api_stub integration_project_runtime_path integration_project_play_path integration_game_package_path integration_lua_runtime_lifecycle_path engine_app sle_editor_native -j 5`.
- All seven CTests selected by
  `ctest --test-dir /tmp/sle-l2-linux -R '^integration_(project_runtime|project_play|game_package|package_(discovery|launch)|distribution_launch|lua_runtime_lifecycle)_path$' --output-on-failure`
  passed with Mesa software rendering and OpenGL 4.6 overrides.
- Gradle 9.5.1 `-p tools/editor --offline --no-daemon --console=plain -PsleBuildDir=/tmp/sle-l5-editor-build test`
  passed all 388 editor tests.
- `python3 -m unittest discover -s EngineModules/Scripting/tools/tests` passed
  all 21 tests. The Lua stub generator passed with `--check`.
- The desktop `run --args=--console-smoke` passed with the rebuilt Linux bridge.
  The focused Game viewport smoke sent F6 through AWT, JNI and Lua without
  pausing Play. The manual settings capture walkthrough and Windows Release
  distribution smoke remain open. Layout-dependent letter and punctuation
  mapping differences between AWT and GLFW remain on some non-QWERTY layouts.

## 6. Controller support moved to a dedicated plan

The S6 audit found keyboard-only project bindings, action resolution, and
editor capture. The broader controller work is tracked in
[Controller input and local multiplayer](controller-input.md). Controller
mapping graphics are deferred to the UI work.

## Session handoff

Add the focused tests alongside each feature using [test guidance](../../tests/AGENTS.md). Include existing projects with no settings and exercise each runtime setting through project-backed Play, standalone project launch and export. Update [Projects](../topics/Projects.md) and the affected runtime, Lua or package docs as each contract lands. Record implementation and validation separately under each session. Keep engine orchestration in Systems and preserve the module dependencies in [Constraints](../CONSTRAINTS.md).
