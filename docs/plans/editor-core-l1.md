# L1 — Editor core: commands, history, selection, status bar, viewport tools

**Status:** Planned 2026-11-17. Sessions 1–4 implemented and validated (see Progress); sessions 5–7 not started.
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

- [x] Add `model/History`. Each entry holds a label, an optional merge key,
  `undo()`/`redo()` and the selection to restore. Keep the 100-entry cap.
  Provide `group(label) { }` for compound edits; a nested group joins its
  parent.
- [x] Give `EntityWorkspaceState` a history *sink*.
  - **Scene-owned entities:** `endGesture` and instant edits post an
    `EntityEdit(key, before, after, label)` to the scene's `History` instead of
    the local `past`.
  - **Standalone prefab workspace:** keeps its own `History` through the same
    API.
  - `beginGesture/cancelGesture` and the numeric-edit contract keep their
    behaviour: undo during an active edit cancels that edit first.
- [x] Rebuild `SceneWorkspaceState.change { }` on `History`:
  - Structural entries keep today's `Structure` snapshots.
  - An `EntityEdit` resolves its entity by key at undo time, so it still works
    after the entity was deleted and restored. Undoing selects that entity.
- [x] Labels: Move/Rotate/Scale `<name>`, `Edit <Component> <field>`,
  Add/Remove `<Component>`, Create/Delete/Duplicate `<name | N entities>`,
  Reparent, Rename, Apply to Prefab.
- [x] Merging: arrow nudges with the same merge key within ~750 ms merge into
  one step.
- [x] Edit menu: a single Undo/Redo with labels; remove Scene Undo/Redo. The
  `GizmoControls` Undo/Redo buttons, viewport keys and commands all use the
  active document's timeline.
- [x] `replaceLinkedScene` still clears history. Record a notice for S4
  ("Undo history cleared after prefab update").
- [x] Tests (extend `SceneAuthoringUxTest`, `SceneHierarchyRegressionTest`,
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
- [x] Smokes: `--scene-smoke`, `--context-menu-smoke`, `--gizmo-smoke`. Update
  fixtures that click "Scene Undo".

### Session 3: Selection service and multi-select

- [x] Add `model/SceneSelection`: an ordered key set plus an active key.
  Operations: `select`, `toggle`, `extendTo` (visible-order range), `set`,
  `clear`, and prune on structural change. Keep `selectedKey`/`selected` as the
  *active* aliases to limit churn, and migrate readers gradually. History
  entries store the full selection.
- [x] Hierarchy: Ctrl-click toggles, Shift-click selects a range, Ctrl+A is a
  registered command, and Escape clears. All selected rows use
  `selectedBackground`, and the active row adds the accent marker.
- [x] Viewport: Shift/Ctrl-click toggles, every selected entity gets an outline,
  and handles appear on the active entity. Moving any selected entity moves the
  whole set as one `History.group`. Arrow nudges also act on the set.
- [x] Set-aware commands:
  - Delete removes the union of subtrees as one step.
  - Duplicate copies only top-most roots, skipping entities whose ancestor is
    also selected, and selects the copies.
  - Commands that need one entity (Edit Entity, Apply to Prefab, entity drag to
    Assets, Inspector Parent) are disabled with a tooltip reason when N≠1.
- [x] Inspector with N>1: a header "N entities selected" and the common actions
  only. Multi-object editing is roadmap item 1.6.
- [x] Tests:
  - selection model: toggle, range, prune, undo restore
  - delete/duplicate with nested selections
  - multi-move is one undo step
  - linked prefab instances in a multi-selection
- [x] Smokes: extend `--scene-smoke` with Ctrl-click, multi-move and multi-delete
  with undo.

### Session 4: Status bar and notices (R8)

- [x] Add a `ui/EditorNotices` service: `post(level, text, ttl?, action?)`.
  - Info and success clear after ~6 s.
  - Warnings and errors stay until dismissed or replaced.
  - Keep a short recent list, until the Console arrives with layer L4.
- [x] Add an `EditorStatusBar` control: new `statusBar` metric (Compact 24 /
  Comfortable 28 dp), tokens only, added to the theme gallery. It shows:
  - left: tool or context hint
  - centre: the latest notice, with a level icon and colour, an optional action
    button and a recent-messages popup
  - right: background task progress, selection count, play state and a dirty
    marker
  - Main window only.
- [x] Migrate document, project, distribution, appearance and layout notices to
  `EditorNotices`. Field validation stays inline in the Inspector.
  `viewportPanel` failure text stays in the viewport, since it explains an empty
  panel.
- [x] Delete and other destructive-but-undoable actions post "Deleted N
  entities · Undo" (R9).
- [x] Tests:
  - service: ttl, sticky errors, replacement, action invocation
  - contrast tests cover the new pairs
  - `--editor-appearance-smoke` captures the status bar in both themes
- [x] Docs: UI_DESIGN.md (status bar, notices rule) and HOW_TO_ADD_UI_FEATURES
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

### Session 2 handoff — 2026-11-17

- Start state: `feat/project-foundation` at 68c6960 (Session 1). Only the untracked
  starter-assets zip and `docs/plans/editor-game-workflows.md` were present; both were
  left alone. The "Current code" evidence matched. Nothing was committed.
- Characterization first: six `undoCharacterization*` tests (five in `SceneAuthoringUxTest`,
  one in `EntityDocumentTest`) were written against the old two-history API and passed on
  the unchanged code. They cover an edit surviving delete/restore, gesture cancel before a
  recorded step, a drag as one step, redo discarding, the 100-entry caps, and linked-scene
  replacement clearing history. Each orders its steps so "undo the latest step" gives the same
  answer in both models; after the refactor they only switched to `undo()`/`redo()`.
- Implemented:
  - `model/History.kt`:
    - `History` keeps entries with a label, an optional merge key, `undo`/`redo` closures
      and the selection to restore before and after. It has the 100-entry cap and
      `group(label) { }` (a nested group joins its parent; a throwing block undoes what it
      recorded and keeps nothing).
    - Equal merge keys within 750 ms merge; the window rolls from the latest step, and
      undo/redo ends a burst. Steps recorded while an entry is applied are ignored. A step
      whose undo throws stays on the timeline, and a compound step is put back if one part
      fails.
    - It also holds `HistorySelection` (one key; S3 extends it), `UndoableDocument`,
      `EntityHistoryOwner` and `describeEntityEdit` (Move/Rotate/Scale `<name>`,
      Add/Remove `<Component>`, `Edit <Component> <field>`, Rename `<name>`).
  - `EntityWorkspaceState` is an `UndoableDocument`.
    - Its `history` is the owning scene's timeline once the scene attaches it, and
      otherwise its own (the standalone prefab workspace).
    - `endGesture` and instant edits post one step. The step's closures restore
      the before/after `Snapshot` and resolve the entity by key when applied.
    - Explicit labels: Rename, Add/Remove `<Component>`, `Edit <Component> <field>` (also for
      numeric edits), Import/Remove `<file>`. Viewport gestures derive the label from the diff.
    - `undo()`/`redo()` on a scene entity delegate to the scene. During an open gesture both
      cancel it first. The second one is a change: standalone Redo during a gesture used to
      do nothing.
    - Edits made through a stale reference to a deleted entity are not recorded.
  - `SceneWorkspaceState` implements `EntityHistoryOwner`.
    - The `entities` setter attaches every held entity to the scene's timeline.
    - `change(label) { }` records `Structure` snapshots as before. Selection moved out of
      `Structure` into the entry, so a selection-only change is never a step.
    - `undo()`/`redo()` replace `undoStructure`/`redoStructure`; `canUndo`/`canRedo`
      replace `canUndoStructure`/`canRedoStructure`.
    - Labels: Create/Delete/Duplicate `<name | N entities>`, Reparent `<name>`, Rename Scene,
      Set Active Camera `<name>`, and Create Prefab `<name>` for drag-to-Assets.
  - `replaceLinkedScene` still clears history. It now returns whether steps were
    cleared and sets `message` to `HISTORY_CLEARED_NOTICE` ("Undo history cleared after
    prefab update"). Apply Changes to Prefab appends the notice to its own message. S4 should
    move it into `EditorNotices`. Because Apply clears history, "Apply to Prefab" is never an
    undo label.
  - Commands and UI:
    - `CommandContext.document` picks the scene (also for a scene entity open in the Entity
      tab) or the prefab workspace. Undo/Redo run on it and report a refused step as that
      document's error.
    - `EditCommands.undoLabel/redoLabel` give the Edit menu "Undo Move Player". Scene
      Undo/Redo commands and menu items are removed.
    - The `GizmoControls` buttons use `state.timeline` and show the label as a tooltip.
    - The `ViewportGizmos.key` Ctrl+Z/Y fallback uses `state.timeline`. Arrow nudges pass a
      `NudgeMergeKey(entity, shape)`, and `applyWorldDocument`/`applyGizmo` take a merge key.
- Deliberate behaviour changes (correct them if wrong):
  - Ctrl+Z from a scene entity now undoes the latest scene step, which may be structural
    or another entity's edit. `NumberEditTest.sceneSelectionFinishesOnlyTheOriginalEntityTransaction`
    now undoes the later Create first.
  - `SceneHierarchyRegressionTest.transparentReparent…` now does its rename before the
    reparent, so the test still shows that undoing a reparent removes only the inserted
    Transform.
  - An entity that survives `replaceLinkedScene` unchanged no longer keeps a private
    component history, because the scene's whole history is cleared.
- Tests: `HistoryTest` adds 10 tests (order/labels, redo discard, cap, merge window, burst
  end, groups, failed group, failing/partial undo, re-entrancy and selection, label
  derivation). `SceneAuthoringUxTest` adds 7 `timeline*` tests:
  - interleaved structure and component edits undo in reverse and redo to the same document
  - an edit on a deleted and restored entity, with its selection
  - labels, including the Edit-menu label and "Undo" during a drag
  - nudge merging through `ViewportGizmos.key` with an injected clock
  - the numeric-edit contract on the scene timeline
  - the prefab workspace keeping its own history, through `CommandContext.document`
  - a refused Animator-breaking undo that stays and becomes the error
- Smoke changes:
  - No fixture clicked the "Scene Undo" menu item. The scene, context-menu, prefab-workflow
    and project-documents smokes called `undoStructure`/`redoStructure` directly; those
    calls became `undo`/`redo`, and every call site was checked against the new ordering.
  - `--context-menu-smoke` gained the `edit-menu-labelled-undo` stage: it renames the
    scene, opens Edit, checks "Undo Rename Scene" with Ctrl+Z and no "Scene …" items, then
    clicks it.
- Commands run, from `tools/editor` through `gradle.ps1` (JDK 21) on an interactive Windows
  desktop. Smokes were started with `powershell -File gradle.ps1 run --args=--<smoke>` from
  Git Bash, because `*>` redirection inside PowerShell turns Gradle's stderr into a
  terminating error under the script's `ErrorActionPreference=Stop`. The first scene-smoke
  attempt aborted that way before the editor ran.
  - `test --tests sle.editor.model.SceneAuthoringUxTest --tests sle.editor.model.EntityDocumentTest`
    on the unchanged code, with the characterization tests: passed.
  - `test` after the refactor: two expected failures (see deliberate changes); both tests
    were updated. Then two failures in new tests: a delta-based merge fixture (merging
    needs state-restoring steps, now documented on `History`) and a label diff that treated
    `0` and `0.0` as different (fixed). Then 284 tests, 0 failures.
  - `build`: passed, 284 tests, 0 failures, 2 existing skips.
  - `run --args=--scene-smoke`: `SCENE_EDITOR_SMOKE_PASS`, `SCENE_TREE_COMPONENT_HANDOFF_PASS`,
    `SCENE_ENTITY_SANDBOX_SWITCH_PASS`.
  - `run --args=--context-menu-smoke`: passed before and after the new stage
    (`CONTEXT_MENU_SMOKE_PASS`, `DETACHED_DELETE_PLAYBACK_SMOKE_PASS`).
  - `run --args=--gizmo-smoke`: `VIEWPORT_GIZMO_SMOKE_PASS` (handles=176).
  - `run --args=--project-documents-smoke`: `PROJECT_DOCUMENTS_SMOKE_PASS`,
    `PREFAB_WORKFLOW_SMOKE_PASS`.
  - `build` again after the last edits: passed.
- Docs: SceneEditor (Undo/Redo bullet, Delete wording, the "unified history later" note),
  Prefabs (replacement clears history, with the notice), the editor README (shortcut table,
  menu summary, Delete wording, nudge merging) and UI_DESIGN (Scene creation and deletion).
- Not done:
  - No manual walk.
  - The Edit-menu label is checked by the smoke and unit tests, not by a screenshot review.
  - Typing in an Inspector text field still records one step per change, as before; only
    arrow nudges merge.
  - `History.group` is unused in production until S3 multi-move.
  - No editor version bump (planned for S7).
- Next: Session 3 (selection service and multi-select), only when requested.
  `HistorySelection` is the place to store the full selection.

### Session 3 handoff — 2026-11-17

- Start state: `feat/project-foundation` at 45c8c79 (Session 2). Only the untracked
  starter-assets zip and `docs/plans/editor-game-workflows.md` were present; both were left
  alone. The "Current code" evidence matched. Nothing was committed.
- Implemented:
  - `model/SceneSelection` holds ordered keys, the active key and a Shift anchor. It is Compose
    state. Operations: `select`, `toggle`, `activate`, `extendTo` (range in visible order,
    optionally additive), `set`, `clear`, `prune`, `snapshot` and `restore`.
  - `SceneWorkspaceState`:
    - `selection` replaces the stored `selectedKey`. `selectedKey`/`selected` remain as aliases
      of the active entity, so most readers did not change.
    - The `entities` setter prunes the selection after every structural change.
    - New members: `toggleSelection`, `extendSelection`, `activate`, `selectAll`,
      `selectedEntities`, `selectionRoots()` (top-most selected keys in scene order), and
      `subjects()`, now public.
    - `removeSelected` deletes the union of the selected subtrees as one step.
    - `duplicateSelected` copies the top-most roots as one step and refuses a linked non-root.
      It selects the copies, and the active copy is the one whose source held the active entity.
  - History:
    - `HistorySelection(keys, active)` stores the full selection; the old one-key constructor
      remains. Structural steps record the full selection.
    - Entity edits ask the scene through `EntityHistoryOwner.historySelection(key)`. It returns
      the whole selection when that contains the edited entity, otherwise the entity alone, so
      undo still selects the entity it changed.
    - `History.group(label, mergeKey)` gained an optional merge key, so grouped nudge bursts
      merge.
  - Viewport:
    - `ViewportGizmos`: a move drag of the active entity also moves the other selected roots,
      inside one `History.group` ("Move N entities"). Cancel cancels them all. A selected entity
      whose ancestor is selected follows that ancestor and is not moved twice.
    - Arrow nudges on a multi-selection run in a group keyed by
      `NudgeMergeKey(entities: Set, target)`. A failure rolls the group back.
    - `NativeViewportOwner`:
      - Shift/Ctrl-click toggles and starts no drag.
      - A plain press on a member of a multi-selection keeps the set so it can be dragged;
        releasing without a drag selects that entity alone.
      - Right-clicking a selected entity keeps the set.
    - `GizmoOverlay.outline` draws every other selected entity's bounds in `selectionOutline`.
      Only the active entity gets handles.
  - Hierarchy (`SceneTree`):
    - Ctrl-click toggles, Shift-click selects a range, and Ctrl+Shift-click adds a range.
      Press modifiers are read with `onPointerEvent(Initial)`. `treeClick` and `treeSelect`
      are testable.
    - Esc clears the selection, after first cancelling an entity drag. Enter edits only a
      single selection. The ancestors of every selected entity expand.
    - `EditorTreeRow(active=…)` paints a leading `accent` marker, `tabUnderline` wide.
  - Commands:
    - `edit.selectAll` is Ctrl+A in the Hierarchy and Scene view, and is in the Edit menu.
    - `EditorCommand.disabledReason` becomes `EditorMenuEntry.help`, the menu item tooltip.
    - Edit Entity, Apply Changes to Prefab and `canApplyToPrefab` need exactly one entity, and
      give `SINGLE_ENTITY_REASON` otherwise.
    - Delete and Duplicate act on the set.
  - Inspector, when N>1:
    - "N entities selected" with Duplicate and Delete.
    - A disabled Parent row with the reason. It shows the common parent, "Mixed" or "Scene
      root".
    - The selected names.
- Deliberate choices (correct them if wrong):
  - Shift-click in the Scene view toggles like Ctrl-click, as the plan's "Shift/Ctrl-click"
    says. Shift snapping now applies only when Shift is pressed after a drag starts. S5
    changes Shift semantics anyway.
  - A Ctrl-click that removes a row still becomes the range anchor, as in desktop file lists.
  - The active marker also shows for a single selection, so the rule is simply "the active row
    has the marker".
  - With N>1, Add Component in the context menu is disabled with the reason. **+ Child** and
    **As Child** still create under the active entity.
  - Dragging a Hierarchy row of a multi-selection to Assets reports "Select a single entity to
    save it as a prefab" in the scene notice line, because a tooltip cannot show during a drag.
    In the viewport, dragging a multi-selection moves it, and drag-out to Assets is off.
  - Snapping during a multi-move snaps the active entity; the others move by the same delta.
  - Esc in the Scene view still only cancels a drag. Clearing the selection is a Hierarchy key.
  - The selection tests are in a new `model/SceneSelectionTest`, not spread over
    `SceneAuthoringUxTest`.
- Tests: `SceneSelectionTest` adds 12 tests:
  - selection model: toggle and order; ranges and the anchor; prune and restore
  - selection alone is not a step, and undo/redo restore the recorded set
  - Hierarchy click rules
  - delete with a nested selection; duplicate with a nested selection, which selects the copies
  - a multi-move drag is one step and restores the selection
  - a child whose selected parent moves
  - a nudge burst on a set merges
  - linked prefab instances in a multi-selection: a partial delete or duplicate is refused,
    whole instances are duplicated and deleted with their links, and Apply is disabled for N>1
  - single-entity commands are disabled with the reason

  `HistoryTest` was updated for `HistorySelection.active`.
- Smoke changes:
  - `--scene-smoke` gained the `SCENE_MULTI_SELECT_SMOKE_PASS` stage, using Robot:
    1. Ctrl-click Crate B in the viewport, saving `scene-multi-select.png`.
    2. Drag the pair and check it is one step, "Move 2 entities"; then Ctrl+Z.
    3. Delete the pair, then Ctrl+Z.
    4. Ctrl-click Ground in the Hierarchy, then Ctrl+A, then Esc.
  - The existing plain click on Crate B now also checks that the selection drops to one entity.
- Commands run from `tools/editor` through `gradle.ps1` (JDK 21) on an interactive Windows
  desktop. Smokes were started from Git Bash with
  `powershell -File gradle.ps1 run --args=--<smoke>`.
  - `compileKotlin`: failed once on a missing `isPrimaryPressed` import, then passed.
  - `test --tests sle.editor.model.SceneSelectionTest`:
    - First, `HistoryTest` did not compile (it used the old `.key`); fixed.
    - Then one failure, the Ctrl+Shift range expectation; toggle now sets the anchor.
    - Then `SceneSelectionTest` 12/12 and `HistoryTest` 10/10.
  - `build`: passed, 296 tests, 0 failures, 2 existing skips.
  - `run --args=--scene-smoke`:
    - Run 1 failed at the existing first stage ("Tree node did not select its entity"),
      before any multi-select step. The cause is unexplained.
    - Runs 2 and 3 passed: `SCENE_MULTI_SELECT_SMOKE_PASS`, `SCENE_EDITOR_SMOKE_PASS`,
      `EDITOR_UI_SCALE_PASS`, `SCENE_TREE_COMPONENT_HANDOFF_PASS`, `SANDBOX_DESKTOP_PASS`,
      `SCENE_ENTITY_SANDBOX_SWITCH_PASS`.
    - The screenshot shows both crates outlined in the same `selectionOutline` colour, and
      handles only on the active one.
    - Treat the first Hierarchy click as possibly flaky.
  - `run --args=--context-menu-smoke`:
    - Run 1 passed the tree, Hierarchy-shortcut, duplicate and inspector stages. It then
      failed: "View → Prefab Workspace" was not in the open popup. That menu is unchanged.
    - Run 2 passed: `CONTEXT_MENU_SMOKE_PASS`, `DETACHED_DELETE_PLAYBACK_SMOKE_PASS`.
  - `run --args=--gizmo-smoke`: `VIEWPORT_GIZMO_SMOKE_PASS` (handles=176).
  - `run --args=--project-documents-smoke`, which covers Hierarchy drag-to-Assets:
    `PROJECT_DOCUMENTS_SMOKE_PASS`, `PREFAB_WORKFLOW_SMOKE_PASS`.
- Docs:
  - SceneEditor: a Multi-select bullet; Delete/Duplicate act on the set; the stale "Duplicate
    Subtree"/"Delete Entity" names are fixed.
  - Editor README: selection text, the Select All row and multi-move undo.
  - UI_DESIGN: a new Selection section.
  - Help → Keyboard Shortcuts lists Select All from the registry, and its mouse section lists
    the selection gestures.
- Not done:
  - No manual walk.
  - The Light theme look of the active marker and outlines was not checked; the screenshots
    are Dark.
  - The disabled-reason menu tooltip is covered by unit tests, not by a screenshot.
  - Marquee selection is S5.
  - No editor version bump (planned for S7).
- Next: Session 4 (status bar and notices), only when requested.

### Session 4 handoff — 2026-11-17

- Start state: `feat/project-foundation` at e946acd (Session 3). Only the untracked
  starter-assets zip and `docs/plans/editor-game-workflows.md` were present; both were left
  alone. Nothing was committed.
- Evidence check ("Current code"). Red notice `Text` was in these places:
  - `app/Main.kt` ~682–686, not 614–658. That earlier range is the `viewportPanel` failure text
    and the field-validation hint, which both stay. Those lines showed the appearance notice, the
    layout notice and `project.error` in red, and the distribution message as a caption.
  - `ui/SceneWorkspace.kt`: the scene error under the Hierarchy toolbar, and the selected
    entity's error in the Inspector.
  - `ui/EntityWorkspace.kt`: the prefab error.
  - "Project controls" render nothing themselves; their `project.error` showed in `Main.kt`.
  - `scene.message` and `entity.message` were never rendered, so "Saved …", "Opened …", "Applied
    instance changes …" and S2's `HISTORY_CLEARED_NOTICE` were invisible until now.
  - The Assets panel's own operation caption (`ProjectAssetControls.message`) is not red and stays.
- Implemented:
  - `ui/EditorNotices.kt`:
    - `EditorNotices` with `post(level, text, ttlMillis?, action?)` and `info`/`success`/
      `warning`/`error`. Info and success clear after 6 s; warnings and errors are sticky; a newer
      notice replaces the current one.
    - `recent` (20, newest first) and `clearRecent`; `dismiss`, `expire` and `remainingMillis`
      with an injectable `clock`.
    - `invoke(notice)` runs an available `NoticeAction` after dismissing the notice, so the
      action's own result stays.
    - `hint`, the context-hint API for S5's tools; null shows the window's default hint.
    - `LocalEditorNotices`, `postedAfter` for smokes, and `documentMessageLevel`.
    - `NoticeSource`/`NoticeBridge`/`ReportNotices`: the bridge from model fields to notices.
  - One service per editor: `EditorWorkspace.notices`, provided to the main and detached windows.
  - Migration. Model and project code still sets its fields, so `model/` gains no UI dependency.
    `ReportNotices` in `Main.kt` posts each field and then clears it:
    - scene, every scene entity and the prefab workspace: `error` → Error. `message` → Success,
      or a sticky Warning when it ends with `HISTORY_CLEARED_NOTICE`. This covers S2's
      `replaceLinkedScene` and the Apply Changes text.
    - `ProjectWorkspaceState.error` → Error.
    - The appearance and layout notices are watched rather than cleared, and posted once per new
      text. The layout timer re-sets the same failure every second.
  - Distribution: `projectDistributionControls(project, notices)` posts the "Exporting…" info and
    a 15 s success. A failure still goes through `project.error`. It exposes `exporting` for
    progress.
  - S3's Hierarchy multi-drag refusal is a Warning through `LocalEditorNotices`; it falls back to
    `scene.error` without the service.
  - The red lines in `Main.kt`, `SceneWorkspace.kt` (two) and `EntityWorkspace.kt` were removed.
    Field validation and the `viewportPanel` failure text stay where they were.
  - Delete:
    - `SceneWorkspaceState.onDeleted(subject)` runs after `removeSelected` records its step, so
      every delete path reports it: the command, the toolbar, the Inspector, context menus and
      the keys.
    - `ui/SceneCommands.reportDeletion` posts "Deleted <name | N entities>" (Info) with Undo. The
      button runs the registered `EditCommands.undo` with the live command context. It shows
      only while the scene is the active document, no gesture is open, and the deletion is still
      `History.latestStep`, which is new.
  - `ui/EditorStatusBar.kt`, main window only:
    - left: the hint
    - centre: the notice on its level background, with a drawn level glyph, the level colour,
      the action, Dismiss for sticky levels, and a recent-messages button. That button opens a
      Swing popup like the other menus, so it stays above the native canvas; clicking a row
      copies it, and the popup has Clear Messages.
    - right: progress ("Exporting game…"/"Working…"), "N selected", Editing/Playing/Paused and
      "Unsaved"
  - Default hints:
    - Scene: "Click to select; Ctrl+click adds; drag to move; middle-drag pans; right-click for
      actions"
    - Game view: Play
    - Play mode: Stop
    - Prefab workspace and Sandbox each have one.
    S5 replaces these through `notices.hint`.
  - Tokens:
    - `EditorMetrics.statusBar`: Compact 24 dp, Comfortable 28 dp.
    - Glyphs `ChevronUp`, `Info`, `Success`, `Warning` and `Error`.
    - `EditorButton(minHeight=)`, so the action fits the bar.
    - Four status-bar specimens in the theme gallery.
- Deliberate choices (correct them if wrong):
  - Document `error` fields are consumed (posted, then cleared) rather than mirrored. Code sets
    the same text again for each new failure, and only consuming reports a repeat. As a result,
    smokes that waited for `scene.error`/`project.error` now look at `notices.postedAfter(…)`:
    prefab-workflow stale drag, project-documents external conflict, project-smoke rejected
    open, and project-runtime missing startup.
  - A cleared error field does not clear its sticky notice. The plan's rule is "until dismissed
    or replaced".
  - Single deletions name the entity ("Deleted Player"); several say "Deleted N entities", where
    N counts the removed subtrees.
  - Only entity Delete posts an Undo notice. Remove Component and Unlist are also undoable but
    have no notice yet.
  - The status bar has no cancel for long work (R8 mentions one); no task supports cancelling.
  - Drop and drag refusals in `Main.kt` ("Stop the game before …") still use `scene.error`, so
    they arrive as Errors.
- Tests:
  - `ui/EditorNoticesTest` (7 tests):
    - ttl and sticky levels, including an explicit ttl on an error
    - replacement, the recent list, `postedAfter` and `clearRecent`
    - action invocation, a stale action, and an action's own result staying
    - the bridge: consume versus watch, repeats and capacity
    - `reportDeletion`: Undo through `EditCommands.undo` with the live context; hidden when the
      prefab is the active document; stale after a later step; selection restored
    - `documentMessageLevel`
    - the `statusBar` metric
  - `EditorAppearanceTest.requiredContrastPairs` adds:
    - each level colour on `surfaceRaised` and `surface`
    - `textPrimary`/`textSecondary` and `focusRing` on each level background
  - `ui/EditorStatusBarRenderTest` renders the bar offscreen (`ImageComposeScene`) in Dark/Light ×
    Compact/Comfortable. It checks every bar is `statusBar` + 1 hairline high with a notice, an
    action and all right-hand facts, and writes `build/reports/status-bar/*.png`. The images were
    reviewed: they show the level colours, Undo, Dismiss on sticky levels, the spinner and the
    right-hand facts.
- Smoke changes:
  - `--editor-appearance-smoke`, in each theme:
    - adds an entity and deletes it
    - waits for "Deleted New Entity" with Undo available, captures `status-bar-undo-<theme>`,
      invokes Undo and checks the entity is back, then undoes the create
    - sets a sticky scene error, checks the bridge consumed it, captures
      `status-bar-error-<theme>` and dismisses it
  - `--scene-smoke` checks that the Robot Delete key posts "Deleted 2 entities" with Undo, and
    that Ctrl+Z hides the button.
- Commands run, from `tools/editor` through `gradle.ps1` (JDK 21), started from Git Bash:
  - `compileKotlin`: failed twice and then passed. The first failure was a duplicate
    `--console` argument (`gradle.ps1` already passes it). The second was a missing
    `postedAfter` import in four smokes.
  - `test --tests sle.editor.ui.EditorNoticesTest --tests sle.editor.ui.theme.EditorAppearanceTest`:
    the first attempt failed to compile (a qualified function reference in `Main.kt`). After the
    fix: 7/7 and 2/2.
  - `build`: passed, 303 tests, 0 failures, 2 existing skips.
  - `test --tests sle.editor.ui.EditorStatusBarRenderTest`: passed (run twice; the second run
    renders a later spinner frame).
  - `build` again after the render test and docs: passed, 304 tests, 0 failures, 2 existing skips.
  - `run --args=--editor-appearance-smoke`: printed `EDITOR_APPEARANCE_SMOKE_PASS`, and every
    state check passed (Delete notice, Undo, consumed error, theme switch). **The Windows session
    was locked**, however (LockApp/LogonUI running), so every capture, including the new
    status-bar ones, shows the lock screen. The captures are not visual evidence.
  - Later the same day, with the desktop unlocked, each smoke ran once and passed on the first run:
    - `run --args=--editor-appearance-smoke`: `EDITOR_APPEARANCE_SMOKE_PASS`. The captures are
      real now. `status-bar-undo-dark/light` show "Deleted New Entity" with Undo in the info
      colours, and `status-bar-error-dark/light` show the sticky error with Dismiss and
      "1 selected | Editing | Unsaved".
    - `run --args=--scene-smoke`: `SCENE_MULTI_SELECT_SMOKE_PASS …/delete-notice/…`,
      `SCENE_EDITOR_SMOKE_PASS`, `EDITOR_UI_SCALE_PASS`, `SCENE_TREE_COMPONENT_HANDOFF_PASS`,
      `SANDBOX_DESKTOP_PASS`, `SCENE_ENTITY_SANDBOX_SWITCH_PASS`.
    - `run --args=--context-menu-smoke`: `CONTEXT_MENU_SMOKE_PASS`,
      `DETACHED_DELETE_PLAYBACK_SMOKE_PASS`, `MENU_KEYBOARD_SMOKE_PASS`,
      `AUXILIARY_APPEARANCE_SMOKE_PASS`.
    - `run --args=--project-documents-smoke`: `PROJECT_DOCUMENTS_SMOKE_PASS` (external conflict
      seen as a notice) and `PREFAB_WORKFLOW_SMOKE_PASS` (stale drag refused as a notice).
    - `run --args=--project-smoke`: `PROJECT_FOUNDATION_SMOKE_PASS` (failed open reported as a
      notice).
    - `run --args=--project-runtime-smoke`: `PROJECT_RUNTIME_SMOKE_PASS` (missing startup
      reported as a notice).
    - `run --args=--project-distribution-smoke`: `PROJECT_DISTRIBUTION_SMOKE_PASS` and
      `PROJECT_RUNTIME_SMOKE_PASS`.
    - The S3 flaky stages (the first Hierarchy click, and View → Prefab Workspace) did not recur.
- Docs:
  - UI_DESIGN: the `statusBar` metric, the component row, the contrast rule, and a new "Status
    bar and notices" section with the rule and the slot table.
  - HOW_TO_ADD_UI_FEATURES: "Example 3: report a result", plus a checklist line.
  - Editor README: the status bar, and the Delete Undo notice.
- Not done:
  - A manual walk, and the `--gizmo-smoke` and `--docking-workspace-smoke` runs (neither was
    in the S4 list).
  - Hover does not pause an info notice's 6 s timer.
  - No editor version bump (planned for S7).
- Next: Session 5 (viewport tools and snapping), only when requested. Tool hints go to
  `workspace.notices.hint`.
