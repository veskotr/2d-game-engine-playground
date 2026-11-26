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
- **File → Project Settings…** groups the project name and startup scene under
  **Startup**, window options under **Game**, and the preset under **Art Style**.
  Save validates and atomically replaces the descriptor. Cancel discards staged edits. Invalid values
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
a relative `assetRoot`, a nullable UUID `startupScene`, `game` settings, and an optional `settings` object.
`game.window` contains title, width, height, vsync and mode; `game.targetFPS` is
nonnegative, with zero meaning unlimited. Dimensions must be 1–16384. Unknown
versions/fields, escaping asset roots and invalid identities are rejected.

### Art style

**New Project** offers **Pixel Art** and **Smooth**. Change the preset in **Project
Settings → Art Style**. The descriptor stores `settings.artStyle` as `pixelArt`
or `smooth`. **Legacy defaults** clears the preset.

| Preset | Inherited sprite filter | Movement snap | Grid spacing |
| --- | --- | --- | --- |
| Pixel Art | Nearest | On | 1 world unit |
| Smooth | Linear | Off | 10 world units |
| Legacy defaults | Existing loader default | Off | 1 world unit |

Per-texture filters and per-field local snap overrides take precedence. Snap
defaults update after Save. Sprite filtering applies when the authoring preview
loads a scene or Play starts again. Export includes the saved preset and requires
an SLE 0.26 or newer runtime bundle for projects with a preset. Art-style
changes preserve sprite dimensions and authored transforms.

### Sorting layers

Open **Project Settings → Sorting Layers** to add a label for an unused
signed integer. Edit or remove labels and choose **Save** to apply the changes.
The SpriteRenderer Inspector shows named choices alongside the editable
integer. Choosing a layer is undoable. A sprite whose value has no label
displays its integer, including after label removal or transfer to another project.

The optional project field `settings.sortingLayers` is a list of entries such
as `{"value":10,"label":"Foreground"}`. Values must be unique. Scenes and
prefabs store the integer layer. Renaming or removing a label preserves those
assignments and draw order. Rendering continues to treat negative values as
layer zero. Both export formats carry the saved labels and require an SLE 0.28
or newer runtime bundle when the project defines labels.

### Entity tags

Define tags under **Project Settings → Tags** and save. In the scene or prefab
Inspector, choose **Add Component → Tags**, then check the tags to assign.
Membership edits can be undone. Names are unique and case-sensitive.

The optional `settings.tags` field contains entries such as
`{"id":"stable-id","name":"Enemy"}`. Scenes and prefabs store these IDs in
`components.Tags.ids`. A rename keeps assignments and changes the name
scripts query. Deleting a definition or moving a scene or prefab to another
project preserves the IDs. The Inspector labels an unmatched ID as unresolved.
Restoring a definition with the same ID recovers its assignments.

Both export formats carry the definitions. Use an SLE 0.29 or newer runtime
bundle for tagged content. See [Lua tag queries](Scripting.md#entity-tag-queries).

### Keyboard input actions

Open **Project Settings → Input Actions**, enter a name and choose **Add action**.
Choose **Add key** beside the action and press a key to capture it. An action
can have several keys, and the same key can be used by several actions. Use
**Remove key** or **Remove action** to delete entries. Save applies the map to
the next Play Scene, Run Project or standalone launch.

The optional `settings.inputActions` list stores entries such as
`{"name":"Jump","bindings":[{"kind":"key","key":32},{"kind":"key","key":87}]}`.
Key codes use GLFW values. Names are unique, nonempty and case-sensitive.
Empty binding lists are valid. Both export formats carry the map and require
an SLE 0.30 or newer runtime bundle when the project defines actions.

Bound keys take priority over editor Play shortcuts while Game has keyboard
focus. The Play toolbar remains available. Standalone Escape exits when it
has no action binding. Keypad Enter is separate from main Enter. The editor
uses AWT key names, so letter and punctuation positions can differ from
standalone GLFW on some non-QWERTY layouts. See
[Lua input actions](Scripting.md#keyboard-input-actions).

### Collision layers

Open **Project Settings → Collision** to name the 16 layer slots and choose
which pairs can interact. An empty name displays the slot number. Each
checkbox in **Allowed pairs** also updates the transposed cell. Save applies
the labels to the collider and zone bit checkboxes in the Inspector. Start
Play again to use the saved matrix. Category and mask integers stay portable
across scenes and prefabs, and renaming a slot preserves its bit.

In the version 1 descriptor, optional `settings.collisionLayers.labels`
lists names in bit order. Missing or empty names use the numeric labels.
`settings.collisionLayers.blockedPairs` lists pairs of zero-based slots from
0 to 15. Either order blocks the same pair. Unlisted pairs are allowed,
including same-layer pairs. Export includes the saved settings and requires
an SLE 0.27 or newer runtime bundle when collision settings are present.
See [physics filtering](Physics.md#collision-layer-filtering).

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
- Visible texture rows show a thumbnail when the editor can decode the image.
  Previews use the full image and the texture's filter setting. Unsupported or
  unreadable images show the file icon. Thumbnails live in
  `.sle/cache/thumbnails`, outside game packages and exports. Delete that folder
  to clear the cache, then refresh the browser to rebuild visible previews.
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

Select a registered file and choose **Find References…** in asset details. The
search reads saved project scenes, entity templates, and the project's startup
scene setting. It lists direct UUID references, including declared document assets.
Select a result and choose **Open** to jump to the document or Project Settings.
Save drafts before searching to include their changes. The search covers UUID
fields and asset declarations in these formats.

Every ordinary asset file receives adjacent `<filename>.slemeta` JSON containing
`format: sle-asset`, `version: 1`, and a UUID `id`. Keep sidecars in version control
and move them with payloads. The `.slemeta` suffix is reserved for metadata, which
is omitted from asset rows. The index is rebuilt from disk; no inventory controls
visibility. Editing a payload keeps its UUID. Moving the complete project, or
moving a payload together with its sidecar, preserves identity.

For a registered image, open **Assets → Details → Texture filter** and choose
**Default**, **Nearest**, or **Linear**. The selection applies to both
magnification and minification of project scene sprites. The editor refreshes
the current preview from its draft, and Play and built games read the saved
setting when they start. Nearest and Linear add an optional
`"texture": {"filter": "nearest"}` or `"texture": {"filter": "linear"}`
to the version 1 sidecar. Default removes the override and inherits the project
art-style sampler or the legacy loader default. Older SLE versions with strict sidecar readers require Default.

### Named sprite regions

Select a registered image in **Assets → Details** and choose **Open spritesheet…**.
In **Grid** mode, enter the cell width and height, horizontal and vertical margins,
and horizontal and vertical spacing in pixels. Each margin applies to both sides
of its axis. Zoom and scroll the checkerboard preview, then choose **Add grid
regions**. Slicing adds complete cells in left-to-right, top-to-bottom order and
skips fully transparent cells by default. Repeated slicing keeps existing regions
and adds new rectangles, preserving the IDs and names that sprites reference.

In **Manual rectangle** mode, select **New region** or an existing region. Drag
over the image to set the bounds or enter the pixel coordinates directly. Enter
a name and choose **Save region**. Use **Delete region…** in the asset details
to remove a region. The editor uses ImageIO for slicing common formats such as
PNG and JPEG.

Assign the texture to a scene sprite and choose a **Sprite region** in the
texture drawer. The sprite stores a texture-scoped `regionId`. The sidecar
stores the corresponding entry in `texture.regions`, with an `id`, `name`,
`x`, `y`, `width`, and `height`. Pixel coordinates start at the image's
top-left. Names and rectangle edits preserve the ID. The scene, project Play,
and packaged game resolve the current rectangle when the scene loads.

Choose **Use UV rectangle** to return to the sprite's normalized UV settings.
If a selected region is missing or extends beyond the loaded image, the sprite
shows the full texture and reports a diagnostic. The reference stays in the
scene so it can be repaired. Older versions with strict sidecar readers require
removing named regions before opening the project.

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

Follow the [tilemap room walkthrough](../../examples/tilemap_room/README.md) to import a PNG, paint a room and export it.

On either editor host, choose **File → Export Game…** and select **Windows x64**,
**Linux x64**, or **Both**. The editor uses a matching bundled runtime when
available and prompts for a prepared Release runtime for each remaining target.
Build each runtime on its target operating system and transfer its complete
bundle to the editor host if needed. For example:

```powershell
cmake --build build/release --config Release --target sle_runtime_bundle --parallel 4
```

```bash
cmake --preset linux-x64-release
cmake --build build/linux-x64-release --target sle_runtime_bundle --parallel 4
```

Select `build/release/runtime/Release` for Windows or
`build/linux-x64-release/runtime/Release` for Linux. Export uses these bundles
offline and requires no compiler. Keep the asset root and bundles separate from
the output. The editor creates `<project>/Exports/<project-folder>` and uses
`-2`, `-3`, and so on for repeat exports.

Choose a startup scene in Project Settings. When documents have unsaved changes,
choose **Save and Export** to save existing project documents or **Export Saved
Versions** to keep your drafts and export the files on disk. Give untitled
documents a project filename first to include them. Export runs in the
background and reports the output folder, package sizes, and any assets grouped
to preserve file-relative lookups.

Each selected target gets a runnable subfolder and a matching download archive:

| Target | Subfolder | Archive | Example launcher |
|---|---|---|---|
| Windows x64 | `windows-x64/` | `windows-x64.zip` | `My Game.exe` |
| Linux x64 | `linux-x64/` | `linux-x64.tar.gz` | `My Game` |

Each runnable folder includes the launcher, runtime files, `config.json`,
`shared.slepkg`, one package per saved scene, and the release and distribution
manifests. Both targets share identical config and package bytes. Copy the
runnable folder or extract the archive, then launch from any working directory.
Extract the Linux archive on Linux to restore the launcher's executable mode
when transferring an export from Windows.

Move or share the complete delivery folder. The launcher discovers its adjacent
config and starts the saved startup scene. Explicit `--project` and `--config`
retain priority. Older folder deliveries continue to launch through their
adjacent `game/game.sleproject` descriptor. Single-package deliveries continue
to use their adjacent `config.json`.

The editor mounts the staged packages with the engine before publication and
checks that the source, runtimes, staged files, and archives match their inventories.
All selected deliveries are published together.
Missing metadata, unresolved known dependencies, changed files, links and
case-insensitive path collisions can fail the export. Use **Refresh Assets** to
assign missing identities. Project-local `.sle` caches and recovery trash
stay in the source project. The no-overwrite publish preserves previous exports.

The Windows x64 Release bundle requires Windows 10 or later and a compatible
OpenGL driver. See [Game packages](GamePackages.md) for ownership and the
delivery format.

## Export and apply a content update

Choose **File → Export Content Update…** and select the folder of the earlier
Windows x64 packaged release, such as the `windows-x64/` subfolder. Use the
same project and exact runtime bundle as that release. Linux x64 supports full
exports only. The baseline needs the release and distribution manifests from
a full automatic package export or an applied update. The same saved-document
choices apply as for a full export.
The editor writes a numbered update folder under the project's `Exports/`
directory when content or launch configuration changes. Use **Export Game…**
for a full delivery when the runtime has changed.

On Windows, deeply nested project folders can exceed native path limits during
update validation. If a missing-package error points into an update staging
folder, move the project closer to the drive root and retry.

Choose **File → Apply Content Update…**, then select the base delivery and the
update folder. Use a clean copy of the original delivery with its manifests
and recorded files intact. The editor verifies the files and mounts the
reconstructed game before publishing a numbered sibling folder. Start the
executable in that folder to play the update. The original delivery remains
available for rollback. See the [content update format](GamePackages.md#content-update-format).

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
fixture with the Export Game menu, both save choices, and both target bundles.
Use `SLE_SMOKE_WINDOWS_RUNTIME` and `SLE_SMOKE_LINUX_RUNTIME` to point to prepared
bundles outside the default build paths. The smoke compares releases and their
package hashes, checks recovery trash and draft preservation, and launches the
archive extracted for the host platform from an unrelated working directory. It also exports a two-scene
tilemap project with a shared template and verifies that a failed export
preserves earlier deliveries. Reports include
`build/reports/project-distribution-editor.png` and
`project-distribution-standalone.log`. Filesystem tests cover source/bundle
changes, custom roots, missing metadata, missing DLLs, staged corruption,
validation failure and concurrent destination arrival.
