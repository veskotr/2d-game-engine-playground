# Game packages and built games

**File → Export Game…** builds a runnable delivery from saved project files.
The editor assigns content to `.slepkg` packages and writes a `config.json` that
mounts them. See [runnable export](Projects.md#export-a-runnable-game) for the
editor workflow.

```text
windows-x64/
  My Game.exe             # the engine executable, named after the project
  *.dll                   # Release C++ runtime and dependency libraries
  assets/                 # engine shaders, UI and fonts
  notices/                # dependency license notices
  config.json             # engine config: window, targetFPS, packages
  shared.slepkg           # common, dynamic and unreferenced assets
  scene-<uuid>.slepkg     # one per saved scene
  sle-release.json        # runtime identity, package hashes and asset ownership
  sle-distribution.json   # delivery file hashes
```

The Windows x64 delivery uses `My Game.exe` and the Linux x64 delivery uses
`My Game`. Export copies the prebuilt engine launcher and renames it after the
project. Characters that Windows forbids
in filenames become `_`, and device names such as `CON` get a trailing `_`. A name
that would replace another runtime file is refused. The executable icon is still the
default. The runtime bundle's own `sle-runtime.json` and `RUNNING.txt` are left out.
No compiler is involved: a game is data, and the engine is prebuilt.

With no options the executable uses the
`config.json` beside it, independently of the working directory. Explicit
`--config`/`--project` still take precedence. An older folder delivery
with `game/game.sleproject` is still discovered first.
Use `--frames N` for a bounded diagnostic run.

## Building

Choose Windows x64, Linux x64, or Both. A prepared `sle_runtime_bundle` must
match each selected target. Packaged builds require a versioned runtime with
package support. Native tile layers require SLE 0.33 or newer. The editor uses
bundled runtimes when available
and prompts for the others. Both builds share identical config and package
bytes. Each target has its own release inventory with its target and runtime
hash. The numbered export directory contains the runnable folders and their
ZIP (Windows) or TAR.GZ (Linux) archives. Extract the Linux archive on Linux
to preserve the launcher's executable mode. See [runnable export](Projects.md#export-a-runnable-game).

A saved scene gets a package named for its UUID. That filename and package ID
survive scene renames. An asset reached by exactly one scene normally goes into
that scene's package. Assets used by several scenes, Lua scripts and
unreferenced assets go into Shared. The planner follows references through entity
templates, overrides and tile resources, keeps sidecars with payloads and groups
files that must resolve relative to one another. The startup package supplies the
main scene and project-wide runtime settings.

The build stages the runtime, packages and config in a sibling `.sle-build-*`
folder. The engine mounts the staged package set before the editor rechecks
source, runtime and staged files and publishes without overwriting. Every asset
needs a `.slemeta` identity. Use **Refresh Assets** if metadata is missing.
The runtime continues to support single-package builds such as the example below.

## `config.json`

```json
{
  "window": {"title": "My Game", "width": 1280, "height": 720, "vsync": true, "mode": "Windowed"},
  "targetFPS": 0,
  "packages": ["game.slepkg"]
}
```

This is ordinary engine configuration: the same `window` object as `engine.json`,
plus `targetFPS` (nonnegative, zero means uncapped). `packages` is a non-empty list
of package paths. Relative paths resolve beside the config file, with no
working-directory search. A config that has `packages` must not contain `startScene` or
`scenes`. Scene data and the main scene come from the packages. Existing
`engine.json` files with `startScene`/`scenes` keep working unchanged.

## Multiple packages

`packages` may list several files, for example a base game plus a level or DLC pack.
They are mounted in order into one namespace:

- **Main scene:** the first listed package whose manifest has a non-null `mainScene`
  provides the startup scene. The `mainScene` of later packages is ignored.
- **UUIDs:** `project-asset://<UUID>` references resolve across packages. A UUID or
  scene key that appears in two packages is an error.
- **Relative paths** (dynamic Lua asset paths, `Engine.switchScene` keys): the first
  package containing the file wins.
- **Lua `require`:** searches `Scripts/` then the asset root of each package, in
  package order.

Export Game writes `shared.slepkg` followed
by `scene-<UUID>.slepkg` files in scene UUID order. Package IDs derive from the
project UUID and the package owner. Scene renames preserve IDs and filenames.
The startup package declares `mainScene` and carries the project gameplay settings.
Each package receives the same project texture default. Asset sidecars and files
that require physical colocation travel with their owners.

The staged delivery includes `sle-release.json` (version 1). It records the project
UUID, runtime bundle manifest SHA-256, launch config SHA-256, ordered package IDs
and paths with their SHA-256 digests, and the owner package ID for every asset UUID.
The native bridge mounts the staged config before the build publishes the folder.
Identical saved inputs and prior ownership produce identical package and release
bytes. The original `GameBuild.build` entry point still produces one `game.slepkg`.

## Content update format

A content update contains `sle-content-update.json` (version 1), the target
`config.json`, `sle-release.json`, `sle-distribution.json`, and whole archives
for new or changed package paths. The update manifest records SHA-256 digests
of the base release and distribution manifests, digests of the update files,
and the obsolete package paths. A config-only update carries the manifests
and config. Identical content and launch configuration produce no update.

The selected release supplies the previous asset owners. Shared ownership
persists, and a private asset promoted to Shared changes both archives in the
same update. Apply reconstructs the target from verified runtime and unchanged
packages in the base plus the update files. It verifies the resulting digests,
checks the config against the target inventory, mounts the packages through
the native bridge, and publishes the sibling delivery. Obsolete archives are
absent from that delivery. The base stays available as exported.

Use a delivery with both versioned manifests and all files intact as the
baseline. To start an update chain for an older loose or single-package
delivery without a release manifest, export a new full game first.

## Package format (version 1)

A `.slepkg` is a ZIP with **STORED** entries only: no compression, encryption,
ZIP64 or data descriptors. It contains:

```text
manifest.json
assets/<path relative to the project asset root>   # every file, including .slemeta
```

```json
{"format": "sle-package", "version": 1,
 "id": "<project UUID>", "name": "My Game", "mainScene": "<scene UUID or null>"}
```

The optional `settings.artStyle` field carries the exported project's preset
(`pixelArt` or `smooth`). It applies to textures from that package whose
sidecars omit a sampler override. Packages without the setting use the legacy
loader default. The package that selects the main scene supplies
`settings.collisionLayers` for the entire run, including scenes from later
packages. The layout matches the [project descriptor](Projects.md#collision-layers).
Manifest settings can also include [sorting layer labels](Projects.md#sorting-layers).
The startup package supplies [keyboard input actions](Projects.md#keyboard-input-actions)
for all scenes in the run. The manifest uses the same `settings.inputActions` field
as the source project.
Sprite draw order continues to use the numeric layer stored on each sprite.
Unknown manifest fields and versions are rejected. Entry paths must be portable:
no `..`, empty or `.` segments, backslashes, drive letters, control characters,
characters from `:*?"<>|`, trailing dots or spaces, or Windows device names
(`CON`, `NUL`, `COM1`, …). Names that collide case-insensitively, or that are both a
file and a folder, are rejected. Limits are 65000 entries, 256 MiB per file and
1 GiB in total. CRC-32 is checked for every entry.

## Engine mounting

`ProjectRuntime::openPackages` validates each archive completely before writing
anything. It then unpacks the archive into an immutable, content-keyed cache in
`<temp>/sle-package-cache/<key>`, published atomically. Later launches reuse the cache
only if it contains exactly the packaged files and bytes. If a cache was changed or
damaged, the engine uses a private extraction under `<temp>/sle-package-runtime/` that
is removed on release. The shared cache is never repaired in place. Unpacked roots are
indexed by the same code as folder projects: scene/prefab envelopes, UUID sidecars,
scene keys, derived runtime prefabs (written to a per-launch temporary folder) and
Lua module paths. Shared caches are disposable and can be deleted while no game is
running. Automatic eviction is not implemented.

## Limits

- Each planned archive must fit the version 1 limits. Export reports any archive
  that exceeds them.
- STORED entries keep the original asset bytes with ZIP headers and a manifest.
- Content updates require a Windows x64 delivery. Use a full export for Linux x64.
- The built executable uses the default icon.
- No encryption, signing or streaming. Packages are unpacked to disk because
  textures, audio and Lua load from files.

## Validation

- `integration_game_package_path`: config contract, two-package mounting,
  cross-package UUIDs and relative paths, first-main-scene rule, cache reuse,
  tampered cache fallback/cleanup, and rejection of traversal, case collisions,
  reserved names, file/folder collisions, missing manifest, future version,
  unknown fields, duplicate UUIDs and CRC corruption.
- `integration_package_discovery_path`: `engine_app` with no options uses the
  `config.json` beside it from an unrelated cwd, and `--config` keeps precedence.
- `integration_package_launch_path` (windowed): runs 8 frames of a two-package game
  in which Lua requires a module from the second package and switches scenes.
- `GameBuildTest` (editor): package/config contents, reproducible output, local
  state exclusion, validation failure, concurrent edits, staged tampering, occupied
  destinations, missing metadata/startup and non-portable names.


The startup package supplies `settings.tags` for Lua tag queries across all
mounted scenes. Scene and prefab memberships retain their IDs when the
startup package has no matching definition. See [entity tags](Projects.md#entity-tags).
- `GameContentUpdateTest` (editor) covers scene and Shared edits, ownership
  promotion, obsolete packages, config-only updates, unchanged builds, damaged
  inputs and failed staging.
