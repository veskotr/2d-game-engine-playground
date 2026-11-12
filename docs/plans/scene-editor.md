# Scene editor and packaged scene loading

## Agreed scope

The user wants engine support for the package format and scene authoring. Existing
PrefabLoader already supports `.sleprefab`. The user chose **editable prefab
copies** rather than linked instances. Build a Scene tab with a flat entity list,
new/import/duplicate/delete/select, per-entity inspector and viewport gizmos,
packaged `.slescene` save/open, and a sample loadable in the engine.

## Design and constraints

Reuse package format version 1 limits and asset machinery. `.slescene` contains
`manifest.json` with format `sle-scene`, `scene.json`, and bundled assets. Existing
`.sleprefab` schema is unchanged. Scene JSON has scene key/name plus keyed entities.
All asset paths become internal IDs; unused imports remain bundled. SceneLoader
dispatches packages before normal staged scene loading; engine_app scene config
therefore accepts packaged scenes without a new executable path.

Add context-independent SceneAuthoring validation/canonicalization for flat scenes
using existing EntityAuthoring/component codecs. Resolve cross-entity Animator
targets by key, reject unsupported metadata/hierarchies rather than discarding.
Existing engine JSON scene/hierarchy/Tiled behavior is unchanged. Initial editor
scope is flat authored entities; prefab imports snapshot and remap identity/self
references. Duplicate creates new UUID and remaps self references too.

Keep per-entity inspector histories across selection. Add scene structural undo/
redo for creation/import/duplicate/delete, clearly separate from entity edit undo.
Dirty state covers every entity, imported asset list and unfinished draft. Open/
New/Close confirm discarded scene changes. Scene save validates before atomic
replacement. Scene viewport renders all entities, picks the selected/other entity
by actual geometry, and uses existing handles only on the selection. Fit frames
all entities; same-scene edits preserve view. Preview runs no gameplay scripts.

## Steps

- [x] Generalize bounded package codec for scene JSON and manifest format; tests
  cover scene relocation/assets and prefab compatibility.
- [x] Add SceneAuthoring and scene package dispatch, scene preview and aggregate
  camera fit. Native tests cover load, references, invalid scene preservation.
- [x] Add Kotlin scene document/state, selection/entity operations, inspector and
  tab; import/duplicate identity, dirty state and structural history tests.
- [x] Wire all-entity preview/picking, verify actual scene viewport edits, save/
  reopen and exact editor output loaded by runtime. Add a small sample scene.
- [x] Update affected docs and record actual Windows build/Gradle/CTest results.

Deferred: hierarchy/reparenting UI, linked prefab overrides, terrain/Tiled editing,
full scene simulation controls, dynamic Lua/XML dependency inference, global
history combining per-entity field edits and scene structural operations.

## Results and review (2026-11-12)

- Full Windows CMake debug build succeeded, including `engine_app`. Full CTest:
  **66/66 passed**, including actual windowed tests. Logs:
  `/tmp/sle-scene-final-native-build.log`, `/tmp/sle-scene-full-native-tests.log`.
- Windows Gradle build: **33/33 editor tests passed**. Tests cover scene identity,
  prefab copy/self-reference remapping, structural history, capacity guards,
  deletion/reference safety, package relocation and old prefab compatibility.
  Logs `/tmp/sle-scene-final-editor.log`, `/tmp/sle-scene-inspector-build.log`.
- Actual Robot Scene workflow passed import-copy, click selection, independent
  entity movement/undo, duplicate/delete/structural undo, invalid hidden draft
  rejection, save/relocate/open and real texture/collider pixels. Three-mode
  switch preserves one GL context and unfinished scene field input. Log
  `/tmp/sle-scene-switch-retry.log`; screenshot editor build reports/scene-editor.png.
- Original single-prefab handle/undo/persistence smoke still passes:
  `/tmp/sle-scene-prefab-regression.log`.
- Native windowed integration CLI loaded/rendered the exact editor-produced
  relocated scene package: `ENGINE_SCENE_PACKAGE_RUNTIME_PASS`. Headless reader
  validated the same package. Native worker built and ran these checks.
- Standalone `engine_app.exe --config examples/editor_scene/engine.json` launched
  the packaged sample, logged `Loaded scene: starter`, ran its physics/render
  pipeline and exited normally after CloseMainWindow. Log
  `/tmp/sle-scene-standalone.log`. Default root engine.json was not modified.
- Lua API generator `--check`, affected documentation links, and scoped
  whitespace checks passed.

Independent review caught numeric target retargeting on deletion, unhandled
history conflicts and capacity overflow before composition; all fixed with
regressions. Native audit added finite extreme-coordinate Fit and shared a
catalog across scene normalization rather than recalculating it per entity.
Initial three-mode screenshot check ran before enough post-audio sandbox frames;
the test now waits for actual rendered frames. Final check passes.

Sample files: assets/scenes/editor_starter.slescene, its JSON equivalent,
assets/prefabs/editor_crate.sleprefab, examples/editor_scene/engine.json. Sample
generation does not overwrite existing user files. Engine app gains optional
`--config`; no arguments retain existing config discovery. The scene inspector
collapses its imported asset list behind Assets to leave more editing space.
