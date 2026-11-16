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
- The expandable **Scene Tree** contains the scene root and entity nodes, with
  no component nodes. Select a node or click its geometry/origin in the viewport.
  Both select the complete entity. Dragging its center or body and arrow nudges
  change Transform position; the outline encloses its visible geometry. Components
  cannot be selected or resized from Scene. Use the tree to choose
  coincident entities. Selecting the root shows scene properties.
- The default layout places the tree on the left, viewport in the center, and selected
  entity inspector on the right. Entity properties contain name, parent, Transform
  and Camera2D when present. **Fit** frames all entities;
  edits preserve the camera. **Right-click → Edit** opens that live scene copy in a temporary entity tab.
  Its component selector and handles edit sprite/collider geometry there.
  Select Scene to return to placement. **File → Save** saves the scene.
- **Create → Entity → As Child** creates a child of the right-clicked selection. **Parent** reparents while
  preserving world pose; cycles and non-invertible parent transforms are rejected.
  **Entity → Duplicate Subtree** copies the complete selected subtree with independent UUIDs;
  **Edit → Delete Entity** removes that subtree.
- **Duplicate Subtree** creates another independent UUID and preserves references to
  other scene entities. **Edit → Delete Entity** removes the selection. Operations that would
  invalidate an Animator target are rejected. Save/reopen converts legacy numeric
  targets to stable keys before structural edits that would change their meaning.
- **Undo / Redo** and viewport Ctrl+Z/Y edit the selected entity. Each entity keeps
  its history across selection changes. **Scene Undo / Scene Redo** separately
  restore additions, imports, duplication, deletion and scene-name changes; they
  preserve component edits on surviving entity objects. There is no unified
  chronological history across both types yet.
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
are described under [workspace windows](../../tools/entity-editor/README.md#workspace-windows).
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

The compact tool layout defaults to **UI 85%**. **View → UI Scale** offers
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
cd tools/entity-editor
./gradlew.bat run --args="--scene assets/scenes/editor_starter.slescene"
```

Gradle launches the editor from the repository root, so that scene path is correct.

## Current scope and validation

The first scene editor supports at most 4096 authored entities and 4 MiB
scene metadata. It accepts scene JSON with key/name/entities; entity keys must
exist and be unique. Tiled imports, linked prefab
instances, scene-root components and unknown metadata are explicitly rejected by
the editor rather than discarded. Existing engine loaders still support their
full JSON scene formats. Linked overrides and a unified scene/entity history remain later work.

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
