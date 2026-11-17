# Scene editor

The desktop editor's **Scene** tab authors keyed entity hierarchies and saves
portable `.slescene` packages. A package includes the scene JSON and all recognized
component assets, using the same bounded ZIP and asset-ID machinery as
[packaged prefabs](PrefabPackages.md). In an open project, Save defaults to plain
`.scene.json` sources with shared UUID asset references; see
[project editing](Projects.md#scene-and-prefab-editing). Export Package retains the
portable `.slescene` workflow.

## Workflow

- Right-click the tree or Scene viewport for **New → Scene**, **Create →
  Entity → At Scene Root**, or **Import → Prefab…**. Select the scene root to
  enter its name. Creation menus replace the former add/import buttons.
- Prefab imports are editable copies, with new UUIDs and remapped self references.
  Editing a scene copy never modifies its source `.sleprefab` or another copy.
- The expandable scene tree in **Hierarchy** contains the scene root and entity nodes, with
  no component nodes. Select a node or click its geometry/origin in the viewport.
  Both select the complete entity. Dragging its center or body and arrow nudges
  change Transform position; the outline encloses its visible geometry. Components
  cannot be selected or resized from Scene. Use the tree to choose
  coincident entities. Selecting the root shows scene properties.
- **Multi-select:** Ctrl-click a Hierarchy row to add or remove it, Shift-click to select
  the visible rows between the last clicked row and this one (Ctrl+Shift adds the range),
  **Ctrl+A** (Edit → Select All) selects every entity and **Esc** in the Hierarchy clears
  the selection. In the Scene view, Shift- or Ctrl-click toggles an entity. The selection
  is ordered and has one *active* entity (the latest one clicked): its Hierarchy row has
  an accent marker, the Inspector edits it and only it gets handles; every selected entity
  is outlined. Dragging any selected entity moves the whole selection, and arrow nudges
  move it too; each drag, and each nudge burst, is one undo step ("Move 3 entities"). A
  selected entity whose ancestor is also selected follows that ancestor. A plain click on
  a selected entity without dragging selects it alone. With several entities selected the
  Inspector shows "N entities selected" with Duplicate and Delete only (editing several
  entities at once is a later roadmap item); Edit Entity, Apply Changes to Prefab, Add
  Component, Parent and dragging to Assets work on one entity and say so when disabled.
- The default layout places the tree on the left, viewport in the center, and selected
  entity inspector on the right. Entity properties contain name, parent, Transform
  and Camera2D when present. **Fit** frames all entities;
  edits preserve the camera. Edit the selected entity's components in the Inspector;
  **Right-click → Add Component** adds one directly. There is no separate entity tab
  for scene entities. **File → Save** saves the scene.
- Drag an entity from the Hierarchy or out of the viewport onto an Assets folder to
  save it as a linked prefab; **Apply Changes to Prefab** writes an instance's edits
  back. See [Prefabs](Prefabs.md#creating-editing-and-applying-prefabs).
- **Create → Entity → As Child** creates a child of the right-clicked selection. **Parent** reparents while
  preserving world pose; cycles and non-invertible parent transforms are rejected.
  **Edit → Duplicate** copies the selected subtrees with independent UUIDs;
  **Edit → Delete** or Delete in the Hierarchy/Scene viewport removes them
  and clears selection, preventing a held Delete from deleting unrelated survivors.
  **Undo** restores the subtrees and selection. Inspector text fields keep Delete
  for text editing; modal dialogs and running/paused playback block scene deletion.
  This also applies to detached windows.
- Right-click empty Scene space to create at the clicked world position. The shared
  creation menu includes Entity, Sprite, Camera, box/circle colliders and zones;
  **As Child** converts the world point through the selected parent. Hierarchy creation
  retains local-origin placement. New sprite/box components use 64×64 world units;
  circle components use radius 32. Inspector-added shapes still fit an existing sprite.
  These are editor presets; saved documents and native codec defaults are unchanged.
- **Duplicate** (Ctrl+D) copies the selected subtrees with new UUIDs, preserves references to
  other scene entities and selects the copies. A selected entity whose ancestor is also
  selected is copied once, with that ancestor. **Edit → Delete** (Delete) removes every
  selected entity and its descendants. Each is one undo step. Operations that would
  invalidate an Animator target are rejected. Save/reopen converts legacy numeric
  targets to stable keys before structural edits that would change their meaning.
- **Undo / Redo** (Ctrl+Z, Ctrl+Y or Ctrl+Shift+Z from any panel) walk one timeline per
  document. In a scene it holds structure (create, import, duplicate, delete, reparent,
  active camera, scene rename) and every entity's component and transform edits, in the
  order they happened. **Edit → Undo** names the step, for example "Undo Move Player".
  Undo restores the whole selection from before the step and selects the entity it changed;
  selecting alone is never a step. A drag, a committed numeric edit and a burst of arrow
  nudges (within 0.75 s) are one step each. Pressed during a drag or numeric edit, Undo
  and Redo cancel that edit first. The history keeps the latest 100 steps. A step that
  would break an Animator target is refused and stays. The standalone prefab workspace
  has its own timeline. Saving a prefab that refreshes linked instances clears the
  scene's history and reports "Undo history cleared after prefab update".
- In standalone mode, **Save / Save As** writes one `.slescene`. **Open** restores entities and assets.
  Save validates every entity, including hidden selections, before atomically
  replacing the file. Invalid drafts in any entity block saving. The scene dirty
  indicator covers all entity edits/imports and structure; New/Open/Close confirm
  before discarding changes. Selection alone does not make the scene dirty.

The top menu strip groups **File** document operations, **Edit** history and
entity operations, **Create** entities/components/imports, **View** navigation and
UI scale, and **Help** controls/About. **File → New/Open → Prefab** switches to a
separate prefab document, which is preserved during scene component editing.
Scene copies retain their identities and do not modify their source prefab.
Valid external Animator lookups are projected to self only for the isolated
component preview; authored/saved references remain intact.

See the [workspace operating guide](EditorWorkspace.md) for docking, shared Play,
window close behavior and layout recovery.

The **Windows** menu shows, focuses, hides and moves the six registered panels.
Detach Scene or Game, or use Move to to split/tab panels and groups. Only the main
window owns application menus. Closing a tool window hides its panels; Show / Focus
restores them with document drafts and Play intact. Restore Default Layout returns
them to the main host. Drag tabs to reorder/merge, drop on a body edge to split,
or outside editor windows to detach. The dotted group grip moves all its tabs;
Escape cancels. Drag dividers to resize. Panel `_` minimizes to a restore strip,
`↗` detaches and `×` hides; the native title bar minimizes the whole window.
Minimum sizes follow UI scale; impossible display constraints recover to tabs.
Layouts save per project in `.sle/workspace-layout.json`, independently of UI
scale and documents. Standalone storage, recovery behavior and the restart smoke
are described under [workspace windows](../../tools/editor/README.md#workspace-windows).
Restored windows recover to connected display work areas; reset changes layout only.

Scene and Game share one simulation and durable GL owner; Scene pan/zoom and Game
dimensions stay independent. Explicitly opening entity editing stops playback;
focusing its panel during Play preserves the session. Entity preview uses the
existing separate content mode. Hierarchy, Inspector and File/Edit commands follow
the active authoring mode, including Save-to-scene for edited scene copies.

## Scene cameras and gameplay

**Create → Camera → At Scene Root** creates an entity with Camera2D. Use
**Create → Camera → As Child** to
attach one to a selected actor. The inspector edits its logical view size, zoom,
enabled and primary flags; **Make Active** selects the primary camera. Camera
position is the view center and world rotation is inherited. Transform scale does
not resize its view. Without a camera entity, legacy scenes retain their runtime
camera. If several enabled primary cameras exist, the lowest runtime entity ID wins.

**Scene** uses an independent editor camera. Middle-drag or Alt+left-drag pans,
mouse wheel zooms, and **Fit** frames the scene. Camera rectangles show authored
Game framing and can be selected like other entities. **Game** displays the active
game camera, with letterboxing to preserve its aspect ratio.

- **Play** starts a snapshot of the current authored scene through the engine's
  normal scripts, physics, animation, events, UI and audio systems. Click Game view
  to focus keyboard/mouse input. Focus loss releases held input.
- **Pause** freezes gameplay and audio; **Play** resumes the same runtime state.
- **Stop** discards runtime changes and restores the authored scene. Opening entity editing also stops playback; switching Scene/Game preserves it. Editing is locked while running or paused; selection
  and Scene camera navigation remain available.

Open `assets/scenes/editor_camera_demo.slescene` for a player with a child camera,
a Lua movement script (WASD), and a falling crate. Run the same package standalone
with `engine_app.exe --config examples/editor_camera/engine.json`. The example runs through the same runtime in the editor and standalone.

The compact tool layout defaults to **UI 100%**. **View → UI Scale** offers
75%, 85% and 100%; changing scale preserves documents/selection and updates native
viewport sizing.

## Files and engine loading

Version 1 scene package:

```text
manifest.json     # format: "sle-scene", version: 1, assets: [...]
scene.json        # scene key/name and entities
assets/<id>.<ext> # bundled component assets and preserved extra imports
```

`scene.json` uses ordinary engine entity components:

```json
{
  "key": "<scene UUID>",
  "name": "Level",
  "entities": [
    {"key":"<entity UUID>","name":"Player","components":{"Transform":{}}},
    {"key":"<camera UUID>","name":"Camera","parent":"<entity UUID>",
     "components":{"Transform":{},"Camera2D":{"viewSize":{"x":480,"y":270}}}}
  ]
}
```

Scene keys are editor identity metadata; entity keys are engine references. The
engine's `SceneLoader::load` dispatches `.slescene` case-insensitively through
`PrefabPackage::readScene`, materializes asset IDs, then uses its normal scene
loader. `Runtime::registerSceneFromFile` stages and commits it like JSON, so
`engine.json` can directly reference a packaged scene:

```json
{"name":"level","file":"assets/scenes/editor_starter.slescene"}
```

`SceneAuthoring::canonicalScene` validates editor scenes without graphics/Lua,
using shared component codecs. It fills defaults and normalizes aliases, vectors
and Animator references. `Runtime::loadScenePreview` stages those scenes while
pausing gameplay and suppressing gameplay lifecycle events. Preview Fit considers
all entities rather than only the selected entity.

A ready-to-load [starter package](../../assets/scenes/editor_starter.slescene),
[plain JSON equivalent](../../assets/scenes/editor_starter.json), and
[crate prefab](../../assets/prefabs/editor_crate.sleprefab) are included. The scene
contains ground and two independent crates. Open the package in Scene, or
run from the repository root with Windows tooling:

```powershell
build/debug/engine_app/Debug/engine_app.exe --config examples/editor_scene/engine.json
```

Without `--config`, the executable retains its existing `engine.json` discovery.
The [sample configuration](../../examples/editor_scene/engine.json) leaves the
repository's default startup config unchanged. To open the scene immediately in
the desktop editor:

```powershell
cd tools/editor
./gradlew.bat run --args="--scene assets/scenes/editor_starter.slescene"
```

Gradle launches the editor from the repository root, so that scene path is correct.

## Current scope and validation

The first scene editor supports at most 4096 authored entities and 4 MiB
scene metadata. It accepts scene JSON with key/name/entities; entity keys must
exist and be unique. Tiled imports, linked prefab
instances, scene-root components and unknown metadata are explicitly rejected by
the editor rather than discarded. Existing engine loaders still support their
full JSON scene formats. Linked overrides remain later work.

Asset bundling and dynamic dependency limitations are the same as prefabs: known
asset fields/JSON dependencies are imported, but arbitrary Lua expressions and
XML dependencies are not inferred. Extra imported files retain their bytes.

`gradlew.bat build` runs model/package/selection/history regression coverage.
`gradlew.bat run --args=--scene-smoke` drives the real desktop viewport, imports a
prefab twice, selects/moves one copy, exercises undo and structural edits, rejects
an invalid hidden draft, relocates/saves/reopens the package, and verifies actual
rendered texture/collider pixels. Reports are under the editor's `build/reports`.

`gradlew.bat run --args=--camera-smoke` additionally checks hierarchy persistence,
editor pan, Game framing, child-camera following, real Lua input and physics,
Pause/Resume/Stop, and unchanged authored data. Screenshots include
`build/reports/game-camera.png` and `camera-editor-layout.png`.

`gradlew.bat run --args=--context-menu-smoke` checks real right-click tree
selection, cascading menus, child-camera creation, subtree duplication and
component attachment/filtering. Menus use the editor colors, icons and UI scale;
creation is disabled while scene gameplay is running or paused.
