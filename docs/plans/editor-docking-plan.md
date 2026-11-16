# Editor Docking Workspace Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement one session milestone at a time. Each milestone below has its own handoff. Do not continue to the next milestone unless the user requests it. Independent review may use a reviewer when authorized by the applicable skills.

**Goal:** Make all editor content panels dockable, tabbed, detachable and restorable, with simultaneous Scene/Game views of one simulation.

**Architecture:** Keep Compose Desktop and existing authoring/project services. Use a pure layout model plus one workspace window manager. Separate the native simulation/GL owner from panel lifetime and separate simulation advance from view drawing.

**Tech Stack:** Kotlin 2.4.21, Compose Desktop 1.12.2, JVM 21, Swing/AWT, LWJGL, JNI, C++20, existing CMake/CTest and Gradle tests.

**Spec:** [Agreed design and technical defaults](editor-docking-design.md).

**Session prompts:** [Copyable handoffs](editor-docking-handoffs.md).

## Global constraints

- Keep Kotlin 2.4.21, Compose 1.12.2, JVM 21 and the existing C++20/OpenGL stack.
- Keep one embedded native Runtime and one simulation update per workspace frame.
- Preserve module dependency direction; Systems owns runtime orchestration.
- Keep GL creation, use and destruction on its owning context/thread.
- Preserve authored documents, drafts, selection, undo and playback on panel moves.
- Preserve standalone engine behavior, file formats and project export.
- Preserve the worktree's existing modified and untracked project/runtime work.
- Use Windows tooling for supported builds and actual desktop verification.
- One main application menu; auxiliary windows never instantiate another editor.
- Closing/hiding/minimizing viewport panels must not stop Play or discard drafts.
- Session 1 has an opt-in prototype and measured graphics evidence; see its dated
  completion record. Sessions 2–8 are recorded below; final automated acceptance
  passed, with native mixed-DPI/unplug checks explicitly unavailable.

## Review focus

1. AWT peer removal or auxiliary closure during Play must not delete the owning GL context/runtime (sessions 1, 4, 5).
2. Two visible views, zero visible views, deferred scene switches and pause must still advance gameplay exactly once per owner tick (sessions 3, 4).
3. Focus loss and drag across DPI boundaries must release game input and avoid stale camera/pixel mappings (sessions 4, 6, 8).
4. Corrupt layout, negative monitor coordinates and disconnected displays must yield accessible windows without altering project data (sessions 7, 8).
5. Project changes or canceled main-window exit during asynchronous saves/dragging must preserve workspace identity, drafts and runtime ownership (sessions 5, 7, 8).

## Read first and current evidence

Read root `AGENTS.md`, `README.md`, `CONTRIBUTING.md`, `PLANS.md`,
`docs/CONSTRAINTS.md`, `docs/topics/SceneEditor.md`,
`tools/entity-editor/README.md`, and this spec/plan. Read `tests/AGENTS.md`
before native tests. Check scoped instruction files again in each new session.

Current integration points:

| Existing file | Responsibility and relevant behavior |
|---|---|
| `tools/entity-editor/src/main/kotlin/sle/editor/app/Main.kt` | Fixed shell, remembered authoring/project state, tab selection, smoke hooks, shutdown |
| `.../ui/EditorMenuStrip.kt` | Swing main menu and application command construction |
| `.../ui/SceneWorkspace.kt`, `SceneTree.kt`, `EntityWorkspace.kt`, `EntityOutline.kt` | Existing panel contents to retain |
| `.../project/assets/AssetBrowserPanel.kt` | Existing assets content and fixed-bottom-dock presentation |
| `.../model/ScenePlaybackState.kt` | Snapshot/play/pause/stop, currently includes shared game-view selection |
| `.../viewport/SharedViewport.kt` | Creates a canvas through SwingPanel |
| `.../viewport/EngineCanvas.kt` | Owns GL/native session, timer, view selection, input and gizmos; peer disposal destroys runtime |
| `.../bridge/NativeEngine.kt` | JNI API |
| `tools/editor-native/EditorBridge.cpp` | One runtime token, context-thread validation, queued input, coupled render/tick |
| `EngineModules/Systems/src/Runtime.cpp` | `renderSceneView`, `tickEmbeddedFrame`, `advanceAndRender`, `renderEmbeddedFrame`, UI update and rendering |
| `.../project/ProjectFiles.kt`, `ProjectControls.kt` | Existing 16 KiB UI-scale settings; preserve them separately |

In this table `...` means `tools/entity-editor/src/main/kotlin/sle/editor`.
Proposed files below use paths relative to that same Kotlin package unless an
absolute repository-relative prefix is shown. Test files mirror the package under
`tools/entity-editor/src/test/kotlin/sle/editor`.

There are many pre-existing uncommitted edits and new files, including Main,
EngineCanvas, native Runtime/bridge, project services and docs. Do not use reset,
clean, blanket staging, or a new checkout that loses those changes. Git commits
are optional checkpoints only when requested and safely separable; disk handoffs
are mandatory. Historical validations in other plans do not validate this work.

## Execution order and session boundaries

| Session | Deliverable | Prerequisite | Stop condition |
|---|---|---|---|
| 1 | Rendering/window feasibility result | None | Transport and GL owner mechanism proven or concrete blocker recorded |
| 2 | Pure docking state and invariants | 1 | Model tests pass; existing editor still boots unchanged |
| 3 | Native advance/draw separation | 1 | Multiple draws demonstrably do not advance gameplay |
| 4 | Durable engine session and multiple presenters | 3 | Scene/Game together survive presenter removal |
| 5 | Registered panels, main/tool windows, Windows menu | 2, 4 | All content in workspace; command-based docking and lifecycle work |
| 6 | Drag docking, tabs, splitters, size limits, minimize | 5 | Real cross-window interaction smoke passes |
| 7 | Layout persistence and display recovery | 6 | Restart/reset/corrupt-layout/project-switch tests pass |
| 8 | Full compatibility and final handoff | 7 | Acceptance matrix verified; limitations recorded |

Execute in listed order for separate sessions. Sessions 2 and 3 have independent
code boundaries, but parallel execution is unnecessary. Do not bundle the native
refactor and full shell replacement into one session. If a milestone is too large,
split it with a new explicit handoff and preserve its original acceptance gate.

## Session 1 — Prove stable graphics ownership and presentation

**Files:** Create bounded prototype under `viewport/experimental/` and
`smoke/DockingFeasibilitySmoke.kt`; hook an opt-in
`--docking-feasibility-smoke` in `app/Main.kt`. Write findings in this plan.
No default shell or runtime behavior change in this milestone.

**Consumes:** Existing single-session JNI API and Windows AWT/OpenGL integration.
**Produces:** A recorded choice of durable GL owner and frame transport, prototype
paths, actual timings/memory behavior, and exact lifecycle/thread contract for session 4.

- [x] Build a minimal opt-in harness with one durable context and two separately
  closable native windows presenting distinguishable frames. Prove context use
  without dependence on a visible viewport's `isShowing` timer gate.
- [x] Try owner-context render targets plus bounded latest-frame CPU image
  publication first. No JNI runtime duplication. Keep GL objects in owner context;
  presenters receive immutable frame data and dimensions, not Runtime ownership.
- [x] Measure two 1280×720 logical views at 60 owner ticks/second, UI 85%, after
  warmup for 30 seconds. Record hardware, pixel dimensions, copy/presentation
  timings, owner tick count, memory growth and input responsiveness. Planning
  target: p95 copy/publication cost across both views <=8 ms, no unbounded queue
  or sustained buffer growth after warmup. This is a transport target, not a promise
  that arbitrary game scenes run at 60 FPS.
- [x] Close/reopen each presenter, detach/recreate its peer, resize through zero
  dimensions, minimize both, then shut down. Owner context identity remains stable;
  session create/destroy counts are one each; no stale frame draws after disposal.
- [ ] Complete manual taskbar/other-app stacking and physical-input validation.
  Native NORMAL decorations, no menus, ownerless/nonmodal flags and focus API
  verified; OS mouse automation inconclusive. Verify native tool-window decorations and focus/taskbar behavior. An auxiliary
  must not have app menus, become modal, or remain forced above unrelated apps.
- [x] If CPU transport misses the measured target, try shared-context GPU
  presentation in this same opt-in harness and record synchronization/cleanup.
  If neither meets lifecycle/performance requirements, stop with a reproducible
  blocker and revised proposal. Do not silently accept a second engine or replace
  the UI framework.
- [x] Record the mechanism chosen, benchmark evidence, exact Windows commands,
  artifacts, limits, and session-4 interface amendments. Prototype stays opt-in;
  only validated pieces may graduate into production in session 4.

**Run:** Windows Gradle `build`, then `run --args=--docking-feasibility-smoke`.
**Pass:** Feasibility report includes all lifecycle observations and transport
measurements; ordinary editor still follows its existing startup path.
**Handoff:** Use session 1 prompt in the handoff document; update its completion
record and this ledger before stopping.

## Session 2 — Pure layout model and panel descriptors

**Create:** `workspace/PanelRegistry.kt`, `DockLayout.kt`, `DockOperations.kt`,
`DockSizing.kt`; tests `workspace/DockLayoutTest.kt`, `DockSizingTest.kt`.

**Consumes:** Stable singleton panel IDs and sizing defaults from the design.
**Produces:** Pure, serializable-by-codec layout values and atomic operations:

```kotlin
@JvmInline value class PanelId(val value: String)
@JvmInline value class HostId(val value: String)
@JvmInline value class GroupId(val value: String)
data class PanelMinimum(val width: Float, val height: Float) // logical dp
data class PanelDescriptor(val id: PanelId, val title: String,
    val minimum: PanelMinimum, val available: Boolean = true)
enum class DockEdge { Left, Right, Top, Bottom }
sealed interface DockTarget // Tab(group,index), Split(group,edge), Detached(bounds)
sealed interface DockNode // Tabs(group,panels,active), Split(axis,ratio,first,second)
data class DockLayout(/* hosts, hidden placements, minimized placements */)
fun defaultDockLayout(panels: List<PanelDescriptor>): DockLayout
fun movePanel(layout: DockLayout, panel: PanelId, target: DockTarget): DockLayout
fun moveGroup(layout: DockLayout, group: GroupId, target: DockTarget): DockLayout
fun hidePanel(layout: DockLayout, panel: PanelId): DockLayout
fun minimizePanel(layout: DockLayout, panel: PanelId): DockLayout
fun restorePanel(layout: DockLayout, panel: PanelId): DockLayout
fun validateLayout(layout: DockLayout, panels: Set<PanelId>): List<String>
```

Add small value types for logical bounds/axis/placement as needed; final constructor
fields and validation errors must be recorded here before downstream work. Keep
Compose content factories out of pure layout values and tests.

- [x] Write failing tests: a panel exists exactly once after move/hide/minimize/
  restore; tab reorder preserves active panel; moving last tab collapses empty
  splits; invalid destination preserves source; group move preserves order/active
  tab; restoring already visible panel does not duplicate it; unknown IDs fail
  safely; a synthetic future panel works without algorithm changes.
- [x] Run `test --tests sle.editor.workspace.DockLayoutTest`; confirm expected
  assertion/compile failure for missing behavior, not broken test environment.
- [x] Implement the pure model/operations, including source-removal before target
  normalization, main host retention and deterministic IDs in tests.
- [x] Write and implement minimum-size tests: horizontal split sums widths and
  maximizes heights; vertical split reverses those rules; tabs take maximum member
  minima plus measured chrome; ratios clamp; impossible display becomes tabbed.
- [x] Run both test classes, then full Gradle `build`. Record actual results and
  public signatures. Leave default editor shell unchanged.

**Pass:** Deterministic invariant and sizing tests pass without native DLL/GL.
**Handoff:** Session 2 completion record lists final types, defaults, tests and
downstream migrations; do not start native refactoring in this session.

## Session 3 — Advance simulation once, render many views

**Modify:** `EngineModules/Systems/include/sle/engine/Runtime.hpp`,
`EngineModules/Systems/src/Runtime.cpp`, `tools/editor-native/EditorBridge.cpp`,
`bridge/NativeEngine.kt`, `tests/integration/CMakeLists.txt`.
**Create:** `tests/integration/systems/integration_embedded_multiview_path.cpp` and
minimal test scene/Lua fixture under `tests/data/` if needed.

**Consumes:** Existing system update order and existing embedded entry points.
**Produces:** New native operations, preserving legacy wrappers:

```cpp
Result<bool> advanceEmbeddedFrame(float dt);
Result<bool> drawSceneView(int width, int height, bool gameView, bool fit);
```

```kotlin
external fun advanceFrame(session: Long, dt: Float)
external fun drawSceneView(session: Long, width: Int, height: Int,
    gameView: Boolean, fit: Boolean)
```

The old `tickEmbeddedFrame` and JNI `renderSceneView` stay compatibility wrappers
until callers migrate. Add per-view returned metrics in session 4 instead of
letting presenters read mutable last-render globals.

- [x] Read actual `advanceAndRender`, `renderEmbeddedFrame`, UI update/draw and
  standalone loop. Identify every update/event/deferred-switch side effect before
  extracting. Preserve established order; do not move UI event updates into every
  draw. Game camera/chunk activity must not depend on which view draws first.
- [x] Add a windowed integration test with a scripted update counter, moving
  physics body and controlled dt: advance once, draw Scene then Game, verify one
  update and identical runtime transforms; draw again without advance and verify
  no update/physics change. Repeat reversed draw order.
- [x] Build/run the narrow test and confirm the intended failing regression.
- [x] Extract simulation/update from render submission. Keep input drain once at
  JNI advance, UI interaction updates once, pause/audio behavior unchanged. Use
  scoped restoration of view flags/camera/debug/framebuffer state on errors.
- [x] Extend regression for pause (no updates across draws), invalid dt/dimensions,
  deferred scene switching once, Game camera selection/letterboxing independent
  of Scene dimensions, and the existing one-view wrapper behavior.
- [x] Build `sle_editor_native`, `engine_app`, and the integration target; run the
  narrow CTest, relevant Systems tests, then existing `--camera-smoke` and
  `--project-runtime-smoke` against the rebuilt bridge.
- [x] Record update order and final JNI/API contracts for session 4. Update affected
  Runtime/Systems documentation when describing the new contract.

**Pass:** Render-only calls have no simulation/event advancement, both orders agree,
legacy single-view and standalone paths retain behavior.
**Handoff:** Record native build configuration, DLL path, actual checks and any
unavailable windowed checks. Do not declare this gate passed using a headless test.

## Session 4 — Durable engine owner and independent Scene/Game presenters

**Create:** `viewport/EditorEngineSession.kt`, `ViewFrame.kt`, `ViewportPresenter.kt`,
`smoke/MultiViewportSmoke.kt`; tests `viewport/EditorEngineSessionTest.kt`.
**Modify:** `viewport/EngineCanvas.kt`, `SharedViewport.kt`, `GizmoOverlay.kt`,
`model/ScenePlaybackState.kt`, `app/Main.kt`, bridge/native files if metrics require it.

**Consumes:** Sessions 1 and 3; keep session-1 validated context/transport choice.
**Produces:** Workspace-owned session and replaceable presenters. Proposed public
contract (record exact field types after feasibility, before session 5):

```kotlin
enum class ViewId { Scene, Game, Entity }
interface EditorEngineSession : AutoCloseable {
    fun setViewRequest(view: ViewId, request: ViewRequest?) // null unsubscribes
    fun latestFrame(view: ViewId): ViewFrame?
    fun enqueueInput(event: ViewInput)
    fun releaseInput()
    override fun close() // idempotent, owner-context cleanup
}
```

`ViewRequest` includes physical pixel size, logical size, visibility and generation.
`ViewFrame` includes view ID, sequence, request generation, rendered dimensions,
camera/game-rect metrics and bounded transport payload. `ViewInput` includes view
ID/generation; reject stale dimensions. Existing content/playback/project/camera
commands move behind session methods, retaining their semantics.

- [x] Fake-backend tests fail for two updates per tick, runtime destruction on
  presenter unsubscribe, stale resize generations, unbounded frame queues, hidden
  views stopping simulation, repeated close, and project-launch handle leaks.
- [x] Implement the durable owner independently of visible peer lifecycle; one
  owner scheduler drains input/commands, updates once, renders requested views,
  and publishes only latest frames. Test owner exceptions stop scheduling safely.
- [x] Migrate existing EngineCanvas command/input/gizmo behavior. Preserve accurate
  hit tests, camera pan/zoom, authoring-ready checks, overlay rendering and undo;
  finish or cancel gestures before presenter removal. No GL calls in Compose paint.
- [x] Introduce independent Scene/Game presenters using one session. Scene may pan
  during Play; Game accepts input only when focused. UI/game settings continue to
  apply. Entity preview preserves the current stop-playback transition.
- [x] Add `--multi-viewport-smoke`: two visible views with distinct cameras and
  dimensions; script/physics motion shared; Play/Pause/Resume/Stop; resize/dispose/
  recreate either presenter while running; all presenters hidden while simulation
  keeps advancing; zero extra audio/runtime instances; shutdown destroys once.
- [x] Run Gradle tests/build, rebuilt native test from session 3, multi-view smoke
  and existing camera/gizmo/scene/runtime smokes. Inspect captures of both views.
  Remove obsolete prototype only after preserving its evidence and useful smoke.

**Pass:** Session lifetime is independent of any visible panel or window; one
simulation produces simultaneous independent Scene/Game images and correct input.
**Handoff:** Document scheduler thread, owner context, buffer ownership, final
interface, test hooks and runtime create/destroy counts.

## Session 5 — Workspace shell, registered content and Windows menu

**Create:** `workspace/EditorWorkspace.kt`, `WorkspacePanels.kt`,
`WorkspaceWindowManager.kt`, `DockHost.kt`, `WindowsMenu.kt`,
`smoke/DockingWorkspaceSmoke.kt`; test `workspace/WorkspaceLifecycleTest.kt`.
**Modify:** Main, EditorMenuStrip, ScenePlaybackState, SharedViewport and the
existing UI/asset panel files only as needed to expose reusable content.

**Consumes:** Session 2 layout operations, session 4 session/presenters.
**Produces:** One workspace, a main host and managed auxiliary hosts. Command
entry points: `showPanel(PanelId)`, `focusPanel(PanelId)`,
`closeHost(HostId)`, `resetLayout()`. UI state observes pure layout changes.

- [x] Write lifecycle tests: close tool host hides panels without stop/draft loss;
  reopen restores singleton; canceled home exit preserves all hosts/session;
  accepted exit closes session once; failed project open preserves existing state;
  successful project switch refreshes panel contents without spawning a workspace.
- [x] Extract ownership from Main into a workspace lifetime, retaining project
  services, background asset scans, source conflicts and document-save routing.
- [x] Bind all six panel IDs to existing content with panel-local toolbars. Expose
  Scene and Game together; make active authoring context explicit for Inspector,
  Hierarchy and Edit/Save commands. File Save on scene-copy entity edits saves the
  containing scene; standalone prefab Save retains its document.
- [x] Implement main and tool host rendering, including tab selection and
  command-based move/detach/redock while drag gestures await session 6. Tool hosts
  have native decorations and no app menu. Do not duplicate timers/services per host.
- [x] Add **Windows** menu with panel states, open/restore/focus, tool-host focus,
  Restore Default Layout. Route old View → Scene/Game and asset show/hide actions
  through the registry so legacy commands cannot disagree with layout visibility.
- [x] Exercise `--docking-workspace-smoke`: hide/restore each panel, detach Scene
  and Game, close tool windows during Play, edit/save a scene copy, preserve an
  invalid prefab draft, cancel main exit, then shut down cleanly.
- [x] Run full Gradle build and relevant existing editor smokes. Adapt stable smoke
  queries to panel IDs and workspace session; retain behavioral assertions.

**Pass:** All content is registered and movable through commands; one home owns
menus/lifecycle; auxiliary close is reversible and runtime/document-safe.
**Handoff:** Record panel context rules, native host type/ownership, window-close
paths and smoke instrumentation before adding drag interactions.

## Session 6 — Interactive docking, resize and minimize

**Create:** `workspace/DockDragController.kt`, `DockDropTargets.kt`,
`DockChrome.kt`; tests `workspace/DockDragTest.kt`.
**Modify:** DockHost, DockSizing, WorkspaceWindowManager and docking workspace smoke.

**Consumes:** Existing atomic model operations and host/panel registry.
**Produces:** Cross-window tab/group drag, insertion/edge previews, split dividers,
panel restore strips and host minimum-size enforcement.

- [x] Test screen-to-host conversion using unequal scale factors and negative
  coordinates; cancel/outside/stale-host drops preserve source; self/group drops
  cannot create cycles or duplicate panels; tab insertion order is stable.
- [x] Implement a workspace drag controller using screen coordinates and a
  snapshot/revision of the source layout. Render drag previews outside native
  viewport occlusion. Commit once on drop; Escape/capture loss cancels safely.
- [x] Add tab reorder, tab-to-group merge, edge split and outside-host detach;
  separate group handle detaches/moves all tabs. Avoid creating a new host before
  a valid committed drop. Close empty auxiliary hosts after successful transfer.
- [x] Implement draggable dividers and recursive minimum size enforcement using
  session-2 sizing. Update native host minimums on contents/UI-scale changes.
  Recover impossible constraints by tabbing; never force an offscreen giant window.
- [x] Add panel minimize/restore strips, hide and detach controls. Keep native
  window minimize separate. Any active Game focus transition releases held keys;
  authoring drag is finished/canceled before layout mutation.
- [x] Extend actual desktop smoke: drag Inspector to Scene tabs, reorder tabs,
  split Assets, detach a whole group, redock Game during Play, Escape cancellation,
  resize at minima, panel minimize/restore and native minimize/restore. Assert
  session identity, draft/history preservation and no stuck input.
- [x] Run layout/drag/sizing tests, full Gradle build, docking smoke and multi-view
  smoke. Manually exercise mixed-DPI monitors when available; record skipped cases.

**Pass:** Every existing panel supports the agreed operations through actual UI,
with accessible content and stable runtime across moves.
**Handoff:** Capture layouts/screenshots, coordinate conversion rules and any
platform-specific pointer/overlay constraints.

## Session 7 — Save layouts and recover displays/settings

**Create:** `workspace/WorkspaceLayoutCodec.kt`, `WorkspaceLayoutStore.kt`,
`WorkspaceDisplayRecovery.kt`; tests `workspace/WorkspaceLayoutPersistenceTest.kt`,
`WorkspaceDisplayRecoveryTest.kt`.
**Modify:** WorkspaceWindowManager, EditorWorkspace, project switch integration;
preserve ProjectFiles UI-scale behavior.

**Consumes:** Complete layout values, registered IDs and actual host bounds.
**Produces:** `WorkspaceLayoutStore.load(key)` and `save(key, revision, layout)`
on an IO worker, plus pure `recoverLayout(layout, displays, panels)` normalization.
Use a project/standalone key captured with each write, never the current project
looked up later inside an asynchronous callback.

- [x] Failing codec/store tests: roundtrip all placements; malformed/unsupported
  version; 1 MiB/32 auxiliary/256 node/depth-32 limits; duplicate/unknown IDs; empty
  groups; new panel descriptors; interrupted atomic write; revision ordering; project
  switch during save. Preserve the last valid file after failed writes.
- [x] Implement versioned JSON at project `.sle/workspace-layout.json` and per-user
  standalone application data; document exact standalone path. No shared-document
  modification and no overwrite of `.sle/editor-state.json` UI scale.
- [x] Add recovery tests: disconnected monitor, entirely offscreen bounds, negative
  monitor coordinates, DPI/work-area change, screen smaller than group minima,
  nonfinite bounds/ratios, native-minimized restore. Invalid layouts produce a
  recoverable notice/default rather than startup failure or hidden documents.
- [x] Implement pure display recovery and apply before showing windows. Bound
  settings reads and tree traversal before recursive construction; unknown IDs
  are pruned, duplicate/malformed structure falls back, new panels added once.
- [x] Debounce ordinary geometry saves; flush ordered state on project switch/exit.
  Reset saves the default layout without changing UI scale/drafts. Recover on
  display changes during the session, including in-progress drag cancellation.
- [x] Extend docking smoke with fresh-process restart and project-specific layouts,
  standalone layout, reset, corrupt file and simulated display topology. Manually
  unplug/reconnect a monitor if available; simulated geometry tests alone do not
  prove native window behavior.
- [x] Run persistence/display tests, full build, docking smoke and project smoke.

**Pass:** Restored layouts remain usable and isolated per project; settings errors
never destroy documents or stop startup. Layout writes cannot cross project keys.
**Handoff:** Record schema, standalone path, recovery policy, limits and IO revision
contract, plus current evidence for restart and real monitor changes.

## Session 8 — Compatibility, acceptance and final documentation

**Modify:** Docking/multi-view smokes and impacted existing smokes;
`tools/entity-editor/README.md`, `docs/topics/SceneEditor.md`, affected
Runtime/Systems topic/module docs, `docs/README_DOCUMENTATION.md` if a durable
workspace topic is introduced. Keep execution status in this plan.

**Consumes:** All seven prior milestone handoffs and current implementation.
**Produces:** Completed acceptance matrix and a final user-facing operating guide.

- [x] Review the complete change for lifetime, invalid entity access, transform
  propagation, duplicate event/update dispatch, Lua error containment, GL cleanup,
  frame payload lifetime and project compatibility. Turn confirmed regressions
  into focused tests before fixes; avoid unrelated cleanup.
- [x] Run Windows native build/CTest appropriate to changed engine code, rebuilding
  tests and `sle_editor_native` first; run full Gradle build.
- [x] Run docking/multi-view, scene, camera, gizmo, context-menu, project, asset,
  project-document and project-runtime desktop checks. Run distribution smoke only
  if export/standalone changes or related regressions justify it; otherwise record
  that existing distribution behavior is outside the shell change's validation.
- [x] Verify the matrix below, inspect actual captures and record command results,
  skips, hardware and artifacts. Fix blocking regressions; report manual limitations
  explicitly rather than claiming full multi-monitor validation.
- [x] Update user docs for Windows menu, drag handles/drop zones, group detach,
  panel/window minimize, close semantics, layout storage/reset and shared Play.
- [x] Fill final handoff with actual implementation status, remaining limitations,
  tests, and commit state. Do not automatically commit/push pre-existing changes.

| Final acceptance | Session 8 result and evidence |
|---|---|
| All six content panels detach/dock/tab/split/minimize/hide/reopen | PASS: full layout tests; production docking smoke now cycles every descriptor through each operation with real peers; Robot exercises shared tab/group/split controls. |
| Main menu only on home; tool windows are one workspace | PASS: each detached host has native decorations, no menu/owner; inspected group and simultaneous captures; one retained session identity. |
| Scene and Game simultaneously show same running scene | PASS: native script/UI/event counters; multiview asserts one update per sequence and distinct textures; separate-monitor capture inspected. |
| Independent view sizes/cameras, focused Game input | PASS: native camera/GL viewport tests, multiview metrics, camera Lua/physics/input smoke; held Game key released on actual redock. |
| Play advances with any number of visible views, including none | PASS: SessionFrames fake-backend tests plus real multiview one/zero-peer phases and docking close-both transform advancement. |
| Move/close/minimize does not recreate runtime, lose drafts or undo | PASS: lifecycle tests; all-panel and Robot smoke identity/create counts, draft/history checks, Play redock. Explicit Stop still resets native Runtime intentionally. |
| Main exit cancellation and accepted cleanup | PASS: actual dirty-document JOptionPane cancellation, retained layout/draft; accepted manager exit and balanced native creation/destruction; owner-failure cleanup smoke. |
| Project switches, async work and existing source-save rules | PASS: lifecycle, FIFO store and project tests; project/assets/documents/runtime desktop regressions; containing-scene Save/reopen. |
| Saved layout/reset, corrupt state and display recovery | PASS: codec/store/recovery suites and five fresh-JVM persistence phases; real native minimize restore; simulated display removal. |
| Separate monitors and mixed DPI | Separate displays PASS: Scene/Game moved to different connected devices and captured. Controlled native mixed-DPI transitions and physical unplug/reconnect NOT RUN; simulated geometry is not a substitute. |
| Future panel registration | PASS: synthetic descriptor model coverage plus new WorkspacePanelsTest for real binding validation and manager lifecycle; missing/extra bindings rejected. |

## Validation commands (planned, not yet run)

From a Windows PowerShell in `C:/projects/engine`:

```powershell
cmake --build build/debug --config Debug --target sle_editor_native engine_app integration_embedded_multiview_path --parallel 4
ctest --test-dir build/debug -C Debug -R '^integration_embedded_multiview_path$' --output-on-failure
ctest --test-dir build/debug -C Debug -L systems --output-on-failure
```

Check actual labels/target registration in that session; mark the new GL integration
test `windowed`. Before broader CTest, build all targets it will execute.

From `C:/projects/engine/tools/entity-editor`:

```powershell
./gradlew.bat test --tests sle.editor.workspace.DockLayoutTest
./gradlew.bat build
./gradlew.bat run --args=--docking-feasibility-smoke
./gradlew.bat run --args=--multi-viewport-smoke
./gradlew.bat run --args=--docking-workspace-smoke
./gradlew.bat run --args=--scene-smoke
./gradlew.bat run --args=--camera-smoke
./gradlew.bat run --args=--gizmo-smoke
./gradlew.bat run --args=--context-menu-smoke
./gradlew.bat run --args=--project-smoke
./gradlew.bat run --args=--asset-browser-smoke
./gradlew.bat run --args=--project-documents-smoke
./gradlew.bat run --args=--project-runtime-smoke
```

New target/test/flag names above are planned. Do not run or report them as existing
before their milestone creates them. Refer to the existing project handoff for
Windows JDK invocation from WSL if necessary; never reuse a Windows CMake cache
through Linux tooling. GUI/native DLL rebuilds may require closing the relevant
editor normally; preserve any unsaved user work.

## Progress and handoff ledger

Planning only, 2026-11-13. No product code changed, no engine/editor tests run for
this feature. Initial worktree contains unrelated ongoing changes; preserve them.

Planning review: an independent builder review of the design found no important
unanswered questions. Graphics transport and native window details remain explicit
session-1 feasibility decisions. Plan self-review mapped the agreed behavior to
the eight milestones and final acceptance matrix; shared API names and prerequisite
order were checked. Documentation link/structure/whitespace checks are separate
from future implementation validation.

| Session | Status | Current evidence | Next handoff |
|---|---|---|---|
| 1 | Implemented; graphics validated; desktop compatibility check unresolved | Shared GPU 1.7299 ms p95; ordinary smoke capture failure; see record | Session 2 prerequisite review |
| 2 | Implemented and validated (pure model) | 15 workspace tests; full build 115 tests, 0 failures, 2 existing skips; review finding fixed | Session 3 native frame split when requested |
| 3 | Implemented and validated (native split) | 73/73 rebuilt native tests; 115 JVM tests (2 existing skips); camera/project desktop smokes | Session 4 durable session when requested |
| 4 | Implemented; automated checks validated; manual desktop/DPI limits remain | 119 JVM tests, real multiview/error cleanup, native regression and compatibility smokes; see record | Session 5 workspace shell when requested |
| 5 | Implemented; automated checks validated; manual display/input limits remain | 127 JVM tests; docking workspace, camera/menu and compatibility smokes; see final record | Session 6 only when requested |
| 6 | Implemented; automated checks validated; manual mixed-DPI limits remain | 137 JVM tests; Robot interactive docking, held-input release, multiview and compatibility smokes; see completion record | Session 7 only when requested |
| 7 | Implemented; automated checks validated; physical monitor checks unavailable | 154 JVM tests; fresh-process persistence, docking and project desktop smokes; see completion record | Session 8 only when requested |
| 8 | Automated acceptance passed; manual DPI/unplug limits remain | 73/73 native; 156 JVM (2 skips); full compatibility and separate-monitor docking; see final record | Reviewable uncommitted result |
| 8 | Not started | None | Final validation prompt |

After each milestone, append a dated record with changed files, final APIs,
commands actually run and results, artifacts, unavailable checks, remaining work,
git status/commit state, and the next milestone. If incomplete, provide the exact
next action and keep its status incomplete. Never advance a status merely because
the session ended.

### Session 1 work in progress — 2026-11-13

- Executing only session 1 in the existing `feat/project-foundation` checkout,
  as required by the dirty-worktree handoff; no worktree migration or commits.
- Pre-flight: session 4 consumes this milestone's graphics/threading decision;
  session 3 remains responsible for native advance/draw separation. Harness will
  use one existing JNI runtime, never simulate twice to make two images.
- Ruling: keep prototype scheduling, GL and image painting on the AWT EDT. This
  allows bounded reusable images without a cross-thread reader racing reuse;
  measure EDT responsiveness explicitly. If too slow, reconsider at this gate.

#### Session 1 intermediate observations

- CPU experiment completed its lifecycle sequence on Intel Core Ultra 7 265 /
  Intel Graphics, driver 32.0.101.8860, Windows 11, JDK 21.0.2, desktop scale 100%.
  Two 1280×720 buffers, 5-second warmup and 30-second measurement: copy/publication
  p95 9.2862 ms (target missed), 1,632 ticks, drawImage p95 3.2226 ms, EDT probe
  p95 16.5758 ms. CPU payload storage stayed exactly 22,118,400 bytes (two images
  and one direct readback per view), released on shutdown. Heap samples rose from
  29,477,008 to 37,865,616 bytes without collection; this is not evidence of a leak.
- Hidden packed, never shown JFrame + 16×16 AWTGLCanvas successfully hosted owner
  context 65537. Two presenter closures/reopens, peer recreation, zero-size,
  resize, both minimized and both disposed did not change that identity. One JNI
  runtime created/destroyed; 116 owner ticks while minimized, 119 while disposed.
- User observed unusual titlebar/X from `Window.Type.UTILITY`. Ruling: use ordinary
  decorated, ownerless `JFrame` (`NORMAL`) for standard OS minimize/maximize/close
  and taskbar behavior, with no app menu or always-on-top. Do not use UTILITY.
  Cost if wrong: normal titlebars occupy more space than utility chrome.
- CPU miss triggers shared-context GPU fallback. Producer completes with glFinish,
  then presenters blit shared textures and complete with glFinish before reuse;
  all context activation and JNI remain on EDT. This deliberately serial contract
  must be measured before adoption; no background render thread is assumed.
- First GPU run failed screenshot capture because preferred sizing was attached
  only to the CPU branch, leaving GPU canvases zero-height. Its timings are invalid
  for transport selection. Fixed common sizing and added explicit measured content/
  framebuffer-size assertions; full-size rerun pending. Preserved diagnostic report
  as `build/reports/docking-feasibility-gpu-invalid-size.txt`.
- New `LatestFrameTest` first failed compilation for the missing class, then full
  Windows Gradle build passed: 100 tests, zero failures/errors, two existing Windows
  symlink skips. Later harness changes require a fresh full build/smoke result.

### Session 1 completion / handoff — 2026-11-13

- **Status:** implemented with unavailable checks. Graphics feasibility validated;
  ordinary desktop compatibility smoke remains unresolved (see final verification).
  Manual window/input checks remain unavailable. Shared-context GPU transport selected. No session-2
  implementation. The prototype is entered only by `--docking-feasibility-smoke`;
  add `--docking-feasibility-gpu` to select the GPU fallback.
- **Files changed in this milestone:** one early branch in `app/Main.kt`;
  `viewport/experimental/LatestFrame.kt`, `FeasibilityOwner.kt`,
  `FeasibilityGpuPresenter.kt`; `smoke/DockingFeasibilitySmoke.kt`;
  `src/test/kotlin/sle/editor/viewport/experimental/LatestFrameTest.kt`;
  this implementation plan and the editor README. Paths without prefixes are
  under `tools/entity-editor/src/main/kotlin/sle/editor`.
- **Hardware/workload:** Intel Core Ultra 7 265 (20 JVM-visible processors), Intel
  Graphics, OpenGL 4.3.0 driver 32.0.101.8860, Windows 11, JDK 21.0.2. Both logical
  content areas and measured framebuffer sizes 1280×720, desktop DPI 100%, nominal
  editor font scale 85%. Harness exercises transport rather than the full Compose
  shell. Five seconds warmup, thirty seconds measurement, target 60 Hz.
- **Valid GPU result:** 1,797 owner ticks / 30 seconds = **59.9 Hz achieved**,
  2,829 ticks over the full lifecycle test. Two-view copy/publication (including
  producer `glFinish`) mean **1.0775 ms**, p95 **1.7299 ms**, max **3.9 ms**.
  Individual presenter blit/swap/consumer finish mean **1.1634 ms**, p95
  **1.9438 ms**, max **6.1698 ms**. Asynchronous 50-ms EDT probe: mean **3.9552 ms**,
  p95 **9.0161 ms**, max **16.6188 ms**. Producer timing excludes native scene
  advance/draw CPU time; these numbers do not promise arbitrary-game 60 FPS.
- **Buffers/memory:** GPU path has no per-frame CPU pixel buffers/images. Three
  owner FBO/texture/depth targets (one native source + two bounded view targets),
  one context-local read FBO per presenter, one latest descriptor per view.
  At 1280×720, owner color+depth texture/renderbuffer payload is approximately
  22,118,400 bytes before driver overhead. Heap samples grew once from 8,485,008
  to 12,679,312 bytes and then plateaued for the rest of the measurement.
  No frame queues; hidden/zero-sized views clear publication. Owner CPU-buffer and
  GL-target counters are zero after cleanup. This is a bounded 48-second test,
  not a long-running process-memory leak study.
- **Actual lifecycle:** never-shown packed 16×16 AWTGLCanvas owner context **65537**,
  JNI token **1**, one create and one destroy. Initial presenter contexts **65538**
  and **65539**; reopen/recreation produced **262144**, **196610**, **196611** while
  owner identity remained unchanged. Closed/reopened each presenter, disposed and
  recreated an existing peer, invalidated a zero-size view, resized publication
  to 640×360, minimized both (119 owner ticks continued), disposed both (118 owner
  ticks continued), and destroyed runtime/GL targets in owner-context shutdown.
  No duplicate Runtime was constructed. Captures show native scene/UI and distinct
  per-view stripes. This milestone copies one simulated snapshot into two views;
  independent Scene/Game cameras remain session 3/4 work.

#### Final threading/lifetime contract consumed by session 4

1. All host peer operations, context creation/current/use/disposal, JNI calls,
   request changes, publication, and presentation run on **AWT EDT**. Do not move
   GL to another thread or consume these mutable/reused payloads cross-thread
   without a separately validated protocol. Runtime remains workspace-owned.
2. Keep the hidden owner host displayable, packed and never shown for the entire
   session. Visible presenters never contain/reparent that canvas. Recreating a
   presenter creates a new context sharing with the existing owner (`shareContext
   = owner`); it never creates a new runtime. Each context installs its own LWJGL
   capabilities whenever current. NORMAL decorated, ownerless JFrames provide
   standard native controls without app menus, modality or always-on-top.
3. Per owner tick: drain commands/input, advance **once**, then draw requested
   views using the session-3 draw-only API. The prototype's legacy single render
   call advances/draws once even with zero views; session 4 may skip unnecessary
   draws after that native split. Its production dt must come from elapsed time,
   rather than copying the prototype's fixed 1/60 diagnostic dt.
4. Bounded latest GPU publication carries **texture ID, pixel width/height and
   resize generation**, owned by the owner context. No Runtime/native-session
   token goes to a presenter. The prototype uses `SharedFrame` and a single
   `LatestFrame<SharedFrame>` slot per view; resize/zero-size/disposal clears the
   slot and invalidates earlier generations. CPU comparison alternates two image
   buffers, reusing an image only while unpublished and never concurrently with
   EDT painting.
5. **Validated conservative GPU synchronization:** complete producer commands with
   `glFinish` before exposing textures; sequentially activate each visible
   presenter, attach its shared texture to its own read FBO, blit to its default
   backbuffer, swap, and `glFinish` before the next producer tick can overwrite,
   resize or delete any texture. No pending consumer survives the EDT call.
   Do not substitute unvalidated async reuse/fences. This serialized baseline met
   the target on this hardware; future optimizations need their own measurements.
6. Stop the scheduler and probes first. Dispose all presenter contexts/FBOs, then
   destroy the native runtime and owner textures/FBOs/readbacks with owner context
   current, dispose owner canvas, and finally dispose its hidden host. Closing a
   presenter only invalidates that view and deletes its own context/FBO.

#### Commands, review, limits and next action

Windows Gradle was invoked from WSL using:

```bash
/mnt/c/java/jdk-21.0.2/bin/java.exe -cp C:/projects/engine/tools/entity-editor/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p C:/projects/engine/tools/entity-editor -g C:/projects/engine/tools/entity-editor/.gradle-user-home build --console plain --no-daemon
```

The same invocation with these task/argument suffixes was actually executed:

- `test --tests sle.editor.viewport.experimental.LatestFrameTest`: RED, missing
  publisher class; subsequent full build GREEN. Latest full build: **100 tests,
  zero failures/errors, two pre-existing Windows symlink skips**.
- `build run '--args=--docking-feasibility-smoke --docking-feasibility-gpu'`:
  final full-size **PASS**, including full build and all lifecycle checks. Earlier
  GPU failures were the zero-height setup and the inconclusive OS mouse assertion;
  they are not accepted benchmark evidence. Physical mouse delivery is now reported
  explicitly, rather than misreported as passed.
- CPU comparison and ordinary-startup results are appended below after completion.
- Windows `javap.exe` inspected the installed AWTGLCanvas implementation. A Windows
  CIM query reported the CPU model. `git diff --check` passed during implementation;
  final documentation check is recorded below. No native C++ source changed or
  native rebuild/CTest was required for this experimental JVM-only milestone.
- GPU artifacts: `tools/entity-editor/build/reports/docking-feasibility-gpu.txt`,
  `docking-gpu-view-0.png`, `docking-gpu-view-1.png`. Both 1280×720 framebuffer
  captures inspected; they contain the actual native scene and distinct stripes.
  Initial CPU report/captures are `docking-feasibility-cpu-initial.txt` and
  `docking-view-{0,1}.png`. Reports/captures are ignored build output; summary and
  reproducible commands remain in this plan.
- Independent read-only review found **no critical/important defects**. Deferred
  minors for session 4: capture/assert pixels again after peer recreation; sample
  actual native scene content automatically beyond the synthetic stripe. The
  current 100%-DPI framebuffer assertion deliberately rejects a scaled desktop.
- **Unavailable checks:** physical mouse automation delivered zero events despite
  AWT reporting Game canvas/window focus; OS taskbar usability and unrelated-app
  stacking were not established by code flags. A default-screen screenshot showed
  unrelated foreground desktop, so it was not used as proof of presenter chrome.
  Manual monitor/DPI/desktop switching, physical input, real taskbar and cross-app
  stacking remain required desktop acceptance checks. No actual multi-monitor,
  mixed-DPI, monitor unplug or clean-machine claim is made.
- Review scope exclusions are intentional: native advance/draw and independent
  cameras (session 3), production input/owner scheduling (4), docking/documents/
  persistence (5–7), and final desktop matrix (8). Background GL or cross-thread
  consumption was not evaluated; arbitrary game performance was not promised;
  invalid-size GPU results were rejected. Existing unrelated edits were excluded
  from this review. Cost if these scope decisions are wrong: later milestone
  validation must catch compatibility or hardware-specific issues before shipping.
- **Next milestone/prerequisite readiness:** session 2 is next, but must read the
  unresolved ordinary desktop check below rather than infer an unconditional pass.
  Graphics ownership/transport evidence is ready for the native session-3 boundary. Session 4 must first
  have session-3 APIs and preserve the precise synchronization/lifetime contract
  above; manual input/window acceptance remains explicitly outstanding.
- **Git/commit state:** existing `feat/project-foundation` dirty checkout preserved;
  all work remains uncommitted/unpushed. No cleanup, migration, staging or commits.

#### Final CPU comparison and selection rationale

The final `run --args=--docking-feasibility-smoke` using NORMAL windows also
**passed**: 1,784 ticks / 30 seconds = **59.47 Hz**, copy/publication mean
**5.7011 ms**, p95 **7.4045 ms**, max **20.4754 ms**; individual drawImage p95
**3.2022 ms**; EDT probe p95 **11.0761 ms**, max **21.1203 ms**. Heap samples
29,462,528 → 37,851,136 bytes, with fixed 22,118,400-byte CPU payload storage and
zero buffers/targets after shutdown. Lifecycle passed again; complete report
`docking-feasibility-cpu.txt`, captures `docking-cpu-view-{0,1}.png`.

**Ruling:** retain shared-context GPU as session 4's selected baseline despite the
passing second CPU result. CPU's first run missed (9.2862 ms); its passing rerun
leaves only 0.5955 ms margin to the transport target. GPU's full-size validated
1.7299 ms result has substantially more headroom and lower EDT delay. Fallback
was attempted only after the first real CPU miss, as planned. Cost if wrong:
shared contexts and serialized GPU completion add implementation/lifetime
complexity versus Swing image painting; the retained CPU prototype permits a
future measured reconsideration rather than assuming CPU can never work.

#### Final ordinary-startup verification / remaining limitation

- Existing `run --args=--smoke-test` on final source **failed** its OS desktop
  screenshot assertion twice: `Sandbox missing on desktop`, NPC pixels zero.
  Both runs first passed framebuffer rendering (`EMBEDDED_SMOKE_PASS`, player
  2,892/NPC 3,046), pause freeze, session guards and native reinitialization.
  Assertion exit was followed by a JVM `EXCEPTION_ACCESS_VIOLATION` in
  `opengl32.dll+0x3741d` on VM Thread; new logs `hs_err_pid40356.log` and
  `hs_err_pid21464.log`. This is **not** a passing ordinary desktop check.
- A controlled baseline temporarily removed **only** the new opt-in Main branch,
  preserving/restoring the entire file byte-for-byte in `finally`. That run
  **passed** the existing smoke, OS screenshot checks and context cleanup. Final
  source restores the branch once and retains `launchEditor(args)` unchanged.
  This establishes that the current desktop check is inconsistent, not its exact
  cause or whether timing/foreground behavior explains the difference. No claim
  that baseline currently reproduces the failure is made.
- A pre-existing 2026-11-12 log (`hs_err_pid36420.log`, authoring UI smoke) has the
  same OpenGL/VM-thread crash fingerprint; historical evidence is background,
  not current validation or proof of identical causation. The existing smoke's
  immediate error-exit cleanup path is outside this opt-in-only milestone.
- Logs: `/tmp/docking-ordinary.log`, `/tmp/docking-ordinary-baseline.log`,
  `/tmp/docking-ordinary-final.log`. Ordinary screenshots remain at
  `tools/entity-editor/build/reports/embedded-smoke.png` and `sandbox-desktop.png`;
  do not confuse an offscreen framebuffer pass with a desktop presentation pass.
- **Exact next action:** rerun the existing ordinary smoke in an unobscured
  interactive desktop, establish focus/monitor placement and inspect its captured
  desktop rectangle. If capture still fails, reproduce against the controlled
  no-hook baseline and diagnose the existing failure-exit context cleanup before
  declaring full desktop compatibility. Session 2 may independently work on its
  pure model only after acknowledging this outstanding prerequisite limitation;
  do not mark session 1 unconditionally passed from the GPU result alone.
- No ordinary-editor assertion was weakened, no default startup/lifetime path was
  changed, and no later-session runtime or shell implementation was introduced.

Final standalone Windows Gradle `build` **passed** (7 seconds; final sources,
all eight tasks up-to-date). Current test XML totals remain **100 tests, zero
failures/errors, two pre-existing skips**, from this session's executed full
suite. Final `git diff --check` and the new README link/heading target check
passed. No unexecuted test or historical result substitutes for the failing
ordinary desktop smoke above. No commits, pushes, staging or cleanup performed.


### Session 2 completion / handoff — 2026-11-13

- **Status:** implemented and validated for the pure-model milestone; final post-review full build passed.
  Default editor shell and native runtime remain unchanged. Session 1's graphics
  evidence is accepted for this pure-model milestone only; its ordinary desktop
  capture failure and manual input/window checks remain unresolved. Session 1 is
  **not** relabeled unconditionally passed.
- **Files changed:** new `workspace/PanelRegistry.kt`, `DockLayout.kt`,
  `DockOperations.kt`, `DockSizing.kt` beneath
  `tools/entity-editor/src/main/kotlin/sle/editor/`; new corresponding
  `workspace/DockLayoutTest.kt` and `DockSizingTest.kt` under `src/test/kotlin/sle/editor/`;
  this plan only. No existing production file was modified in session 2.

#### Final layout and registration contracts

- `PanelId`, `HostId`, `GroupId` wrap nonblank `String` values.
  `PanelMinimum(width: Float, height: Float)` is finite nonnegative logical dp.
  `PanelDescriptor(id, title, minimum, available = true)` has the proposed fields.
  `PanelRegistry(panels: List<PanelDescriptor>)` rejects duplicate IDs and exposes
  `byId`. `standardPanels()` supplies all six approved IDs and content minima.
  Factories, shared workspace state, Compose, native objects and serialization IO
  are deliberately outside these values.
- `LogicalBounds(x, y, width, height: Float)` uses logical screen coordinates,
  accepts negative origins, and exposes `isValid()` for finite positive extents.
  `DockAxis` is `Horizontal` / `Vertical`; `DockEdge` is Left/Right/Top/Bottom.
- `DockNode.Tabs(group: GroupId, panels: List<PanelId>, active: PanelId)` and
  `DockNode.Split(axis: DockAxis, ratio: Float, first: DockNode, second: DockNode)`.
  Ratios must be finite and strictly between zero and one in validated layouts.
  `DockHost(id: HostId, root: DockNode?, bounds: LogicalBounds)`.
  `PanelPlacement(host: HostId, group: GroupId, index: Int, bounds: LogicalBounds)`.
  `DockLayout(mainHost: HostId, hosts: List<DockHost>,
  hidden: Map<PanelId, PanelPlacement> = emptyMap(),
  minimized: Map<PanelId, PanelPlacement> = emptyMap())`.
- `DockTarget.Tab(group, index)` uses the destination insertion index **after
  source removal**; invalid indices or stale destinations return the original
  layout. `Split(group, edge)` and `Detached(bounds)` have no platform references.
  `movePanel`, `moveGroup`, `hidePanel`, `minimizePanel`, `restorePanel` retain the
  proposed signatures. `activatePanel(layout, panel)` selects a visible tab;
  `layout.tabs()` returns the visible groups in tree order.
- Operations return immutable proposals; cancellation discards the proposal.
  No IDs or source placements are consumed by failed/canceled moves. Generated
  `group-N` / `host-N` IDs use the first free positive suffix, reserving IDs in
  hidden/minimized placements too. A singleton moved intact or a detached/split
  whole group retains its group ID. Tab merge adopts destination group identity;
  whole-group merge retains source tab order/active tab. Same-group panel reorder
  retains active selection. Removing an active panel selects the next tab at the
  old position, or the previous final tab.
- Main host is always retained. Empty branches collapse; empty auxiliary hosts
  disappear unless they still own minimized panels. Minimized panels retain a
  live host for the restore strip; hidden panels only retain preferred placement.
  Minimizing a hidden panel is a no-op; hiding a minimized panel is supported.
  Restore uses the saved group/index if it exists, otherwise recreates the saved
  group beside current host content, or recreates a missing host at saved bounds.
  Restore of an already visible/unknown panel is a no-op.
- `defaultDockLayout(panels)` retains main ID `main`, default 1280×800 bounds,
  hierarchy left, center Scene/Game/Entity (plus future panels in registration
  order), inspector right and assets below. Unavailable descriptors start hidden.
  Availability affects the initial layout; downstream workspace commands must
  enforce current availability when invoking operations. Restoring defaults is a
  fresh pure layout; it does not touch document/project state.
- `validateLayout(layout, panels: Set<PanelId>): List<String>` returns empty on
  success; reports missing/duplicate/unknown panels, duplicate group/host IDs,
  missing main host, empty groups/auxiliary hosts, invalid active tabs, bounds,
  ratios, placements and missing minimized hosts. Text is diagnostic, not an
  encoded persistence/error protocol. Hidden placement hosts/groups may be absent.

#### Final sizing contracts

- `DockChrome(tabHeight = 28f, splitter = 6f, restoreStrip = 28f)` uses finite
  nonnegative logical dp. These are provisional defaults: session 5/6 must pass
  measured chrome/native insets from the real shell rather than claiming these
  are desktop measurements.
- `nodeMinimum(node, registry, chrome = DockChrome(), scale = 1f)` takes maximum
  member minima plus tab chrome, sums widths/maximizes heights horizontally, and
  maximizes widths/sums heights vertically, including splitter thickness.
- `hostMinimum(host, registry, chrome = DockChrome(), hasMinimized = false,
  insets = PanelMinimum(0f, 0f), scale = 1f)` adds restore strip and native insets.
  Positive finite scale multiplies content/chrome only; insets already use logical
  OS units. Recompute from original descriptors after UI-scale changes.
- `clampSplitRatio(ratio, span, firstMinimum, secondMinimum, splitter = 6f)` divides
  the span remaining after the splitter and clamps to the actual feasible child
  minimum interval, keeping ratios strictly inside 0–1 using adjacent finite
  float endpoints. Nonfinite desired ratios use .5; impossible spans (including
  minima requiring exactly 0/1) return .5
  as a signal for caller fallback, not a claim both children fit.
- `fitHostToWorkArea(layout, host, workArea, registry, chrome = DockChrome(),
  insets = PanelMinimum(0f, 0f), scale = 1f)` collapses an impossible split tree to
  one ordered tab group, retains its first group's identity/active tab, and clamps
  logical bounds to the supplied work area. Single-panel minima may exceed a tiny
  display; available bounds win. The later shell must supply scrolling/overflow
  handling so controls remain reachable. This helper does not discover monitors,
  persist geometry or implement the session-7 recovery codec.

#### Decisions, verification and next action

- **Ruling:** continue in the existing dirty `feat/project-foundation` checkout
  and use this plan's milestone record instead of creating a clean isolated
  checkout or commit-based executor ledger. The handoff explicitly requires
  preserving current uncommitted work and no automatic cleanup/commits. Cost if
  wrong: validation includes unrelated inherited code; review scope is explicitly
  restricted to the new workspace files.
- **Ruling:** proceed with pure session 2 despite the unresolved ordinary desktop
  capture check, as explicitly permitted by session 1's exact next action. This
  does not establish native/desktop compatibility; the unresolved check must be
  addressed before declaring those gates passed.
- Windows Gradle command prefix (all runs used Windows JDK 21 and the same cached
  user home):

  ```bash
  /mnt/c/java/jdk-21.0.2/bin/java.exe -cp C:/projects/engine/tools/entity-editor/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p C:/projects/engine/tools/entity-editor -g C:/projects/engine/tools/entity-editor/.gradle-user-home
  ```

  Actual suffixes/results:
  - `test --tests sle.editor.workspace.DockLayoutTest --console plain --no-daemon`:
    RED, missing model/registry symbols; WSL interop initially failed inside sandbox,
    then the approved Windows invocation reached Gradle/compiler.
  - `test --tests sle.editor.workspace.DockSizingTest --console plain --no-daemon`:
    RED, missing sizing symbols (also exposed/fixed one test syntax typo).
  - `test --tests 'sle.editor.workspace.*' --console plain --no-daemon`:
    first run 12 tests, two failures. Corrected the test to inspect the source host
    when a singleton retains its group ID; fixed final-tab minimization retaining
    its tool host. Next run passed. Added mixed-operation and boundary regressions;
    14-test run reproduced zero-minimum ratio clamp throwing, then fixed it.
  - `build --console plain --no-daemon`: **PASS**, 114 tests, 0 failures/errors,
    2 existing Windows symlink skips; all 14 workspace tests passed. Includes
    deterministic 500-operation invariant regression. Native DLL/GL not needed
    by the workspace tests. Logs: `/tmp/docking-session2-{red,sizing-red,tests,edge-red,build}.log`;
    XML/HTML under `tools/entity-editor/build/test-results/test` and
    `build/reports/tests/test`.
- `git diff --check` passed. No new desktop captures: no shell/native integration
  in this milestone, and no ordinary smoke rerun is claimed. Existing session-1
  desktop capture/input/window limitations remain open, not waived.
- **Next milestone:** session 3 (native advance/draw separation), only when requested;
  session 2 does not introduce any of it. Session 5 consumes these types after
  session 4; bind factories and enforce availability there. Session 6 must connect
  real chrome/insets/scale, splitter clamps, drop proposals and overflow UI.
  Session 7 must bound/validate decoded trees before recursive operations and add
  codec/version/recovery logic; this milestone assumes valid trusted input layouts.
- **Git/commit state:** all inherited modifications/untracked files preserved;
  new workspace files and plan changes are uncommitted/unpushed. No staging,
  commits, reset, cleanup or branch migration.

- **Independent review:** read-only review of the six new source/test files found
  one low-severity positive-minimum ratio boundary: an artificial .001–.999 margin
  could allocate less than a child minimum. Focused regression
  `asymmetricPositiveMinimaAreNeverReducedByEndpointSafeguards` reproduced it
  (6 sizing tests, one failure; `/tmp/docking-session2-review-red.log`). Removed
  that artificial margin; only the nearest representable interior float endpoints
  remain. A minimum requiring exactly ratio 0/1 is impossible under the layout's
  strict ratio invariant and returns the documented .5 fallback signal. No other
  confirmed correctness findings. Fixed this small requirement violation within
  the milestone rather than deferring the known minimum-size defect.

- **Final post-review validation:** same Windows Gradle prefix with
  `build --console plain --no-daemon` **passed**, 115 tests, zero failures/errors,
  two existing Windows symlink skips. All **15** workspace tests passed (nine
  layout tests, six sizing tests). `/tmp/docking-session2-final-build.log` records
  the final command result. `git diff --check` and trailing-whitespace scan of
  the six new Kotlin files passed. Session 2 is complete; no session-3 work started.

### Session 3 work in progress — 2026-11-13

- Scope: native/JNI advance/draw separation only, requested by user.
- Prerequisites: session 1 graphics transport/ownership validated; session 2 model
  validated. Manual window/input checks and the prior ordinary desktop capture
  limitations remain outstanding; new native/desktop checks will be recorded here.
- Ruling: use the existing dirty feature checkout and this milestone ledger, as
  required by the handoff; no clean checkout, staging, commits or cleanup. Cost:
  builds include inherited project/runtime changes. Baseline copies of the six
  implementation files are in `/tmp/docking-session3-baseline` for review.
- Pre-flight: session 4 consumes explicit advance/draw and the session-1 EDT/current
  context contract. Legacy callers remain on wrappers until that milestone.
- Actual ordering inspected: deferred switch/debug, event flush, chunk gates,
  animation/audio/scripts/state machines/physics/transforms/game camera, render
  submission/UI interaction, flush. UI currently combines interaction and drawing.
- Ruling: add a geometry-suppressed UISystem update mode for embedded advance;
  preserve the standalone update-and-submit path. Interactions use gameplay camera
  even with only Scene visible. Cost: Scene-view UI interaction changes to the
  explicitly agreed Game coordinate contract; session 4 must gate focused input.
- Regression added before implementation. Windows build initially blocked by WSL
  sandbox interop (`UtilBindVsockAnyPort`); rerun through approval mechanism.

- Initial RED: the new regression target failed compilation on missing
  `advanceEmbeddedFrame`/`drawSceneView` APIs; after implementation the windowed
  test passed. Expanded framebuffer/pixel/chunk assertions also passed.
- Windows full native Debug build and Gradle build passed; 115 JVM tests, zero
  failures/errors, two existing Windows symlink skips. Systems suite excluding
  the separately run multiview test passed 46/46 (no skips). Existing camera and
  project-runtime desktop smokes passed against rebuilt Debug DLL.
- Independent read-only review found one legacy Game/F3 compatibility regression.
  Added assertion failed at `legacy Game wrapper ignores editor debug shortcut`
  with debug toggled ON. Fix scopes the Game override around wrapper advance,
  retaining legacy shortcut suppression. Final native rebuild/tests pending.
- Ruling: fix the low-severity review finding within this milestone because it is
  an explicit wrapper compatibility requirement, rather than defer it as polish.
  Cost if wrong: tiny wrapper scope change; focused RED→GREEN and full checks guard it.

### Session 3 completion / handoff — 2026-11-13

- **Status:** implemented and validated for the native split. Final full native
  Debug suite **73/73 passed**, including 14 windowed tests, with no skips.
  Final camera desktop smoke passed. Prior manual window/input/mixed-DPI
  limitations remain explicitly open for later workspace acceptance.
- **Files changed in this milestone:**
  `EngineModules/Systems/include/sle/engine/Runtime.hpp`,
  `EngineModules/Systems/src/Runtime.cpp`,
  `EngineModules/UI/include/sle/ui/UISystem.hpp`,
  `EngineModules/UI/src/UISystem.cpp`, `tools/editor-native/EditorBridge.cpp`,
  `tools/entity-editor/src/main/kotlin/sle/editor/bridge/NativeEngine.kt`,
  `tests/integration/CMakeLists.txt`,
  `tests/integration/systems/integration_embedded_multiview_path.cpp`,
  `tests/data/scripts/embedded_multiview.lua`, `docs/modules/Systems.md`,
  `docs/modules/UI.md`, `docs/topics/Runtime.md`, and this plan.
  Inherited modifications to these paths are retained.

#### Final contracts consumed by session 4

- Native: `Result<bool> advanceEmbeddedFrame(float dt)`;
  `Result<bool> drawSceneView(int width, int height, bool gameView, bool fit=false)`.
  Both require initialized embedded Runtime/current owner GL context. Advance
  rejects NaN/infinity/negative dt before doing work; zero dt still runs callbacks.
  Draw rejects nonpositive physical pixel dimensions before changing view state.
- JNI/Kotlin: `advanceFrame(session: Long, dt: Float)` and
  `drawSceneView(session: Long, width: Int, height: Int, gameView: Boolean, fit: Boolean)`.
  Existing guarded token/thread checks and exception translation apply. Advance
  validates dt then clears input transients/transfers queued input once. Draw
  never drains input. Hosts call advance once, then zero/one/multiple draws; no
  autonomous timing or presenter/session ownership is introduced here.
- Running advance order: pending scene switch → debug input → dt/FPS accounting
  → scene/global deferred events → chunk gates using established pre-simulation
  gameplay camera focus → animation → audio requests → scripts → state machines
  → physics → resolved world transforms → gameplay camera → UI behavior/resource
  preparation, bindings and clicks with that gameplay camera. UI uses
  `UISystem::update(ctx, false)` and queues no quad/text geometry. Advance does
  not clear/draw/present a framebuffer or poll OS events.
- Paused advance validates then returns without simulation, event flush, pending
  switch commit or UI interaction; audio pause policy is unchanged. Draw may
  resolve derived transforms/camera and prepare cached UI resources, but never
  runs behavior chunks, clicks, events, scripts, physics, audio updates or chunk
  activation. Stopped/paused UI still renders. Game logical camera stays independent
  of Scene size, pan, fit and draw order.
- Draw scopes view/preview/debug overrides, restores renderer camera to gameplay,
  preserves host draw/read framebuffer bindings and restores GL viewport on exit
  (also on exceptions). Scene draws restore host dimensions so legacy
  `sceneGameViewport()` describes the most recent **Game** draw. Before any Game
  draw it uses existing/default host metrics. Session 4 must publish per-view
  metrics with frames; this legacy getter is not a per-presenter metric channel.
- `tickEmbeddedFrame(width,height,dt)` remains advance + renderEmbeddedFrame.
  `renderSceneView(...,playing,...)` advances only when playing, then draws,
  preserving Game's F3 suppression. Wrapper validation precedes simulation.
  Standalone retains combined renderer/UI submission after the shared simulation
  helper, including its original event/poll/present ownership and timing logs.
- All session-1 EDT/current-context/shared-texture synchronization rules still
  apply. No second Runtime, background GL, scheduler, presenters or docking shell
  has been introduced. Existing default single-view editor remains on wrappers.

#### Commands and evidence

Windows CMake/CTest were invoked from WSL (approved escalation was required for
Windows interop, which failed in sandbox with `UtilBindVsockAnyPort`). Commands:

```bash
'/mnt/c/Program Files/CMake/bin/cmake.exe' --build build/debug --config Debug --target integration_embedded_multiview_path --parallel 4
'/mnt/c/Program Files/CMake/bin/cmake.exe' --build build/debug --config Debug --target integration_embedded_multiview_path sle_editor_native engine_app --parallel 4
'/mnt/c/Program Files/CMake/bin/cmake.exe' --build build/debug --config Debug --parallel 4
'/mnt/c/Program Files/CMake/bin/ctest.exe' --test-dir build/debug -C Debug -R '^integration_embedded_multiview_path$' --output-on-failure
'/mnt/c/Program Files/CMake/bin/ctest.exe' --test-dir build/debug -C Debug -L systems -E '^integration_embedded_multiview_path$' --output-on-failure
'/mnt/c/Program Files/CMake/bin/ctest.exe' --test-dir build/debug -C Debug --output-on-failure
```

- First narrow build RED: APIs absent. After extraction, narrow windowed CTest
  GREEN. Expanded tests cover one update/physics movement followed by both draw
  orders/repeated draws, no advance clearing, UI load/click once under rotated
  gameplay camera, rendered HUD/letterbox pixels, host FBO/viewport restoration,
  invalid dt/sizes, legacy wrappers, pause/resume, deferred scene commit/events,
  and chunk gates changing only at advance.
- Independent review finding reproduced RED after the initial green suite, then
  fixed. Final full native rebuild passed (`/tmp/docking-session3-final-native.log`);
  final CTest **73/73, no failures/skips**, 11.89 seconds
  (`/tmp/docking-session3-final-ctest.log`). Systems subset before the fix was
  46/46 excluding multiview; final full suite includes **47 Systems tests** and
  the repaired multiview regression. No historical executable is counted as
  current validation: all native targets were rebuilt before final CTest.
- Final DLL: `build/debug/tools/editor-native/Debug/sle_editor_native.dll`;
  executable: `build/debug/engine_app/Debug/engine_app.exe`.
- Windows Gradle prefix:

  ```bash
  /mnt/c/java/jdk-21.0.2/bin/java.exe -cp C:/projects/engine/tools/entity-editor/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p C:/projects/engine/tools/entity-editor -g C:/projects/engine/tools/entity-editor/.gradle-user-home
  ```

  Actual suffixes: `build --console plain --no-daemon` passed (**115 tests**,
  zero failures/errors, two existing symlink skips);
  `run --args=--camera-smoke --console plain --no-daemon` passed before and after
  review fix; `run --args=--project-runtime-smoke --console plain --no-daemon`
  passed before review fix (final rerun recorded below). Logs are
  `/tmp/docking-session3-{gradle,camera,project,final-camera}.log`.
- Inspected `tools/entity-editor/build/reports/game-camera.png` and
  `project-runtime-editor.png`: rendered tile bodies/ground and project Game
  tile with retained unsaved authoring draft respectively. These are real
  desktop captures, not proof of physical-input or multi-monitor behavior.
- Review: independent read-only six-file baseline comparison found only the
  Game/F3 regression; fixed with a failing assertion then final green suite.
  No deferred review findings. `git diff --check` passed; changed relative doc
  links checked separately at finalization.
- **Checks not run:** Release configuration, physical mouse/keyboard, mixed DPI,
  multi-monitor/removal, prolonged sessions and the future multi-presenter JNI
  scheduling path. Native advance/draw was exercised with a real Windows GL
  context, including a host FBO; existing desktop smokes still use compatibility
  wrappers. New JNI signatures compile into the bridge and Kotlin but production
  scheduling moves to them only in session 4.
- **Remaining work / next milestone:** session 4 is ready to implement when
  requested: workspace-owned durable session, zero/one/two-view scheduling,
  per-view frame/input metrics and focused Game input using session-1 transport.
  Do not infer final docking/physical-input acceptance from this native gate.
- **Git state:** `feat/project-foundation`, dirty inherited checkout preserved;
  all session-3 work uncommitted/unpushed. No staging, resets, cleanup or migration.

- **Final post-fix desktop/document checks:** final project-runtime rerun
  **PASS**, `PROJECT_RUNTIME_SMOKE_PASS`, Windows Gradle exit 0 in 15 seconds
  (`/tmp/docking-session3-final-project.log`). It used the final rebuilt Debug
  DLL and engine_app. Final camera rerun also passed in 15 seconds.
  `git diff --check` and relative-link existence checks for the four changed
  docs passed. Session 3 is complete; no session-4 implementation started.

### Session 4 work in progress — 2026-11-13

- User requested session 4 only; session-1 GPU ownership contract and session-3
  native APIs/evidence read. Manual input/window/DPI limitations remain open.
- Ruling: retain the existing dirty feature checkout and use this milestone ledger,
  preserving inherited edits; no migration, staging or automatic commits. Cost:
  current validation includes inherited project work.
- Pre-flight: session 4 consumes EDT-only shared textures from 1 and explicit
  advance/draw from 3; session 5 consumes the resulting session/presenter API.
- Baseline viewport/Main copies: `/tmp/docking-session4-baseline`.
- First task: fake-backend regression for once-per-tick advancement, zero views,
  bounded latest publication, resize/input generation and idempotent cleanup.
- Core RED: missing session/frame types; GREEN: 3 fake-backend tests. Initial
  desktop startup exposed JFrame.owner shadowing; explicit outer owner fixed it.
  Camera behavioral checks then passed, but shutdown exposed nested JAWT locking;
  cleanup now runs in the already-current owner context.
- Real multiview smoke passed with one create/destroy and two actual scene images.
  A settled-body comparison falsely reported hidden simulation as stopped; replaced
  it with the native multiview Lua update counter, including sequence/update equality.
- Additional regression reproduced Game frames carrying editor-camera metrics.
  Adding an immediate native Game camera snapshot (position/size/rotation/zoom),
  rather than sharing mutable last-view globals. Fit command path also under test.
- Expanded multiview REDs: actual Game camera assertion failed with editor metrics;
  Scene Fit assertion failed after zoom. New `gameCamera` JNI snapshots six floats
  (x/y, viewport width/height, rotation, zoom); draw-only JNI now consumes pending
  Fit only on Scene draws. Native targets rebuilt, narrow windowed CTest passed;
  expanded multiview smoke passed. Four framebuffer captures inspected: real
  textured bodies/ground and independent Scene grid/Game framing, also after peers.
- Compatibility: camera, gizmo and scene passed. Project-runtime first failed its
  File-menu automation before launch; unchanged rerun passed, including VSync,
  deferred switch, relocation and Stop. Context-menu check still pending.
- Independent final review found one P2: input immediately following resize could
  use prior metrics until the next timer tick. Focused regression RED on missing
  frameForInput; input now refreshes its request synchronously and rejects events
  until a matching frame is published. Actual same-EDT resize/input smoke added.
- Ruling: keep the established Stop/native reset within the durable GL owner.
  Presenter hide/disposal never resets the Runtime; explicit Stop restores native
  project/Lua defaults by replacing the one live Runtime, as before. Cost: native
  token identity changes on Stop; workspace/GL identity and document state do not.

### Session 4 completion / handoff — 2026-11-13

- **Status:** implemented with unavailable manual checks; automated validation passed. Final results below.
  Full docking/window management remains session 5 onward. Manual monitor/DPI and
  physical-input acceptance is not established by automation.
- **Files changed in this milestone:**
  `viewport/EditorEngineSession.kt`, `NativeViewportOwner.kt`, `SessionFrames.kt`,
  `ViewFrame.kt`, `EngineCanvas.kt`, `SharedViewport.kt`, `app/Main.kt`,
  `bridge/NativeEngine.kt`, `smoke/MultiViewportSmoke.kt`,
  `src/test/kotlin/sle/editor/viewport/EditorEngineSessionTest.kt`,
  `EngineModules/Systems/include/sle/engine/Runtime.hpp`,
  `tools/editor-native/EditorBridge.cpp`, editor README,
  `docs/topics/SceneEditor.md`, `docs/topics/Runtime.md`, `docs/modules/Systems.md`, and this plan.
  Unqualified implementation paths are beneath
  `tools/entity-editor/src/main/kotlin/sle/editor`.

#### Final session/presentation contracts

- Main remembers one `EditorEngineSession` and closes it on workspace disposal.
  `start()` lazily packs a never-shown, undecorated 16×16 owner JFrame/canvas on
  EDT. All native/context calls, request changes, input, timers, painting and
  disposal remain EDT-only. Production dt uses elapsed nanoseconds, clamped to
  0–0.1 seconds; this is the existing hitch cap, not fixed diagnostic dt.
- Session commands: `setProject(PreparedProject?)`,
  `setContent(ViewportMode,String?,Playback)`, `setScene(SceneWorkspaceState?)`,
  `setAuthoring(EntityWorkspaceState?)`, `setSceneView(Boolean,(String)->Unit)`,
  `finishGesture()`, `fitEntity()`, `zoomEntity(Float)`,
  `setViewRequest(ViewId,ViewRequest?)`, `latestFrame(ViewId): ViewFrame?`,
  `enqueueInput(ViewInput)`, `releaseInput()` and idempotent `close()`.
  Command setters preserve existing preview/playback/document semantics;
  native loading and playback synchronization occur at the next owner tick.
- `NativeViewportOwner` retains existing command/preview/gizmo behavior in the
  durable context. `EngineCanvas` is now the replaceable presenter; its established
  name and smoke/document hooks are retained rather than adding a duplicate
  `ViewportPresenter` adapter. The session is the sole native/GL owner.
- `ViewId` is `Scene`, `Game`, `Entity`. `ViewRequest` holds Int physical
  `pixelWidth/pixelHeight`, Int `logicalWidth/logicalHeight`, Boolean `visible`,
  Long `generation`. Zero/hidden requests never draw. Generations come from a
  monotonically increasing session counter, including presenter recreation.
- `ViewFrame` contains `view: ViewId`, `sequence: Long`, `request: ViewRequest`,
  `texture: Int`, `camera: PreviewCamera?`, `gameRect: List<Float>`,
  `cameraRotation: Float`, `cameraZoom: Float`. `PreviewCamera` contains immutable
  position/viewport-size Vec2s and logical dimensions. Its authoring projection
  helpers apply to Scene/Entity; Game consumers use its position/size plus the
  separate rotation/zoom and letterbox tuple. The six Game rectangle floats are
  physical x/y/width/height (bottom-left origin), logical game width/height.
  Game camera snapshots use new `gameCamera(Long): FloatArray` JNI, six floats:
  x/y, viewport width/height, rotation radians, zoom. These are copied immediately
  after drawing, never read from another presenter's mutable last-render state.
- `ViewInput` carries `view: ViewId`, `generation: Long`, `type/code: Int`,
  `x/y: Double`. Presenter focus gates Game delivery; scheduler policy rejects
  non-Game, hidden and stale-generation input. Activation refreshes the current
  request synchronously, including resize/DPI/visibility changes between ticks.
  No matching frame means no gesture/key/button/cursor forwarding; release events
  still release held input. Focus loss, unsubscribe, resize and view switches
  finish/cancel gestures and release input. Scene pan/zoom remains usable in Play.
- One owner timer synchronizes commands, drains queued native input and advances
  once (running/paused native policy), then draws requested views without updates.
  Hidden/absent views continue owner advancement and audio initialization but skip
  image production. Project FPS controls the timer; VSync applies to Game's actual
  presenting context. Opening Entity retains the existing stop-playback rule.
- GPU transport retains the validated serial `glFinish` producer/consumer contract.
  One texture/FBO/depth target per requested drawable view, one latest descriptor
  per view, one local read FBO per presenter; no CPU images or frame queue in
  production. Consumers detach texture attachments after blit/capture. Resize
  clears publication before target replacement; hide/unsubscribe deletes owner
  targets. Shutdown stops timer, disposes presenters, closes native/targets with
  owner current, then retires owner context/host. Errors stop timer and attempt
  the same cleanup. No nested AWT drawing-surface lock is taken during cleanup.
- Native draw-only JNI now consumes `fitRequested` only on a successful Scene draw,
  preserving Fit across Game-first drawing. Native advance/draw APIs and legacy
  wrappers otherwise remain as session 3. `Runtime::getCameraRotation()` is an
  inline read-only accessor used by JNI camera snapshots.
- Project launch retains the existing consuming-handle bridge: `startProject`
  removes its prepared handle before starting and Runtime retains the launch
  context. Unlaunched/discarded preparation still uses projectRunAction cleanup.
  No presenter owns a project handle. Explicit Stop retains legacy native reset;
  presenter removal never resets a runtime. One live Runtime exists at a time.
- **Ruling:** scheduler tests use a fake GL/native backend for ownership/advance/
  frame/input/failure contracts; project handle consumption is verified through
  existing native code and actual project launch/Stop/relocation smoke, rather
  than inventing a fake project-handle manager that production does not use.
  Cost: handle release is not a separately isolated JVM unit test.

#### Verification and review

Windows Gradle prefix (same JDK/cache as earlier sessions):

```bash
/mnt/c/java/jdk-21.0.2/bin/java.exe -cp C:/projects/engine/tools/entity-editor/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p C:/projects/engine/tools/entity-editor -g C:/projects/engine/tools/entity-editor/.gradle-user-home
```

- `test --tests sle.editor.viewport.EditorEngineSessionTest --console plain --no-daemon`:
  initial RED on missing session API; three initial scheduling/ownership tests
  GREEN. Review resize-input regression RED on missing `frameForInput`, then GREEN
  in the full build. Final **4 focused tests**, full **119 tests**, zero failures/
  errors, **2 existing Windows symlink skips**. Logs `/tmp/docking-session4-{red,
  core,input-red,final-build-shell,final-menu}.log`.
- `build run '--args=--camera-smoke --simultaneous-views' --console plain --no-daemon`:
  PASS after review fix, including full JVM build and actual ordinary Compose shell
  with simultaneous Scene/Game, input, camera following, pause/resume/Stop, save/open.
  Inspected `camera-editor-layout.png`: both native presenters, scene grid/gizmos,
  textured Game entities and retained inspector/tree in the existing shell.
- Native build (Windows CMake; Debug):

  ```bash
  '/mnt/c/Program Files/CMake/bin/cmake.exe' --build build/debug --config Debug --target sle_editor_native integration_embedded_multiview_path engine_app --parallel 4
  '/mnt/c/Program Files/CMake/bin/ctest.exe' --test-dir build/debug -C Debug -R '^integration_embedded_multiview_path$' --output-on-failure
  ```

  Both PASS; windowed native multiview test **1/1**, no skips. Logs
  `/tmp/docking-session4-{native,ctest}.log`. Final bridge at
  `build/debug/tools/editor-native/Debug/sle_editor_native.dll`; engine_app rebuilt.
- Multiview desktop smoke: initial lifecycle pass, then added regressions reproduced
  wrong Game metrics and ignored Fit. Fixed both and rebuilt native targets.
  Final resize/input regression includes changing the Game component dimensions
  and dispatching input in the same EDT callback, asserting old publication clears
  before another owner tick. Actual final results appended below.
- Core owner failure test verifies no future advance after an exception and one
  close. Hidden/unsubscribed views continue advancing, two draws share sequence,
  resize invalidates old input, latest publication stays bounded through 1,000 ticks.
- Independent fresh-context read-only review found one P2 resize/input issue; its
  focused test failed then passed after synchronous request invalidation. No other
  confirmed findings or deferred minor findings. No second review was requested.
- Early failed desktop runs are retained in `/tmp/docking-session4-*.log`:
  hidden-host property shadowing, nested JAWT cleanup lock (which produced an ignored
  JVM crash report), and a false hidden-body assertion after physics settled.
  The latter now uses an always-updating Lua counter and asserts update/tick equality.
  These failures are not counted as passes. Project-runtime's first File-menu
  automation attempt failed before project launch; its unchanged rerun passed.
- Late compatibility regression: Sandbox used Game draw policy and hid its enabled
  F3 collider overlay. Added enabled/disabled cyan framebuffer assertions around
  a real queued F3 press. RED confirmed zero enabled collider pixels. New JNI
  `drawEmbeddedFrame(session,width,height)` exposes the existing render-only native
  path for Sandbox without imposing Game's debug suppression; no simulation occurs.
- That owner-frame failure also exposed a callback-shadowing bug: timer initialization
  captured the constructor's handler instead of the mutable workspace handler.
  Actual `--owner-failure-smoke` deliberately sends malformed authoring content
  after valid initialization; RED invoked the original failure handler. Timer now
  uses `this.onFailure` after cleanup. The installed AWTGLCanvas bytecode confirms
  runInContext unlocks in finally; the earlier timeout was swallowed notification,
  not an unreleased AWT lock. Final GREEN results follow below.

#### Final verification / prerequisite readiness

- Final native bridge rebuild PASS (`/tmp/docking-session4-native-final-bridge.log`),
  with the same Windows CMake prefix and `--target sle_editor_native --parallel 4`.
  Final full Gradle `build` PASS, **119 tests, 0 failures/errors, 2 existing Windows
  symlink skips**. Logs `/tmp/docking-session4-verified-build.log` and
  `docking-session4-verified-multi-final.log`; the latter includes another full
  build after the final smoke additions.
- Actual final `run --args=--owner-failure-smoke`: PASS,
  `OWNER_FAILURE_SMOKE_PASS callback/stop/cleanup`. It asserts the current handler
  runs after publication clears and owner context/native runtime are gone.
  `/tmp/docking-session4-verified-owner-failure-smoke.log`.
- Actual final `build run --args=--smoke-test`: PASS, session/thread guards,
  pause/resume/Stop/Run, framebuffer/desktop images, and F3 collider states.
  The fixture has tiny one-unit colliders: the raw draw yields 16 cyan pixels
  enabled and 0 disabled; the regressed Game-scoped path yielded 0 enabled.
  The initial 40-pixel test threshold was inappropriate for this fixture and
  was corrected to a minimum of 8. Both debug captures were inspected, showing
  cyan collider marks in the sprites and their removal after F3.
  `/tmp/docking-session4-verified-sandbox-final.log`.
- Actual final `build run --args=--multi-viewport-smoke`: PASS,
  `MULTI_VIEWPORT_SMOKE_PASS owner=65537 playCreates=1 finalCreates=2 finalDestroys=2
  explicitStopReset=1`. No runtime creation/destruction while resizing, hiding,
  closing or recreating either presenter during Play. One intentional legacy
  reset on explicit Stop restores authored transforms with the same GL context;
  each native runtime is destroyed exactly once. Same-tick resize/input rejection,
  distinct cameras/dimensions, script-update versus owner-sequence equality,
  zero visible views, pause/resume and captures all passed.
  `/tmp/docking-session4-verified-multi-final.log`.
- Final compatibility runs passed: `--camera-smoke --simultaneous-views`
  (`/tmp/docking-session4-final-build-shell.log`), `--gizmo-smoke`, `--scene-smoke`,
  `--project-runtime-smoke` (`/tmp/docking-session4-final-*-smoke.log`), and
  `--context-menu-smoke` (`/tmp/docking-session4-final-menu.log`). These exercised
  real input, gizmos/undo/drafts, scene/entity/sandbox switching, project VSync/FPS,
  Run Project/deferred switches/relocation/standalone launch, and menu/save routing.
  They passed after the review resize fix; the later change restores only Sandbox
  raw-debug drawing and error callback notification, both separately tested above.
- Captures/reports under `tools/entity-editor/build/reports`:
  `multi-viewport.txt`, `multi-scene.png`, `multi-game.png`,
  `multi-scene-reopened.png`, `multi-game-recreated.png`,
  `camera-editor-layout.png`, `sandbox-debug-true.png`, `sandbox-debug-false.png`,
  existing camera/gizmo/project images. Four multiview images and ordinary-shell
  simultaneous-view capture inspected, as were both Sandbox debug-state images.
- Documentation relative links, `git diff --check` and explicit new viewport-file
  trailing-whitespace checks PASS. No generated Lua API change.
- **Unavailable checks / limits:** physical mouse/taskbar/cross-application stacking,
  mixed-DPI/multi-monitor/removal, prolonged stress and Release were not validated.
  GPU synchronization remains the conservative EDT-only protocol measured in
  session 1. No background rendering or new performance claim. Full native suite
  was not rerun: changed native targets were built and the focused windowed
  multiview regression plus actual rebuilt-JNI desktop paths were run.
- **Next milestone:** session 5 is ready when requested. It consumes the session
  command/frame contracts above and session-2 layout model. Full docking,
  native tool-host management/menu, dragging, sizing and persistence remain
  sessions 5–7. No session-5 implementation started.
- **Git/commit state:** dirty `feat/project-foundation` checkout preserved in place;
  all inherited and new changes remain uncommitted/unpushed. No staging, reset,
  cleanup or worktree migration. Baseline and logs retained under `/tmp`.
- Final post-bridge focused CTest rerun PASS: **1/1**, no skips, 0.23 seconds;
  `/tmp/docking-session4-verified-ctest.log`. No broader CTest result is claimed.

### Session 5 implementation / handoff — 2026-11-13

- Scope: workspace shell and command-based docking only. Session 6 drag gestures,
  interactive splitters, recursive native minimums and minimize strips remain pending.
- `EditorWorkspace` retains scene/prefab/project/playback state, UI scale, scene-copy
  selection and one lazy `EditorEngineSession`. `WorkspaceWindowManager` retains the
  observable immutable layout, native host bindings and idempotent shutdown path.
  Existing project/document/asset composables run once beneath the retained home;
  moving/hiding their content never recreates those services or their coroutine scopes.
- `WorkspacePanels` binds all six singleton IDs to existing Hierarchy, Inspector,
  Assets, Scene, Game and Entity content. `DockHost` recursively renders splits and
  active tabs. Material tab measurement is keyed by membership to avoid stale index
  state when panels move or disappear.
- Home and auxiliary hosts are ordinary decorated Compose `Window`/AWT JFrames.
  Only home installs `EditorMenuStrip`. Auxiliary windows are unowned, nonmodal,
  independently focusable frames; closing them hides their panels and removes the
  host from the layout. Restore uses the retained placement and creates a fresh peer.
- Windows menu provides Show / Focus, Hide, panel detach, tab/edge movement,
  group detach/merge, native-host focus and Restore Default Layout. View → Scene,
  Game and asset visibility route through the same manager. Empty home retains menus.
- Authoring context follows explicit Scene/Game/Entity selection; Hierarchy and
  Inspector adapt to that context. Hiding the focused panel selects the exposed tab;
  restoring defaults selects Scene. During Play, merely focusing Entity does not stop
  gameplay. Explicit Open Entity Editor / Edit Entity retains the established Stop
  rule. The existing session has one content mode: Entity preview and scene runtime
  are alternative authoring modes; Scene and Game can be presented simultaneously.
  In Entity mode the other viewport panels offer Show Scene/Game, and vice versa.
- File/Edit menus continue using the existing document commands. Scene-copy entity
  editing saves its containing scene; standalone prefab editing saves the prefab.
  Source conflict checks, dirty confirmations and project switching remain in their
  existing shared services. Failed project open retains existing documents/layout.
- The durable controller observes serialized content, playback/project and game-view
  values during composition before applying them on EDT. Presenters only bind a
  fixed view; no panel sets competing session content. Each toolbar retains its own
  presenter. Scene/Game movement and hidden views release held input/finish gestures
  through the manager before committing layout changes.
- Home window close and File → Exit use `requestExit`: cancellation commits nothing;
  acceptance closes the engine once before application disposal. The disposal
  fallback is also idempotent. No layout action invokes engine shutdown.
- Smoke hooks now resolve the current presenter instead of retaining disposed peers.
  Existing document, camera, input, physics, pixel and save assertions remain.
  `--docking-workspace-smoke` exercises all six panels, two native menu-free tool
  hosts, simultaneous Scene/Game, close while playing, hidden-view simulation,
  restore with one runtime, scene-copy File Save, invalid prefab draft retention,
  canceled exit and accepted cleanup. `WorkspaceLifecycleTest` covers the matching
  pure lifecycle/context contracts and real document/project state.

Validation results and final status are recorded below after the final run.

- Default Assets allocation is 35% vertically (previous model default: 25%):
  registered tabs add chrome above the existing browser toolbar, so the larger
  initial region keeps ordinary project folder rows accessible at the supported
  default window/scale. The tree remains scrollable; browser smoke scrolls when needed.
- Authoring context is tracked separately from utility-panel focus. Focusing Assets,
  Hierarchy or Inspector does not erase the context needed when Entity is hidden.

### Session 5 paused for the weekend — 2026-11-13

User requested stopping for tonight and resuming Monday. **Session 5 is in progress;
not yet signed off.** All code and documentation edits are saved in the existing
worktree. No commit, reset, merge or session-6 work was performed. Preserve the
large pre-existing dirty tree from sessions 1–4 and project/distribution work.

Current validation performed this session:

- Windows JDK 21, `./gradlew.bat -g .gradle-user-home build`: passed with
  126 JVM tests, zero failures/errors, two existing skips **before the final
  utility-panel-context regression/fix**.
- Successful desktop checks: `--scene-smoke`, `--gizmo-smoke`,
  `--context-menu-smoke`, `--project-smoke`, `--asset-browser-smoke`,
  `--project-documents-smoke`, `--project-runtime-smoke`, `--preview-smoke`,
  `--smoke-test`, `--multi-viewport-smoke`, `--docking-workspace-smoke`,
  `--camera-smoke`, `--authoring-ui-smoke`. Logs for the sequential runs are
  `tools/entity-editor/build/reports/session5-*.log` (PowerShell UTF-16).
- The latest passing docking smoke includes actual Scene toolbar zoom after
  focusing Game, native unowned/decorated/menu-free hosts, close during Play,
  hidden-view simulation, restore, scene-copy Save, draft retention and cleanup.
  Its exit-cancellation check at that point called the shared manager directly.
- Fresh read-only review found shared Scene toolbar targeting Game (fixed and
  desktop-verified), then stale authoring context after hide/reset (initial
  regression fixed and full build passed). Follow-up found the additional sequence
  `show Entity → show Assets → hide Entity`. The expanded lifecycle test was run
  and failed at WorkspaceLifecycleTest.kt:116, reproducing that remaining bug.

Latest saved changes **still requiring validation**:

1. `WorkspaceWindowManager` now tracks authoring-panel selection separately from
   utility-panel focus, so Assets/Inspector/Hierarchy cannot mask the context that
   must be reconciled when Entity closes/hides. `EditorWorkspace` no longer keeps
   the redundant authoring-panel property. Run the expanded lifecycle test and full
   build; extend its utility-panel coverage if needed.
2. Docking smoke now cancels the actual home WINDOW_CLOSING discard dialog via
   JOptionPane NO, rather than injecting a false manager confirmation. Main exempts
   docking smoke from the legacy testing shortcut that bypassed confirmations.
   Run `--docking-workspace-smoke` to validate this final change.
3. The final `build run --args=--docking-workspace-smoke` tool invocation was
   interrupted by the user before a result was received. Do not report it as a
   pass; check for a leftover Gradle/editor process before starting GUI checks.

Resume commands (Windows PowerShell, tools/entity-editor, JAVA_HOME=C:\java\jdk-21.0.2):

```powershell
.\gradlew.bat -g .gradle-user-home test --tests sle.editor.workspace.WorkspaceLifecycleTest
.\gradlew.bat -g .gradle-user-home build
.\gradlew.bat -g .gradle-user-home run --args=--docking-workspace-smoke
```

Then assess whether the final focused changes require any additional compatibility
reruns, inspect the final diff/docs, mark the session-5 checklist and completion
record accurately, and stop before session 6. Manual mixed-DPI/monitor and physical
input checks remain unperformed. No native C++ files were changed in session 5;
existing native changes belong to earlier work.

Scratch bookkeeping is `.superpowers/sdd/editor-docking-plan/progress.md` and the
sequential validation helper is in that same ignored directory. The durable
resume authority is this plan plus `editor-docking-handoffs.md`, not scratch files.

### Session 5 completion / final handoff — 2026-11-16

**Status: implemented and automated validation passed; stop before session 6.**
This completion record supersedes the weekend pause above.

Final corrections on resume:

- Kept utility focus separate from authoring context and filtered exposed tab
  candidates to Scene/Game/Entity. Added a mixed Assets/Entity group regression;
  it failed before the correction and passed afterward. Hiding Entity now returns
  to the visible scene context even after focusing utility panels or grouping them.
- Docking smoke synchronizes native Scene-tool focus before its Robot toolbar
  click and uses a short Robot event delay. It still focuses Game first and
  verifies that clicking Scene zoom changes the Scene camera. This avoids relying
  on an inactive Windows frame accepting an immediate click.
- The smoke now sends the actual home WINDOW_CLOSING event, answers the real
  dirty-document JOptionPane with NO, and checks that layout, drafts and runtime
  remain alive. Accepted cleanup still verifies balanced native creates/destroys.
- Failed docking smoke cleanup closes the workspace before exiting, avoiding
  abrupt JVM termination with live GL resources. Timeout diagnostics capture the
  desktop to `build/reports/docking-workspace-failure.png`.
- Fresh read-only review confirmed the mixed-group correction and reported no
  further high-confidence session-5 findings.

Validation actually run on Monday (Windows JDK 21, editor directory):

```powershell
.\gradlew.bat -g .gradle-user-home test --tests sle.editor.workspace.WorkspaceLifecycleTest
.\gradlew.bat -g .gradle-user-home build run --args=--docking-workspace-smoke
```

The final full build and docking run passed: **127 JVM tests, zero failures/errors,
two existing skips**, plus `DOCKING_WORKSPACE_SMOKE_PASS`. The initial Monday
camera-toolbar smoke attempt failed before native-focus synchronization; its later
corrected run passed, including real canceled home exit. The focused mixed-group
regression likewise failed before its fix; neither failed attempt is counted as a
successful validation.

The Friday compatibility checks listed in the weekend handoff remain historical
results, not Monday reruns. No native C++ code was changed in this milestone.
Manual mixed-DPI/monitor, physical input and interactive session-6 operations remain
unvalidated. All edits remain in the existing worktree; no commit/merge/reset was
performed. The next authorized milestone is session 6 when the user requests it.

Final focused desktop reruns on Monday also passed (exit 0 each):

```powershell
.\gradlew.bat -g .gradle-user-home run --args=--camera-smoke
.\gradlew.bat -g .gradle-user-home run --args=--context-menu-smoke
.\gradlew.bat -g .gradle-user-home run --args=--docking-workspace-smoke
```

Logs: `build/reports/session5-monday-{camera,context-menu,docking-workspace}-smoke.log`
under the editor directory. This final docking repeat verifies native-focus click
synchronization and real canceled home exit a second time. `git diff --check` also
passed. No additional compatibility reruns were needed after these focused fixes.

### Session 6 execution ledger — 2026-11-16

Session 6 is authorized by the current user request. Preserve the existing dirty
`feat/project-foundation` checkout, including all session 1–5 and project work;
no isolation copy, reset or bulk commit of existing edits. Work stops before 7.

- Interfaces: consumes session-2 atomic operations and recursive sizing; session-5
  manager is the single layout/lifecycle commit boundary; before-layout callback
  already finishes authoring gestures and releases Game input.
- Coordinate contract: MouseEvent screen positions and AWT host bounds use screen
  user units. Compose bounds are physical pixels relative to the content pane;
  convert with that window's current OS transform, including negative origins.
  UI scale is already represented in measured pixels. It is applied once to
  registry minima, never again to pointer coordinates.
- Drag captures source layout/revision, proposes a target without mutating layout,
  and commits through existing atomic operations once. A group grip moves all tabs.
  Window chrome is an invalid target; only outside all visible hosts detaches.
- Preview contract: a nonfocusable transparent AWT JWindow paints tab insertion/
  split/detach previews above heavyweight native peers. No new document host is
  created until drop. Escape, host removal/move, native minimize, external focus
  loss and layout commands cancel the capture. Crossing workspace hosts alone
  does not cancel.
- Tests: initial six DockDragTest cases ran against stubs and all six failed
  (session6-red.log). First integration compile exposed a read-only AWT property;
  corrected to `focusableWindowState`. Focused workspace tests subsequently passed
  (session6-focused.log). Broader checks and desktop smoke still pending.


### Session 6 completion / handoff — 2026-11-16

**Status: implemented and automated validation passed. Stop before session 7.**

Implemented `DockDragController.kt`, `DockDropTargets.kt`, `DockDesktopDrag.kt`,
`DockChrome.kt`; updated `DockHost.kt`, `DockSizing.kt`,
`WorkspaceWindowManager.kt`, `WindowsMenu.kt`, `app/Main.kt`, workspace tests and
production docking desktop smoke. User instructions and the existing handoff
require retaining this checkout's pre-existing changes; no commits, resets,
worktree copies or merges were performed.

Behavior and final contracts:

- Tab drag supports stable insertion/reorder/merge, body-edge splits and outside
  detach. The dotted grip moves all tabs, preserving order/active selection.
  Layout snapshots/revisions reject stale drops; invalid/self targets do nothing.
- AWT activation ordering resolves the foremost workspace host before its content.
  Foreground native chrome blocks groups underneath. Tab geometry is clipped to
  the scrollable strip; insertion indices still count offscreen tabs in full model
  order, excluding moving panels. No document host is created by a preview.
- Nonfocusable transparent JWindow previews sit above native viewport peers;
  capture/listeners/overlays are disposed on completion, cancellation and shutdown.
  AWT supplies captured drag/release events outside the source window. Escape,
  external focus loss, source removal/move/minimize and intervening layout changes
  invalidate the drag. The peer watcher also cancels if the source disappears.
- Splitters use recursive child minima with the measured remaining span. Native
  host minimums include measured shell overhead, decorations, restore strip and
  UI scale; oversized constraints fall back to tabs inside the monitor work area.
  Panel `_`, detach and hide controls travel with groups. Restore strips scroll;
  native minimize remains independent of panel minimized placement.
- Shared before-layout/start-drag callbacks finish authoring gestures and release
  Game input. Robot checks actual Lua movement while D is held, redocks Game during
  Play, then verifies movement stops before releasing the physical key. Runtime
  context/create counts, drafts, undo, authoring data and canceled-exit state survive.

Review and regressions:

- Fresh read-only reviewer identified one Important bug: foreground chrome could
  accept a background target. `foregroundHostChromeBlocksBackgroundDropTargets`
  failed before the fix and passed in the final suite. Activation-ordered native
  host hit-testing now blocks it.
- The review's narrow/scrolled-tab suggestion led to explicit clipped tab geometry
  and full-order insertion tests. The scrolled insertion regression failed before
  its correction, then passed; offscreen source geometry is also covered.
- Initial six drag tests all failed against stubs before implementation. Intermediate
  compilation failures during UI wiring are in logs, not counted as successful
  validation. No outstanding confirmed review findings remain.

Commands actually run from `tools/entity-editor` using Windows JDK 21:

```powershell
.\gradlew.bat -g .gradle-user-home test --tests sle.editor.workspace.DockDragTest
.\gradlew.bat -g .gradle-user-home test --tests sle.editor.workspace.*
.\gradlew.bat -g .gradle-user-home test --tests sle.editor.workspace.DockDragTest.foregroundHostChromeBlocksBackgroundDropTargets
.\gradlew.bat -g .gradle-user-home test --tests sle.editor.workspace.DockDragTest.scrolledTabInsertionCountsOffscreenTabsAndExcludesMovingSource
.\gradlew.bat -g .gradle-user-home build run --args=--docking-workspace-smoke
.\gradlew.bat -g .gradle-user-home run --args=--multi-viewport-smoke
.\gradlew.bat -g .gradle-user-home build run --args=--camera-smoke
.\gradlew.bat -g .gradle-user-home run --args=--context-menu-smoke
```

The final build/docking run is `build/reports/session6-verified-docking.log`:
**137 JVM tests, zero failures/errors, two existing skips**, `INTERACTIVE_DOCKING_SMOKE_PASS`,
`GAME_PLAY_REDOCK_SMOKE_PASS` and `DOCKING_WORKSPACE_SMOKE_PASS`, exit 0. It covers
actual cross-window tab/group gestures, splits/divider, Escape, minimum resize,
panel/native minimize/restore, Play redock/input release, document history and cleanup.
`session6-multiview.log` reports `MULTI_VIEWPORT_SMOKE_PASS` (balanced native ownership).
`session6-camera.log` reports `CAMERA_SCENE_PLAY_PASS`. These are current session-6
runs, not historical session-5 results. `session6-context-menu.log` reports `CONTEXT_MENU_SMOKE_PASS`, exit 0.
Captures: `build/reports/session6-{split,group-detached,restored}.png` and
`docking-workspace.png`. `git diff --check` also passed.

Limits: controlled mixed-DPI/negative-monitor desktop transitions, physical mouse
capture-loss scenarios and manual monitor reconfiguration were not performed.
Negative origins/unequal scales, stale targets and impossible display minima have
pure regression coverage. No C++ source changed in session 6; native CTest was not
rerun. Layout persistence/display restoration remains explicitly session-7 work.


### Session 7 implementation ledger — 2026-11-16

Scope: session 7 only; retain this shared checkout and all prior changes. Existing
approved design/session brief used directly; no new worktree, commits or merges.

Interface review: layout operations produce immutable DockLayout/revision values;
codec consumes registered descriptors; store captures key/revision/value on enqueue;
project activation selects storage without changing ProjectFiles editor-state JSON;
window manager applies pure display recovery before showing restored hosts.

Implemented version-1 JSON codec, FIFO background atomic storage, debounced manager
lifecycle, project-key callbacks, startup visibility gate, and work-area/DPI polling.
New persistence/display tests and five fresh-process desktop phases cover standalone,
projects A/B, native minimized restore, reset, corruption and simulated display loss.
Initial tests failed to compile because APIs were absent (session7-red.log); focused
suite then passed. Intermediate compile errors are not validation passes.

Review: independent read-only reviewer identified failed-write revision consumption.
`retriesFailedRevisionWithoutAllowingOlderWrites` failed before correction; store now
tracks highest requested and completed revisions separately so a failed newest write
can retry while older writes remain rejected. Additional duplicate JSON object-key
regression failed before the scanner fix; escaped equivalent keys are checked before
JSON map construction. Deep in-memory trees receive iterative limits before helpers.
Final full build, docking/project/restart evidence and limitations pending below.


### Session 7 completion / handoff — 2026-11-16

**Status: implemented and automated validation passed. Stop before session 8.**

Created `WorkspaceLayoutCodec.kt`, `WorkspaceLayoutStore.kt`,
`WorkspaceDisplayRecovery.kt`, `WorkspacePersistenceSmoke.kt` and persistence/display
regression tests. Updated manager/workspace/project activation/startup integration,
editor README and SceneEditor topic. Preserved all pre-existing edits; no commits,
resets, worktree copies, native engine changes or merges.

Schema and storage contract:

- UTF-8 version-1 JSON: `version`, `mainHost`, `hosts` array (`id`, `bounds`, `root`),
  `hidden` and `minimized` maps keyed by panel ID. Bounds are `[x,y,width,height]`
  in AWT logical screen coordinates. Tabs contain `group`, `panels`, `active`;
  splits contain `axis` (`Horizontal`/`Vertical`), `ratio`, `first`, `second`.
  Placement values contain `host`, `group`, `index`, `bounds`. Native minimize is
  deliberately absent; restored hosts return normal. Documents/assets are absent.
- Project path: `<project-root>/.sle/workspace-layout.json`. Standalone Windows:
  `%LOCALAPPDATA%/SLE/EntityEditor/workspace-layout.json`; fallback without
  LOCALAPPDATA: `~/.local/share/sle/entity-editor/workspace-layout.json`.
  `-Dsle.workspaceData=<directory>` overrides standalone directory for isolation.
  `.sle/editor-state.json` and its existing UI-scale behavior are preserved.
- Reads take at most 1 MiB + one sentinel byte. JSON nesting is checked before
  parsing; layout traversal allows 256 nodes total, depth 32, 32 auxiliary hosts.
  IDs are bounded to 128 characters and placement indices to 0..256. Duplicate
  JSON keys (including escaped spellings), panels/groups/hosts, invalid ratios,
  nonfinite/nonpositive bounds, empty groups and unsupported versions reject the
  file. Load returns defaults plus a notice instead of failing startup. Unknown
  panels prune/collapse empty branches; new panels join main once (unavailable
  descriptors stay hidden). Deep in-memory recovery inputs are bounded iteratively.
- A single FIFO IO worker owns reads/writes and per-key requested/completed revision
  watermarks. Every save captures its immutable key/revision/layout before enqueue.
  Lower revisions are rejected; successful equal revisions are idempotent; failed
  equal revisions may retry. Temp files are unique siblings, forced to disk before
  atomic replacement; failed writes retain the last valid destination. No unsafe
  non-atomic fallback. Revision watermarks are process-local, not schema fields.
- Geometry/ordinary changes debounce 350 ms. Project switch queues the outgoing
  value before the incoming load on the same worker; generation guards reject stale
  callbacks. Shutdown drains ordered writes. Loading gates window visibility; no
  document reset is performed by layout IO. Windows reset persists layout only.
- Recovery selects the connected work area with greatest intersection (primary
  enumeration first when entirely offscreen), constrains complete window bounds,
  preserves valid negative coordinates and never rescales logical positions by DPI.
  Impossible split minima collapse to tabs; displays smaller than a panel win over
  content minima. Hidden/minimized restore bounds are recovered as well as hosts.
  One-second work-area/DPI polling cancels drags and reapplies geometry; measured
  native shell/inset minimum handling remains in the manager's existing sizing path.

Evidence from this session (Windows JDK `C:\java\jdk-21.0.2`, working directory
`tools/entity-editor`; PowerShell captured combined output to the listed logs):

```powershell
.\gradlew.bat -g .gradle-user-home test --tests "sle.editor.workspace.*"
.\gradlew.bat -g .gradle-user-home test --tests "sle.editor.workspace.Workspace*Test"
.\gradlew.bat -g .gradle-user-home build run --args=--docking-workspace-smoke
.\gradlew.bat -g .gradle-user-home build run --args=--project-smoke
.\gradlew.bat -g .gradle-user-home build run --args=--layout-persistence-smoke
```

Final `session7-restart.log`: **BUILD SUCCESSFUL**, **154 JVM tests, zero failures
or errors, two existing skips**, five `WORKSPACE_LAYOUT_PHASE_PASS` markers and
`WORKSPACE_PERSISTENCE_SMOKE_PASS`. Each phase is a fresh JVM with actual production
windows/storage: standalone write/read, project A/B isolation, native minimized
restore, simulated monitor removal, layout reset then restart, corruption then
restart/default notice. Fixture directory from final run:
`C:/Users/VESELI~1.TRI/AppData/Local/Temp/sle-layout-desktop10778643582724508115`.
Tests include a blocked in-flight atomic write while another key/save/load is
queued, failure preservation/retry, lower revisions, malformed/version/size/tree
limits, duplicate/unknown/new IDs, negative monitors, scaled minima, offscreen and
hidden restore geometry. Persistence/display suites contain 11/6 tests respectively.

`session7-docking.log`: BUILD SUCCESSFUL, `INTERACTIVE_DOCKING_SMOKE_PASS`,
`GAME_PLAY_REDOCK_SMOKE_PASS`, `DOCKING_WORKSPACE_SMOKE_PASS`.
`session7-project.log`: BUILD SUCCESSFUL, `PROJECT_FOUNDATION_SMOKE_PASS`.
Inspected `build/reports/docking-workspace.png`: main menu, six panel tabs,
scene tree/viewport/Inspector and Assets render normally. Current docking smoke
also refreshed `session6-{split,group-detached,restored}.png` (legacy filenames).
`git diff --check` passed. Logs are under `tools/entity-editor/build/reports/`.

Review fixes: failed revision retry and duplicate JSON keys each had an observed
failing focused regression before correction (`session7-retry-red.log`,
`session7-duplicate-red.log`), then passed in the full suite. Final recovery check
found hidden detached bounds surviving monitor removal; `session7-hidden-red.log`
records that failure and the final suite verifies the correction. No outstanding
confirmed review issues or deferred minor findings remain.

Limitations: physical monitor unplug/reconnect and controlled native mixed-DPI
transitions were unavailable, so simulated geometry checks do not establish those
native behaviors. Atomic replacement depends on filesystem support; unsupported
replacement produces a notice and retains the previous file. Concurrent editor
processes writing the same project are not coordinated (ordering is per process).
No C++ changed; native CTest was not rerun. Session 8 acceptance remains unstarted.

### Session 8 execution ledger — 2026-11-16

Authorized session 8 from the existing shared `feat/project-foundation` checkout.
Preserve all previous modified/untracked files; do not create an isolation copy,
commit, push, reset or merge. Existing design and prior completion records define
this acceptance pass; no new feature design is required.

- Reviewed milestone interfaces: durable session → per-view immutable metrics;
  one advance → zero/multiple draws; manager → panel factories; project callbacks
  → FIFO layout storage. Existing defaults/storage and native wrappers retained.
- Separate read-only reviewer identified Game F3 reaching debug input in the new
  advance path. Root cause: draw-scoped `gameViewOverride` is absent during advance.
  `integration_embedded_multiview_path` failed with “separate Game advance ignores
  editor debug shortcut” (`session8-debug-red.log`). Added explicit
  `allowDebugInput` (default true) to advance; JNI disables it for Scene/Project
  Play using existing `scenePlaying`, Sandbox retains true. Rebuilt all native
  targets; all 73 CTests passed, including the regression and Sandbox F3 assertion.
- Review also found content binding validation hard-coded to six IDs. Both new
  `WorkspacePanelsTest` cases failed (`session8-panels-red.log`), then passed with
  registry-based validation. Main supplies its manager's registry. Missing/extra
  bindings still fail; synthetic future panel traverses normal window operations.
- Fresh Gradle `build --rerun-tasks`: 156 JVM tests, zero failures/errors, two
  existing skips. Initial native/editor runs passed; first PowerShell wrapper used
  Stop for native stderr and interrupted the first docking run. Corrected the
  diagnostic wrapper to Continue and checked each process exit code explicitly.
- Current desktop batch: docking failed at Inspector merge (not counted as pass);
  multiview, scene, camera, gizmo, menu and project checks passed. Added failure
  capture/details to the interactive await before rerunning. Remaining results,
  capture review and final acceptance follow below.
- Ruling: continue in the shared checkout and use this plan as the ledger, as
  explicitly required by its handoff; automatic skill commit/worktree/cleanup
  steps do not apply. No user changes are moved, staged or removed.


### Session 8 completion / final acceptance — 2026-11-16

**Status: implemented and automated validation passed, with unavailable manual
checks.** All eight milestones have completion records. This is a reviewable
uncommitted result; no commit, push, merge, reset, cleanup or isolation copy was
performed. Preserve all pre-existing project/runtime/editor work.

Files changed for this milestone (in addition to retained earlier changes):

- Runtime.hpp / Runtime.cpp and EditorBridge.cpp: explicit advance debug-input
  policy, disabled by JNI during Scene/Project Play, default enabled for Sandbox.
- `tests/integration/systems/integration_embedded_multiview_path.cpp`: observed
  failing Game-F3 regression, then green; default Sandbox F3 retained.
- WorkspacePanels.kt, Main.kt and new WorkspacePanelsTest.kt: validate bindings
  against the actual registry rather than hard-coded standard IDs. Both focused
  tests failed before correction and passed in the full build.
- DockingWorkspaceSmoke.kt: every panel's real detach/tab/split/minimize/hide/
  restore peers and identity checks; separate-monitor Scene/Game and capture.
  InteractiveDockingSmoke.kt: failure capture, input diagnostics and a temporary
  foreground activation pulse, released before ordinary-window drag assertions.
- New `docs/topics/EditorWorkspace.md` operating guide; affected editor README,
  SceneEditor/Runtime/Systems docs and documentation index; this plan/handoffs.

Final current commands, Windows tooling from `C:/projects/engine`:

```powershell
cmake --build build/debug --config Debug --parallel 4
ctest --test-dir build/debug -C Debug --output-on-failure
cd tools/entity-editor
.\gradlew.bat -g .gradle-user-home build --rerun-tasks --console plain
.\gradlew.bat -g .gradle-user-home run --args=--multi-viewport-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--scene-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--camera-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--gizmo-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--context-menu-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--project-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--asset-browser-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--project-documents-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--project-runtime-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--layout-persistence-smoke --console plain
.\gradlew.bat -g .gradle-user-home run --args=--owner-failure-smoke --console plain
.\gradlew.bat -g .gradle-user-home build run --args=--docking-workspace-smoke --console plain
```

All final commands above exited 0. Native build includes `sle_editor_native`,
`engine_app` and every CTest executable before execution. **73/73 CTests passed,
zero skipped** (`session8-native-build.log`, `session8-ctest.log`); the multiview
regression is test 64 with `windowed` label. **156 JVM tests, zero failures/errors,
two existing Windows symlink skips**, fresh `--rerun-tasks` build and again in
final docking build. Skips: AssetBrowserTest.linksAreShownWithoutTraversingExternalContents
and ProjectAssetImportsTest.linkSourceAndLinkedDestinationAreRejected.

All reports/logs are under `tools/entity-editor/build/reports/` (ignored build
artifacts). `session8-{multi-viewport,scene,camera,gizmo,context-menu,project,
asset-browser,project-documents,project-runtime,layout-persistence,owner-failure}.log`
record successful desktop checks. Final docking is `session8-docking-final.log`:
`INTERACTIVE_DOCKING_SMOKE_PASS`, `SEPARATE_MONITORS_SMOKE_PASS`,
`GAME_PLAY_REDOCK_SMOKE_PASS`, `DOCKING_WORKSPACE_SMOKE_PASS`, BUILD SUCCESSFUL.
Persistence has five WORKSPACE_LAYOUT_PHASE_PASS markers and its final pass;
fixture `C:/Users/VESELI~1.TRI/AppData/Local/Temp/sle-layout-desktop17705509375270382978`.
`session8-debug-red.log` / `session8-panels-red.log` retain expected regression
failures; those failures are not counted as passes.

Docking initially failed Inspector merge: OS captures showed the editor covered
by other applications, and the temporary AWT mouse listener received no presses.
A temporary smoke-only always-on-top activation pulse restored actual foreground
visibility; it is reset to false before drag assertions. Final traces show real
press/release delivery and all original gesture assertions pass. Production host
policy is unchanged. The original failing batch log remains
`session8-docking-workspace.log`; diagnostic reruns were failures, not successes.

Inspected current captures: `multi-{scene,game}.png` (same textured scene,
independent framing), `camera-editor-layout.png`, `gizmo-editor.png`,
`session6-group-detached.png` (legacy filename refreshed by this run),
`session8-simultaneous.png` (native Scene and Game on separate displays), and
`docking-workspace.png` (restored menus, tabs, hierarchy/inspector and scene).
Two Windows displays: primary (0,0) 1920×1080 / work area 1920×1032, secondary
(1920,0) 1920×1080 / work area 1920×1032. Intel Graphics driver 32.0.101.8860,
Windows 11 Pro 10.0.26300, Windows JDK 21.0.2. Current runs did not vary native DPI.

`git diff --check` passed; relative link target check passed for six affected user/
module docs. Independent read-only review found the F3 regression and the binding
restriction, both covered/fixed above; it reported no other high-confidence
production blockers in lifetime/GL/frame/input/project paths.

Remaining limitations / exact future checks:

- Controlled mixed-DPI native transitions, physical monitor unplug/reconnect,
  negative-origin physical monitors and physical mouse capture-loss interactions
  were not performed. Exercise those manually before claiming full hardware
  acceptance. Pure geometry recovery/scale/cancellation coverage passed.
- Existing two Windows symlink tests remain skipped. Full export/distribution
  desktop smoke was not run: no export or standalone-launch behavior changed;
  distribution launch CTest did pass as part of all 73 native tests.
- Deferred minor: legacy `--smoke-test --simultaneous-views` combines Sandbox with
  two presenters sharing ViewId.Game. Ordinary production UI does not expose
  Sandbox; supported Scene/Game multiview passes. Fix that diagnostic-only flag
  combination separately if it becomes a supported use case.
- Concurrent editor processes still do not coordinate layout writes; filesystem
  atomic replacement support remains required. These existing limits are in the
  operating guide. No further implementation milestone starts automatically.

Ruling carried from execution: retain this shared checkout and plan ledger rather
than apply automatic worktree/commit/cleanup skill steps; this follows the explicit
handoff and avoids losing unrelated user changes. Cost if wrong: reviewers must
separate milestones from a combined uncommitted diff; the changed-file list and
fresh evidence above provide the handoff boundary.
