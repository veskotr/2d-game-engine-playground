# Project folders

A project is an ordinary folder with a readable JSON `.sleproject` descriptor.
The descriptor stores identity and settings, not a list of files. Files remain
accessible to VS Code and other tools.

## Foundation workflow

- **File → New → Project…** selects a parent folder and a new project name. The
  editor creates a new directory; it never replaces an existing directory. It saves
  an empty `Assets/Scenes/Main.scene.json`, assigns its asset UUID as the startup
  scene, and opens it immediately. The editor camera works without authored entities.
- **File → Open → Project…** opens the descriptor. Moving the whole folder preserves
  the project identity and relative asset-root location. The last successfully
  opened/saved project scene is restored; otherwise the startup scene opens.
  Missing, corrupt or escaping remembered paths show a notice and try the startup
  scene. If neither opens, the workspace stays empty; saved scenes are never replaced.
- **File → Open Recent** lists up to ten normalized project paths, most recent first.
  History persists in user-local `recent-projects.json` beside the standalone layout
  (including `sle.workspaceData`). Missing projects report an error and retain the
  current workspace. Project settings refresh preserves the current scene and draft.
- **File → Project Settings…** edits the project name, startup scene and game/window
  settings. Save validates and atomically replaces the descriptor. Invalid values
  remain in the dialog; externally changed descriptors require reopening before
  saving so stale settings do not overwrite them.
- **File → Close Project** closes the project context and returns to an empty
  standalone workspace. Switching projects or closing one confirms unsaved scene
  and entity template changes before resetting those documents. Failed opens keep the
  current workspace. Edits made during background loading are checked again.

Default structure:

```text
My Game/
  My Game.sleproject
  Assets/
    Scenes/
      Main.scene.json
      Main.scene.json.slemeta
    Prefabs/
    Scripts/
    Textures/
    Audio/
    UI/
    Animations/
    Data/
  .gitignore             # excludes .sle/
  .sle/
    cache/
    editor-state.json    # local UI scale and project-relative last scene
```

The initial folders are organizational defaults. Project sources contain no
absolute machine paths. `.sle` contains local data; retain `.sle/trash` while deleted assets may need
recovery. Other caches are disposable; invalid local
state falls back to defaults without invalidating the project. Scale-only saves,
including project close/switch and normal editor exit, preserve the remembered scene.
Older scale-only local files remain supported. Opening existing projects does not
rewrite their descriptor or scene files.

The descriptor has format `sle-project`, version 1, a UUID `id`, a display `name`,
a relative `assetRoot`, a nullable UUID `startupScene`, and `game` settings.
`game.window` contains title, width, height, vsync and mode; `game.targetFPS` is
nonnegative, with zero meaning unlimited. Dimensions must be 1–16384. Unknown
versions/fields, escaping asset roots and invalid identities are rejected.

Launch the editor with a project from the repository root:

```powershell
cd tools/editor
./gradlew.bat run --args='--project "C:/Games/My Game/My Game.sleproject"'
```

## Asset browser

Opening a project shows a resizable asset dock below the workspace. Its folder
tree and file list reflect actual files beneath the configured asset root,
including unknown file types. The descriptor never lists which files can appear.

- Select a folder in the tree, or double-click a folder in the file list.
- Use the breadcrumbs or **Up** to navigate; select a file for path/type/size/time.
- Drag the dock's top divider to resize it.
- Use **View → Hide/Show Asset Browser** and **Refresh Assets**, or the dock's
  Refresh button. Returning focus to the editor also requests a refresh.
- External additions, renames, moves and removals are discovered automatically.
  A full background reconciliation every three seconds covers missed events.
  Missing/unreadable folders show an error and recover when accessible again.
- Links appear as links and are not traversed. `.sle` local storage is outside
  the asset root and is not included in discovery.

Scanning and filesystem notifications run on a worker thread. Switching or closing
projects shuts down the old watcher; asset discovery does not change documents,
reload the runtime or apply external edits to a running gameplay snapshot.

## Imports and asset identity

- Navigate to a destination folder and choose **Import…** in the dock. Select
  files or folders; imports copy sources and preserve nested folders (including
  Lua modules). Originals are never removed. Existing files beneath Assets are
  reused directly.
- A name collision offers **Rename**, **Skip**, or **Replace**. Rename picks an
  unused numbered name. Replace merges folders, retains unmentioned files and
  preserves the identity of existing destination assets. Each file replacement
  is atomic; a whole folder/batch is not a transaction. An I/O failure may leave
  earlier successfully imported files in place.
- Component **Browse…** starts in project storage. External images go to Textures,
  Lua files to Scripts, and other files to the currently browsed folder. If a
  default folder was removed, the asset root is used. Import script dependency
  folders together through the dock; Browse does not infer Lua dependencies.
- Select a file for its UUID, **Reveal in Explorer**, **Open Externally** (supported
  document/media extensions), or **Assign New UUID…**. The latter requires explicit
  confirmation because references to the old identity will need repair. Links
  cannot be used for project operations.

Every ordinary asset file receives adjacent `<filename>.slemeta` JSON containing
`format: sle-asset`, `version: 1`, and a UUID `id`. Keep sidecars in version control
and move them with payloads. The `.slemeta` suffix is reserved for metadata, which
is omitted from asset rows. The index is rebuilt from disk; no inventory controls
visibility. Editing a payload keeps its UUID. Moving the complete project, or
moving a payload together with its sidecar, preserves identity.

Missing sidecars receive new UUIDs. Moving only a payload externally can therefore
lose its old identity. Malformed, orphan and duplicate metadata appear under the
dock's asset issues button. Duplicate UUIDs are excluded from both lookup indexes;
imports with conflicting IDs are rejected before copying. Repair a duplicate or
malformed sidecar by selecting the affected asset and assigning a new UUID. Orphan
sidecars can be inspected/removed in Explorer. Existing metadata is never silently
rewritten to resolve ambiguity.

## Scene and entity template editing

With a project open, **File → New → Scene/Prefab** creates a draft. **Save** and
**Save As** default to `.scene.json` and `.prefab.json` beneath Assets. The initial
Scenes/Prefabs folders are suggestions; other existing asset folders work too.
Double-click either source file in the asset dock to open its workspace. Scene
**Import → Entity Template…** inserts a linked subtree instance for a project entity template,
retaining shared assets and scene undo history. Legacy/package imports remain
independent. Saving the entity template refreshes linked instances in the open scene;
reopening other scenes resolves the latest saved entity template without rewriting them.
Placement and explicit component overrides remain local. See
[linked entity template semantics and current editing limits](Prefabs.md#desktop-entity-authoring).

Project sources are readable JSON envelopes with `format: sle-project-scene` or
`sle-project-prefab`, `version: 1`, `document` (the ordinary scene/prefab schema),
and `assets` (UUIDs for explicit extra imports). Recognized component asset fields
store `project-asset://<UUID>`, resolved through adjacent `.slemeta` identities.
Scene `prefabs[].file` also stores a project asset UUID, and override component
asset fields use the same asset resolver. Names, entity keys and script variables
are preserved as data. The descriptor
still has no inventory. Saving multiple documents referencing the same texture
writes no additional texture copies. Assets must already be beneath Assets;
use Browse/Import for external resources before saving a project source.

The editor resolves UUIDs to paths on a worker before engine validation, preview
and Play. Missing or ambiguous identities fail explicitly and preserve the open
draft. Moving the whole project, or an asset with its sidecar, preserves source
references; reopen a document to resolve its current paths. Native project launch uses the same UUID identities. Nested asset formats and
dynamic XML links still follow their existing loaders; arbitrary script
dependencies are not inferred.

**New/Open/Close** retain unsaved-change confirmations. External changes never
silently reload an open draft or a running Play snapshot. Save compares the file
contents with the opened/saved revision and refuses externally changed or deleted
sources. Reopen (confirming discard) to load disk changes, or **Save As** to a new
name to preserve the draft. Existing Save As destinations are refused; open one
first to edit it. Edits made during background loading are confirmed again. Edits
made during saving remain dirty against the snapshot that actually reached disk.

**File → Export Package…** writes a portable `.slescene` or `.sleprefab` without
changing the source binding. Existing packages can still be opened and saved as
standalone documents. Opening a project does not automatically convert a package;
project-source saving requires project-owned assets. Package `asset://<SHA-256>`
references remain separate from project UUIDs.

## Managed assets

Use **New Folder…** in the asset dock to create a folder inside the current one.
Select a file or folder for **Rename…**, **Move…**, and **Delete…**. Move takes the
complete destination path relative to Assets, including the new name. Destinations
must have an existing parent; collisions, links, escapes and reserved names are
rejected. Scene/prefab sources retain their dedicated suffixes. File moves carry
sidecars; folder moves carry all contents, preserving UUID references without
rewriting saved documents. A failed sidecar move attempts to roll back the payload;
a process crash during a two-file move can require manual sidecar recovery.

Save your drafts, stop playback and choose New in both scene and entity template workspaces
before moving, deleting or recovering assets. Open documents retain resolved paths,
so this prevents managed operations from invalidating their authoring state. The
editor temporarily blocks input while those operations run on a worker.

Delete reports saved-source UUID references and the current project startup
identity. A confirmed referenced deletion can make those documents or the startup
scene fail to load until restored or repaired. Malformed sources or metadata block
managed move/delete rather than pretending reference analysis succeeded. Checks
conservatively include UUID strings in saved sources. Lua `require`, dynamic paths,
and nested XML/animation links are **not rewritten or inferred**; move/delete
confirmation explains that those may require manual updates.

Deletion moves payloads and sidecars to `.sle/trash/<token>` with an original-path
receipt. **Recover Deleted…** lists receipts across editor/project restarts and
whole-project relocation. Recovery never replaces an existing payload or sidecar;
restore a removed parent first. Discovery recognizes an interrupted sidecar-only
deletion and offers recovery without minting a replacement UUID. Keep `.sle/trash`
until recovery is no longer needed; it is local, ignored by version control and
must not be purged along with disposable caches. Recovery is not a backup against
loss of the project folder. Files changed externally still use the existing
conflict/identity-repair workflow.

## Run the whole project

Save the scenes you want to run, then choose **File → Project Settings… → Startup**.
The selector lists plain project scenes by their asset-relative paths and stores
the selected scene UUID. **Run Project** launches the saved startup scene in Game;
it preserves the open scene/prefab drafts, including unsaved edits. Ordinary
**Play** continues to launch the current scene draft. Pause/Resume/Stop work for
both modes; Stop restores authoring without copying gameplay state into sources.

Run the same project in the standalone application from any working directory:

```powershell
C:/projects/engine/build/debug/engine_app/Debug/engine_app.exe --project "C:/Games/My Game/My Game.sleproject"
```

Standalone applies title, logical window dimensions, window mode, VSync and target
FPS. Embedded project playback applies logical dimensions, VSync (where supported
by the host graphics driver) and target FPS; title/fullscreen mode belong to the
standalone game window, while the editor keeps its shell. Zero FPS means no
explicit frame cap. `--frames N` performs a bounded standalone diagnostic run and
reports its final scene. Existing no-argument and `--config engine.json` launches
remain available.

Native `ProjectRuntime` validates the descriptor, sidecar index, all plain project
scene/prefab sources and their UUID references before launching. Missing startup,
resources, malformed metadata or duplicate IDs produce errors; the editor retains
its drafts. Native launch never assigns IDs or modifies source files. The asset
browser normally creates missing sidecars before launch. Scene registrations use
both each scene's document `key` and its asset UUID; keys must be unique and cannot
collide with another asset UUID. Lua can switch by either alias with
`Engine.switchScene(...)`.

Scene documents are resolved once per launch, so external edits do not replace a
running scene snapshot. Resource payloads still use the existing resource caches
and loaders; this is not a content-freezing pack or hot-reload system. Deferred
component assets resolve to absolute project paths. Lua `require` searches
`Assets/Scripts/?.lua`, `Assets/Scripts/?/init.lua`, then the asset root equivalents
(using the configured asset-root folder). Existing Lua search paths follow those.
Dynamic engine texture/audio/animation/component/prefab operations accept
asset-root-relative paths or `project-asset://<UUID>` references. Raw Lua file IO
and nested XML/animation/state-machine path syntax retain their loader contracts;
use their supported document-relative paths or explicit resolved resources.

Runtime entity template instances use derived ordinary JSON in a unique `.sle/runtime/`
folder owned by the launch context. The context removes its folder when released;
a crash can leave disposable cache folders. Project source files remain
filesystem-authoritative. Runnable folder export is documented below and tracked in the
[distribution plan](../plans/project-distribution.md).

## Export a runnable game

Build a prepared Windows x64 Release runtime once, using the existing Windows
CMake build tree:

```powershell
cmake --build build/debug --config Release --target sle_runtime_bundle --parallel 4
```

The bundle appears at `build/debug/runtime/Release`. The same target works in a
separately configured Release build tree. Export does not compile or download
anything. Debug runtimes cannot be distributed through this workflow.

Choose **File → Export Game…**, select that prepared runtime folder. The editor
automatically creates an `Exports` folder inside your project and places the game
in a subfolder named after the project folder. Repeated exports use `-2`, `-3`,
and so on, preserving previous exports. The configured asset root must be separate
from `Exports`; overlapping custom asset roots are rejected. A packaged Windows editor ships the
runtime and skips the runtime chooser. Export uses saved files: unsaved
scene/prefab drafts are kept in the editor and excluded from the game. Select a startup scene in Project
Settings and save your intended changes before exporting. Export runs on a worker
and reports completion or an error without replacing the current workspace.

The delivery contains:

```text
My Delivery/
  engine_app.exe
  *.dll                    # Release runtime/dependency libraries
  assets/                  # engine shaders, UI and fonts
  notices/                 # dependency license notices from the build
  sle-runtime.json         # versioned runtime file hashes
  sle-distribution.json    # delivery file hashes and descriptor location
  game/
    game.sleproject
    Assets/                # or the project's configured relative asset root
```

Double-click `engine_app.exe` to run the saved startup scene. With no explicit
config/project option the executable selects `game/game.sleproject` beside itself,
independently of the working directory. Explicit `--project` and `--config` retain
priority; executables without a delivered project keep their existing engine.json
workflow. For a bounded diagnostic run:

```powershell
C:/Deliveries/MyGame/engine_app.exe --frames 8
```

The first distribution format is a folder containing ordinary source assets and
UUID sidecars. It copies the complete asset tree, including unknown file types and
Lua module directories, because dynamic asset paths cannot be inferred safely.
Moving the delivery preserves references; edits to its original source project do
not change the exported copy. Existing relative/dynamic XML, Lua IO and nested
resource-format contracts still apply; export does not rewrite those strings or
include arbitrary files outside the configured asset root.

A unique sibling staging folder receives the copy, native project validation and
SHA-256 inventory checks before a no-overwrite publish. Missing/invalid metadata,
unresolved UUIDs, source/runtime changes during export, damaged bundle files,
links, case-insensitive name collisions and occupied destinations are rejected.
The exporter never assigns source UUIDs; Refresh Assets first if metadata is
missing. Source `.sle` caches/editor state/recovery trash are excluded and retained
in place. Native validation's staged entity template caches are removed before publishing.
A running delivery may later create its own `.sle/runtime` derivatives, so keep
its game directory writable. An interrupted export can leave a sibling
`.sle-export-*` staging folder; the final destination appears only after validation.

The bundle includes app-local Release MSVC libraries and engine resources. It
expects Windows 10 or later and a compatible OpenGL driver. Relocation and source
independence are tested locally; a separate clean-machine deployment test remains
necessary for any wider platform support claim. Installers, encrypted archives,
asset pruning and content streaming are future formats.

**Export Game…** is the editor's game delivery action. Internal package tooling and
the runtime still support `config.json` and `.slepkg`; see [game packages](GamePackages.md).

## Validation

`gradlew.bat build` covers descriptor parsing, ordinary folders, relocation,
settings preservation, malformed/future files, path escapes, existing-directory
preservation, external settings conflicts and disposable local state. Asset tests
cover disk discovery, unknown files, external moves/deletion, stale UI clicks,
watcher cancellation and reconciliation without notifications. The symbolic-link
fixtures are skipped on hosts that cannot create symbolic links. Import/identity
tests cover folder copies, source removal, collisions, in-project reuse, invalid
metadata, duplicate IDs, payload edits, relocation and explicit repair. Selection
tests cover manual drafts, late edits and document switches during Browse.

`gradlew.bat run --args=--project-smoke` checks actual desktop settings editing,
invalid values, File menu actions, UI-scale persistence on Close Project,
relocation/reopen, rejected opens and edits made during background switching.
It writes `build/reports/project-foundation-editor.png` and temporary example
projects beneath `build/reports/project-smoke-*`.

`gradlew.bat run --args=--asset-browser-smoke` checks tree/list/breadcrumb navigation,
selected-folder imports, collision renaming, Lua module folders, source removal,
UUID stability, script reuse and texture Browse, external file changes, View
commands, background scanning while gameplay runs, and project switch/close/reopen. It writes `build/reports/asset-browser-editor.png`
and temporary projects beneath `build/reports/asset-browser-*`.

`gradlew.bat run --args=--project-documents-smoke` checks actual project Save and
browser double-clicks, scene/prefab sharing, entity template insertion and undo, dirty
cancellation, external conflicts and reopen, Lua Play, unchanged running snapshots,
package export, full-project relocation and relocated script/texture loading.
It writes `build/reports/project-documents-editor.png`. Persistence tests also
cover missing/duplicate UUIDs, invalid destination metadata, validation failure,
concurrent file arrival and external deletion without overwriting existing data.


`gradlew.bat run --args=--project-runtime-smoke` checks actual startup selection,
Run Project with dirty drafts, Lua modules and deferred scene switches, logical
resolution/VSync, Pause/Resume/Stop, relocation, standalone launch from an unrelated
working directory, and rejected startup preservation. Screenshot:
`build/reports/project-runtime-editor.png`. Native `integration_project_runtime_path`
and windowed `integration_project_play_path` cover resolver failures and actual
Lua gameplay after relocation.

`gradlew.bat run --args=--project-distribution-smoke` extends the project runtime
fixture with actual Export Game menu/choosers, saved-source copies, native entity template
cache cleanup, source trash preservation, dirty-draft/context preservation,
collision failure and a relocated Release no-config launch from an unrelated cwd.
It changes the original script temporarily to prove delivery independence and
restores it afterward. Reports: `build/reports/project-distribution-editor.png`
and `project-distribution-standalone.log`. Filesystem tests cover source/bundle
changes, custom roots, missing metadata, missing DLLs, staged corruption,
validation failure and concurrent destination arrival.
