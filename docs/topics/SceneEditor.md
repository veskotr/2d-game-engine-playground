# Scene editor

The desktop editor's **Scene** tab authors keyed entity hierarchies and saves
portable `.slescene` packages. A package includes the scene JSON and all recognized
component assets, using the same bounded ZIP and asset-ID machinery as
[packaged entity templates](PrefabPackages.md). In an open project, Save defaults to plain
`.scene.json` sources with shared UUID asset references; see
[project editing](Projects.md#scene-and-prefab-editing). Export Package retains the
portable `.slescene` workflow.

## Workflow

- Right-click the tree or Scene viewport for **New → Scene**, **Create →
  Entity → At Scene Root**, or **Import → Entity Template…**. Select the scene root to
  enter its name. Creation menus replace the former add/import buttons.
- Entity template imports are editable copies, with new UUIDs and remapped self references.
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
  an accent marker, the Details panel edits it and only it gets handles; every selected entity
  is outlined. Dragging any selected entity moves the whole selection, and arrow nudges
  move it too; each drag, and each nudge burst, is one undo step ("Move 3 entities"). A
  selected entity whose ancestor is also selected follows that ancestor. A plain click on
  a selected entity without dragging selects it alone. With several entities selected the
  Details panel shows "N entities selected" with Duplicate and Delete only (editing several
  entities at once is a later roadmap item); Edit Entity, Apply Changes to Entity Template, Add
  Component, Parent and dragging to Assets work on one entity and say so when disabled.
- **Tools and snapping:** **Q** Select, **W** Move, **E** Rotate and **R** Scale tools
  (viewport toolbar and the Tools menu; the keys work while the Scene view or the Hierarchy
  has focus). Select draws a box on empty space to select by selection bounds (Shift/Ctrl
  adds). Move shows X/Y arrows and a free-move square on the active entity; body drags still
  move the selection in every tool. Rotate shows a ring: dragging it turns the selection,
  and with several entities selected their positions orbit the pivot. Scale shows X/Y handles
  along the active entity's rotation and a uniform centre box; Shift makes any scale drag
  uniform. Scale changes Transform scale only (never sprite size), never crosses zero
  (minimum magnitude 0.001, or one scale step while snapping) and keeps mirrored signs.
  The **Pivot/Center** toggle in the toolbar (also Tools menu) picks what Rotate and Scale
  turn around: the active entity's position or the centre of the selection bounds. Each
  drag is one undo step ("Rotate Player", "Scale 3 entities"); Esc cancels it. Rotation
  stays stored in radians; the status-bar hints show degrees. **F** frames the selection.
  Snapping (toolbar magnet, **Configure Snap…**) is per user and per project
  (`.sle/editor-state.json`): Ctrl inverts it during a drag or nudge, Shift keeps a move on
  its dominant axis, and positions, rotations (rotate step, degrees) and scales (scale
  step) snap the active entity's world value before conversion to local through the parent
  pose; the rest of the selection follows by the same amount. Right-click creation and
  Assets drops use the move step. The grid follows the move step through the editor-only
  `Runtime::setEditorGrid`. The Entity Template tab keeps its own shape handles; there is no Rect
  tool yet.
- The default layout places the tree on the left, viewport in the center, and selected
  entity Details on the right. Entity properties contain name, parent, Transform
  and Camera2D when present. **Fit** frames all entities;
  edits preserve the camera. Edit the selected entity's components in the Details panel;
  **Right-click → Add Component** adds one directly. There is no separate entity tab
  for scene entities. **File → Save** saves the scene.
- Drag an entity from the Hierarchy or out of the viewport onto an Assets folder to
  save it as a linked entity template; **Apply Changes to Entity Template** writes an instance's edits
  back. See [Entity Templates](Prefabs.md#creating-editing-and-applying-prefabs).
- **Create → Entity → As Child** creates a child of the right-clicked selection. **Parent** reparents while
  preserving world pose; cycles and non-invertible parent transforms are rejected.
  **Edit → Duplicate** copies the selected subtrees with independent UUIDs;
  **Edit → Delete** or Delete in the Hierarchy/Scene viewport removes them
  and clears selection, preventing a held Delete from deleting unrelated survivors.
  **Undo** restores the subtrees and selection. Details text fields keep Delete
  for text editing; modal dialogs and running/paused playback block scene deletion.
  This also applies to detached windows.
- Right-click empty Scene space to create at the clicked world position. The shared
  creation menu includes Entity, Sprite, Camera, box/circle colliders and zones;
  **As Child** converts the world point through the selected parent. Hierarchy creation
  retains local-origin placement. New sprite/box components use 64×64 world units;
  circle components use radius 32. Inspector-added shapes still fit an existing sprite.
  These are editor presets; saved documents and native codec defaults are unchanged.
- Right-click a property label in Scene or Entity Template Details for **Reset**,
  **Copy**, and **Paste**. Reset uses the catalog default for that property.
  Vector axes have their own menus, so resetting one axis preserves the
  others. Property clipboard values use JSON, including quotes around strings.
  Copy is available for read-only properties while editing. The text fields
  keep their usual clipboard shortcuts.
- Sprite color exposes RGBA fields, and **Pick color…** preserves the authored
  alpha. The texture row offers **Browse…**, **Clear**, a thumbnail, and
  **Use image size**. Expand the SpriteRenderer section to edit the UV rectangle
  using U0, V0, U1, and V1. Free-form Script variables remain JSON.
- Right-click a component heading in either Details host for **Reset**, **Copy**,
  and **Paste**. Reset restores writable catalog defaults. Copy writes a JSON
  object with `component` and `values` keys. Paste requires the same component
  type and updates the supplied fields. Both actions preserve destination entity
  references, legacy-only fields, and read-only values. Copy is available while
  editing a read-only component. Component reordering is deferred because the
  document format stores components by type.
- Reset and Paste validate the destination document before changing it.
  An invalid value, an unresolved asset, or another invalid authored value
  can produce a diagnostic in the status bar. Each successful change can
  be undone in one step.
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
  would break an Animator target is refused and stays. The standalone entity template workspace
  has its own timeline. Saving an entity template that refreshes linked instances clears the
  scene's history and reports "Undo history cleared after entity template update".
- In standalone mode, **Save / Save As** writes one `.slescene`. **Open** restores entities and assets.
  Save validates every entity, including hidden selections, before atomically
  replacing the file. Invalid drafts in any entity block saving. The scene dirty
  indicator covers all entity edits/imports and structure; New/Open/Close confirm
  before discarding changes. Selection alone does not make the scene dirty.

The top menu strip groups **File** document operations, **Edit** history and
entity operations, **Create** entities/components/imports, **View** navigation and
UI scale, and **Help** controls/About. **File → New/Open → Entity Template** switches to a
separate entity template document, which is preserved during scene component editing.
Scene copies retain their identities and do not modify their source entity template.
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
existing separate content mode. Hierarchy, Details panel and File/Edit commands follow
the active authoring mode, including Save-to-scene for edited scene copies.

## State Machines and Event Areas

- In **Details → Add Component → Scripting**, add **State Machine**. Use
  **Browse…** to attach an existing definition, or **Create Definition…** in
  a project to create and attach a starter JSON asset. The starter begins in
  `Idle`, switches to `Active` when Lua calls
  `Engine.setStateMachineTrigger(entity, "activate")`, and returns to `Idle`
  after one second. To change the state graph, select the definition in Assets
  and use **Open Externally**. Use the Live Inspector during Play to see the
  current state.
- To place a trigger, use **Create → Box Event Area** or **Circle Event Area**
  in the Scene view. To attach one to an existing scene entity or entity
  template, use **Add Component → Event Areas**. The editor initializes the
  zone ID and supplies a static Rigid Body when the entity has none. Adjust the
  shape, zone ID and collision layers in Details. The runtime component names
  are `BoxZone` and `CircleZone`.
- Add a **Lua Behavior** using the **Event area listener** template on the
  zone entity. During Play, move an eligible dynamic body with a collider
  through the shape. The template logs `zone.enter` and `zone.exit` callbacks
  in the Console. Replace the log calls with your gameplay logic.

## Scene cameras and gameplay

The [editor property schema](EditorPropertySchema.md) lists the metadata supplied
to Details for transforms, sprites, cameras, rigid bodies and colliders.

**Create → Camera → At Scene Root** creates an entity with Camera2D. Use
**Create → Camera → As Child** to
attach one to a selected actor. The Details panel edits its logical view size, zoom,
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

## Live Inspector

During embedded Play, the Hierarchy and Details panels switch to **Live
Hierarchy** and **Live Details**. Select a runtime entity to inspect its local
Transform, SpriteRenderer values and Lua behaviour attachments. Runtime-spawned
entities appear under their current parents. The hierarchy uses scene and
prefab names when available, then the authored key or runtime ID.

Live values refresh during Play and after **Step**, and
remain available while paused. The runtime selection clears when its entity is
destroyed, the runtime switches scenes, or you stop Play. **Stop** returns the
panels to the authored scene and its selection.

During **Play Scene**, pause and select an authored entity in **Live Hierarchy**.
If its local Transform changed, **Live Details** previews the changed fields.
Click **Keep Changes** to apply the preview to the scene in one Undo step, then
save normally to keep it on disk. If any affected authored field changed since
Play started, the action rejects the entire preview. The action requires the
original scene and parent mapping. Spawned entities, linked prefab instances,
and **Run Project** sessions are ineligible. **Stop** discards any remaining
runtime changes.

### Reload a Lua behaviour

With a project open, save the Lua file under its asset root. Select an
entity in **Live Hierarchy** during **Play Scene** or **Run Project**, then
click **Reload Script** beside its attachment in **Live Details**.
Reload works while running or paused. It restarts enabled
attachments using the same saved source in the current scene, including
active attachments that failed to initialize. Attachments in suspended
chunks are skipped. The Console reports the result for each attempt.

The new file must compile and run its top-level code before the current
attachment is retired. Keep top-level code to declarations and local
setup. Move game API calls, module imports and other runtime work into
`init`. A syntax or top-level error leaves the previous attachment active.
After successful staging, the old behaviour is torn down and the new
behaviour starts with a fresh environment and fresh local state. If `init` fails, correct the source
and click **Reload Script** again. Any game-state changes made by `destroy`
or `init` persist in the current run.

## Engine Console

Select **Console** beside **Assets**, or choose **Windows → Console →
Show / Focus**. The panel captures engine messages while hidden. Filter by
severity or search message text, source locations, behaviour keys and Lua
tracebacks. Consecutive identical entries show a repeat count. The Console
reports when either the native queue or the displayed history drops older
messages.

**Clear** clears pending and displayed messages. **Clear on Play** is a
per-user preference that clears before a new scene or project run starts,
so startup errors are visible. Stopping Play leaves the captured history
available.

Enable **Pause on Lua Error** in the Console to pause embedded Play after
the first frame that reports a Lua error. The status bar shows the first
error from that frame, and the viewport marks its entity while it remains
in the live scene and has a transform. Each step refreshes the pause
message and marker for that frame. The preference also works while
the Console is hidden.

While Play is paused, use **Step** in the Edit menu or viewport toolbar
to advance the scene and UI by 1/60 s. Each step leaves playback and audio
paused.

Lua errors include the entity ID and behaviour key when available. Select the
source button to open the resolved Lua file at the reported line. The Lua
Profiler can also open the source file for an entry.

In **Details**, expand **Script** and choose **Open in IDE** beside the
primary script or a named behavior. The same action is available in read-only
Live Details during Play. In **Assets**, select a Lua file and choose **Open in
IDE** in its details pane.

Use **Configure Script Editor…** in the Console to choose an IDE across all
projects. Enter the executable path and place each argument on its own row.
Use `{file}` for the path and `{line}` for the line number. For example, VS
Code uses `{file}` for file arguments and two rows, `--goto` and
`{file}:{line}`, for line arguments. If you leave line arguments blank or no
positive line is available, SLE uses the file arguments. Paths with spaces
stay within the corresponding argument.

**Reset to Defaults** clears the saved preference. SLE then uses the executable
in `SLE_SCRIPT_EDITOR` with VS Code arguments, or discovers VS Code if the
variable is unset. On Windows it looks for `Code.exe`. On Linux it runs
`code` from PATH. Enter executable paths without surrounding quotes.

### Run Lua

Open **View → Run Lua…** from the Scene workspace. Enter multiline Lua and
use **Run Lua** or **Ctrl+Enter** to submit. **Previous** and **Next** recall
commands. Use `print(...)` to write to the Console.

While stopped, each command gets a fresh, sandboxed Lua state and a detached
`snapshot`. Read `snapshot.scene.entities`, `snapshot.selection` (ordered
entity keys), and, in a project, `snapshot.project` and `snapshot.assets`.
Asset paths are relative to the configured asset folder. To edit the scene,
return an ordered array of operation tables. For example, select an entity
with a Transform and run this command.

```lua
local entity = assert(snapshot.selection[1], "Select an entity first")
return {
    {op = "setField", entity = entity, component = "Transform",
     path = {"position", "x"}, value = 100},
}
```

Other operations use the following fields.

| Operation | Fields |
| --- | --- |
| `addComponent`, `removeComponent` | `entity`, `component` |
| `createEntity` | A unique `entity` key and `name`. Later edits can use the new key. |
| `deleteEntity` | `entity`. Removes its subtree. |
| `reparentEntity` | `entity`, optional `parent`. Omitting `parent` moves the entity to the root. The local transform stays the same. |
| `createAsset` | `path`, `contents` (UTF-8 text). The parent asset folder must already exist. |

The editor validates the whole list before applying it as one **Run Lua**
undo step. Asset creation requires an open project and an unused destination.
Undo checks that the created file and its asset metadata still match the command.
Unpack linked entity templates before deleting or reparenting them from Lua.

During embedded Play or Pause, the console runs commands in the game Lua VM
with the gameplay APIs. Changes affect the current run and errors appear
in the Console. **Pause on Lua Error** also applies to these commands.

## Project Editor Commands

Save a text Lua script in `Editor/` beside the `.sleproject` descriptor with
a name ending in `.editor.lua`. Open or reopen the project to load it. The
first time the editor finds scripts, it asks whether you trust the project.
The decision stays in your local editor settings for that project ID and
canonical descriptor path. Use **File → Project Settings → Project Editor
Scripts** to grant or revoke trust.

Return an array of command descriptors. Each command needs a project-unique
`id`, a `label`, and a `run(snapshot)` function. The optional `menu` names the
top-level menu and defaults to **Tools**. The optional `shortcut` uses the
editor's key chord syntax, such as `Ctrl+Shift+F8`. Conflicting shortcuts are
reported in the Console. Keep the returned descriptors independent of the
scene snapshot. Use an optional `enabled(snapshot)` function to determine
whether each command is available.

```lua
return {
    {
        id = "move-to-origin",
        label = "Move Selection to Origin",
        menu = "Tools",
        shortcut = "Ctrl+Shift+F8",
        enabled = function(s) return #s.selection > 0 end,
        run = function(s)
            local edits = {}
            for _, entity in ipairs(s.selection) do
                for _, axis in ipairs({"x", "y"}) do
                    edits[#edits + 1] = {op = "setField", entity = entity,
                        component = "Transform", path = {"position", axis}, value = 0}
                end
            end
            return edits
        end,
    },
}
```

Commands are available in the stopped Scene workspace. Callbacks run in a
fresh, bounded editor Lua sandbox and receive the same detached snapshot
described under **Run Lua**. Return the same edit operations to apply them as
one undo step, or return `nil` for a command that makes no edits. Lua errors
and `print(...)` output appear in the Console. Revoking trust removes the
project's commands as soon as the editor accepts the change.

### Project drawers and panels

To declare UI alongside commands, return a table with optional `commands`,
`drawers`, and `panels` lists. Put your existing command descriptors in
`commands`. For example, if your project registers `move-to-origin`:

```lua
return {
    drawers = {
        {component = "SpriteRenderer", path = {"color"}, widget = "color"},
    },
    panels = {
        {id = "placement", label = "Placement Tools", widgets = {
            {kind = "label", text = "Reset the selected entities’ positions."},
            {kind = "button", command = "move-to-origin"},
        }},
    },
}
```

A drawer binds one component field path in Details to a built-in
presentation. Available widgets are `color` (four-channel vector), `uv`
(vector), `resource` (resource field), `texture` (`SpriteRenderer.texture`),
and `structured` (structured object). The editor uses its normal widget
if the binding is incompatible with the field. Editing through a drawer
uses the field's existing validation, write permissions and undo history.

Open the panel by its label in **Windows**. Each panel has a project-unique
ID and up to 16 widgets. A button takes the ID of a registered project
command, including commands declared in another file. It uses the command's
label, availability and undo transaction. Invalid declarations are reported
in the Console. A Lua execution or result serialization error skips that
script file. Switching projects or revoking trust closes project panels
and clears their drawer bindings.

## Lua Profiler

Open **Lua Profiler** beside **Console** or from **Windows**, then enable
**Capture Lua** before or during Play. The graph shows up to 120 samples.
The latest sample shows Lua VM memory and the 512 behaviour rows with the
most Lua time. Each row reports timings and counts for init, update, callback
and chunk execution, plus bytes allocated or freed within those calls. Use
**Select entity** to open its Live Details, or use the filename button to open
the Lua source through the same launcher as the Console.

Call times are wall-clock and exclude nested engine-dispatched Lua calls
that have their own row. They include synchronous engine work invoked by Lua.
The explicit GC figure is the measured time in Lua's collection and step
requests and is part of the Lua total. Automatic collection inside Lua
execution is included in call timings. VM memory covers all Lua allocations.
The profiler clears samples on Stop, scene replacement or capture disable.
The capture toggle stays armed across Play runs until disabled.

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
[crate entity template](../../assets/prefabs/editor_crate.sleprefab) are included. The scene
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
exist and be unique. Tiled imports, linked entity template
instances, scene-root components and unknown metadata are explicitly rejected by
the editor rather than discarded. Existing engine loaders still support their
full JSON scene formats. Linked overrides remain later work.

Asset bundling and dynamic dependency limitations are the same as entity templates: known
asset fields/JSON dependencies are imported, but arbitrary Lua expressions and
XML dependencies are not inferred. Extra imported files retain their bytes.

`gradlew.bat build` runs model/package/selection/history regression coverage.
`gradlew.bat run --args=--scene-smoke` drives the real desktop viewport, imports a
entity template twice, selects/moves one copy, exercises undo and structural edits, rejects
an invalid hidden draft, relocates/saves/reopens the package, and verifies actual
rendered texture/collider pixels. Reports are under the editor's `build/reports`.

`gradlew.bat run --args=--camera-smoke` additionally checks hierarchy persistence,
editor pan, Game framing, child-camera following, real Lua input and physics,
Pause/Resume/Stop, and unchanged authored data. Screenshots include
`build/reports/game-camera.png` and `camera-editor-layout.png`.

`gradlew.bat run --args=--context-menu-smoke` checks real right-click tree
selection, cascading menus, child-camera creation, subtree duplication and
component attachment/filtering. Menus use the editor colors, icons and UI scale;
creation is disabled while scene gameplay is running or paused. The context-menu
smoke checks dialogs in the starting theme. The dedicated
`--editor-appearance-smoke` checks them in both themes.

## Create a Lua behavior

With a project open, select an editable entity in Scene or Entity Template
and choose **New Lua Behavior…** in Details. Choose a basic template or one
with an exposed property, then enter an unused `.lua` path relative to the
project's asset root. The default destination is `Scripts/Behavior.lua`.

Add project templates by placing direct `.lua` files in `ScriptTemplates/`
at the project root. `Basic.lua` and `ExposedProperty.lua` replace the matching
built-ins. Other filenames appear as additional choices. The picker skips
files larger than 1 MiB and links that resolve outside `ScriptTemplates/`.
The editor copies the selected file byte for byte without running or parsing it.

**Create and Attach** registers the source in Assets and adds a named
behavior to the entity. Save the scene or template to keep the attachment.
Undo detaches the behavior, leaving the Lua file and any IDE edits on disk.
Redo reattaches the same file. Use **Open in IDE** on the attachment or in
Assets to edit the source.

## Behavior parameters

Expand the Script component in the scene or entity template Inspector. Named
behaviors with exposed properties show a field for each declaration. An edit
saves an override for that attachment. **Reset** removes the override and
restores the script default. Edits and resets participate in document undo and
redo, and the overrides persist with the scene or template.

The Inspector refreshes its fields when the script changes. A discovery error
appears under the attachment and preserves its saved overrides. See
[exposed behavior parameters](Scripting.md#exposed-behavior-parameters) for the
Lua declaration and runtime API.
