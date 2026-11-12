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

The **File**, **Edit**, **Create**, **View**, and **Help** menu strip contains
file operations, separate entity/scene histories, creation/import commands,
Scene/Game/Prefab navigation, Fit, UI scale and controls/About. Use
**File → New/Open → Prefab** for independent prefab authoring. File operations
retain unsaved-change confirmations and portable package validation.

Creation is also available through nested right-click menus. Right-click selects
the tree node before offering root/child creation and **Entity → Duplicate
Subtree**. Menus use the editor palette, vector icons and UI scale. Hierarchy
expanders and entity/camera icons are drawn vectors rather than font glyphs.

Open `assets/scenes/editor_camera_demo.slescene` for the included child-camera,
WASD and physics example, or `assets/scenes/editor_starter.slescene` for ground
and crates. Save produces a portable `.slescene` with assets, loadable by the
engine. See [scene workflow](../../docs/topics/SceneEditor.md).

The layout defaults to UI 85%; **View → UI Scale** also offers 75% and 100%.
The hierarchy stays left, viewport center, and selected inspector right. The standard window menu bar uses flat headings, Alt mnemonics, a subtle
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

## Create and save an entity

The editor opens on **Scene**. Use **File → New → Prefab** for a standalone entity. A new entity starts with the engine's
Transform defaults and an automatically generated UUID key. Enter its **Name**,
right-click the component outline, inspector, or viewport and choose
**Add Component → category → component**, then edit values. Keys stay internal; renaming preserves
identity, and opening existing files retains their original keys. Booleans use checkboxes, enums use menus, vectors expose their
coordinates, and maps/arrays use JSON fields. Components can also be removed.

Add **SpriteRenderer** from **Add Component → Rendering**, then use **Browse…** beside **Texture** to import an image.
The entity viewport updates immediately; use **Fit** to frame it. The image is
copied into editor storage, so its original location is no longer needed. Use the right-click **Import → Assets…** menu to include additional files,
even ones not attached to a component.

Use **File → Save** or **Save As…** to write a self-contained `.sleprefab` package containing
the entity and its assets. Move that one file to share it. The chooser also offers
plain JSON export with copied sidecar assets; JSON retains path-based references.
See [package format and dependency rules](../../docs/topics/PrefabPackages.md).
Save validates with the
engine's component codecs before atomically replacing the destination. Incomplete
input stays visible and blocks saving; invalid engine values leave existing files
untouched. New/Open/Close ask before discarding changes. Switching views preserves
the entity document and unfinished field input. One native canvas/context survives
tab changes; switching modes reloads scene data without rebuilding the graphics
host. Switching Scene/Game keeps the running session. Stop resets that session.

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

## Source layout

Kotlin sources use `sle.editor` subpackages: `app` for the desktop entry point,
`ui` for Compose workspaces and controls, `model` for authoring documents and state,
`files` for asset/package persistence and sample generation, `viewport` for the
native canvas, picking and gizmos, `bridge` for JNI, and `smoke` for desktop smoke
checks. Tests follow the corresponding domain packages. The application entry
point is `sle.editor.app.MainKt`; JNI exports match `sle.editor.bridge.NativeEngine`.

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

- Compose hosts one AWTGLCanvas directly inside SwingPanel. A Swing timer schedules
  frames on the AWT event thread; the canvas owns context creation and swaps.
  The host paints an immediate dark loading surface. Sandbox audio initializes
  only after its first frame is presented, so device setup does not delay that image.
- The canvas uses lwjgl3-awt 0.2.5 with LWJGL 3.4.2. The earlier canvas release
  produced valid framebuffer pixels but failed actual Windows presentation in
  desktop tests. Its replacement keeps rendering/presentation on the locked native
  drawing surface and provides a context-current `disposeGL` cleanup callback.
- JNI creates one opaque engine session, renders and destroys it with that
  context current. Exceptions become Java errors; stale handles and calls from
  another thread are rejected. `disposeGL` destroys SLE resources before the canvas
  releases its context and AWT peer.
- `Runtime::initEmbedded()` reuses engine initialization without creating a GLFW
  window. `tickEmbeddedFrame()` advances the same engine systems as the standalone
  loop; `renderEmbeddedFrame()` draws a stopped/paused scene without gameplay or UI
  callbacks. `setEmbeddedPaused()` suspends audio as well as simulation. A logical 480x270 camera scales to the physical
  framebuffer for the sandbox. Entity preview uses an aspect-correct fitted logical
  viewport without changing authored transforms. `EntityPreviewSystem` submits
  grid/origin/bounds render commands; scene queries stay in Systems.
- The embedded path never polls GLFW or swaps its buffers. GLFW remains linked
  through the existing Platform module, but no GLFW window is created.
- Only one embedded session is allowed because engine resource/input services
  currently have process-wide state. Native crashes can terminate the editor.

Implementation progress and verification are recorded in
[the viewport plan](../../docs/plans/embedded-editor-viewport.md) and
[the running sandbox follow-up](../../docs/plans/embedded-sandbox-runtime.md), and
[entity authoring](../../docs/plans/entity-authoring.md), and
[live entity preview](../../docs/plans/entity-preview.md).
