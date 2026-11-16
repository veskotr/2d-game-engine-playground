# Editor workspace

The editor has one project, document set and playback session across its windows.
Hierarchy, Inspector, Assets, Scene, Game and Entity are singleton panels. The
home window owns **File / Edit / Create / View / Help / Windows**. Detached tool
windows have native title bars and resize/minimize/close controls, without another
application menu. Use the home window's **Windows → Focus Host** to find a tool.

## Arrange panels

- Drag a panel tab along a tab row to reorder or insert it into another group.
- Drop near a panel body's edge to create a split. The preview shows the target;
  drag the divider afterward to set the split size.
- Drop outside all editor windows to detach. Drag the dotted grip at the left of
  a tab row to move the entire group, keeping its tab order and active panel.
- Press Escape to cancel a drag. Window chrome is not a docking target.
- Use **Windows → panel → Move to** or **Detach group / Move group to** for the
  same arrangements through menus. **Show / Focus** restores or selects the
  existing panel; it does not create another copy.

The `_` panel button minimizes that panel to its window's restore strip. Click
its name there or choose **Windows → panel → Show / Focus** to restore it.
`↗` detaches the active panel; `×` hides it. A native title-bar minimize affects
all panels in that window. Closing a tool window hides its panels and retains
its placement for reopening. Closing the home window asks before discarding
unsaved documents; cancel keeps the workspace running, accept closes all tools.

Minimum sizes follow UI scale. Windows that cannot fit their splits on an
available display collapse into tabs so controls remain reachable.

## Scene and Game together

Detach Game or move it beside Scene to see both. Scene uses the editor camera;
Game uses the active scene camera and letterboxes its image. Resize or pan Scene
without changing Game's logical view. **Play / Pause / Resume / Stop** act on one
shared simulation, including **Run Project**. Play continues with zero visible
viewports, including while tools are closed or minimized. Pause freezes gameplay
and audio; Stop restores authored state.

Click Game content for gameplay input. Focus loss, moving, hiding or minimizing a
panel releases held input; click Game again after rearranging. Scene navigation
and selection remain available during Play. Explicitly opening an entity for
component editing stops playback; merely focusing a panel preserves it.

Moving panels or resetting layout retains documents, unfinished fields, selection
and undo. File Save keeps its existing context: a scene copy saves its containing
scene; a standalone prefab saves its own document. Project source Save and Export
Package retain their existing formats. See [scene editing](SceneEditor.md) and
[projects](Projects.md#scene-and-prefab-editing).

## Save, recover and reset layouts

Layouts save automatically after a short debounce, on project switch and at exit:

| Workspace | Layout file |
|---|---|
| Open project | `<project-root>/.sle/workspace-layout.json` |
| Standalone Windows | `%LOCALAPPDATA%/SLE/EntityEditor/workspace-layout.json` |
| Without LOCALAPPDATA | `~/.local/share/sle/entity-editor/workspace-layout.json` |

UI scale uses its separate existing settings file. **Windows → Restore Default
Layout** resets only panel/window placement; it also saves that reset. It does not
clear drafts, project files, undo or UI scale. A damaged or unsupported layout
shows a notice and restores defaults. A failed atomic save preserves the previous
file and reports the failure.

Restored windows open unminimized within connected monitor work areas. Negative
screen coordinates are supported. Display/work-area/DPI changes cancel active
docking and recover bounds; a smaller display can collapse splits into tabs.
Physical monitor unplug/reconnect and mixed-DPI native transitions still need
manual validation. Automated recovery tests use simulated display geometry.
Concurrent editor processes writing the same project's layout are not coordinated.

For isolated standalone diagnostics, the JVM property
`-Dsle.workspaceData=<directory>` selects a different layout directory. Layout
files contain placement, not documents, asset paths or native runtime handles.

See [editor setup and smokes](../../tools/entity-editor/README.md#workspace-windows)
and the [acceptance record](../plans/editor-docking-plan.md#session-8--compatibility-acceptance-and-final-documentation).
