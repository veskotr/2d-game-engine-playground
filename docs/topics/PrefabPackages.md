# Prefab packages

A `.sleprefab` is a portable, self-contained prefab ZIP, accepted anywhere
`PrefabLoader` accepts a prefab file, including a scene's `prefabs[].file`.
Existing JSON prefabs remain supported. The prefab workspace edits one entity;
the package container itself does not impose that entity-count restriction.
Self-contained [scene packages](SceneEditor.md) reuse the same machinery with
extension `.slescene`, manifest format `sle-scene`, and `scene.json` in place of
`entity.json`. Prefab and scene readers reject each other's document format.

## Editor workflow

Add SpriteRenderer and use **Browse** to import a texture. The editor copies the
image into its working storage immediately, so the original can be moved or
removed. The viewport renders that copy through the engine. Use **Import Asset**
for additional files of any type; unused imports are retained in the package.
**Unlist** removes an explicit import from the list; a component's referenced
assets are still included automatically. Clear a texture to detach it.

**Save As** defaults to `.sleprefab`; type a name and the extension is added.
Opening a package restores the entity and all imported files. Renaming or moving
the package does not change its internal references. Assets with identical
payloads are deduplicated; their first imported display name is retained.

The JSON file type exports a conventional JSON prefab and copies assets into a
sibling `<filename>.assets` directory. JSON uses absolute paths under the legacy
loader rules and does not retain an unused-import list when reopened. Use the
package format when moving or sharing content. Failed validation or package
creation does not replace the previous prefab file.

## Version 1 format

ZIP uses standard **STORED** entries (no compression/encryption/ZIP64):

```text
manifest.json
entity.json
assets/<sha256>[.<extension>]
```

```json
{
  "format": "sle-prefab",
  "version": 1,
  "assets": [
    {"id": "<64 lowercase hexadecimal characters>",
     "path": "assets/<id>.png", "name": "Player.png"}
  ]
}
```

`entity.json` uses the ordinary prefab schema. Asset references are strings such
as `asset://<id>`. Manifest names are display metadata; extraction paths come
exclusively from validated IDs and archive entry paths. Every asset is listed,
including unused imported files. Unknown versions are rejected. The container
can hold textures, Lua, audio, animation/definition JSON, UI, fonts and arbitrary
additional files; a file's runtime support still depends on its existing loader.

Recognized file fields are `texture`, `scriptAsset` (including behaviours),
`assetPath`, `layoutAsset`, `fontAsset`, `behaviorAsset`, `definitionAsset`,
`clipAsset` and `stateClipMap` values. These are bundled automatically. The same
fields inside JSON assets recursively import dependencies, resolving relative to
that JSON file first, then the editor working directory. Cyclic dependencies are
rejected. Ordinary JSON strings are preserved. Lua expressions, `require`, XML
links and other dynamic or custom dependency formats are not analyzed. Explicit
extra imports preserve their bytes but do not automatically connect those
formats to asset IDs or make filesystem-relative script imports work.

Both readers enforce 4096 total entries, 128 MiB per entry, 512 MiB total archive
and payload sizes, and 4 MiB each for entity/manifest JSON. They reject duplicate
entries/IDs, missing references, unsafe paths, unsupported compression and CRC
mismatches. The editor additionally verifies asset SHA-256 IDs against payloads.
Package save atomically replaces the destination after completing the archive.

## Engine materialization

`PrefabPackage::read(path)` returns JSON with asset IDs resolved to local files.
`PrefabLoader` dispatches `.sleprefab` through this reader before ordinary prefab
expansion, validation and scene staging. Renderers and lower modules remain
unaware of archives. Scripts, audio and other resources use their existing file
APIs. JSON asset references are resolved before materialization as well.

The engine publishes an immutable cache directory beneath the OS temporary
folder's `sle-prefab-cache`, keyed by archive content. Complete directories are
published atomically; existing payloads are verified before reuse. Files remain
available for deferred resource loads. This derived cache can be removed when
engine processes are closed; automatic eviction is not implemented. The editor
uses process-scoped working copies, removed on normal JVM exit. The saved package
is always the authoritative copy.
