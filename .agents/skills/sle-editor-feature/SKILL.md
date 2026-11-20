---
name: sle-editor-feature
description: Add or extend a tool in the SLE Compose Desktop editor. Use for editor feature requests, especially when moving controls from a menu into a window, adding a menu entry that opens a tool, or integrating a dockable panel.
---

# Add an SLE editor feature

Read the repository's AGENTS.md and tools/editor/HOW_TO_ADD_UI_FEATURES.md. Find the nearest existing feature and follow its state, command, and rendering patterns. Lua Profiler and Console are examples of panels available from the Windows menu.

For a dockable tool:

1. Give it a stable PanelId and register its title and minimum size in workspace/PanelRegistry.kt. Decide whether it belongs in the default layout or opens on demand. Check how existing saved layouts handle a newly registered ID.
2. Provide one composable binding for that ID in the WorkspacePanels map in app/Main.kt. The same binding renders in the main workspace and detached hosts. Keep state that must survive moving or hiding the panel in the existing feature or workspace owner.
3. Open or focus the panel through WorkspaceWindowManager. A registered panel is exposed by workspace/WindowsMenu.kt according to its availability and temporary-panel policy. If another menu or shortcut should open it, use the existing command and EditorActions routing where applicable. When moving controls into a window, preserve their existing actions, enablement, and document undo behavior.
4. For engine-backed data, consume the supplied editor/native bridge contract. Use the existing project, scene, and session ownership patterns so stale callbacks cannot act on a replacement document or project.

Check the normal menu entry, restoration of saved layouts, repeated open/focus, and docked and detached hosting for a new panel. Run focused editor JUnit tests and the relevant desktop smoke when available. Report which checks ran.
