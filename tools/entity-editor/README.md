# SLE Entity Editor

Kotlin/JVM Compose Desktop authoring tool using SLE's native renderer and runtime.

**Scene** arranges entities through an independent editor camera. **Game** shows
the active scene camera; Play runs the current scene's actual scripts, physics,
animation, UI and audio. Switching Scene/Game preserves runtime state. Pause
freezes it; Stop restores authored data. The former Sandbox tab is removed.
Legacy sandbox smoke commands remain available for runtime regression checks.

Right-click an entity in the hierarchy or Scene viewport and choose **Edit** to
open its component editor in a temporary tab. Closing that tab returns to Scene.
Opening component editing stops gameplay. Component handles edit sprites and
colliders; Scene handles move complete entities. Entity editing operates on the
live scene copy, and File → Save saves its containing scene.

The **File**, **Edit**, **Create**, **View**, **Help**, and **Windows** menu strip contains
file operations, separate entity/scene histories, creation/import commands,
Scene/Game/Prefab navigation, Fit, UI scale and controls/About. Use
**File → New/Open → Prefab** for independent prefab authoring. File operations
retain unsaved-change confirmations and portable package validation.

Creation is also available through nested right-click menus. Right-click selects
the tree node before offering root/child creation and **Entity → Duplicate
Subtree**. Menus use the editor palette, vector icons and UI scale. Hierarchy
expanders are drawn vectors; entity/component/asset icons use the bundled editor family.

Open `assets/scenes/editor_camera_demo.slescene` for the included child-camera,
WASD and physics example, or `assets/scenes/editor_starter.slescene` for ground
and crates. Standalone Save produces a portable `.slescene` with assets, loadable by the
engine. Project Save uses plain JSON sources; Export Package produces the portable archive. See [scene workflow](../../docs/topics/SceneEditor.md).

UI work follows the design system in [UI_DESIGN.md](UI_DESIGN.md); see
[HOW_TO_ADD_UI_FEATURES.md](HOW_TO_ADD_UI_FEATURES.md) before adding panels, inspector
rows, dialogs or commands.

The editor defaults to Dark Forest, Compact density and UI 100%. **View → Appearance**
switches Dark/Light live across home and detached windows; **View → Density** selects
Compact/Comfortable metrics. **View → UI Scale** also offers 75% and 85%.
Saved appearance and project-local scales keep their stored value.
Appearance preferences save atomically in user-local `appearance.json`, beside the
standalone workspace layout (including the `sle.workspaceData` override). Failed
saves report a notice and preserve the previous file. Project-local scale restore
continues to take precedence when opening a project.

Run `.\gradlew.bat run --args=--theme-gallery` for a standalone presentation fixture
without project data or JNI. The foundation exposes `LocalEditorColors`,
`LocalEditorMetrics`, and `LocalEditorAppearance` under `sle.editor.ui.theme`.
Use these tokens in subsequent UI migrations; load icons through `EditorIcons`.
Roboto headings and body text use bundled licensed resources; numeric inputs retain
monospace. Compose controls and Swing menus/dialogs share this font choice.
Editor confirmations, input prompts and file choosers go through `EditorDialogs` and
`EditorFileChooser` (`ui/EditorDialogAppearance.kt`): they keep `JOptionPane` /
`JFileChooser` results and filters, but style only their own dialog roots instead of
changing global `UIManager` defaults. Use them for new editor dialogs and pass
`null` (or a component) as parent: `EditorDialogOwner` makes the active editor
window the owner, so dialogs always open in front of it. `EditorFileChooser` uses
`EditorFileChooserUI` (Metal layout without the system places bar, editor folder/
file/action icons, slim scrollbars) instead of the system look-and-feel installed
by Compose. On Windows, `EditorWindowChrome` themes every editor window caption
(dark mode on Windows 10 2004+, caption/text/border colors on Windows 11); it is
best effort and otherwise leaves the OS caption. Drive/computer icons in choosers
still come from the OS shell. Final dark/light visual acceptance is still pending.
Workspace tabs, hierarchy and asset folders share compact theme controls and density
metrics. Viewport commands wrap into one band; narrow tab strips scroll to the active
tab. In the asset dock, the toolbar scrolls, Type/Size columns hide as width decreases,
and **Details** exposes metadata and file actions below the list. Wide docks retain
the sidebar. Resize Assets with the existing dock splitter. Saved placements remain
compatible. Selection handles follow the theme's `selectionOutline`; the native C++
grid/axes remain unchanged pending a separate engine task.

Shared compact controls live in `ui/controls/EditorControls.kt`: buttons, toggles,
icon/glyph buttons, flat tabs, toolbars, separators, empty states, text/number/vector
fields, checkbox/select, property rows, sections, panel headers, tooltips and tree
rows. They consume theme metrics, semantic colors and `LocalEditorTypography`
(body, secondary, heading, toolbar, brand, numeric in `ui/theme/EditorTypography.kt`);
feature code should not set font sizes, control heights or colors itself.
`EditorButtonStyle` has three emphases: **Primary** (filled green, the main action
such as Play), **Secondary** (default; restrained fill and subtle outline) and
**Ghost** (no resting chrome; zoom, undo/redo, navigation and dock actions).
Property rows use 36% labels and fill the value column, stacking below 240dp;
labels display humanized keys (`linearDamping` → "Linear damping"). Swing menus
and dialogs size text with `editorAwtFont` from the same tokens and the UI scale
only (Java2D applies OS DPI). Roboto is the UI face; Handjet is reserved for
branding (the empty-project state). The gallery includes tabs, button variants,
interaction-state specimens and 100%/150% font controls.

Numeric presenters obtain `state.numberEdit(component, path, NumberOptions(...))`;
this owner-cached handle preserves drafts through presenter recreation. Valid
values preview live; Enter or valid blur finishes one undo transaction. Escape
restores the entry snapshot. Invalid blur retains the last valid value and a
save-blocking error. JSON decimal syntax is locale independent. Focused undo
cancels the active edit, following existing gesture semantics. Selection-only and
read-only events do not start transactions. Hosts must finish edits on tab/target
changes with the existing `endGesture()` boundary; presenter disposal must not
unconditionally cancel owner state. Scene and prefab inspectors share these bindings. Component/tool/tab changes and
playback entry finish owner gestures; panel disposal does not discard drafts.

Inspector fields use `ui/inspector/EditorPropertyDefinition.kt` and the shared
renderer: booleans, catalog enums, strings, finite numbers, numeric axis objects,
resource references and structured JSON. Vectors share one row; other objects
(including texture regions) retain all keys in a multiline JSON editor. Catalog
metadata supplies choices; no new native schema or numeric limits are inferred.
`EntityWorkspaceState.inspectorSections` owns session-only collapse state by
entity identity/component across Scene/Prefab panels and dock remounts. This
state never enters scene files or undo history. Future component authors should
reuse `InspectorComponent`/`ComponentField` and native catalog metadata.

Run `.\gradlew.bat run --args=--inspector-gallery` for both real inspector paths
without JNI. Add `--capture <directory>` to capture dark/light, 100%/150% font
scale and 900/540px window widths. Full inspector keyboard, detached window and
mixed-DPI acceptance remains a manual check.

The default layout places hierarchy left, viewport center, and selected inspector right. The standard window menu bar uses flat headings, Alt mnemonics, a subtle
bottom border, and the editor surface across its full width. Instructional text
is available in Help instead of beneath toolbars.

The embedded runtime runs the engine's Lua scripts, animation, state machines,
physics, audio, events and XML UI through the same frame update as the standalone
executable. Click Game to focus input; movement controls come from the scene scripts.

- **Play / Resume** starts or resumes execution.
- **Pause** freezes gameplay and audio while retaining the current image/state.
- **Stop** discards runtime changes and restores the authored scene. Play starts
  a fresh runtime snapshot.

Keyboard and mouse input comes from the focused native canvas. AWT key codes are
translated to the existing GLFW-compatible Input codes; Lua continues to read the
engine's Input API. These are event identifiers, not synthesized OS key presses.
Unfocused mouse motion is ignored and focus loss releases held input. The first
click can be used to acquire focus. Wheel/text input are not exposed by the current
engine Input API. VS Code remains the intended external Lua editor.

## Project folders

Use **File → New Project…**, **Open Project…**, **Close Project**, and
**Project Settings…** for an ordinary game folder and readable `.sleproject`
configuration. New projects create `Assets` subfolders and ignored `.sle` local
storage. Moving the complete folder preserves its identity. Project switching
confirms unsaved documents, and failed opens preserve the current workspace.

The asset browser below the workspace reflects real disk contents, including
unknown file types. Navigate folders through its tree, double-click or breadcrumbs;
select a file for details. External changes refresh automatically in the background.
Use **View → Hide/Show Asset Browser** or **Refresh Assets**, and drag the dock's
top divider to resize it. **Import…** copies files/folders into the selected folder,
with Rename/Skip/Replace collision choices. UUID `.slemeta` sidecars preserve
identity through payload edits and moves with sidecars; asset issues report invalid
or duplicate IDs. Selection details offer Reveal, external open and explicit UUID
repair. Component Browse reuses project files or copies external assets into the
project. Plain project scenes/prefabs use UUID references. Choose the startup scene
in Project Settings and use Run Project for the saved whole game. Existing standalone scene/prefab saves remain available. See [project workflow and current scope](../../docs/topics/Projects.md).

## Create and save an entity

The editor opens on **Scene**. Use **File → New → Prefab** for a standalone entity. A new entity starts with the engine's
Transform defaults and an automatically generated UUID key. Enter its **Name**,
right-click the component outline, inspector, or viewport and choose
**Add Component → category → component**, then edit values. Keys stay internal; renaming preserves
identity, and opening existing files retains their original keys. Booleans use checkboxes, enums use menus, vectors expose their
coordinates, and maps/arrays use JSON fields. Components can also be removed.

Add **SpriteRenderer** from **Add Component → Rendering**, then use **Browse…** beside **Texture** to import an image.
The entity viewport updates immediately; use **Fit** to frame it. The image is
copied into project storage when a project is open, or temporary editor storage
in standalone mode, so its original location is no longer needed. Use the right-click **Import → Assets…** menu to include additional files,
even ones not attached to a component.

In standalone mode, use **File → Save** or **Save As…** to write a self-contained `.sleprefab` package containing
the entity and its assets. Move that one file to share it. The chooser also offers
plain JSON export with copied sidecar assets; JSON retains path-based references.
See [package format and dependency rules](../../docs/topics/PrefabPackages.md).
Save validates with the
engine's component codecs before atomically replacing the destination. Incomplete
input stays visible and blocks saving; invalid engine values leave existing files
untouched. New/Open/Close ask before discarding changes. Switching views preserves
the entity document and unfinished field input. One durable native session survives
panel changes; presenters can be replaced without rebuilding its graphics owner. Switching Scene/Game keeps the running session. Stop resets that session.

**Open** accepts single-entity packages and JSON prefab files. Engine normalization exposes omitted
defaults and adapts legacy aliases/vector arrays while preserving their values.
Unknown components, runtime-only fields, unsupported metadata and hierarchies are
rejected with an error rather than silently removed. The component catalog and
validation come from C++, not a second Kotlin schema.

The JSON document is no longer shown in the editor. The viewport displays a grid,
entity origin, sprite bounds, cyan collider outlines and yellow zone outlines.
Edits update the preview automatically; invalid edits keep the last valid visual.
Use **Fit**, **+ / −**, or the mouse wheel to frame/zoom the entity. Preview never
runs gameplay scripts or physics and does not write temporary scene files.

### Viewport controls

In **Entity Editor**, choose a component in the left outline or right inspector,
choose its visual **Tool**, or click a sprite/collider/
zone to select it. Magenta
squares are drag handles. The menu lets you select overlapping shapes precisely.
Collider/zone outlines and selected handles can be picked; their transparent
interiors do not block sprite clicks. Disabled outlines are ignored by automatic
picking, but can still be selected from the menu.

- **Transform:** drag its center handle to move Transform position.
- **BoxCollider / BoxZone:** drag the center to change local offset, or an edge/
  corner to resize with the opposite edge fixed.
- **CircleCollider / CircleZone:** drag the center for offset, or a radius handle.
- **SpriteRenderer:** drag its center to change its local offset; edge/corner
  handles edit its own size. These edits leave Transform and colliders unchanged.
- **Shift** while dragging or **Snap 0.1** snaps authored values to 0.1 units.
  Arrow keys nudge by 0.1 world units; Shift+arrows use 1 world unit.
- **Escape** cancels the current drag. **Undo / Redo**, or **Ctrl+Z / Ctrl+Y** while
  the viewport is focused, restore complete edits. Ctrl+Shift+Z also redoes.

Each drag is one undo step. Inspector, component and imported-asset changes also
enter history; New/Open reset it. Handles account for rotation, mirrored scale
and viewport DPI. Collapsed shapes with a zero scale must be corrected in the
inspector before their collider/size handles can be used. Radius follows the
engine's maximum-scale circle rendering. Rotation, tint and UV values remain
editable in the inspector. All edits are saved in the same prefab components.
Transform position/scale still affect the whole entity intentionally; use Entity
or Transform fields for that. Older prefabs default sprite size to (1,1) and
offset to (0,0), retaining their existing appearance.

The camera stays fixed while editing component values. Middle-drag or
Alt+left-drag pans the editor camera. **Fit** finishes a drag and reframes the
entity. Mouse-wheel zoom is available between drags. Switching tabs retains edits.
Resize during a drag cancels it so picking cannot use stale camera dimensions.

To run the actual desktop manipulation regression:
`gradlew.bat run --args=--gizmo-smoke` (with the native editor bridge built).

Export format (keys generated automatically by the editor):

```json
{
  "root": "9b2f5c08-4b12-4c9b-b910-2f93d4ed0a13",
  "entities": [{
    "key": "9b2f5c08-4b12-4c9b-b910-2f93d4ed0a13",
    "name": "Actor",
    "components": {
      "Transform": {"position": {"x": 12, "y": 34}, "rotation": 0, "scale": {"x": 1, "y": 1}},
      "BoxCollider": {"size": {"x": 20, "y": 30}}
    }
  }]
}
```

Instantiate using `PrefabLoader::instantiate(path, scene, "instanceKey")`, Lua
`Engine.instantiatePrefab`, or include the file in a scene's `prefabs` array; see
[engine prefab rules](../../docs/topics/Prefabs.md). Asset paths follow the
existing engine loader rules; repository-root paths such as `assets/scripts/...`
work when launching from the repo root.

The prefab workspace edits one entity per file; Scene Editor assembles entities
into hierarchies. Both provide undo/redo and asset browsing. Scene gameplay uses
a runtime snapshot and Stop restores authoring. Open
`assets/scenes/editor_camera_demo.slescene` to try a child camera and WASD script.

## Project source documents

With a project open, new scene/prefab Save defaults to ordinary `.scene.json` /
`.prefab.json` source files beneath Assets. Double-click sources in the asset dock
to edit them; Scene Import Prefab accepts project prefabs too. Textures/scripts
use shared sidecar UUIDs, resolved before native preview and Play. Moving the
whole project preserves those references. External changes/deletions block stale
saves and preserve drafts; Reopen or Save As resolves the conflict. File → Export
Package writes a portable archive while retaining the project source binding.
See [project workflow and format](../../docs/topics/Projects.md#scene-and-prefab-editing).
**Run Project** launches the saved startup scene without replacing unsaved drafts;
ordinary Play still runs the open scene. Logical dimensions, VSync and FPS apply
in the viewport. Standalone `engine_app --project <file.sleproject>` also applies
the game window title and mode, and works from an unrelated working directory.
Lua require searches the project Scripts folder and asset root. Scenes register
both document keys and UUID aliases for switching. Project prefabs use disposable
`.sle/runtime` JSON; sources and metadata are never rewritten by native launch.

Run `gradlew.bat run --args=--project-documents-smoke` for Save/browser/Play/
conflict/export/relocation desktop coverage. Use `--project-runtime-smoke` for
startup selection, Run Project/Stop/draft preservation, VSync, deferred scenes,
Lua modules, relocation and the actual standalone project command.

## Source layout

Kotlin sources use `sle.editor` subpackages: `app` for the desktop entry point,
`ui` for Compose workspaces and controls, `model` for authoring documents and state,
`files` for asset/package persistence and sample generation, `viewport` for the
native canvas, picking and gizmos, `bridge` for JNI, and `smoke` for desktop smoke
checks, and `project` for project descriptors, storage and project controls. Tests follow the corresponding domain packages. The application entry
point is `sle.editor.app.MainKt`; JNI exports match `sle.editor.bridge.NativeEngine`.

## Launch from VS Code

Open the repository root in **Windows VS Code**. In **Run and Debug**, select
**SLE Editor** and press **Ctrl+F5** (or F5). Alternatively use
**Terminal → Run Task… → SLE: Launch Editor**.

The shared `run-editor.ps1` launcher finds JDK 21, configures the Debug CMake build
with editor support, builds `sle_editor_native`, and runs the Gradle editor. Build
or runtime output appears in the terminal. Close the editor window to finish.
This launch entry runs the app; it does not attach a Kotlin/JVM debugger.

Install the prerequisites below first. If JDK discovery fails, set `JAVA_HOME` to
64-bit JDK 21 and restart VS Code. Use a Windows workspace rather than Remote WSL,
since the native editor host and build presets target Windows. You can also run
`./tools/entity-editor/run-editor.ps1` directly from PowerShell; `-EditorArgs` passes
editor arguments, for example `-EditorArgs '--project "C:/Games/Game/Game.sleproject"'`.

## Build and run (Windows x64)

Install a 64-bit JDK 21, Visual Studio 2022 C++ tools, CMake and Python/jinja2 as
listed in [CONTRIBUTING.md](../../CONTRIBUTING.md). Use a Windows terminal and a
graphics driver supporting OpenGL 4.3 or later. No Android SDK is required.

From the repository root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
cmake --preset debug -DSLE_BUILD_EDITOR=ON
cmake --build build/debug --config Debug --target sle_editor_native
cd tools/entity-editor
.\gradlew.bat run
```

The editor expects the DLL at
`build/debug/tools/editor-native/Debug/sle_editor_native.dll`. To use another build:

```powershell
.\gradlew.bat run '-PsleNativeLibrary=C:\full\path\to\sle_editor_native.dll'
```

Gradle sets the application's working directory to the repo root for asset
resolution. When running directly from an IDE, set that working directory and
`-Dsle.nativeLibrary=<absolute DLL path>`. Open `tools/entity-editor` as a Gradle
project in IntelliJ IDEA and use JDK 21 as its Gradle JVM.

`SLE_BUILD_EDITOR` defaults to OFF. Ordinary CMake builds still produce
`engine_app` and the sandbox executable with GLFW, without requiring JNI/JDK.
The embedded DLL currently supports Windows x64 only, independently of Debug or
Release optimization; it is a hosting choice, not an `NDEBUG` switch.

## Validation

```powershell
.\gradlew.bat build
.\gradlew.bat run --args="--smoke-test"
.\gradlew.bat run --args="--camera-smoke"
.\gradlew.bat run --args="--context-menu-smoke"
.\gradlew.bat run --args="--project-smoke"
.\gradlew.bat run --args="--asset-browser-smoke"
```

Entity-authoring checks:

```powershell
.\gradlew.bat run --args="--authoring-smoke"
.\gradlew.bat run --args="--authoring-ui-smoke"
.\gradlew.bat run --args="--preview-smoke"
```

The first uses the real engine catalog and JNI validators to create/edit/save/open
an entity, checks UTF-8 names and failed-save preservation, and writes
`build/reports/authored-entity.prefab.json`. The UI check opens the entity workspace
and captures `build/reports/entity-editor.png`; it checks actual desktop collider
pixels in addition to GPU framebuffer pixels. The preview smoke edits a collider,
switches between preview and sandbox repeatedly, verifies visible sandbox pixels,
and checks that UUID identity and an unfinished field draft survive. Unit tests
cover UUID/name behavior, field editing, unfinished numbers, JSON round trips,
filename suggestions and persistence. Native
`integration_entity_authoring_path` checks codec defaults, derived fields, invalid
values, references, legacy/partial import and actual PrefabLoader compatibility.
To load the editor-generated artifact through the native test executable:

```powershell
# From the repository root, after running --authoring-smoke:
.\build\debug\tests\integration\Debug\integration_entity_authoring_path.exe tools/entity-editor/build/reports/authored-entity.prefab.json
```

The finite viewport GUI smoke opens the real Compose viewport, resizes it, renders 60
frames, checks the animated player's and NPC's pixels, and closes. It also
checks JNI session/thread guards, session recreation and OpenGL context deletion,
cycles Pause/Resume/Stop/Run and checks paused frames remain identical. It
fails on initialization/pixel errors or a 30-second timeout. Its framebuffer
captures are `build/reports/embedded-smoke.png` and `sandbox-desktop.png`. Readback
and desktop capture are used only by tests, never for normal viewport presentation.

From the repository root:

```powershell
cmake --build build/debug --config Debug --target integration_runtime_embedded_preview_path
ctest --test-dir build/debug -C Debug -R '^integration_runtime_embedded_preview_path$' --output-on-failure
```

The preview regression checks a host-owned context, scene rendering, invalid
sizes, resize, unchanged authored transforms and repeated Runtime destruction.
`integration_runtime_embedded_play_path` additionally covers Lua/input, animation,
physics, scene switching, pause/resume, and stopped UI rendering without behavior
execution. It checks UI behavior and clicks execute when running and stay frozen
when paused. `integration_entity_preview_path` checks authored collider pixels,
live edits, failed-edit preservation, preview lifecycle isolation and switching
back to gameplay without changing the host context.
It returns skip code 125 if no graphics context can be created. This is separate
from the JNI/Compose smoke, which requires an interactive Windows desktop.

## Ownership

- The workspace owns one `EditorEngineSession`. Its never-shown, packed AWT GL
  owner remains displayable until workspace shutdown. A Swing timer advances once
  per elapsed-time tick even when every presenter is hidden or disposed.
- `EngineCanvas` is a replaceable presenter. Each presenter owns only a shared GL
  context and local read framebuffer; it never creates or destroys the runtime.
  Use **Windows → Game → Detach** or **Move to** to display Scene and Game together.
  Scene remains navigable during Play; only focused Game accepts gameplay input.
  Explicitly opening Entity editing stops playback; focusing its panel during Play
  preserves the runtime.
- All peer/context operations, JNI commands, rendering and presentation run on
  the AWT event thread. The owner draws each requested view into an owned texture,
  completes producer GL work, then presenters blit/swap and complete consumer work
  before reuse. There is one latest frame per view, no CPU image transport or queue.
  Frames carry resize generation, physical/logical sizes and their own camera and
  Game letterbox metrics. Hidden/zero-size/retired presenters invalidate publication
  and release held input; presenter recreation uses a fresh generation.
- Shutdown stops scheduling, releases presenter contexts, then destroys native
  resources and render targets in the durable owner context before retiring it.
  Stop retains the established native-runtime reset to restore authoring and
  project defaults inside the same GL owner; panel disposal never triggers it.
- Project launch still consumes its prepared native handle once. Native runtime
  state retains the launch context until Stop/cleanup. Game presenters apply
  project VSync and the owner scheduler applies its target FPS.
- JNI uses opaque session tokens with thread validation. The embedded runtime
  creates no GLFW window, never polls GLFW and never swaps the host buffers.
  Only one live runtime is allowed because resource/input services have process
  state. The standalone executable retains its existing loop.

Run `gradlew.bat run --args=--multi-viewport-smoke` for real shared-context Scene/
Game rendering, update-count, Fit/camera metrics, pause/resume, peer disposal and
recreation, hidden-view advancement and cleanup checks. Captures/reports are
`build/reports/multi-*`. `--simultaneous-views` opens the ordinary shell with Scene and Game split beside
each other and can be combined with `--camera-smoke`.
`--owner-failure-smoke` checks real owner-error notification and cleanup;
`--smoke-test` also checks Sandbox collider pixels before/after F3.

Implementation progress and verification are recorded in
[the viewport plan](../../docs/plans/embedded-editor-viewport.md) and
[the running sandbox follow-up](../../docs/plans/embedded-sandbox-runtime.md), and
[entity authoring](../../docs/plans/entity-authoring.md), and
[live entity preview](../../docs/plans/entity-preview.md).

Managed project assets: the dock offers New Folder and Recover Deleted; selected
files/folders offer Rename, Move and Delete. Save drafts, stop playback and use New
in both authoring workspaces before move/delete/recovery. UUID sidecars move with
payloads; referenced deletion requires confirmation. Retain `.sle/trash` for
restart-visible recovery. Dynamic Lua/XML/module paths still require manual repair.
See [managed asset details](../../docs/topics/Projects.md#managed-assets).

Runnable game export: build a prepared runtime with
`cmake --build build/debug --config Release --target sle_runtime_bundle`, then use
**File → Export Game…** and choose `build/debug/runtime/Release` plus a new output
folder. Export uses saved project files, preserves drafts and excludes `.sle`
state/trash. Start the delivered `engine_app.exe`; its adjacent `game/` directory
selects the startup project independently of cwd. See
[distribution details](../../docs/topics/Projects.md#export-a-runnable-game).
`run --args=--project-distribution-smoke` exercises actual export and relocated
standalone launch; it requires that Release runtime bundle first.

The opt-in docking graphics experiment runs for about 48 seconds after startup:
`gradlew.bat run --args=--docking-feasibility-smoke` measures CPU image transport;
`gradlew.bat run --args="--docking-feasibility-smoke --docking-feasibility-gpu"`
measures shared-context GPU presentation. It opens two ordinary native decorated
windows without app menus and retains one hidden GL/runtime owner while closing,
recreating, resizing and minimizing presenters. Reports and framebuffer captures
are under `build/reports/docking-*`. It does not change ordinary startup or enable
production docking. See [session-1 measurements and limits](../../docs/plans/editor-docking-plan.md#session-1-completion--handoff--2026-11-13).

## Workspace windows

See the [workspace operating guide](../../docs/topics/EditorWorkspace.md) for
arrangement, shared Play, close behavior and layout recovery.

The **Windows** menu manages Hierarchy, Inspector, Assets, Scene, Game and Entity.
Use Show / Focus to restore a hidden panel, Hide to retain its state, Detach to open
a native window, or Move to to choose a tab group or edge. Groups can also detach
or merge through these commands. Restore Default Layout returns panels to the main
window.

Drag a panel tab to reorder it or merge with another group. Drop near a panel body
edge to split; drop outside all editor windows to detach. The dotted grip to the
left of each tab row moves the whole group. Escape cancels a drag. Previews remain
visible over native viewports. Drag a divider to resize; window and panel minima
follow the UI scale, and layouts too large for the display fall back to tabs.
The `_` panel control minimizes to the current window's scrollable restore strip;
`↗` detaches and `×` hides. Native title-bar minimize affects the entire window.
Layouts save automatically per project in `.sle/workspace-layout.json`. Standalone
layouts use `%LOCALAPPDATA%\SLE\EntityEditor\workspace-layout.json` on Windows
(or `~/.local/share/sle/entity-editor/workspace-layout.json` without LOCALAPPDATA).
`-Dsle.workspaceData=<directory>` overrides standalone storage. Appearance (including standalone UI scale) uses
user-local `appearance.json`; project-local scale behavior is retained. **Windows → Restore Default Layout** saves only the
layout; documents, drafts, undo and UI scale remain intact.

Corrupt or unsupported layouts show a recovery notice and use defaults. Unknown
panels are removed and newly registered panels added once. Windows restore
unminimized in logical screen coordinates, constrained to connected work areas;
negative monitor origins are supported. Display/work-area/DPI changes cancel dock
drags and recover windows during the session. Smaller displays collapse splits to
tabs. Geometry changes save after a short debounce; project switches and exit queue
the latest state in order. Failed atomic writes preserve the previous file.

Only the main window owns app menus and project/asset services. Closing an auxiliary
window hides its panels; Play and document drafts continue. Main exit asks before
discarding dirty documents and shuts down the shared engine once. The Inspector,
Hierarchy and File/Edit commands follow the active authoring mode. Editing a scene
copy and saving still writes the containing scene; standalone prefab Save writes
its own file. Scene and Game share a simulation. Entity preview is a separate
editing mode, entered explicitly when playback must stop.

Run `.\gradlew.bat run --args=--docking-workspace-smoke` for panel registration,
native tool hosts, Robot tab/group dragging across windows, insertion/edge docking,
Escape cancellation, splitter and native minimum sizing, panel/native minimize and
restore, Game redocking during Play with held-input release, retained drafts,
scene-copy Save, exit cancellation and cleanup coverage. Captures are in
`build/reports/session6-{split,group-detached,restored}.png`; logs and the exact
validation record are in the [docking plan](../../docs/plans/editor-docking-plan.md).
Mixed-DPI monitor transitions still require manual validation.


Run `.\gradlew.bat build run --args=--layout-persistence-smoke` for fresh-process
standalone/project isolation, native minimized restore, reset/corruption recovery,
and simulated display changes. Physical monitor unplug/reconnect and mixed-DPI
native transitions still require manual checks.
