# Hierarchy, camera entities and current-scene play

## Goal

Implement the approved sequence: parent/child authoring, Camera2D entities and
viewport outlines, independent Scene/Game views, Play/Pause/Stop of the authored
scene. Stop restores authoring; runtime changes are never saved into the document.

## Contracts

- Existing scene/prefab packages remain compatible. Scene JSON now accepts entity
  `parent` keyed links; validate missing links and cycles before mutations.
  Engine local/world Transform composition remains authoritative.
- New data component `Camera2D` (`Camera2DComponent`): `enabled=true`,
  `primary=true`, `viewSize={x:480,y:270}`, `zoom=1`. Positive finite view size and
  zoom. Camera transform is view center; world rotation is inherited. Transform
  scale does not multiply view size, so cameras can attach to scaled actors.
  Lowest runtime entity ID among enabled primary cameras wins deterministically;
  old scenes without camera entities retain the legacy runtime camera.
- Camera system runs after world transforms and before rendering. Editor Scene
  camera is separate from the game camera; scene navigation never changes game
  Camera2D fields. Game view letterboxes to authored aspect, no editor gizmos.
- Scene view shows camera rectangles; tree contains cameras as normal entities.
  Parent selector and Add Child support attachment. Reparent preserves world pose
  and rejects zero-scale inverse/cycles; duplicate/delete operate on subtrees.
- Play loads a snapshot through normal runtime systems, including scripts/physics/
  animation/UI/audio. Pause retains runtime state; Stop recreates runtime and
  reloads authoring. Editing is locked in Play/Pause. Mode exit stops scene play.
  Game input is focus-gated; Scene view retains editor pan/zoom during play.

## Ownership / interfaces

Native worker owns C++/JNI/component tests. Kotlin hierarchy worker owns
SceneDocument/SceneWorkspaceState/SceneTree/ScenePicking/SceneWorkspace and world
geometry/controller support. Root owns Main/EngineCanvas/SharedViewport, view/play
controls and integration. Coordinate fixed JNI contract before implementation.

## Steps

- [x] User steering: reorganize Kotlin source/tests into app, ui, model, files,
  viewport, bridge and smoke packages first; update launch class/JNI names, compile
  and validate before continuing feature integration. No behavior change in move.
- [x] Hierarchy data, validation, subtree operations, world-aware picking/dragging,
  tree navigation and parent UI; focused unit tests.
- [x] Native Camera2D codec/system, camera outlines, Scene/Game rendering, editor
  navigation and scene-play host APIs; native regression tests.
- [x] Wire Scene/Game + Play/Pause/Stop controls and document locking; scene input,
  editor pan/zoom, stop/mode-exit reset and camera editing.
  User layout steering: hierarchy on the left, viewport center, entity/component
  inspector on the right. Keep compact UI scaling.
- [x] End-to-end tests: camera child follows parent, Game view framed correctly,
  real scripts/physics run, pause frozen, stop restores authored transforms and
  asset refs, save/reopen hierarchy and backwards-compatible prefab editing.
- [x] Update docs, build both projects, run focused then full required validation,
  independently review and reopen the starter scene with a camera example.

Preserve all prior worktree edits and unsaved user changes. No direct Scene
queries in Renderer or lower-module orchestration. No arbitrary runtime snapshot
serialization. Camera follow smoothing and multiple split-screen outputs deferred.

## Verification (2026-11-12)

- Windows Debug native rebuild completed; CTest: 69/69 passed, no skips.
  Includes Camera2D codecs/hierarchy, scene gameplay and standalone GLFW
  letterbox/HiDPI input regression coverage.
- Windows JDK 21 Gradle `build`: 47 Kotlin tests passed.
- `run --args=--camera-smoke`: passed actual desktop hierarchy/pan/framing,
  child-camera follow, Lua keyboard input, physics, pause/resume/stop and
  save/reopen checks. First presented frame: 886 ms in this run; deferred
  audio initialization: 6093 ms (device-dependent and after presentation).
- `run --args=--scene-smoke`: passed prefab copies, entity selection/movement,
  structure/undo, invalid hidden drafts, portable save/relocate/open, UI scale,
  component handoff and sandbox switching. Sandbox scene load: 26 ms.
- Independent review found and resolved stale inspector drafts after reparenting
  and standalone cursor mapping through camera letterboxing. Focused regressions
  exercise each fix.

- `run --args=--gizmo-smoke`: passed actual desktop picking, box/circle handles,
  independent sprite editing, move/snap/nudge, undo/redo/cancel and save/open.

The portable camera example is `assets/scenes/editor_camera_demo.slescene`;
its standalone configuration is `examples/editor_camera/engine.json`.
Linked prefabs, unified structural/component history, split-screen cameras and
runtime state writeback remain outside this change.
