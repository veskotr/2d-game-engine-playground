# Game packages and built games

**File → Build Game…** turns a saved project into a runnable folder where the
game content lives in `.slepkg` packages and the engine reads a small
`config.json`:

```text
My Build/
  My Game.exe             # the engine executable, named after the project
  *.dll                   # Release C++ runtime and dependency libraries
  assets/                 # engine shaders, UI and fonts
  notices/                # dependency license notices
  config.json             # engine config: window, targetFPS, packages
  game.slepkg             # all project assets, scenes, prefabs and the main scene
  sle-distribution.json   # delivery file hashes
```

Double-click `My Game.exe`. Build Game copies the prebuilt engine executable
(`engine_app.exe`) and renames it after the project. Characters that Windows forbids
in filenames become `_`, and device names such as `CON` get a trailing `_`. A name
that would replace another runtime file is refused. The executable icon is still the
default. The runtime bundle's own `sle-runtime.json` and `RUNNING.txt` are left out.
No compiler is involved: a game is data, and the engine is prebuilt.

With no options the executable uses the
`config.json` beside it, independently of the working directory. Explicit
`--config`/`--project` still take precedence. A folder delivery from
**Export Game…** (`game/game.sleproject`) is still discovered first.
Use `--frames N` for a bounded diagnostic run.

## Building

The packaged editor ships the prebuilt Windows x64 Release runtime in its resources
(`runtime/`), so Build Game and Export Game use it without asking. Editors run from
the repository (`gradlew run`) have no bundled runtime. They ask for a prepared one,
built once with ([runnable export](Projects.md#export-a-runnable-game) describes it):

```powershell
cmake --build build/debug --config Release --target sle_runtime_bundle --parallel 4
```

Select a startup scene in Project Settings and save your work. Then choose
**File → Build Game…** and a **new** output folder outside the project and the
runtime bundle. Build uses saved files only:
unsaved scene/prefab drafts stay in the editor and are excluded.

The build runs on a worker. It stages the runtime, `game.slepkg` and `config.json` in
a sibling `.sle-build-*` folder. The engine then mounts that staged package exactly as the
delivered game will. Next the build checks that sources, the runtime and the staged files
did not change, and publishes with a no-overwrite move. Failures leave no destination
folder. The same preconditions as Export Game apply: every asset needs its
`.slemeta` identity (Refresh Assets first), links and case-insensitive name collisions
are rejected, and source files are never modified. Identical sources produce a
byte-identical package.

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

The editor currently writes one `game.slepkg` per build. Additional packages can come
from other builds of separate projects. Add them to `config.json` by hand.

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

- One package per editor build. Splitting a project into several packages is not
  automated yet.
- STORED only: package size equals the sum of the asset sizes.
- Windows x64 Release runtime bundles only, as with Export Game. Linux editors
  ship no runtime yet, because `cmake/BuildRuntimeBundle.cmake` is Windows-only.
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
