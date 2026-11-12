# Systems Module

`PrefabPackage` reads versioned `.sleprefab` archives, validates their entries and
materializes asset references into a derived disk cache before `PrefabLoader`
expands the ordinary prefab JSON. See [package format](../topics/PrefabPackages.md).
`readScene` and `SceneLoader` likewise support `.slescene`. `SceneAuthoring`
validates flat editable scenes through shared component codecs, while
`Runtime::loadScenePreview` stages a paused full-scene preview. See the
[scene editor](../topics/SceneEditor.md).

## Responsibility

Runtime orchestration and per-frame systems.

## Owns

- `Runtime`
- `Context`
- `SceneManager`
- `SceneLoader`
- standalone `engine_app` bootstrap path via `Runtime::registerSceneFromFile()`
- transform, script, state-machine, animation, audio, physics, render, and UI orchestration systems
- `ScriptApiImpl`

## Dependencies

- Core
- Events
- Platform
- Renderer
- Resources
- Scene
- Physics
- Scripting
- UI

## Important Paths

- `EngineModules/Systems/include/sle/engine/`
- `EngineModules/Systems/src/`
- `EngineModules/Systems/CMakeLists.txt`
- `docs/topics/Runtime.md`

Chunk activation uses independent runtime subsystem gates; see [Chunks](../topics/Chunks.md).

SceneLoader and ScriptApiImpl share per-component validation/defaults through
`ComponentRegistry`. SceneComponentAdapter only translates legacy JSON syntax;
Runtime retains native mutation hooks. ComponentAssets normalizes canonical asset
paths for both frontends. See [component rules](../topics/LuaComponents.md).

SceneLoader allocates JSON entities before resolving keyed parent and animator
references. Legacy numeric references are 1-based JSON file positions, remapped on
every load. Invalid links fail staging; see [scene authoring](../topics/Scenes.md).

## Rules

- Systems coordinate modules through `Context`, public APIs, and registry views.
- Keep frame order documented. Resolve world transforms after simulation writes
  and before rendering/UI; physics consumes local dirty flags before this pass.
- Keep the JSON scene/bootstrap path documented when runtime startup changes.
- File-registered scenes load into staging before replacing the live scene.
  Return parse/I/O/type errors to the caller; failures preserve active entities,
  hierarchy, scene name, and event state. Existing `void(Runtime&)` C++ builders
  retain their imperative behavior and do not have this rollback guarantee.
- Add tests for behavior that crosses module boundaries.
- Commit-time Lua teardown follows successful staging, with candidate entity IDs
  reserved before destroy callbacks and the live allocation cursor retained.
- Runtime releases UI caches/default shader references before window/context teardown.

## Update This File When

- Runtime ownership, frame order, scene loading, or system integration changes.

PrefabLoader expands namespaced entity subtrees and component overrides through
SceneLoader::loadData, which also loads top-level scene-root components. Dynamic
instantiation validates before appending and rolls back only new entities on error.
ScriptSystem snapshots and sorts named attachments, initializes due work before
updates, and rechecks lifetime/generation before callbacks.

## Embedded editor preview

`Runtime::initEmbedded()` borrows a host-owned current OpenGL 4.3+ context with
GLAD already loaded. `renderEmbeddedFrame(width, height)` takes physical pixels,
resolves transforms, and renders sprites/debug geometry without advancing
simulation, executing UI behaviors, polling window events, or presenting buffers.
Cached/prepared XML UI can still be drawn. The sandbox camera retains configured
logical dimensions; entity preview frames its authored geometry. All graphics calls and Runtime
destruction require the host context to remain current on its owner thread.
Standalone `init()`/`run()` retain GLFW ownership. The optional Windows editor JNI
target and setup are documented in [the editor README](../../tools/entity-editor/README.md).


The editor's default hosted sandbox now uses `tickEmbeddedFrame()` to execute the
same system pipeline as the standalone application. Input is supplied through
Platform's host event methods, and `setEmbeddedPaused()` freezes gameplay and the
audio engine. The render-only API remains for paused/stopped presentation. Stop
recreates the Runtime rather than saving transient simulation state to authored
assets. Hosted gameplay, UI and pause regressions are covered by
`integration_runtime_embedded_play_path`.


## Entity authoring

`EntityAuthoring` exposes writable component defaults/enum choices, validates
single-entity prefab JSON, and normalizes accepted legacy syntax plus omitted
defaults for editing. It reuses ComponentRegistry and SceneComponentAdapter;
WorldTransform/ChunkMap and derived/native fields are not offered as authorable
components. Authoring metadata/validation do not require a Lua VM or GL context.
The Kotlin editor owns document state and atomic file saves; no runtime snapshot
is serialized. See [entity editor](../../tools/entity-editor/README.md).


`EntityPreviewSystem` owns preview camera framing and grid/origin/sprite-bound
render commands. `Runtime::loadEntityPreview` uses validated staged in-memory JSON
rather than a file round trip; `renderEntityPreview` never advances gameplay.
The desktop inspector generates UUID keys for new documents and exposes only the
name; legacy keys remain valid and are preserved on open. Preview edits suppress
scene gameplay lifecycle notifications so paused editing cannot queue an event
backlog. `integration_entity_preview_path` exercises pixels, edits and isolation.
