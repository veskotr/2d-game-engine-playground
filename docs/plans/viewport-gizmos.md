# Viewport Gizmos Implementation Plan

Follow-up: [independent sprite geometry and outline picking](sprite-picking-and-size.md)
supersedes the original sprite Transform-scaling and filled collider picking
behavior described in this historical implementation record.

**Goal:** Select and drag entity/sprite/collider geometry in the engine viewport,
with inspector synchronization, undo/redo and portable prefab persistence.

**Design:** Editor-owned picking/handles use the actual native camera bounds.
The editor draws handles with its own small GL overlay on the existing canvas.
The native runtime continues rendering scene geometry. Same-entity edits preserve
the camera, while initial/new entity loads and explicit Fit frame the entity.
World/local conversion matches engine radians, rotation, scale and circle radius
(maximum absolute scale). Zero-scale shape editing is disabled. Box edges/corners
resize against the opposite edge and update local offset; circle handles change
radius; sprite handles change entity Transform scale about its origin. Moving
collider centers changes local offsets, while Entity move changes world position.
Selection is available from a menu and by clicking visible shapes. Shift/snap
supports precise edits; arrows nudge, Escape cancels, Ctrl+Z/Y undo/redo. A drag
is one undo transaction. Inspector edits and asset/component changes also enter
history; invalid drafts are retained. New/Open reset history after success.

- [x] Add focused geometry/history tests, implement pure gizmo model and history.
- [x] Expose camera bounds, preserve view during edits and test native behavior.
- [x] Add canvas handles/input and toolbar selection/history controls.
- [x] Verify rotated/scaled edits, cancellation, persistence, desktop dragging,
  normal sandbox input and native teardown. Update editor docs.

Validation: Windows Gradle build/tests and new viewport manipulation smoke;
Windows native debug build and relevant preview tests, broader CTest as needed.
Preserve existing worktree edits. No archive/rendering architecture rewrite.

## Validation and review (2026-11-12)

- Windows CMake debug build completed; full CTest suite 66/66 passed, including
  the preview regression asserting that reload with preserveView retains exact
  camera position/size. Log `/tmp/sle-gizmo-engine-tests.log`.
- Windows Gradle `build`: 24/24 unit tests passed. Geometry coverage includes
  rotated/mirrored box anchors, max-scale circles (including engine radius clamp),
  zero scale, off-center grabs and undo transaction/draft preservation.
  Log `/tmp/sle-gizmo-final-sandbox.log`.
- `run --args=--gizmo-smoke` used actual AWT/Robot mouse drags and keyboard input:
  box resize, collider offset, circle radius, sprite scale, entity move, snap,
  arrows, undo/redo, Escape cancellation and package save/reopen passed. Actual
  desktop handle pixels verified. Log `/tmp/sle-gizmo-final-smoke.log`, screenshot
  `tools/entity-editor/build/reports/gizmo-editor.png`.
- `run --args=--preview-smoke` repeated Entity/Sandbox switching in one context,
  verified real desktop game pixels, preserved drafts, and checked context cleanup.
  Log `/tmp/sle-gizmo-switch-smoke.log`.
- `run --args=--smoke-test` verified sandbox rendering, pause/resume/stop/reset,
  resize and native cleanup after handle integration.
- Independent review found off-center grabs could jump; resize now retains the
  initial grab offset, with a regression. Additional audit fixed Ctrl+Z during
  an active drag undoing the preceding edit as well; a controller test covers it.
- First desktop attempt did not activate the window before the test drag. The
  harness now explicitly activates it. Its failure path was also corrected to
  dispose the native runtime in context before terminating. Diagnostic JVM report
  preserved under the ignored editor build reports directory.

Scope: sprite handles modify entity Transform scale and therefore attached
colliders. Rotation/tint/UV editing remains in the inspector. Undo keeps 100
document/draft/import snapshots and resets on successful New/Open. Zoom is
ignored while dragging; resizing cancels the drag. Handle overlay is editor-owned
and never appears in the standalone engine or sandbox.
