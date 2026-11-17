# L1 — Editor core: commands, history, selection, status bar, viewport tools

**Status:** Planned 2026-11-17. Session 1 implemented and validated (see Progress); sessions 2–7 not started.
**Parent:** [editor-game-workflows.md](editor-game-workflows.md) §5, base layer
**L1** (roadmap items 0.1, 0.2, 0.4, 1.1, 1.2 and rule R3).
**Request:** Plan L1 only. Tilemaps and the other layers come later.

## Goal and acceptance criteria

Build the shared editor plumbing every later feature relies on, and deliver
the first visible slice: Unity-style viewport tools with snapping.

- **Commands:** every editor action is registered once with an id, label,
  default shortcut, scope and enabled rule. One keyboard dispatcher runs all
  shortcuts. Menus and context menus show each command's shortcut. Help →
  Keyboard Shortcuts is generated from the registry.
- **One undo timeline per document:** Ctrl+Z/Ctrl+Y undo and redo scene
  structure *and* component/transform edits in the order they happened. Edit →
  Undo shows a label ("Undo Move Player"). The separate "Scene Undo/Redo" items
  are removed.
- **Multi-select:** Ctrl/Shift-click in the Hierarchy, Shift/Ctrl-click and
  marquee in the viewport. Delete, Duplicate, move drags and arrow nudges act on
  the whole selection as one undo step.
- **Status bar** in the main window shows the tool hint, the latest notice
  (auto-clearing info, sticky errors, optional Undo action), background progress,
  selection count and play state. Scattered red notice lines move into it.
- **Viewport tools:** Q Select, W Move, E Rotate, R Scale; F frames the
  selection. A snapping service provides move/rotate/scale steps (Configure
  Snap…). Ctrl held during a drag inverts snapping, and the grid follows the
  move step.
- No scene, prefab or project file format changes. Existing workflows and smokes
  keep working, apart from the documented shortcut/label changes below.

## Current code (evidence, 2026-11-17)

Paths are relative to `tools/editor/src/main/kotlin/sle/editor/`.

- **Menus:** `ui/EditorMenuStrip.kt` `editorApplicationMenus(...)` builds
  File/Edit/Create/View/Help from inline lambdas. `EditorMenuEntry(label,
  enabled, children, action)` in `ui/EditorContextMenu.kt` has no id, shortcut
  or separator. Help → Controls is hand-written text, which is already stale.
- **Shortcuts are in four places:**
  - `ui/SaveShortcut.kt` handles Ctrl+S through a global `KeyEventDispatcher`.
    It finds the command by matching the menu label "Save". A known deferred
    bug: releasing S outside the app leaves its repeat guard armed.
  - `ui/SceneTree.kt` handles Delete/Backspace, Ctrl+D, Up/Down and Escape.
  - `viewport/ViewportGizmos.kt` `key()` handles Escape, Ctrl+Z/Y/Shift+Z,
    Delete, Ctrl+D and arrow nudges.
  - `workspace/DockDesktopDrag.kt` handles Escape while dragging a dock.
- **Two histories:**
  - `model/EntityWorkspaceState.kt` keeps a per-entity `past/future` of
    `Snapshot` (100 entries) with `beginGesture/endGesture/cancelGesture`. The
    numeric-edit contract builds on this.
  - `model/SceneWorkspaceState.kt` keeps a separate structural `past/future` of
    `Structure`, exposed as "Scene Undo/Redo". `change { }` wraps structural
    edits.
  - So viewport Ctrl+Z undoes only the *selected entity's* component history,
    and the user must know which history holds which edit.
  - `replaceLinkedScene` clears scene history.
- **Selection:** `SceneWorkspaceState.selectedKey` holds one key, and
  `selected` derives from it. Hierarchy, `viewport/ScenePicking.kt`,
  `NativeViewportOwner` and the Inspector all use it.
- **Viewport input:** `viewport/NativeViewportOwner.kt` mouse handlers handle
  right-click menus, middle/Alt pan, pick-and-select, entity drag to Assets,
  and `ViewportGizmos.press/move/finish`. `viewport/GizmoMath.kt` handle kinds
  are `move`, `radius` and axis resize; there are no rotate or scale handles.
  Rotation is used in radians. `viewport/GizmoOverlay.kt` draws handles with GL
  after the engine frame.
- **Snapping:** `EntityWorkspaceState.snap` is per entity. The "Snap 0.1"
  checkbox is in `ui/GizmoControls.kt`, Shift snaps to 0.1, and arrows nudge
  0.1 (Shift: 1). The native grid comes from C++, with spacing fixed by the
  bridge.
- **Notices:** `state.error`/`message` strings are rendered as red `Text` in
  `app/Main.kt` (lines ~614–658), `ui/SceneWorkspace.kt`,
  `ui/EntityWorkspace.kt` and project controls. Field validation errors are
  inline in the Inspector, which is correct and stays.
- **Per-user project state:** `project/ProjectFiles.kt`
  `ProjectEditorState(uiScale, lastScene, notice)` in `.sle/`. It has optional
  fields, so old files parse.
- **Panels:** `workspace/PanelRegistry.kt` holds descriptors only. Focus per
  panel is not tracked centrally.

Constraints: AGENTS.md, [docs/CONSTRAINTS.md](../CONSTRAINTS.md),
[UI_DESIGN.md](../../tools/editor/UI_DESIGN.md) tokens/components,
the numeric edit contract, the gating of Play/modal/text fields, detached
windows, and the Game view never losing input to editor shortcuts while it is
focused and playing.

## Decisions taken in this plan (correct me if wrong)

1. **Shortcut scheme.** Unity for scene work: Q/W/E/R tools, F frame
   selected, Ctrl+D duplicate, Delete, Ctrl+A select all. The play controls use
   F5 Play/Resume, F6 Pause, Shift+F5 Stop and Ctrl+F5 Run Project, because
   Unity's Ctrl+P / Ctrl+Shift+P are reserved for Quick Open and the Command
   Palette (roadmap 1.8). Other keys:
   - Ctrl+S Save, Ctrl+Shift+S Save As
   - Ctrl+Z Undo; Ctrl+Y and Ctrl+Shift+Z Redo

   All of these can be changed later through the keymap.
2. **Modifiers.** Ctrl *inverts* snapping during a drag (Godot/UE style).
   Shift constrains a move to its dominant axis and makes scale uniform. This
   changes today's "Shift snaps 0.1" and is documented in Help.
3. **Undo and selection.** Undo restores the selection that was current when
   the step was recorded, and selects the entity it changed. Selection changes
   by themselves are not undo steps.
4. **Snap settings home (R10):** per user, per project, in the `.sle/` editor
   state. Standalone mode uses user-local prefs. Defaults are move 1 unit,
   rotate 15°, scale 0.1, and snapping is off. Roadmap item 6.0 (art style)
   will set these defaults later.
5. **Scope of the tools:** Scene mode only. The temporary Entity/prefab
   component tab keeps its current shape handles until a later "Shape" tool
   migration. Rect tool (T) is deferred, because sprite size and transform scale
   would make its meaning ambiguous.
6. **Keymap overrides:** the data model supports user overrides, but there is
   no rebinding UI or override file in L1. Both come with the command palette.

## Sessions

Each session ends with tests green, docs for that slice updated (R12), and a
handoff note in **Progress** below. Sessions are ordered by dependency; each one
is shippable alone.

### Session 1: Command registry and shortcut dispatcher

- [x] Create `command/`:
  - `CommandId`
  - `KeyChord`
  - `CommandScope` (Global, Document, Hierarchy, SceneViewport, Assets)
  - `EditorCommand(id, label, icon, defaultChord, scope, repeatable, enabled(ctx), run(ctx))`
  - `CommandContext`: active document kind, scene/prefab state, selection,
    playback, focused panel, modal open, text input focused, project busy
  - `CommandRegistry`
  - `Keymap`: defaults plus an override map; detects conflicting chords in
    overlapping scopes at registration
- [x] Track the focused panel. `DockHost` focus-within and native canvas focus
  report a `PanelId` to a small `WorkspaceFocus` state, which works in detached
  windows too.
- [x] Add one `ShortcutDispatcher` (`KeyEventDispatcher`) that replaces
  `SaveShortcut`. Rules:
  - A modal dialog blocks everything.
  - The Game panel, focused while running or paused, receives all keys except
    Play-control chords.
  - With text input focused, only Ctrl/Alt chords fire (Delete, arrows and
    letters edit text).
  - Scoped commands fire only when their panel has focus.
  - A held key repeats only for `repeatable` commands.
  - Repeat guards reset on key release, focus loss and window deactivation,
    which fixes the deferred Ctrl+S bug.
- [x] Register the existing commands with ids: Save, Save As, Undo, Redo,
  Delete, Duplicate, Edit Entity, Apply Changes to Prefab, Fit, Play/Pause/Stop,
  Run Project, New/Open entries. Move their logic out of the menu lambdas in
  `EditorMenuStrip.kt`/`Main.kt` into `command/*Commands.kt`.
- [x] Give `EditorMenuEntry` an optional `command`, plus `separator`.
  `buildEditorPopup` renders the chord as right-aligned secondary text
  (display only, so menus never trigger the same key twice). Context menus in
  `EditorContextMenu.kt` reference the same commands (R4).
- [x] Route `SceneTree` Delete/Ctrl+D and the `ViewportGizmos.key` Delete/Ctrl+D
  through the dispatcher. Hierarchy Up/Down navigation and gesture Escape stay
  local, because they are widget behaviour rather than commands.
- [x] Replace Help → Controls with Help → Keyboard Shortcuts, generated from
  the registry and grouped by scope, plus a short mouse-gesture section.
- [x] Tests:
  - `KeymapTest`: conflicts and overrides.
  - `ShortcutDispatcherTest`: modal, text focus, Game passthrough, scope,
    repeat, release outside the app.
  - Migrate `SaveShortcutTest`.
  - Menu entries take their enabled state from commands.
- [x] Smokes: `--context-menu-smoke` (Robot Ctrl+S/Ctrl+D/Delete with native
  canvas and hierarchy focus, Delete in an Inspector text field, modal) and
  `--project-documents-smoke` (Ctrl+S).
- [x] Docs: HOW_TO_ADD_UI_FEATURES "Example 2" becomes "register a command";
  update the README shortcut text.

### Session 2: One undo timeline per document (R3)

- [ ] Add `model/History`. Each entry holds a label, an optional merge key,
  `undo()`/`redo()` and the selection to restore. Keep the 100-entry cap.
  Provide `group(label) { }` for compound edits; a nested group joins its
  parent.
- [ ] Give `EntityWorkspaceState` a history *sink*.
  - **Scene-owned entities:** `endGesture` and instant edits post an
    `EntityEdit(key, before, after, label)` to the scene's `History` instead of
    the local `past`.
  - **Standalone prefab workspace:** keeps its own `History` through the same
    API.
  - `beginGesture/cancelGesture` and the numeric-edit contract keep their
    behaviour: undo during an active edit cancels that edit first.
- [ ] Rebuild `SceneWorkspaceState.change { }` on `History`:
  - Structural entries keep today's `Structure` snapshots.
  - An `EntityEdit` resolves its entity by key at undo time, so it still works
    after the entity was deleted and restored. Undoing selects that entity.
- [ ] Labels: Move/Rotate/Scale `<name>`, `Edit <Component> <field>`,
  Add/Remove `<Component>`, Create/Delete/Duplicate `<name | N entities>`,
  Reparent, Rename, Apply to Prefab.
- [ ] Merging: arrow nudges with the same merge key within ~750 ms merge into
  one step.
- [ ] Edit menu: a single Undo/Redo with labels; remove Scene Undo/Redo. The
  `GizmoControls` Undo/Redo buttons, viewport keys and commands all use the
  active document's timeline.
- [ ] `replaceLinkedScene` still clears history. Record a notice for S4
  ("Undo history cleared after prefab update").
- [ ] Tests (extend `SceneAuthoringUxTest`, `SceneHierarchyRegressionTest`,
  `EntityDocumentTest`):
  - interleaved structure and component edits undo in reverse order
  - undo of a component edit on an entity that was deleted and then restored
  - labels
  - merge window
  - cap
  - redo invalidation
  - group atomicity on failure
  - numeric edit contract unchanged
  - prefab workspace unaffected
- [ ] Smokes: `--scene-smoke`, `--context-menu-smoke`, `--gizmo-smoke`. Update
  fixtures that click "Scene Undo".

### Session 3: Selection service and multi-select

- [ ] Add `model/SceneSelection`: an ordered key set plus an active key.
  Operations: `select`, `toggle`, `extendTo` (visible-order range), `set`,
  `clear`, and prune on structural change. Keep `selectedKey`/`selected` as the
  *active* aliases to limit churn, and migrate readers gradually. History
  entries store the full selection.
- [ ] Hierarchy: Ctrl-click toggles, Shift-click selects a range, Ctrl+A is a
  registered command, and Escape clears. All selected rows use
  `selectedBackground`, and the active row adds the accent marker.
- [ ] Viewport: Shift/Ctrl-click toggles, every selected entity gets an outline,
  and handles appear on the active entity. Moving any selected entity moves the
  whole set as one `History.group`. Arrow nudges also act on the set.
- [ ] Set-aware commands:
  - Delete removes the union of subtrees as one step.
  - Duplicate copies only top-most roots, skipping entities whose ancestor is
    also selected, and selects the copies.
  - Commands that need one entity (Edit Entity, Apply to Prefab, entity drag to
    Assets, Inspector Parent) are disabled with a tooltip reason when N≠1.
- [ ] Inspector with N>1: a header "N entities selected" and the common actions
  only. Multi-object editing is roadmap item 1.6.
- [ ] Tests:
  - selection model: toggle, range, prune, undo restore
  - delete/duplicate with nested selections
  - multi-move is one undo step
  - linked prefab instances in a multi-selection
- [ ] Smokes: extend `--scene-smoke` with Ctrl-click, multi-move and multi-delete
  with undo.

### Session 4: Status bar and notices (R8)

- [ ] Add a `ui/EditorNotices` service: `post(level, text, ttl?, action?)`.
  - Info and success clear after ~6 s.
  - Warnings and errors stay until dismissed or replaced.
  - Keep a short recent list, until the Console arrives with layer L4.
- [ ] Add an `EditorStatusBar` control: new `statusBar` metric (Compact 24 /
  Comfortable 28 dp), tokens only, added to the theme gallery. It shows:
  - left: tool or context hint
  - centre: the latest notice, with a level icon and colour, an optional action
    button and a recent-messages popup
  - right: background task progress, selection count, play state and a dirty
    marker
  - Main window only.
- [ ] Migrate document, project, distribution, appearance and layout notices to
  `EditorNotices`. Field validation stays inline in the Inspector.
  `viewportPanel` failure text stays in the viewport, since it explains an empty
  panel.
- [ ] Delete and other destructive-but-undoable actions post "Deleted N
  entities · Undo" (R9).
- [ ] Tests:
  - service: ttl, sticky errors, replacement, action invocation
  - contrast tests cover the new pairs
  - `--editor-appearance-smoke` captures the status bar in both themes
- [ ] Docs: UI_DESIGN.md (status bar, notices rule) and HOW_TO_ADD_UI_FEATURES
  ("report a result").

### Session 5: Viewport tool framework, Select/Move tools, snapping, Frame Selected

- [ ] Add a `viewport/tools/ViewportTool` interface: id, label, icon, chord,
  cursor, `hint(ctx)`, `press/drag/release/key/cancel`, `handles()`,
  `draw(overlay)`.
  - `ToolHost` in `NativeViewportOwner` keeps right-click, pan, zoom and
    entity-drag-to-Assets ahead of the tool.
  - Switching tools finishes the gesture; Escape cancels it.
  - The active tool is per-user editor state, not undoable.
- [ ] **Select (Q):**
  - click picks; Shift/Ctrl toggles
  - dragging on empty space draws a marquee; entities whose selection bounds
    intersect it are selected (Shift adds)
  - dragging a selected entity still moves it, as in today's behaviour
- [ ] **Move (W):** X/Y axis arrows and a centre square for free movement. Body
  drag remains. Shift constrains to the dominant axis. Multi-selection moves as
  one group.
- [ ] **Snapping:** add `viewport/SnapService` with enabled, move/rotate/scale
  steps and Ctrl inversion. Snap world positions, then convert to local through
  the parent pose. It applies to move drags, arrow nudges (one step when
  snapping, else 0.1 or Shift 1), click-to-create positions and asset drops.
  - Remove `EntityWorkspaceState.snap` and the "Snap 0.1" checkbox.
  - Toolbar: a snap toggle plus Configure Snap… (a small form dialog).
  - Persist through `ProjectEditorState`, which gains optional fields so old
    files still parse.
- [ ] **Grid follows snap** (Engine, small): a bridge call sets the editor grid
  spacing and major-line interval in the native renderer. Add a native
  integration check that the setting only affects editor drawing. Bump the
  engine version per CONTRIBUTING.
- [ ] **Frame Selected (F):** fit the camera to the selection bounds. Fit
  (frame all) stays available.
- [ ] Viewport toolbar: a tool group (Q/W/E/R as `EditorToggle` icons with chord
  tooltips), snap toggle, Configure Snap. Add a Tools menu with the same
  commands. Tool hints go to the status bar.
- [ ] Tests:
  - snapping math: negative values, rotated or mirrored parents, world→local
  - marquee bounds with rotated sprites
  - switching tools finishes the gesture
  - one undo step per drag and per nudge burst
  - persistence of snap settings, including old state files
- [ ] Smokes: update `--gizmo-smoke` and `--scene-smoke` (Shift semantics);
  add tool switching via keys with native focus and a marquee drag.

### Session 6: Rotate and Scale tools

- [ ] Add a Pivot/Center toggle: rotate and scale around the active entity's
  pivot or the selection bounds centre.
- [ ] **Rotate (E):** a ring handle. Dragging changes the angle, which is
  stored in radians as today; the hint and status bar show degrees. Snapping
  uses the rotate step. With multiple entities selected, their positions orbit
  the pivot.
- [ ] **Scale (R):** X/Y axis handles and a uniform centre handle; Shift forces
  uniform. Snapping uses the scale step. Never cross zero (clamp to a minimum
  magnitude). Mirrored scale is preserved. Scales Transform only; sprite size
  is unchanged.
- [ ] Tests:
  - rotate and scale math for children of rotated or scaled parents
  - mirrored and zero-scale guards
  - multi-entity orbit
  - one undo step per drag
- [ ] Smokes: `--gizmo-smoke` gains rotate/scale drags and undo.
- [ ] Docs: SceneEditor topic (tools, snapping, shortcuts) and README Viewport
  controls.

### Session 7: Verification and handoff

- [ ] Run the full editor build and tests, plus all listed smokes. Build and run
  native CTest for the grid bridge change.
- [ ] Manual walk, with `-Dsle.workspaceData=<temp dir>` so the real layout is
  untouched:
  1. Create entities; Ctrl/Shift select; marquee.
  2. W/E/R drags with snapping and Ctrl inversion.
  3. Undo/redo across structural and transform edits.
  4. Delete with the status-bar Undo.
  5. Shortcuts in a detached Hierarchy/Scene window.
  6. Game focused while playing: W/Delete reach the game; F5/Shift+F5 still
     work.
  7. Dark and Light, Compact and Comfortable, 150% scale.
- [ ] Bump the editor version, and the engine version if not already done in S5.
  Record untested platforms (Linux/WSL, mixed DPI) honestly.

## Not in L1 (later layers or roadmap items)

Command palette, Quick Open and the shortcut rebinding UI (1.8), Alt-drag
duplicate (1.3), Hierarchy search/rename/drag-reparent/hide/lock (1.4),
multi-object Inspector editing (1.6), the Console (L4), the Rect tool, migrating
the Entity tab's shape handles to tools, smart snapping and guides, and the
History panel (6.5, which becomes trivial after S2).

## Risks

- **Unified history (S2)** is the biggest behavioural change. It touches the
  numeric-edit contract, linked-prefab reconciliation and many smokes. Keep
  `Structure` snapshots and entity `Snapshot` types to reduce risk, and add
  characterization tests before refactoring.
- **Keyboard routing across Compose, AWT and the native canvas** has caused
  regressions before (see editor-polish session 2). Keep the dispatcher rules
  table-driven, unit-test them, and verify with Robot smokes.
- **Overlap with editor-polish session 6** (entity drag, prefab tab, Apply to
  Prefab) in `Main.kt`, the menus and `EditorContextMenu.kt`. Finish or pause
  that session before L1 S1, and check `git status` at each session start.
- **The Shift semantics change** may surprise current users. Help, the README
  and tool hints must state it.

## Validation commands

From `tools/editor` (Windows PowerShell; JDK 21 via `find-jdk.ps1`/`gradle.ps1`
as in CONTRIBUTING). Confirm smoke flag names in `app/Main.kt` before running.

```powershell
.\gradlew.bat test --tests "sle.editor.command.*" --no-daemon --console=plain
.\gradlew.bat test --tests sle.editor.model.SceneAuthoringUxTest --tests sle.editor.model.SceneHierarchyRegressionTest --no-daemon --console=plain
.\gradlew.bat test --tests sle.editor.viewport.GizmoTest --no-daemon --console=plain
.\gradlew.bat build --no-daemon --console=plain
.\gradlew.bat run --args=--context-menu-smoke
.\gradlew.bat run --args=--scene-smoke
.\gradlew.bat run --args=--gizmo-smoke
.\gradlew.bat run --args=--editor-appearance-smoke
.\gradlew.bat run --args=--docking-workspace-smoke
.\gradlew.bat run --args=--project-documents-smoke
```

Native (session 5 grid bridge), from the repository root:

```powershell
cmake --build build/debug --config Debug --target sle_editor_native
ctest --test-dir build/debug -C Debug --output-on-failure
```

Desktop smokes need an unlocked interactive Windows session.

## Progress

- 2026-11-17: plan written from a code survey of menus, shortcuts, histories,
  selection, viewport input, notices and project state. No code changed; no
  build or tests run.

### Session 1 handoff — 2026-11-17

- Start state: editor-polish session 6 had already landed in commit 80ad176, so
  nothing overlapped. The "Current code" evidence matched, except that `SceneTree` also
  handled Backspace and Enter. The untracked plan files and the starter-assets zip were
  kept. Nothing was committed.
- Implemented:
  - The `command/` package:
    - `CommandId`, `KeyChord` (locale-independent display and parse), `CommandScope`
    - `EditorCommand`, `CommandContext`, `EditorActions`
    - `CommandRegistry` and `Keymap`, which reject conflicts in overlapping scopes at
      registration and with overrides
    - `ShortcutDispatcher` and its `shortcutRules` table
    - `DocumentCommands`, `EditCommands`, `PlayCommands` and `ViewCommands`
    - `menuEntry`, plus generated Help content
  - Focus tracking:
    - `workspace/WorkspaceFocus` stores Compose focus-within per host. `DockHost`
      reports it through `onFocusChanged` on the tab content.
    - Registered heavyweight components (the native canvas) take precedence over
      Compose focus.
    - `TextInputFocus` is reported by `EditorTextField`.
  - `ui/EditorShortcuts` installs the dispatcher for all editor windows. It resets
    held keys whenever the active window or focus owner changes, which fixes the
    deferred Ctrl+S repeat-guard bug. `SaveShortcut` and its test were removed.
  - Menus:
    - `EditorMenuEntry` gained `command`, `shortcut` and `separator`.
    - `EditorMenuItemUI` paints the chord right-aligned (display only, not a Swing
      accelerator).
    - `EditorMenuSeparator` is new.
    - `editorApplicationMenus` now takes a `CommandContext`.
  - Context menus reuse the Duplicate/Delete/Apply commands.
  - `SceneTree` and `ViewportGizmos.key` no longer handle Delete or Ctrl+D.
  - Help → Controls became **Help → Keyboard Shortcuts**, generated from the registry
    and grouped by scope, plus a mouse section.
  - Play/Pause/Stop tooltips show their chords.
- Deviations from the plan text. All are deliberate; correct them if wrong:
  - `EditorCommand` has `defaultChords` (a list) and `scopes` (a set): Redo needs
    Ctrl+Y and Ctrl+Shift+Z, and Delete/Duplicate need both Hierarchy and Scene view.
    It also has `menuLabel` and `playControl`.
  - Text rule:
    - With a text field focused, function-key chords fire as well as Ctrl/Alt
      chords.
    - The field's own editing chords stay with the field:
      - Ctrl(+Shift) with Z, Y, A, C, X, V, Insert, Delete, Backspace, arrows,
        Home or End
      - Shift+Insert and Shift+Delete
  - An open Swing menu blocks shortcuts, like a modal dialog.
  - Backspace also deletes in the Scene view; before, it worked only in the
    Hierarchy.
  - Undo/Redo are Document-scoped, so Ctrl+Z works from any panel. It still uses the
    active entity's history until S2. Redo during a drag cancels the drag, matching
    the old viewport key. The `ViewportGizmos.key` Ctrl+Z/Y handling remains as a
    fallback when the command is disabled.
  - Edit menu:
    - Labels are now **Duplicate** and **Delete** (were "Duplicate Subtree" and
      "Delete Entity").
    - It gained Play, Pause, Stop and Run Project.
    - The context menu's redundant Entity → Duplicate Subtree submenu was removed.
  - New/Open commands have ids but no chords. Scene Undo/Redo are still separate
    commands until S2.
- Smoke changes:
  - `--context-menu-smoke` gained:
    - Robot Ctrl+D (with held-key repeat) from the native canvas and from the Hierarchy
    - Ctrl+S from the Hierarchy and from the canvas
    - a check that File → Save shows "Ctrl+S"
    - "Duplicate" in place of the removed submenu
  - `--project-documents-smoke` now clicks the canvas before Ctrl+S. Before, it only
    called `requestFocusInWindow`, which does not activate the window. On the first
    run, the Ctrl+S went to another app that owned the keyboard (the failure
    screenshot shows the editor inactive).
- Commands run, all from the repository root in Windows PowerShell through
  `tools/editor/gradle.ps1` (JDK 21):
  - `compileTestKotlin`: passed.
  - `test --tests "sle.editor.command.*" --tests sle.editor.workspace.WorkspaceLifecycleTest --tests sle.editor.model.SceneAuthoringUxTest --tests sle.editor.viewport.GizmoTest`:
    passed. `KeymapTest` ran 6 tests and `ShortcutDispatcherTest` ran 10.
  - `build`: 261 tests, 0 failures, 2 existing skips.
  - `run --args=--context-menu-smoke`:
    - Run 1 failed at the existing tree stage with "Submenu did not open: Create".
      This was after the new canvas Delete/Ctrl+D stage had passed. The screenshot
      showed the popup away from the clicked row, with New hovered. The cause is
      unexplained.
    - Run 2 passed with `CONTEXT_MENU_SMOKE_PASS` and every new `SHORTCUT_SMOKE_STAGE`.
      Treat the tree stage as possibly flaky.
  - `run --args=--project-documents-smoke`:
    - Run 1 failed: the editor window was inactive (see above).
    - After the smoke fix, it passed with `PROJECT_DOCUMENTS_SMOKE_PASS` and
      `PREFAB_WORKFLOW_SMOKE_PASS`.
  - `run --args=--scene-smoke`: passed.
  - `run --args=--docking-workspace-smoke`: passed.
  - `run --args=--editor-appearance-smoke`: passed (default dark theme only; its
    captures do not open menus).
- Not done:
  - No manual walk and no Linux/WSL run.
  - The Light-theme look of menu chords and separators was not checked visually. The
    context-menu smoke screenshot shows the Dark theme.
  - No editor version bump (planned for S7).
- Next: Session 2 (one undo timeline), only when requested.
