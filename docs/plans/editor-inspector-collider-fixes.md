# Inspector layout and collider authoring fixes

Status: implemented and automated validation passed; manual coverage gaps listed below. User project assets unchanged.
Date: 2026-11-26.

## Goal and acceptance criteria

Make Details readable and make collider authoring match actual physics geometry.
The reported project is `C:\Users\veselin.trichkov\Documents\test`; use a
temporary copy for reproduction and interactive checks.

- Vector axes and scalar inputs share a consistent value-column boundary.
  Wide inspectors show compact axis inputs; narrow inspectors stack without
  clipping fields, labels, errors, or action buttons.
- Idle numeric fields show readable values, including friction `0.4` instead of
  `0.4000000059604645`. Focusing and leaving a field without typing creates no
  edit, rounding, dirty state, or undo entry.
- Fit on a sprite of size `(1, 1)` with Transform scale `(32, 32)` produces a
  box collider of size `(32, 32)`, with correctly transformed offsets.
- Runtime debug outlines and editor shape handles describe the same geometry
  that physics uses, including parent scale and mirrored transforms.
- Existing scenes retain their authored values until an explicit edit or Fit.

## Evidence and constraints

The supplied scene's player has sprite size `(1, 1)`, Transform scale `(32, 32)`,
and collider size `(1, 1)`. Its platform also has independently authored sprite
and collider dimensions and offsets. A discrepancy there is not evidence that
the editor should automatically overwrite authored collision geometry.

Relevant current code:

- `tools/editor/src/main/kotlin/sle/editor/ui/inspector/EditorPropertyRenderer.kt`
  wraps vector children in recursive full property rows, introducing another
  proportional label column inside the existing value column.
- `tools/editor/src/main/kotlin/sle/editor/ui/inspector/PropertyDrawers.kt`
  resolves vector axes, captions, and grouping. Preserve these metadata rules.
- `tools/editor/src/main/kotlin/sle/editor/ui/controls/EditorControls.kt`
  supplies property rows, numeric inputs, and an existing compact vector pattern.
- `tools/editor/src/main/kotlin/sle/editor/ui/controls/NumberEdit.kt` and
  `model/EntityWorkspaceState.kt` own numeric drafts and edit transactions.
  Precision metadata currently formats generated values, not ordinary idle text.
- `tools/editor/src/main/kotlin/sle/editor/ui/EditorContextMenu.kt` contains
  `fitShapeToSprite`, which copies sprite size and offset without own scale.
- `EngineModules/Systems/src/PhysicsSystem.cpp` applies ancestor scale to
  explicit collider dimensions; the body's own scale does not multiply them.
- `EngineModules/Systems/src/RenderSystem.cpp` passes full WorldTransform scale
  to collider/zone outlines, unlike physics.
- `tools/editor-native/EditorBridge.cpp` owns native editor geometry; inspect its
  shape picking, drawing, and drag conversion together before changing handles.

Follow `AGENTS.md`, `docs/CONSTRAINTS.md`, `tests/AGENTS.md`, and editor UI tokens.
Keep engine physics semantics and scene formats compatible. Preserve existing
unrelated documentation edits. Do not edit the user's external project as part
of implementing these repository fixes.

## Recommended approach and alternatives

Fix presentation and authoring against the current explicit collider-unit
contract. Reuse shared controls and owner-managed edits. This addresses the
reported behavior without migrating scenes.

Changing physics to apply own scale would make some initial authoring intuitive,
but would resize existing explicit colliders and require scene migration. A
project-only repair would correct this example while leaving Fit and misleading
outlines broken. Neither is recommended for this work.

## Ordered implementation

### 1. Repair Details layout

Files: `ui/inspector/EditorPropertyRenderer.kt`,
`ui/inspector/PropertyDrawers.kt`, and shared controls only where necessary.
Paths in this section are relative to `tools/editor/src/main/kotlin/sle/editor/`.

- [x] Render one outer property label, with short axis labels inside its value
  area; avoid recursively allocating another full property label fraction.
- [x] Choose wrapping from the available value width and existing metrics.
  Two-axis vectors should fit side by side when space allows; narrow layouts
  and four-channel values may wrap. Keep numeric editors filling available width.
- [x] Keep field-specific drawers, read-only axes, visibility, groups, error
  messages, per-axis menus, override indicators, and project-defined captions.
  Use the existing recursive path for axes needing specialized controls.
- [x] Place collision filter value and Layers action together when space allows,
  wrapping the action below at narrow widths. Keep actions reachable by keyboard.
- [x] Keep property projection unchanged and run `InspectorPropertiesTest.kt`;
  use the inspector gallery for actual alignment and sizing verification rather
  than source-text assertions or tests duplicating Compose layout code.

### 2. Format idle numeric values without mutating data

Files: `ui/controls/NumberEdit.kt`, `ui/controls/EditorControls.kt`,
`ui/inspector/EditorPropertyRenderer.kt`.
Tests: `tools/editor/src/test/kotlin/sle/editor/ui/controls/NumberEditTest.kt`.

- [x] First add failing tests for idle formatting, focus/blur without typing,
  Escape, invalid drafts, undo/redo, and incoming runtime value changes.
- [x] Add a presentation-only formatter. Honor explicit `ui.precision`; use six
  significant digits as the proposed fallback for ordinary floating-point rows.
  Strip insignificant trailing zeros, normalize displayed negative zero, and
  retain scientific notation for very small or large values. Integers stay exact.
- [x] On focus show the original editable token; while editing preserve entered
  precision, exponent spelling, caret, and incomplete drafts. Only actual user
  edits may enter the document/history. Never write the formatted label back.
- [x] Test `0.4000000059604645` displaying as `0.4`, a tiny nonzero value remaining
  nonzero, large values staying finite, integer masks staying unchanged, and a
  high-precision typed value surviving commit and reload unchanged by presentation.
- [x] Run the focused numeric suite red before implementation and green after it.

### 3. Correct Fit and component creation

Files: `ui/EditorContextMenu.kt`, `ui/EntityWorkspace.kt`, and a focused model
helper for fit calculations if needed. Add
`tools/editor/src/test/kotlin/sle/editor/ui/ShapeFitTest.kt`.

- [x] Add failing tests through `fitShapeToSprite(state, type)` for the supplied
  `(1, 1)` sprite / `(32, 32)` scale example and identity-scale compatibility.
- [x] For boxes and box zones calculate size as
  `abs(sprite.size * ownTransform.scale)` and offset as
  `sprite.offset * ownTransform.scale`. Missing Transform means identity scale.
  Do not bake ancestor scale or rotation into these authored values.
- [x] For circle colliders/zones preserve the existing enclosing-circle policy:
  radius is half the larger absolute scaled sprite dimension; offset uses the
  same signed conversion. Do not introduce an ellipse component or schema change.
- [x] Validate all calculated values before mutation. Reject nonfinite or zero
  dimensions with an actionable editor notice; preserve the previous shape.
- [x] Fit size/radius and offset as one undoable operation. Adding a shape with
  automatic Fit should also undo as one component-add action. Respect read-only
  and playback restrictions, including delayed or stale callbacks.
- [x] Cover nonuniform/negative scale, nonzero offsets, absent sprite/Transform,
  unsupported component types, invalid geometry, undo/redo, and add-component Fit.
  Verify two differently scaled parents do not change the fitted local values.
- [x] Explain collider units and Fit in tooltips and
  `docs/topics/EditorPropertySchema.md` or the relevant SceneEditor section.

### 4. Align debug outlines and editor handles with physics

Files: `EngineModules/Systems/src/RenderSystem.cpp`,
`tools/editor-native/EditorBridge.cpp`, and relevant geometry helpers discovered
there. Tests: extend `tests/integration/systems/integration_hierarchy_physics_path.cpp`
where its harness fits, or register a focused geometry integration target.

- [x] Trace native selection bounds, outline generation, and shape drag conversion
  before editing. Record which paths already honor explicit collider units.
- [x] Add regression coverage demonstrating full own scale being applied to the
  current debug box although physics uses only ancestor scale.
- [x] Compose body world position/rotation with parent scale for collision shapes;
  obtain parent scale directly rather than dividing world scale by own scale.
  Keep sprite rendering on full visual scale.
- [x] Match physics for signed offsets, mirrored parents, nonuniform circles,
  and ellipse polygon approximation. Preserve activity gates; account for shapes
  suspended because geometry is invalid rather than implying an active fixture.
- [x] Make editor pick bounds and drag conversion follow that same contract.
  Verify draw/pick/drag agree before and after Fit, reparenting, and undo.
- [x] Cover root and nested boxes/circles/zones, own scale 32, negative/zero scale,
  nonuniform ancestor scale, rotation, and transformless hierarchy nodes.
  Keep lower modules free of Systems dependencies.
- [x] Update `docs/topics/Physics.md` to document outline consistency and authoring
  expectations. Apply patch-version bumps to changed engine/editor products per
  `CONTRIBUTING.md`, based on versions at implementation time.

Implementation coverage note: property projection did not change, so no
InspectorPropertiesTest cases were added; its existing suite passed. Responsive
layout was checked with the actual inspector gallery. The implementation tests
cover the geometry and transaction contracts; the manual combinations listed
below remain unverified.

## Validation and completion

Commands below are planned, not executed. Use the repository's Windows toolchain
for native builds and desktop checks; Linux checks can supplement it.

From `tools/editor`:

```powershell
.\gradlew.bat test --tests "sle.editor.ui.controls.NumberEditTest" --tests "sle.editor.ui.inspector.InspectorPropertiesTest" --tests "sle.editor.ui.ShapeFitTest"
.\gradlew.bat build
.\gradlew.bat run --args=--inspector-gallery
.\gradlew.bat run --args=--gizmo-smoke
```

Both desktop arguments are present in `app/Main.kt`. From root:

```powershell
cmake --build build/debug --config Debug --target sle_editor_native integration_hierarchy_physics_path
ctest --test-dir build/debug -C Debug -R '^integration_hierarchy_physics_path$' --output-on-failure
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
```

Build and run any additional geometry test target introduced in task 4. Run
broader non-windowed integration coverage after building its targets when feasible.

Manual checks: dark/light themes, Compact/Comfortable density, 75/85/100% UI scale,
narrow docks and detached Details, Tab navigation, per-axis menus, typing and
Escape, one-step undo, and no dirty state from simply opening/focusing fields.
In a temporary copy of the supplied project, explicitly Fit the player and
platform, inspect collision outlines, then Play and verify contact at the visible
surfaces. Do not retune the controller's speed or impulse as part of these fixes;
changing fixture size also changes mass, so report any resulting control-tuning
needs separately.

Capture before/after images at the same viewport and panel sizes. Report commands
actually run and any unavailable graphics or native validation.

## Progress and open decisions

- [x] Inspect latest screenshots, supplied project, and current authoring paths.
- [x] Write proposed scope, compatibility constraints, and validation plan.
- [x] Implement six-significant-digit idle fallback and responsive vector layout.
- [x] Implement tasks 1–4; validation and remaining manual checks recorded below.

No tests or builds were run during the original planning phase; implementation
validation is recorded below. Repository-wide `git diff --check`
reports pre-existing whitespace in `tools/editor/README.md`; leave that unrelated
edit intact. The debug outline discrepancy is
supported by source inspection; its visual reproduction is part of task 4.


## Implementation record (2026-11-26)

- Details uses one property label with compact numeric axes; values wrap within
  the available value width. Specialized drawers and axis groups retain recursive
  rendering. Collision masks place Layers beside the value when space allows.
- Numeric idle formatting is presentation-only: six significant digits by default,
  explicit decimal precision where supplied, scientific notation for extremes,
  exact integer tokens. Focus shows the original token; edits retain owner history.
- Fit validates a complete candidate and publishes size/radius plus signed offset
  atomically. Auto-fit component creation is also one undo step. Live context and
  document guards protect callbacks; menu callbacks additionally expire on context
  transitions. Review found persistent Fit buttons must survive untouched numeric
  focus/blur; a failing regression reproduced this and the callback guard was fixed.
- Runtime outlines and preview framing use direct ancestor scale. Shared validity
  checks hide suspended fixtures. Editor draw/pick/bounds/drag use parent scale,
  with the same eight-vertex nonuniform circle approximation as physics.
- Engine version is 0.33.1; editor version is 0.34.1. Physics semantics and scene
  serialization remain compatible; existing authored geometry is unchanged.

Decisions:

- Work stayed in the supplied checkout to preserve the shared workspace and its
  existing documentation edits; no commits, branch changes or external project edits.
- Native bridge inspection showed pick/drag logic resides in Kotlin viewport code,
  so that code was corrected alongside C++ outlines.
- Editor handles remain authoring tools usable to repair small geometry rejected
  by native physics; runtime outlines follow suspension and activity gates.

Validation observed so far:

- Windows full Debug CMake build succeeded, including the native editor bridge.
- Focused native geometry/hierarchy CTest: 2/2 passed; smoke: 2/2 passed;
  non-windowed integration: 64/64 passed.
- Windows editor `gradle.ps1 build`: 475 tests, 0 failures/errors (70 suites),
  including 16 NumberEdit and 16 ShapeFit cases. Final review regression observed
  RED then GREEN. Existing deprecation/compiler warnings remain.
- Inspector gallery captured eight light/dark, width 900/540, font 100/150% images
  in `build/inspector-collider-gallery`; reviewed wide/narrow images. Gallery fixture
  now includes collider dimensions, friction float noise and collision masks.
- Initial gizmo desktop smoke exposed obsolete click locations that now land on
  correctly smaller collider outlines/competing handles; the fixture was updated
  to choose clear interior/outline points. Rerun passed:
  `VIEWPORT_GIZMO_SMOKE_PASS` and `VIEWPORT_TRANSFORM_TOOLS_SMOKE_PASS`.
- Fresh review found one important callback-lifetime issue; fixed with a failing
  regression followed by the full green editor suite. No deferred review findings.
- `git -c core.whitespace=cr-at-eol diff --check` passed, respecting existing CRLF.

Remaining manual coverage: same-size before/after inspector captures; every density
and UI-scale combination, detached Details keyboard navigation, and gameplay contact
checks in a temporary copy of the supplied external project. These are not covered
by unit tests or the non-windowed native suite. No controller values were retuned.

Environment: Windows processes required sandbox escalation under WSL. A full /tmp
blocked tools; `/tmp/sle-audit.vKm6m7` was preserved under
`build/sle-audit.vKm6m7` to free temporary space. Unrelated edits were preserved.

Final screenshot rerun succeeded after the full build. Wide dark and narrow light
collider fixtures were inspected: axis controls use the shared value boundary,
friction shows `0.4`, and wide collision mask actions sit beside their numeric
values. Eight gallery images were refreshed. Native and editor automated checks
listed above are current; outstanding manual combinations remain listed explicitly.
