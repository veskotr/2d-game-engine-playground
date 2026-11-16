# Editor docking workspace design

Status: agreed product design recorded for implementation planning, 2026-11-13.
Implementation has not started. Technical defaults below are planning decisions,
not claims about existing capabilities.

## Intent and agreed behavior

One editor workspace should support rearranging all content panels, including
Scene and Game, without creating additional editor instances. Scene and Game
must be independently visible on different monitors and show one simulation.
This is also the foundation for later Logs, Console and Events panels; implementing
those tools is outside this project.

- One main home window owns File/Edit/Create/View/**Windows**/Help, the project,
  documents, dirty confirmation and application lifetime.
- Auxiliary tool windows have a minimal native title bar and normal OS resize,
  minimize and close controls, but no application menu. They are part of the same
  workspace, not independent editors. Do not force them always on top.
- Every content panel can move, detach, redock, join a tab group, or dock beside
  another group. A tab group can also detach as a unit. Split dividers resize.
- Panel minimize sends it to a restore strip in its current window. Native window
  minimize affects the whole auxiliary window. Hide removes a panel from the
  visible layout; **Windows** can reopen it. None of these discards documents.
- **Windows** lists registered panels, indicates visible/hidden/minimized state,
  opens/restores/focuses them, lists auxiliary windows for focus, and offers
  **Restore Default Layout**. Selecting an already open panel never duplicates it.
- Closing a tool window hides its panels without stopping Play, deleting drafts,
  changing project or exiting. Closing the main window uses the existing unsaved
  confirmation, then closes every auxiliary and destroys the engine exactly once.
- Panels have minimum usable sizes. Group minima account for tabs/chrome and all
  member panels; split minima compose from their children. Do not allow negative
  content space or make controls unreachable when the screen is smaller than the
  preferred layout: recover to tabs on the available monitor instead.
- Layout persists between sessions. Restore must recover windows after monitor
  removal, DPI changes or invalid local settings. Reset restores layout only.

The application menu itself and modal dialogs are not detachable panels. Toolbars
belong to their panel and travel with it. Playback is workspace-owned; controls
can appear in Scene/Game, but operate on one playback state.

## Current implementation and constraints

The editor is Kotlin/JVM Compose Desktop, using Swing/AWT integration and LWJGL
OpenGL. `app/Main.kt` builds fixed left hierarchy, center native viewport, right
inspector and bottom project assets. `ui/EditorMenuStrip.kt` installs a real
Swing menu bar on the main JFrame. `viewport/SharedViewport.kt` constructs an
`EngineCanvas` at a stable Compose slot.

`EngineCanvas` owns a timer, the GL context and a JNI session token. `removeNotify`
disposes the GL context and native runtime. Scene/Game currently switch a flag on
that single canvas. `EditorBridge.cpp` explicitly rejects a second runtime because
Resources and Input are process-wide. `Runtime::renderSceneView` calls
`tickEmbeddedFrame` while playing, so rendering twice currently advances twice.
There is also host-size-dependent input/letterboxing state. These are required
refactor boundaries, not reasons to start another runtime per window.

- Keep Kotlin 2.4.21, Compose 1.12.2, JVM 21 and the existing C++20/OpenGL stack.
- Keep one embedded native Runtime and one simulation update per workspace frame.
- Preserve module dependency direction; Systems owns runtime orchestration.
- Keep GL creation, use and destruction on its owning context/thread.
- Preserve authored documents, drafts, selection, undo and playback on panel moves.
- Preserve standalone engine behavior, file formats and project export.
- Preserve the worktree's existing modified and untracked project/runtime work.
- Use Windows tooling for supported builds and actual desktop verification.

## Approaches

1. **Extend the existing Compose editor with a docking model and shared rendering
   owner (selected).** Reuses controls, documents, menus and project services.
   Requires explicit cross-window drag/drop, sizing, session ownership and view
   presentation. Provides incremental checkpoints without a UI migration.
2. **Replace the editor shell with a docking UI framework.** Could supply docking
   primitives but would require migrating existing controls and native hosting.
   Rejected for the scope and compatibility cost.

Separate runtimes per viewport are excluded: Scene and Game must observe the same
physics, Lua, audio, events and deferred scene switches.

## Proposed architecture

### Workspace state and panel registration

`EditorWorkspace` owns existing scene/prefab/project/playback state and shared
commands, independently of Compose panel lifetime. Extract ownership from Main
without rewriting the established document and asset services.

Each singleton panel has a stable string ID, title, minimum logical size, default
placement and a renderer binding. Initial IDs: `hierarchy`, `inspector`, `assets`,
`scene`, `game`, `entity`. Hierarchy/Inspector continue to adapt to the active
authoring mode. Entity contains the existing component/prefab editor view; opening
entity editing retains its existing stop-playback rule. Scene and Game are separate
panels, not two renderers hidden behind one selected-mode flag. Selecting a panel
does not silently stop Play. Focus determines the target of compatible editing
commands; File Save retains the existing scene-copy versus prefab rules.

Pure layout state contains a main host, auxiliary hosts, split nodes and tab-group
nodes. Panel IDs occur at most once across visible groups, minimized strips and
hidden placement records. A hidden panel retains a preferred placement for reopen.
Tab-group identity survives tab changes; removing its final panel collapses empty
branches. A failed/canceled move leaves the original layout intact. Panel content
factories receive shared state; moving a panel does not construct an editor.

Default layout: hierarchy left, Scene/Game/Entity in a center tab group, inspector
right, Assets below (available when a project is open). Future panel descriptors
can be added without modifying docking algorithms. Do not add empty production
Logs/Console/Events panels now; a synthetic test descriptor verifies extension.

### Window and input ownership

A single window manager maps host IDs to native windows. Tool windows use native
decorations without app menus; preserve taskbar/focus usability and avoid modal or
always-on-top ownership semantics that obstruct other applications. Verify actual
Windows behavior rather than assuming a Compose `Window` is an owned tool window.

Drag operations use screen coordinates and explicit source/target IDs, never a
single window's local coordinates. Show tab insertion and edge split previews.
Dropping outside any host detaches; Escape cancels. Divider dragging clamps to
computed minima. Document keyboard shortcuts are routed once to the active panel;
Game input is accepted only from focused Game content. Focus loss, hide, minimize,
detach and close release held game input and finish/cancel authoring gestures.

### Stable engine owner and multiple view presentation

Introduce `EditorEngineSession` with one durable GL owner independent of visible
panel peers. Only this owner creates/destroys the runtime, drives input and updates,
and handles project launch and preview loading. Moving or closing a viewport only
changes its presentation registration. No visible canvas owns engine lifetime.

Separate advance from draw in the embedded runtime. The session drains input once,
advances simulation once if running, then draws requested views without simulation
updates. Scene uses the editor camera and overlays; Game uses the active gameplay
camera and aspect-preserving viewport. Both draw the same post-update scene.
Paused frames may redraw; they do not advance scripts, physics or audio. Drawing
must not overwrite the viewport metrics subsequently used by another view's input.
Keep game logical dimensions independent of Scene panel size.

The initial transport proposal is render-to-texture in the stable owner, with
bounded latest-frame publication to panel presenters. Start with CPU image
publication if it meets the feasibility check; a GPU presenter using shared
contexts is the fallback when measured copy costs fail it. Never create another
Runtime to solve presentation. Run a bounded feasibility milestone first, recording
the selected mechanism, context lifecycle and measurements before building against
it. This plan deliberately does not assume AWT peers survive reparenting.

Owner scheduling must continue while Play runs even if all viewport panels are
hidden or minimized. Skip unnecessary image production for hidden views. Bound
in-flight frames to one latest image per view, release superseded buffers and
handle resize generations so stale frames/input dimensions cannot be used.
Shutdown stops scheduling before releasing native resources in their owner context.

### Persistence and recovery

Use a versioned `workspace-layout.json` separate from `ProjectEditorState`'s current
16 KiB UI-scale file. Store project layouts beneath `.sle/`; standalone mode uses
the editor's per-user application-data directory. No paths, scene contents, JNI
tokens or GL objects in layout JSON. Read/write off the UI thread with atomic
replacement and revision checks so old writes cannot overwrite a newer project.

Store host bounds in logical screen coordinates with monitor identity/work-area
hints, split ratios, active tabs, minimized and hidden placement records. Proposed
limits: 1 MiB file, 32 auxiliary hosts, 256 nodes, depth 32. Reject malformed or
unsupported-version state with a visible recoverable notice and default layout;
ignore unknown panel IDs, add new registered panels once, normalize empty groups.
Keep recovered windows fully usable on a connected work area, including negative
monitor coordinates. Restore tool windows unminimized. Do not persist native
window minimize as panel minimize. Reset must not clear UI scale or other settings.

### Proposed sizing defaults (builder may tune after desktop checks)

Panel content minimums in logical dp: Hierarchy 200×160, Inspector 260×200,
Assets 360×180, Scene 320×200, Game 320×200, Entity 320×240. Add actual tab bars,
restore strips and native insets when computing host minimums. Smaller displays
recover to tab groups rather than restoring impossible side-by-side bounds.

## Acceptance and validation

The plan must produce automatic layout transition/invariant/persistence tests,
simulation advance-versus-render tests, and real Windows desktop smoke coverage.
Headless model tests cannot establish OpenGL peer lifetime, focus or multi-monitor
usability. Existing scene, camera, context-menu, asset, project-document and runtime
desktop checks must remain meaningful after the shell changes; adapt their hooks,
do not delete assertions to make the new shell pass.

Required final observations: Scene and Game visible simultaneously; one game update
per frame regardless of view count; independent camera/size; detach/redock/close
while playing without runtime recreation; tab groups and split resizing; minimize
and reopen; document/undo/draft preservation; project-switch and shutdown cleanup;
layout restore/reset; monitor-disconnect/DPI recovery; future panel registration.

No implementation or test pass is claimed by this document. The linked
[implementation plan](editor-docking-plan.md) owns session handoffs and evidence.
