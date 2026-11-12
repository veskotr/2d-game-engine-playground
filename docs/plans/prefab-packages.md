# Prefab Packages Implementation Plan

**Goal:** Move a prefab and its assets as one `.sleprefab` file, editable in the
desktop editor and loadable through the engine's existing PrefabLoader.

**Design:** The user's approved ZIP package proposal contains `entity.json`, a
versioned `manifest.json`, and `assets/<content-id>.<extension>`. JSON references
use `asset://<id>`. Version 1 uses standard ZIP STORED entries, CRC validation,
and bounded extraction into a content cache. Existing JSON prefabs remain valid.
An extensible manifest retains all imported files, including unused extras.
Component file references are bundled on save; recognized file fields inside
JSON assets recursively resolve dependencies. Ordinary strings are preserved. Arbitrary Lua string expressions
and XML dependencies are not inferred; additional files can be imported explicitly.
Plain JSON export writes its assets alongside it, rather than cache paths.

**Constraints:** Preserve all existing edits. No lower-module orchestration
dependency. Reject unsupported versions, compression, unsafe archive paths,
duplicates, corrupt data and missing asset IDs before changing the editor/scene.
Save atomically. Bound archive size/count and uncompressed bytes. No new library
dependency is needed: Java ZIP and a bounded native STORED ZIP reader suffice.

## Steps

- [x] Add Kotlin package codec, cache/import ownership and focused tests for
  relocation, extras, deduplication, corruption, missing references and traversal.
- [x] Integrate package open/save, default package chooser, texture import and
  generic additional asset import; preserve JSON open/export.
- [x] Add native package reader and PrefabLoader dispatch, with integration tests
  exercising an editor-produced package after relocation and original removal.
- [x] Validate editor build/tests, native relevant tests and actual textured
  viewport after package reopen. Document format, usage and limitations.

## Validation

Windows Gradle wrapper: `build`, `run --args=--authoring-smoke`,
`run --args=--authoring-ui-smoke`. Native CMake debug build, focused package and
authoring CTest cases, then full suite if feasible. Record actual results below.

## Progress

Implemented and independently reviewed. Review found and corrected ordinary
strings mistaken for files/references, and missing writer metadata limits.
Regression cases include literal asset-like entity/state names, a file named
`Idle` beside a state definition, nested JSON dependencies, deduplication,
relocation, removed originals, corrupt data, unsupported versions/compression,
missing references and preserving existing files on failed saves.

Current validation (2026-11-12): Windows CMake debug build; full CTest suite
66/66 passed, including 10 windowed tests. Windows Gradle `build`: 17/17 unit
tests passed. Final `run --args=--authoring-smoke` passed package relocation,
original removal, reopen and resave. Native package test read that exact editor
output successfully. Final desktop preview passed actual texture/collider
pixel checks after package save/reopen and verified context cleanup.
Logs: `/tmp/sle-package-final-native-tests.log`,
`/tmp/sle-package-final-editor2.log`, `/tmp/sle-package-final-save-load.log`,
`/tmp/sle-package-final-cross-language.log`, `/tmp/sle-package-final-desktop.log`.

Cache is derived data, never the authoritative saved prefab. Package imports
remain readable without original files. Unknown versions are rejected. Version
1 is intentionally uncompressed and bounded; dynamic Lua/XML/custom dependencies
are not automatically wired by importing extra files. Native persistent cache
eviction is future work. Plain JSON export copies sidecar assets and retains
legacy absolute references, while packages are portable.
