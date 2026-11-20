# Little Forest editor UI: new-chat handoff

Status: sessions 1–6 implemented; see their completion records for current
validation and outstanding interactive checks. 2026-11-16.

## Scope and decisions

Implement `docs/design/editor/SLE_Design_Prompt.txt` using `docs/design/editor/SLE_EDITOR_UI_DESIGN.md` and
`docs/design/editor/SLE_Starter_Assets_Handjet_v1 (1).zip`. The user requested exploration and
self-contained implementation sessions to reduce context costs. They confirmed
that this work is for the editor; engine changes come later.

Keep Compose Desktop, existing commands, docking/detached windows, native viewport
ownership, source formats, project operations and undo. Adopt Little Forest dark
and light themes, shared compact metrics, reusable controls, horizontal inspector
rows, bundled editor icons, app branding and shared typography. Keep a Roboto
for headings and dense text (user override during session 2); numeric fields
retain monospace. Do not adopt runtime textures, change game pixels, or alter engine
behavior. Runtime asset integration needs its own later task.

## Approaches

1. **Incremental migration (recommended plan):** introduce tokens and shared
   components, then migrate existing surfaces. Each session ends with a working
   editor and focused checks. Preserves architecture and makes regressions local.
2. A single global Material palette/density override is smaller, but cannot solve
   inspector layout, Swing appearance, numeric transactions or duplicated states.
3. Replacing the UI framework/layout architecture would enlarge risk and violate
   the brief's instruction to preserve integrations. Excluded.

The visual direction is already marked approved in the supplied spec. This plan
chooses incremental migration; the user has not yet reviewed these session details.

## Actual implementation audit

Paths below are relative to `tools/entity-editor/src/main/kotlin/sle/editor/`.

| Area | Evidence and implications |
|---|---|
| Framework | `tools/entity-editor/build.gradle.kts`: Kotlin/JVM 2.4.21, Compose Desktop/Material 1.12.2, JDK 21. Keep dependencies pinned. |
| Theme | `ui/EditorTheme.kt`: single hardcoded dark palette, scale applied through `LocalDensity`, partial typography and shapes. Extend this entry point. |
| Roots | `app/Main.kt`: separate `EditorTheme(uiScale)` calls for home and detached windows. Both must receive the same preference state. |
| Menus/dialogs | `ui/EditorMenuStrip.kt`, `EditorContextMenu.kt`, `MenuAppearance.kt` bridge Compose to Swing; `JOptionPane` and `JFileChooser` are used throughout. Compose-only theming is insufficient. |
| State | `workspace/EditorWorkspace.kt` owns documents/session/windows; UI scale defaults to .85. No preference persistence was found in inspected code; topic documentation's claim about an existing settings file needs verification. |
| Inspector | `ui/EntityWorkspace.kt`: `ComponentField` branches over JSON types and native enum catalog; large Material inputs, 14dp cards, recursively stacked vector fields. `ui/SceneWorkspace.kt` reuses it for Transform/Camera2D. |
| Editing | `model/EntityWorkspaceState.kt`: `editField` records drafts and updates valid JSON immediately; invalid input preserves the last valid document. `beginGesture/endGesture/cancelGesture` exist. Preserve scene history ownership when integrating transactions. |
| Metadata | `model/EntityDocument.kt`: `ComponentDefinition(defaults, enums)` comes from JNI. Do not invent native ranges or a parallel schema. |
| Docking | `workspace/DockChrome.kt`, `DockSizing.kt`, `DockHost.kt`, `WorkspaceWindowManager.kt` implement real docks, tabs, recovery and scale-aware minima. Avoid replacing them or silently resetting saved layouts. |
| Layout persistence | `workspace/WorkspaceLayoutStore.kt`: project `.sle/workspace-layout.json` or user storage, bounded reads and atomic replacement. Appearance is user preference, not scene/project content. |
| Assets | `project/assets/AssetBrowserPanel.kt`: already a folder tree/details list, actions and selection details. Preserve asynchronous service and UUID workflows. |
| Icons | `ui/TreeIcons.kt` and `MenuAppearance.kt` draw existing vectors. Bundle has 20 icon kinds; missing command icons need a centralized fallback from existing vectors. |
| Validation | Existing JVM tests and finite GUI smokes cover documents, projects, asset operations, docking, gizmos and runtime behavior. Reuse them. |

## Asset audit

The ZIP contains 151 files, approximately 2.16 MB uncompressed. Root directory:
`SLE_Starter_Assets_v1/`. Editor resources are `editor/icons/svg/`, PNGs in
`editor/icons/png/{dark,light}/{16,24}/`, four `fonts/handjet/*.ttf` weights,
`licenses/Handjet-OFL.txt`, `branding/app_icon/`, and `themes/little_forest.json`.
There are icons rather than prebuilt button widgets. Buttons must be implemented
as reusable Compose controls. Preserve font license/provenance when copying.

Bundle README explicitly assigns Handjet to personality/headings and a readable
system font to dense data. The JSON contains only the initial 11 colors per mode;
additional interaction/status/grid tokens must be chosen and contrast-checked.
The ZIP also contains a second design doc. Compared during planning: it is
identical to the root design after line-ending normalization. The root design
governs UI requirements and bundle README governs asset usage. Do not silently
overwrite either original input.

## Session order

Run one session per new chat, in order. Each file is a bounded implementation
contract, with narrow reads, tests and a completion record. Later sessions must
read earlier completion records, not previous chat history or the entire repo.

1. [Theme, preferences and resources](01-foundation.md)
2. [Compact shared controls and numeric editing](02-controls.md)
3. [Scene and prefab inspectors](03-inspectors.md)
4. [Workspace chrome, trees and assets](04-workspace.md)
5. [Menus, dialogs and remaining surfaces](05-auxiliary.md)
6. [Acceptance, screenshots and contributor guides](06-acceptance.md)

Start a new chat with this, replacing `01-foundation.md` as appropriate:

```text
Implement only docs/plans/little-forest-ui/01-foundation.md.
Read its required context and the decisions in docs/plans/little-forest-ui/README.md.
Preserve existing edits and implement against current code, not historical plans.
Editor UI/branding only; engine/runtime texture changes are deferred.
Complete its focused checks, record actual results and limitations in that file,
and stop at the session boundary. Do not start the next session.
If the work exceeds this session, record a precise continuation instead of expanding scope.
```

## Shared contract for every session

- Read root `AGENTS.md`, `CONTRIBUTING.md`, relevant editor README sections and
  `docs/CONSTRAINTS.md`; inspect `git status` and preserve pre-existing changes.
- Main source prefix: `tools/entity-editor/src/main/kotlin/sle/editor/`;
  test prefix: `tools/entity-editor/src/test/kotlin/sle/editor/`.
- Follow existing package conventions. Proposed new filenames/interfaces may be
  refined if current code requires it; record the exact resulting API for the next
  session. Avoid speculative widget frameworks or unrelated refactors.
- Use semantic tokens only in migrated UI. Keep literal palette values in one
  source, metrics in one source, and Compose/Swing adapters at the boundary.
- Compact targets: toolbar 30–32dp; button 26–28dp; field 24–26dp; tree/list
  22–24dp; property row 28dp; section header 26dp; gap 4dp; panel padding 6–8dp;
  body 12–13sp; icons 14–16dp. Allow height growth for OS/font accessibility scaling.
- UI scale, OS density and font scale must not multiply twice. Retain existing
  scale choices; density is a metrics choice, not a replacement for accessibility.
- Existing source compatibility wins over generic advice to preserve unknown keys:
  do not change existing rejection/normalization rules in this UI task.
- No new docking system, prefab semantics, native ownership, fake actions, game
  content changes or dependency upgrades. Reuse command callbacks and state.
- Add focused regression tests for changed behavior. Pure styling changes need
  representative visual checks rather than tests that restate implementation.
- Update the session record with files/APIs, commands/results, screenshots,
  blockers, remaining work and the next session. Commit only if requested.

## Validation environment

Supported editor validation is Windows x64, JDK 21, MSVC and an interactive desktop.
The planning shell is WSL. `powershell.exe` is discoverable; no `java` was found on
the WSL PATH. This is not proof Windows tooling or a desktop can run successfully.
No build, test or screenshot was executed during planning. All seven handoff files
were checked for valid local Markdown links, and the two design copies were
compared. Original inputs remain untouched.

In a Windows PowerShell terminal, build the existing native host if needed:

```powershell
cmake --preset debug -DSLE_BUILD_EDITOR=ON
cmake --build build/debug --config Debug --target sle_editor_native
cd tools/entity-editor
.\gradlew.bat build
```

All session Gradle commands below run from `tools/entity-editor`. If a GUI check
cannot run, record it as unverified and leave visual acceptance open. Do not call
the entire migration complete without real dark/light screenshots and interaction
checks. Do not rebuild C++ for documentation-only work or unchanged JNI code.

## Progress

- [x] Inspect input files, archive inventory, framework and relevant implementation.
- [x] Confirm editor-only scope; defer engine changes.
- [x] Write bounded new-chat implementation handoffs.
- [ ] User review of session plan.
- [x] Sessions 1–6 implemented and validated (acceptance partial on real 200% DPI and manual keyboard traversal; see 06-acceptance.md).
