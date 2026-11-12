# Editor shell and navigation

User request: repair hierarchy icons, replace Sandbox/Entity workspace tabs with
Scene/Game plus contextual entity editing, remove helper prose, and add a desktop
menu strip with appropriately grouped commands.

- Draw hierarchy expanders and entity/camera/component icons as vectors.
- Keep Scene/Game on one runtime canvas; switching these views must preserve play.
  Open entity editing via right-click Edit in a temporary tab; entering entity
  editing stops scene play. Keep legacy sandbox commands only as test harnesses.
- File owns New/Open/Save/Save As/Exit; Edit owns entity and scene history, duplicate
  and delete; Create shares context actions; View owns navigation/framing/UI scale;
  Help owns controls/About. Preserve document confirmation and asset packaging.
- Remove redundant file toolbars, view-toggle buttons and instructional captions;
  retain actual errors and essential controls.
- Build/unit tests, desktop context-menu and camera playback checks; update docs
  and reopen the demo. Preserve all existing worktree changes.

## Final UI

The top strip is a real JFrame JMenuBar with flat text headings, standard
menu traversal and Alt mnemonics. It is compact with a subtle bottom border;
individual boxed menu buttons were removed after user feedback. The bar, its
flexible spacer and document label explicitly paint the editor surface to avoid
a light default background in unused space. Desktop regression samples this
area and verifies actual menu/file/tab actions.

Verified: Windows JDK 21 Gradle build (48 tests), desktop context-menu smoke
including viewport/tree selection, contextual Edit, File Save, prefab navigation,
Scene/Game tabs and dark menu-bar pixels. Camera smoke checks real tab clicks
while paused, followed by Resume/Stop and unchanged authored state.

The scene desktop regression also passed entity editing/save/open, 75/85/100%
UI scale, component handoff and the legacy embedded sandbox harness. Native menu
installation is deferred until composition completes to prevent Skia reentry
during scale changes. The Sandbox remains absent from normal navigation.
