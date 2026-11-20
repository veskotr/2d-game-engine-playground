# L7 — Lua script authoring

**Status** — Slices 1 through 4 implemented. Slice 4 desktop acceptance is
open. Covers roadmap items 3.1 and 3.2 in the
[editor workflow roadmap](editor-game-workflows.md).

## User workflow

- In editable Details, choose **New Lua Behavior…**, even on an entity without a
  Script component. Choose a template and an unused destination under the
  project's asset root, defaulting to `Scripts/`. Creating the file adds a named
  behavior to the current entity in one document undo step. Undo detaches the
  behavior and leaves the source available for reuse, including any IDE edits.
  Redo reattaches the same reference. The user can open the new source from its
  attachment or Assets.
- **Open in IDE** opens an attached behavior, legacy primary script, or Lua file
  selected in Assets. Console and Lua Profiler continue to open at the reported
  line. The editor preference is local to the user and applies across projects.
- Initial scope is project-backed authoring, with built-in templates for a basic
  named behavior and a behavior with an exposed property. Files in project-root
  `ScriptTemplates/` are copied verbatim as data. A direct `.lua` file overrides a
  built-in with the same filename or adds a new choice. Template selection does
  not execute Lua.

## Dependencies and delivery order

L7 is editor-only. It uses L1 command and undo paths, L3 project asset identity
and L4 source locations. L6 property discovery reads created scripts when they
declare exposed parameters. The launcher and template picker work independently
of project editor-script trust. Deliver each slice and run its checkpoint before
starting the next.

### Slice 1 — Shared IDE launcher and preference

- [x] Extract source command construction and process launch from
  `console/ConsoleState.kt` into a service under `scripts/`. Retain Windows VS
  Code discovery and use saved preference, then `SLE_SCRIPT_EDITOR`, then the
  VS Code default. Launch directly with `ProcessBuilder`.
- [x] Add an editor-local preference using the same storage directory convention
  as `SnapSettingsStore`. Store the executable and separate file and optional
  line argument lists. Expand `{file}` and `{line}` within individual arguments.
  Use the file list when no positive line is available or the IDE has no line
  configuration. Add a reset to defaults and show configuration and launch
  failures through existing notices.
- [x] Route Console and Lua Profiler through the service. **Checkpoint:** existing
  open-at-line behavior works out of the box. Unit tests cover preference
  precedence, line fallback, invalid settings, and argument boundaries for paths
  with spaces.

Session 1 uses a shared `ScriptEditor` for path validation, argument expansion,
and process launch. `ScriptEditorSettingsStore` reads and writes the local
JSON preference beside standalone snap settings. The Console opens the
settings dialog and both source actions report failures through editor notices.
The Console passes a positive line when a diagnostic has one. The current
Profiler payload supplies a path, so navigation from the Profiler uses the
file arguments.

Validation: `bash /tmp/sle-l2-s4-gradle.sh test` passed 402 editor JUnit tests
under Linux, including all five launcher tests. These cover precedence,
reset, invalid settings, Windows VS Code discovery, and argument boundaries
for a real process and source path with spaces.

On Windows, `cmake.exe --build build/debug --config Debug --target
sle_editor_native --parallel 4` rebuilt the native bridge successfully.
`tools/editor/gradle.ps1 run --offline --args=--console-smoke` failed before
source navigation, both before and after the rebuild. The project Lua command
fixture produced an empty command list at `ConsoleSmoke.kt:92`. Desktop
source navigation remains unverified.

### Slice 2 — Open scripts from Details and Assets

- [x] Add **Open in IDE** for named behaviors and the legacy primary script in
  Details, and for Lua files in the Asset Browser. Route actions through the
  shared launcher and resolve the current project reference at invocation.
- [x] Reuse `ProjectDocuments` for portable asset references and support existing
  resolved local paths. Keep opening available during read-only inspection.
  Report missing source paths and reject stale targets after a project switch.
  **Checkpoint:** test local and project references, unavailable paths, and
  invocation from Details and Assets. Capture arguments with a test executable.

Session 2 adds **Open in IDE** to Script in Details, read-only Live Details,
and the selected Lua file's details in Assets. Asset Browser actions follow
a known UUID after a source move. Attachment actions also accept existing
local paths. Missing targets and stale project actions report editor notices.

The full Linux editor JUnit suite passed all 404 tests using Gradle 9.5.1
with `-p tools/editor --project-cache-dir /tmp/sle-l7-project-cache test
--offline --console=plain -PsleBuildDir=/tmp/sle-l7-editor-build` and an
isolated Gradle user home. The source action tests captured file arguments
from a real executable and checked missing and stale targets. Desktop
button clicks remain unverified.

### Slice 3 — Create and attach from a built-in template

- [x] Add the basic named behavior and exposed-property templates under
  `scripts/`. In editable Details, offer **New Lua Behavior…** with a template
  and destination picker under the project asset root. Default to `Scripts/`
  and make the action available on entities without a Script component.
- [x] Use the project asset operations and identity handling to create an unused
  file, rejecting escapes and collisions. Recheck the project and target after
  file I/O. Append a uniquely named attachment through `EntityWorkspaceState`
  in one document transaction. Keep the file if attachment fails and report it
  as unattached. Refresh Assets after creation.
- [x] **Checkpoint:** create and attach in Scene and Entity Template, verify
  save/reopen, and undo/redo the attachment while preserving file edits.
  Unit tests cover collisions, stale targets, and preservation of existing
  attachments and their parameters.

Session 3 adds the Details dialog and uses the asset root-relative destination
`Scripts/Behavior.lua` by default. The attachment uses the resolved path in
the open editor document. Project save writes its registered asset UUID.
The source and its metadata stay on disk through undo and redo.

Validation: Gradle 9.5.1 passed all 407 editor JUnit tests with
`-p tools/editor --project-cache-dir /tmp/sle-l7-project-cache test --offline
--console=plain -PsleBuildDir=/tmp/sle-l7-editor-build`. The new tests cover
Scene and Entity Template save/reopen, undo and redo, preserved IDE edits and
parameters, collisions, path escapes, and stale targets. Desktop interactions
remain part of the slice 4 smoke checkpoint.

### Slice 4 — Project templates and end-to-end validation

- [x] Discover direct `.lua` files in project-root `ScriptTemplates/`. Bound
  reads and reject links that escape the directory. Merge by filename, with a
  project file replacing the corresponding built-in. Copy template bytes
  verbatim without executing the source.
- [ ] **Checkpoint:** test overrides, additional templates, malformed or
  unavailable files, and the built-in fallback. Run the full editor JUnit suite
  and desktop smoke for template selection, create/attach, undo/redo, and IDE
  argument capture, including Console line navigation through a path with
  spaces. Update the Scene Editor guide and record the commands and results here.

Session 4 discovers choices when the Details dialog opens. Project files named
`Basic.lua` and `ExposedProperty.lua` replace those built-ins. The picker skips
oversized files and escaping links. Creation rechecks the selected file and
reads up to 1 MiB before passing the bytes to the existing asset creation path.
The [Scene Editor guide](../topics/SceneEditor.md#create-a-lua-behavior)
describes the workflow.

- Linux Gradle 9.5.1 with `-p tools/editor --project-cache-dir
  /tmp/sle-l7-project-cache test --offline --console=plain
  -PsleBuildDir=/tmp/sle-l7-editor-build` passed all 409 JUnit tests. Coverage
  includes template overrides, malformed Lua copied verbatim, missing and
  oversized files, escaping links, built-in fallback, and project-template
  attachment through save/reopen and undo/redo. A Console diagnostic with a
  path containing spaces passed the expected line arguments to a real process.
- Windows `tools/editor/gradle.ps1 run --offline --args=--authoring-smoke`
  passed the existing authoring smoke. The same command with
  `--args=--console-smoke` failed at `ConsoleSmoke.kt:92` when the project Lua
  command list was empty, before source navigation. Interactive template
  selection, create/attach, undo/redo, and Console navigation remain open.
- Windows `tools/editor/gradle.ps1 test --offline --tests "*ScriptCreationTest"`
  passed four tests. The symlink test was skipped on Windows and passed on Linux.

## Boundaries

Use the existing named-behavior schema and document formats. No C++ gameplay
API changes, built-in code editor, or debugger integration are required.
