# L2 — Property Descriptors Implementation Plan

**Status** — Sessions 1–6 are implemented. Session 7 has verified the Windows native and JVM suites. Targeted Details desktop acceptance remains open. See the handoffs for results and limitations.

**Goal** — Give Scene and Entity Template Details a shared descriptor contract and validated property/component actions on the L1 undo timeline.

**Architecture** — Extend the existing C++ catalog over JNI with optional metadata. Resolve it against authored JSON in the existing Kotlin inspector model. Stage command mutations, validate through the native authoring path in the destination document context, and publish one history entry on success.

**Stack** — C++20, nlohmann JSON, JNI, Kotlin serialization, Compose Desktop, CTest, JUnit.

**Spec and dependencies** — This document carries the L2 design and execution plan. See [workflow roadmap](editor-game-workflows.md) §5, R4, R6 and items 1.5–1.7, and [completed L1](editor-core-l1.md). For approved implementation use the applicable Superpowers execution skill. Start each session with [repository guidance](../../AGENTS.md), [validation instructions](../../CONTRIBUTING.md), [plan conventions](../../PLANS.md), [constraints](../CONSTRAINTS.md), and the previous session's handoff.

## A. Current state and reuse

| Area | Evidence and impact on L2 |
|---|---|
| Native catalog | [EntityAuthoring.cpp](../../EngineModules/Systems/src/EntityAuthoring.cpp) builds defaults by snapshotting default components and filtering fields through the codec validator. Sprite texture is an added authoring-only default. Enum probing is a separate heuristic. Scene canonicalization already reads catalog defaults, so changing their meaning would affect save/open. |
| Shared validation | [ComponentRegistry.hpp](../../EngineModules/Systems/src/ComponentRegistry.hpp), [ComponentRegistry.cpp](../../EngineModules/Systems/src/ComponentRegistry.cpp), and [ValueValidation.hpp](../../EngineModules/Systems/src/lua/components/ValueValidation.hpp) underpin authoring and Lua mutation. Validation applies codec fields to a copy. [SceneComponentAdapter.hpp](../../EngineModules/Systems/src/SceneComponentAdapter.hpp) handles legacy names, vector arrays, resources and legacy RigidBody mass. Canonical enum validation uses exact strings. Legacy key aliases are a separate concern. |
| JNI and context | [EditorBridge.cpp](../../tools/editor-native/EditorBridge.cpp) exposes catalog, single-entity prefab validation and canonicalization, and Scene canonicalization. [PrefabDocument.kt](../../tools/editor/src/main/kotlin/sle/editor/model/PrefabDocument.kt) uses the Scene codec for whole template trees. [SceneAuthoring.cpp](../../EngineModules/Systems/src/SceneAuthoring.cpp) checks hierarchy and Animator targets, including legacy ordinals. Isolated single-entity validation would reject legitimate cross-entity references. |
| Details | [EntityDocument.kt](../../tools/editor/src/main/kotlin/sle/editor/model/EntityDocument.kt) decodes defaults/enums. Both workspaces converge on ComponentSections, InspectorComponent and ComponentField in [EntityWorkspace.kt](../../tools/editor/src/main/kotlin/sle/editor/ui/EntityWorkspace.kt). [EditorPropertyDefinition.kt](../../tools/editor/src/main/kotlin/sle/editor/ui/inspector/EditorPropertyDefinition.kt) infers types, preferred order and vector captions. Live rendering already expands fixed objects such as region.uv into rows and keeps free-form maps as JSON. The README's broader description of texture regions as multiline JSON is stale for that fixed shape. [EditorPropertyRenderer.kt](../../tools/editor/src/main/kotlin/sle/editor/ui/inspector/EditorPropertyRenderer.kt) owns the color chooser, resource interactions and texture preview. InspectorSectionState owns collapse state across remounts. |
| L1 commands and history | [EditorCommand.kt](../../tools/editor/src/main/kotlin/sle/editor/command/EditorCommand.kt) supplies document/mode/play/focus context. The registry is assembled in ViewCommands.kt, and CommandMenus.kt adapts commands into shared menus. The shortcut dispatcher reserves text clipboard chords. [EntityWorkspaceState.kt](../../tools/editor/src/main/kotlin/sle/editor/model/EntityWorkspaceState.kt) posts to the owning Scene's history or the standalone template history. applyStructure already clears affected drafts, while setValue only clears the exact row. Existing asynchronous resource selection guards against stale context. |
| Authoring defaults and order | Add Component may use editor creation defaults or size-fitting behavior. L2 Reset uses native catalog defaults. Component membership is an object keyed by type. Native canonical JSON orders object keys, and current Details pins Transform first. Reordering has no reliable persisted authoring meaning. Defer Move Up/Down. |
| Test baseline | Existing native integration targets include entity authoring, camera scene, prefab/package, and scene round-trip. Kotlin has InspectorPropertiesTest, NumberEditTest, HistoryTest, SceneAuthoringUxTest, SceneSelectionTest, command tests and workspace tests. Main.kt exposes the real Details gallery, authoring, context-menu, Scene, camera, L1 walk and docking smoke runners. Inspection used the current working files with local edits present. Recheck scoped instructions and working diffs at each session boundary. |

## B. Descriptor contract and compatibility

### Wire format

Keep the catalog object keyed by component. Preserve the shape and values of `defaults` and `enums`, including legacy display order for enum options. Add optional `fields` as an array of descriptor records with a unique nonempty `path` of literal object keys. Add optional `groups` keyed by component-local group ID. Reserve array indexing and cross-object visibility conditions for a later contract extension. The legacy defaults object remains the native source of defaults, including defaults addressed by nested paths. A consumer-facing resolved descriptor carries its default as an optional JSON value. Other providers may author their own default and normalize it into that same slot. A missing default and explicit JSON null are distinct.

Selected entries below illustrate the contract. Existing legacy keys and omitted entries remain in the actual catalog.

```json
{
  "Camera2D": {
    "defaults": {"enabled": true, "primary": true, "viewSize": {"x": 480, "y": 270}, "zoom": 1},
    "enums": {},
    "groups": {"view": {"label": "View", "order": 10}},
    "fields": [
      {"path": ["enabled"], "type": "boolean", "order": 10},
      {"path": ["zoom"], "type": "number", "hard": {"min": 0, "minExclusive": true}, "ui": {"min": 0.25, "max": 4, "step": 0.25, "precision": 2, "unit": "×", "slider": true}, "group": "view", "order": 10, "help": "Sets the camera zoom multiplier.", "visibleWhen": {"path": ["enabled"], "equals": true}},
      {"path": ["viewSize"], "type": "vector", "group": "view", "order": 20},
      {"path": ["viewSize", "x"], "type": "number", "hard": {"min": 0, "minExclusive": true}}
    ]
  }
}
```

The example uses the current Camera2D defaults. Exporters obtain defaults from snapshots. Additional representative records, shown without the owning component envelope:

```json
[
  {"path": ["bodyType"], "type": "enum", "choices": ["Static", "Dynamic", "Kinematic"]},
  {"path": ["texture"], "type": "resource", "drawer": "texture"},
  {"path": ["color"], "type": "vector", "drawer": "color", "axisLabels": {"x": "R", "y": "G", "z": "B", "w": "A"}},
  {"path": ["region"], "type": "object", "drawer": "structured"},
  {"path": ["region", "uv"], "type": "vector", "label": "UV rect", "drawer": "uv", "axisLabels": {"x": "U0", "y": "V0", "z": "U1", "w": "V1"}},
  {"path": ["categoryBits"], "type": "integer", "hard": {"min": 0, "max": 65535}}
]
```

### Semantics

- Supported types are boolean, string, number, integer, enum, vector, resource, object, array and json. Vector stays an object with explicit axes. The type guides presentation and clipboard compatibility. Resolve against the live JSON shape before choosing a drawer. An unknown kind or a mismatch falls back to the existing shape-aware control, including raw JSON when necessary. Known independent optional attributes can still apply if they are valid for that control. Metadata alone never creates an authored value or removes an authored key.
- `hard` carries explicitly documented numeric bounds and inclusivity. Shape, float representability, contextual references and other codec rules stay native. Export bounds from shared constraint constants/specs used by the relevant validator branches, including the conversion to float before comparisons. Avoid a second component validation table. A slider uses `ui.min/max` only. Positive finite `step` controls slider increments and numeric step gestures. Nonnegative `precision` controls slider/step display and generated step text only. `unit` is display text. Label Transform rotation in radians, as shown by TransformTools.kt's degree snap conversion back to radians. Avoid unit claims for unresolved coordinate conventions.
- Choices are typed JSON scalars. Native L2 exports string enums and retains enum order in both representations. Duplicate or invalid choices fall back to legacy enums or inference. Keep an unknown authored value visible and allow recovery through an appropriate control. Compare choice identity by value.
- `visibleWhen` contains one sibling-relative path of one key and exactly one of `equals` or `in` (array of scalar literals). Evaluate against committed/live authored sibling values, including valid previews, with type-sensitive JSON equality. An absent sibling, invalid expression or unavailable value leaves the row visible. Preserve hidden values, drafts and validation state. Finish a live gesture when its row becomes hidden through the existing owner boundary. A hidden field remains subject to native validation.
- Boolean `readOnly` combines with host restrictions and inherits from a containing object/vector. It blocks mutation routes including reset, paste, pickers and shortcuts. Read-only rows can copy. L2 is allowed to use an explicit true fixture in the gallery rather than making a writable native field read-only just to demonstrate the feature.
- Keep declared group ordering, then declared field ordering within each group, then stable input ordering for ties. Numbered entries precede unnumbered entries in their bucket. Ungrouped rows stay in the main block, followed by grouped blocks ordered by group order and declaration order. Unspecified fields keep existing preferred-field/axis behavior then incoming authored order. A missing group declaration places a field in the main block. Keep unknown fields visible. Collapsed groups are keyed by entity, component and group in session UI state.
- `drawer` is a stable ID resolved by a built-in Kotlin registry. Use descriptor ID first and the existing inferred presentation for fields without a usable registration. The shared row owns help, menu/reset, validation and read-only state. Reserve a nullable override indicator slot. A drawer receives a resolved property and owner callbacks and follows the same enabled and history rules. Group layout is a shared renderer responsibility, including when the structured drawer delegates child rendering. Use at most one editor-side override map keyed by provider/component and path for presentation data that cannot live in the native catalog. Populate it only if a concrete need emerges.

### Native and Kotlin representation

Keep editor metadata beside component registrations/codecs in Systems. Add a small native metadata declaration unit if needed to keep authoring presentation out of Runtime. Share canonical enum tables with ValueValidation.hpp and bind specific fields to those tables through explicit declarations. Preserve old catalog enum ordering by declaring the old order in the tables. Share hard-bound issuance with the existing constraint definitions, using typed bound information including exclusive minima. Keep codec signatures, registration order behavior, accepted inputs and runtime hooks stable. Bridge catalog export stays on the existing JNI entry point. Check catalog construction cost because SceneAuthoring uses it as well as editor startup.

Add typed Kotlin records in the editor model (PropertyDescriptor, NumericConstraints, NumericPresentation, VisibilityCondition and PropertyGroup), using `List<String>` or a small value type for path identity. Extend ComponentDefinition with empty-by-default field and group collections so current fixtures and callers continue compiling. Split parsing from JNI for JUnit. Adapt EditorPropertyDefinition into the resolved row model for current value, effective metadata, default, axes and choice list. Use path segments end to end for lookup and replacement. For new property identity, use structured keys or unambiguous escaping wherever map keys are needed. Keep the existing draft-key convention on supported existing paths and test any bridge to those keys. Omit malformed paths, reject ambiguous duplicates by falling back for that path, ignore unknown keys, and degrade malformed optional attributes locally. Avoid interpreting an unknown metadata version as a reason to reject a document.

A future Lua provider can map `properties = { speed = { type = "number", default = 3, ui = { min = 0, max = 10, step = 0.5 }, visibleWhen = { path = {"enabled"}, equals = true } }, enabled = {type = "boolean", default = true} }` into sibling descriptor records with paths `["speed"]`, `["enabled"]` and a defaults object. Lua array-style tables map to ordered JSON arrays, named record tables to JSON objects. Use an explicit ordered list or numeric order when Lua table iteration cannot supply stable order. Future provider validation owns its own authoritative rules. Test normalized JSON fixtures in Kotlin. Lua evaluation, discovery and runtime parameter extraction belong to L6/item 3.3.

### Editing and validation contract

- Today Details number text preserves JSON numeric spelling and previews finite parses immediately. Invalid text keeps the last valid authored value and save-blocking draft feedback. Enter and valid blur finish, invalid blur retains the invalid draft, Escape and focused document Undo cancel the active gesture. Caret changes and read-only input create no edit. Owner-cached handles outlive presenter remount. Viewport drag/nudge has its own gesture/merge behavior and snap service. Characterize both before adding descriptor numeric interaction. Plain typing remains unquantized, and suggested bounds never limit typed input. L2 surfaces hard bounds as help and authoritative action validation. Keep existing live typing/save-time native validation timing, so previously editable authored values and invalid-draft recovery remain reachable. Avoid passing UI limits into NumberOptions. The slider preview display may indicate an out-of-range current value without changing it. A deliberate slider move generates in-range steps, with an explicit endpoint rule (include both ends, quantize interior relative to minimum). Use Alt+Up and Alt+Down for step keys on an editable numeric field with a step, preserving unmodified arrows for normal text behavior. Group repeats through release or blur, with Escape restoring the entry snapshot. A slider gesture follows the same owner transaction and completes on release. Check generated values against declared hard bounds and skip a generated step that crosses a hard limit. Plain text keeps its existing validation timing. Integer controls emit integral JSON tokens when stepped, and keep typed validation behavior. Input such as `1e1` remains a JSON number; the authoritative integer codec can reject it on Paste.
- Property clipboard uses standard platform text with compact JSON (strings quoted), paired with an optional in-process typed record for semantic provenance. Match typed provenance to current system contents using a same-Transferable private flavor where practical, with JSON fallback for plain external text. Never resurrect stale internal contents after replacement or a clipboard read failure. Bare text is invalid JSON. No coercion between booleans, numbers and strings. Resource and enum compatibility checks refine the JSON shape. Exact vector shape, object/array outer shape, and valid target kind precede native validation. Use source kind to disallow known semantic mismatches (for example an editor-copied string into a resource slot); external resource literals still go through destination asset rules. Keep text-field Ctrl+C/Ctrl+V behavior. Menus use registered commands and open on the intended property/axis or component heading.
- Reset looks up the exact catalog path. Restore only that subtree and preserve siblings. Component Reset resets present writable catalog fields and fills absent catalog-default fields where structurally valid, preserving legacy authored-only keys, contextual references and protected fields. Treat entity-reference maps such as Animator.targetEntities as destination-owned for component commands. No reset or paste may indirectly edit read-only descendants. Component Copy exports a typed envelope with component type and eligible values. Component Paste requires exact matching type, overlays supplied eligible fields onto the existing component, preserves omitted values, rejects foreign/unsupported payload keys, and validates the full candidate. Ordinary property actions on explicit reference fields still use destination validation. Component actions never add, remove or replace entity identity, hierarchy, component membership, or template children.
- Stage an immutable candidate before touching document, drafts or history. Validate the candidate through the destination's existing authoring path with full reference context. For Scene, use the same editable Scene projection as save (linked records are managed outside the native Scene shape). For Template, replace the root in its current tree and use canonicalPrefabDocument. Feed resolved asset values through the existing document asset conventions. Use canonicalization as a validation result and commit the staged JSON, keeping canonicalization's broad normalization and default filling out of the local edit. Retain app-native syntax and structural checks, including scene authoring size limits. Address asset resolution explicitly in Session 3. If an unrelated invalid authored field blocks whole-document validation, return its native diagnostic and leave all state intact. Limit validation to explicit Reset/Paste actions, never keypresses or menu enablement. Revisit validation cost only after measuring a valid large scene.
- Capture document/owner identity, entity key, path and context generation when a menu opens. Recheck editability, current target, expected state and owner at invocation, including in detached Details, after document switches, and in Play/Pause. Successful commands close prior gestures using the existing owner boundary, then publish one labeled snapshot via EntityWorkspaceState.change and selective draft invalidation. A rejected operation leaves even a pre-existing gesture and redo stack alone. A no-op creates no history entry. Use the current notice service and keep field-level draft feedback. If changed fields supersede drafts, clear only intersecting drafts/errors and let Undo restore the prior snapshot. Copy changes clipboard only. Test stale menu/picker handles and duplicate entity keys across documents.

### Review focus

| Failure case | Owning session |
|---|---|
| Suggested slider bounds constrain typing or mutate a value on mount | 1–2 |
| Invalid or stale Paste ends a gesture, clears redo or changes another document | 3 |
| Native alias/vector syntax, entity references or resolved resource identity change after open/save | 1, 3–4, 7 |
| Malformed metadata hides an existing field or chooses an incompatible drawer | 1–2, 5 |
| Ancestor replacement bypasses read-only descendants or erases legacy/reference data | 3–4 |

## C. Independently verifiable sessions

All commands are planned for Windows PowerShell with VS 2022, JDK 21 and a configured Debug build. Use repository root for CMake/CTest, and `tools/editor` for Gradle. Build native targets before CTest and rebuild `sle_editor_native` before graphics/JNI checks. When invoking a new test class or smoke below, add it in its owning session first. Start regression work with focused failing assertions for new behavior or characterization assertions for existing behavior. Record commands, outcomes, skipped checks, decisions and the next boundary in Progress. If a boundary grows, split it into a working slice with independent checks and record the new dependency.

### Session entry and exit

1. Read sections A, B and the chosen session. Check the dependency rows in Progress, inspect `git status --short --branch`, and resolve any difference between the plan and the working code before editing. Use the current branch and preserve local changes.
2. Run the relevant existing regression checks. Add the session's targeted assertions before changing behavior. Follow the task order in the session and keep the editor usable at each checkpoint.
3. Run the listed automated checks and desktop walk. Record pass, fail or blocked with the actual command, fixture and environment. A blocked check stays open and travels with the handoff. If it covers a contract the next session needs, resolve it before building on that contract.
4. Update the session's row in Progress and append its handoff. End at the named checkpoint. Start the next session as a separate unit of work.

### Checkpoints and interfaces

| Session | Start when | End with | Contract the next session can use |
|---|---|---|---|
| 1 | L1 baseline is available | A real Details row shows catalog-driven metadata and the enum probe is removed | Existing catalog JNI endpoint plus additive fields and groups. A pure Kotlin catalog decoder accepts old and new payloads. The resolved row model carries effective kind, path, default and optional metadata. |
| 2 | 1's decoder and pilot are verified | Camera zoom demonstrates slider versus direct entry, with groups, visibility and shared row state | The shared row takes its resolved property, effective read-only flag and optional action content. The numeric control uses the owner-cached NumberEdit lifecycle. |
| 3 | 2's row and numeric contracts are verified | Both hosts can reset, copy and paste a property, including a nested axis | The Details target identifies the live owner and path. A reusable candidate operation accepts the proposed EntityDocument, invokes the destination validator, then commits the staged values or returns a diagnostic. The clipboard adapter accepts typed payloads and exposes JSON text. |
| 4 | 3's action path is verified | Both hosts can reset, copy and paste an existing component's values | Component actions use the same target, clipboard adapter and candidate operation. The version-independent component envelope carries component and values. |
| 5 | 2–4 are verified | Color, UV, resource and structured controls use registrations with usable fallbacks | A built-in drawer resolver accepts a resolved property and returns a compatible presenter. Child rows and all commands still receive the shared row and owner context. |
| 6 | 5's drawer behavior is verified | Common fields use descriptors, and remaining inference is inventoried | A reproducible field coverage inventory and schema guide identify supported data, fallback and extension points. |
| 7 | 1–6 are verified | Windows build, regression results and desktop evidence are recorded | The final handoff includes exact results, versions, outstanding limitations and the separate Track A scope. |

### 1. Catalog compatibility and first Details slice

**Depends on** — L1. **Files** — EntityAuthoring.cpp, ComponentRegistry.hpp/.cpp, ValueValidation.hpp and affected codec headers, EntityDocument.kt, EditorPropertyDefinition.kt, EntityWorkspace.kt, existing native entity authoring/camera tests, InspectorPropertiesTest and NumberEditTest.

- [x] Characterize finite JSON text, exponent preservation, invalid blur/save blocking, Escape, focus Undo, remount, keyboard, read-only and viewport gesture baselines before extending controls. Add coverage where existing tests lack it.
- [x] Replace enum probing for RigidBody.bodyType, Animator.loopMode, UI.spaceMode and ChunkActivation.mode. Assert old choice order, each declared choice accepted by the codec, and unknown values rejected. Check legacy loader/adapter compatibility separately. Verify fields exported under actual component names in the registry.
- [x] Export initial descriptors for Camera2D zoom/enabled/viewSize, RigidBody bodyType and SpriteRenderer texture/color/region.uv. Add an observable help label and explicit type for the pilot rows. The rest of the numeric and drawer behavior stays on existing controls.
- [x] Parse fields/groups defensively, test nested/default lookup, optional/unknown metadata, malformed records, type mismatch and a normalized Lua fixture. Test old catalog parsing and preserve existing fixtures via constructor defaults.
- [x] Verify declared paths exist in writable authored defaults for pilot fields (including the texture adapter). Validate each default, validate hard limit boundaries including zoom zero, and confirm the codec accepts zoom above the proposed UI maximum. Round-trip legacy serialized shapes and assert metadata stays out of documents.
- [ ] Run the commands below. In both Details hosts inspect the pilot fields via the real bridge, verify help and enum order, edit a pilot field and an inferred field, Undo, and reopen an existing file. The native ABI entry remains the existing catalog call.

```powershell
cmake --build build/debug --config Debug --target sle_editor_native integration_entity_authoring_path integration_camera_scene_path integration_scene_loader_roundtrip_path
ctest --test-dir build/debug -C Debug -R '^(integration_entity_authoring_path|integration_camera_scene_path|integration_scene_loader_roundtrip_path)$' --output-on-failure
.\gradlew.bat test --tests sle.editor.ui.inspector.InspectorPropertiesTest --tests sle.editor.ui.controls.NumberEditTest --tests sle.editor.model.HistoryTest --tests "sle.editor.viewport.tools.*" --no-daemon --console=plain
.\gradlew.bat run --args=--authoring-smoke
```

**Done** — Native metadata reaches real Details, old C++/Kotlin catalog consumers work, enum probes are gone and the numeric baseline is recorded. **Risk** — Default/enum drift in canonicalization and shared Lua rules. Retain old validation and test both compat and boundary cases.

### 2. Shared rows, numeric controls and conditional layout

**Depends on** — 1. **Files** — EditorPropertyDefinition.kt, EditorPropertyRenderer.kt, InspectorSectionState.kt, InspectorGallery.kt, EntityWorkspace.kt, NumberEdit.kt, EditorControls.kt, EntityWorkspaceState.kt, InspectorPropertiesTest and NumberEditTest.

- [x] Project explicitly ordered fields and collapsible groups from live authored values. Test ordering ties, absent groups, fallback fields, sibling equality and membership, malformed/missing conditions, and retained hidden values. Test inherited read-only and live preview visibility.
- [x] Compose one common row API for help, validation, read-only, property menu/reset and an optional override indicator. Wire existing scalar and vector presenters through it. Establish row-level action context for Session 3 with vector axes individually targetable. Use accessibility labels and existing token-based row layout.
- [x] Add descriptor-driven slider and step interaction to the shared numeric controls. Route generated values through owner-cached NumberEdit lifecycle. Keep typed input and viewport tools unchanged. Test exact step/endpoints, integer step tokens, a typed value above UI range, invalid metadata, out-of-range mount, Escape/Undo, remount and one history record per completed gesture.
- [ ] Exercise the gallery using both real composition paths, then check on JNI with Camera zoom. Type 6 with a 4 slider maximum, move the slider deliberately, Undo to 6, then collapse, detach and reattach the group. Check focus, clipboard keys, invalid draft feedback, read-only controls and small dock layout.

```powershell
.\gradlew.bat test --tests "sle.editor.ui.inspector.*" --tests sle.editor.ui.controls.NumberEditTest --tests sle.editor.model.HistoryTest --tests sle.editor.model.SceneAuthoringUxTest --no-daemon --console=plain
.\gradlew.bat run --args=--inspector-gallery
.\gradlew.bat run --args=--camera-smoke
```

**Done** — All ordinary row capabilities work on pilot fields and fixtures, and existing rows retain usable controls. **Risk** — Disposed presenters ending a live edit or precision/slider logic leaking into keyboard input. Keep draft ownership in the workspace and use effective read-only for every callback.

### 3. Property Reset, Copy and Paste

**Depends on** — 1–2. **Files** — CommandContext in EditorCommand.kt, standard registry in ViewCommands.kt, CommandMenus.kt, new command/DetailsCommands.kt, shared inspector rows, EntityWorkspaceState.kt, SceneWorkspaceState.kt, PrefabDocument.kt, project/ProjectDocuments.kt and existing desktop clipboard/menu helpers. Add DetailsCommandsTest and DetailsPropertyActionsTest.

- [x] Extend command context with a targeted Details row (owner identity, entity, component, path, context generation). Use a live scope check in the menu callback, the registered definitions for all equivalent row menus, and no default clipboard shortcuts. Update Help mouse controls. Test ready, modal, stale, Play/Pause, detached and read-only contexts.
- [x] Implement a testable typed clipboard adapter and immutable proposal path. Use the save projection for Scene, including expanded linked entities while keeping instance declarations outside the native payload, and tree canonicalization for Template. Trace existing resource selection and package/UUID mapping. Resolve changed resource values with the destination's existing project index or standalone document/package path conventions. Native resolveAssetPath can return unresolved input, so native success alone does not establish that a reference will persist. Allow blank where the codec allows it and leave unrelated existing resource values and bookkeeping alone. A clipboard action does not import, copy or register a file. Report an unresolved or cross-project reference before publish. Validate using the destination projection, preserve the destination's asset bookkeeping, and test same-project resource copy, resource reset, legacy aliases and cross-entity Animator keys.
- [x] Add nested property Reset, typed Copy and validated Paste. Test one-entry Undo/Redo in both owners, no-op, wrong scalar type, malformed text, integer syntax, invalid bounds, protected descendants, failed native validation with redo available, unaffected sibling values, unchanged unrelated drafts, and command admission after target deletion/replacement. Invalid operations only publish a diagnostic. Fake the clipboard/native validator for Kotlin transaction tests, and exercise real boundary acceptance/rejection in the desktop walk.
- [ ] In both Details contexts, reset a UV axis with another axis changed, Copy/Paste an enum and a vector, attempt an invalid paste and verify native diagnostic plus Undo label. Open a menu and change document context before invoking it. Check native menus in a detached window and keyboard text selection. Use existing L1 and context-menu runners as regression checks.

```powershell
.\gradlew.bat test --tests "sle.editor.command.*" --tests sle.editor.model.DetailsPropertyActionsTest --tests sle.editor.model.LinkedPrefabWorkspaceTest --tests sle.editor.model.HistoryTest --no-daemon --console=plain
.\gradlew.bat run --args=--context-menu-smoke
.\gradlew.bat run --args=--l1-walk-smoke
```

**Done** — Property actions are usable, validated and undoable in both hosts. Document the intended scope of validation feedback and clipboard JSON in SceneEditor.md and editor Help. **Risk** — Full validation of every candidate can surface an unrelated invalid field or reveal asset-owner differences. Preserve the diagnostic and stage before gesture/history changes.

### 4. Component Reset, Copy and Paste

**Depends on** — 3. **Files** — DetailsCommands.kt, component headings in EntityWorkspace.kt, the proposal/clipboard path from 3, EntityWorkspaceState.kt and its history tests. Add DetailsComponentActionsTest.

- [x] Add heading commands using the same Details target and history path. Use a typed JSON envelope with `component` and `values`. Require exact type match, validate the complete candidate with destination context, keep copied reference maps out of the transferable values, and preserve target references and legacy-only keys on reset and paste. Whole-component replacement is a path-level merge of eligible authored values.
- [x] Test Transform membership/parent preservation, Camera values and existing Make Active behavior (the primary flag is also independently writable today), RigidBody legacy mass, Animator references and template descendants. Test read-only fields inside compound values, foreign key rejection, one invalid field among valid fields, full rollback, no-op and one successful history entry with correct Undo/Redo.
- [ ] Exercise component commands across two entities and across both Details hosts, then try the wrong component envelope. Verify existing Edit/Fit/Remove behavior, linked scene persistence and Undo labels. Document component command availability and the deferred reordering decision.

```powershell
cmake --build build/debug --config Debug --target integration_prefabs_path integration_prefab_package_path
ctest --test-dir build/debug -C Debug -R '^(integration_prefabs_path|integration_prefab_package_path)$' --output-on-failure
.\gradlew.bat test --tests sle.editor.model.DetailsComponentActionsTest --tests sle.editor.model.DetailsPropertyActionsTest --tests sle.editor.model.LinkedPrefabWorkspaceTest --tests sle.editor.project.LinkedPrefabPersistenceTest --tests "sle.editor.command.*" --no-daemon --console=plain
.\gradlew.bat run --args=--scene-smoke
```

**Done** — Same-type component actions preserve structure and references in both hosts. **Risk** — Reset accidentally reuses Add Component heuristics, or paste drops omitted fields. Capture those guarantees in focused fixtures.

### 5. Specialized drawer registration and migration

**Depends on** — 2 for row contract, 3–4 for action regressions. **Files** — EditorPropertyRenderer.kt, EditorPropertyDefinition.kt, EntityWorkspace.kt, InspectorGallery.kt and new inspector PropertyDrawers.kt. Keep existing native catalog records from 1.

- [x] Register vector-based color and UV drawers first, with stable IDs and explicit captions. Preserve RGBA alpha on choosing color, all vector axes and menu targets. Test unknown/incompatible drawer fallback.
- [x] Route resource browse/clear and texture preview through resource and texture registrations. Preserve asset owner callbacks, stale picker guards, thumbnail, Use image size and read-only behavior. Route fixed structured objects through a drawer that delegates child rows. Keep Script variables, Animator targets and other free-form JSON maps on their existing editing path. Migrate one drawer at a time and run focused tests after each.
- [ ] Test registry lookup, effective read-only and fallback for undescribed values. Inspect gallery and real bridge for color, UV, group, resource and generic JSON behavior. Browse in a detached window, switch targets while a picker is open, and verify the stale callback is ignored. Exercise color/UV nested property actions and texture preview with both themes.

```powershell
.\gradlew.bat test --tests "sle.editor.ui.inspector.*" --tests sle.editor.model.DetailsPropertyActionsTest --tests sle.editor.project.ProjectAssetOperationsTest --tests sle.editor.ui.ProjectAssetSelectionTest --no-daemon --console=plain
.\gradlew.bat run --args=--inspector-gallery
.\gradlew.bat run --args=--authoring-ui-smoke
```

**Done** — Pilot specialized controls render through registered drawers and inferred controls remain usable. **Risk** — Migration changes asset ownership, dialog parent, or row action behavior. Keep those callbacks with the existing hosts and retire only fallbacks made redundant by proven coverage.

### 6. Common field coverage, inventory and author guidance

**Depends on** — 1–2, 5. **Files** — native metadata declarations from 1, associated codec constraint specs, InspectorPropertiesTest, native catalog tests, SceneEditor.md, tools/editor/README.md and HOW_TO_ADD_UI_FEATURES.md. Add a focused schema topic and link from applicable docs during implementation.

- [x] Expand metadata for Transform position/rotation/scale; SpriteRenderer texture/size/color/region/offset/layer/visible; Camera2D view fields, enabled and primary; RigidBody body type, damping, gravity scale, velocity, fixed rotation and enabled; Box/CircleCollider size/radius, offset, material values, enables and filter bits. Review geometry/float/integer limits separately from interaction ranges. Retain authoring compatibility for UV/color values outside typical display ranges. Add a string and explicit read-only fixture where coverage needs it. Keep enum metadata for UI, Animator and ChunkActivation even where other fields remain inferred.
- [x] Produce a field-level inventory from catalog defaults versus metadata paths (including nested children), plus recognized legacy fields absent from defaults and read-only runtime-only fields as relevant. Record what still uses inference and which controls it receives. Replace a preferred-order or caption rule only when equivalent paths are covered. Explain intentional differences in step or slider behavior.
- [x] Test representative per-category boundaries, defaults, fallback, read-only, visibility and the Lua-normalized fixture. Save and reopen a legacy project scene fixture and the checked-in standalone starter scene. Document field attributes, drawer registration, inference and limitations.
- [ ] Manually inspect common components, Transform radians and integer bit fields on an unlocked desktop.

```powershell
cmake --build build/debug --config Debug --target sle_editor_native integration_entity_authoring_path integration_camera_scene_path
ctest --test-dir build/debug -C Debug -R '^(integration_entity_authoring_path|integration_camera_scene_path)$' --output-on-failure
.\gradlew.bat test --tests "sle.editor.ui.inspector.*" --tests sle.editor.ui.controls.NumberEditTest --no-daemon --console=plain
.\gradlew.bat run --args=--inspector-gallery
```

**Done** — Common edits exercise the new contract and the remaining inference inventory is reproducible. **Risk** — Conflating typical ranges, physics rules and authoring limits. Audit the exact codec predicate before declaring any hard bound.

### 7. Milestone verification and handoff

**Depends on** — 1–6. **Files** — Main.kt and smoke harness only if adding the focused L2 walk, existing regression tests as needed for L2 findings, version declarations in root CMakeLists.txt and editor build.gradle.kts, relevant docs and this plan.

- [ ] Add or extend a focused real-app walk for the flows in §F using existing smoke infrastructure. Prefer deterministic assertions of document/history state over screenshot-only verification. Record fixture, graphics environment, snapshots and result. Fix L2 failures at their owning boundary or split follow-up work explicitly.
- [x] Bump engine and editor MINOR per CONTRIBUTING when landing the feature, using the versions on the actual integration branch. Verify generated engine version and editor window title. Confirm the smoke list, Help text, schema topic and coverage inventory match shipped behavior.
- [ ] Run the full verification gate in §F. Record exact commands, status, reported test counts and desktop matrix results. Preserve any unrelated existing worktree changes and record outstanding environment-dependent checks.

**Done** — End-to-end and regression evidence covers the completion checklist in §F, with explicit limitation records. **Risk** — Mistaking Java2D scaling for Windows display DPI or relying on L1 historical results. Capture the real display and test environment with the evidence.

## D. Implementation order

Sessions 1 and 2 prove the contract through native export, Kotlin fallback and real Details, with characterization ahead of numeric behavior. Session 3 ships the highest-value action and proves staged native validation first. Session 4 reuses the same command and validation path for larger payloads with separate structural risks. Session 5 migrates drawers against working action and regression contracts. Session 6 grows the catalog once renderer semantics are proven. Session 7 integrates evidence and versions.

## E. Decisions and approval

- **Specified scope** — Additive catalog. Native validation authority. Compatibility for existing file formats, runtime and Lua mutations. Single-object Details, property and component actions, representative metadata and inferred fallback. Multi-object Details is the next Track A milestone. Lua discovery, project/asset/tile settings migration, prefab override detection/Revert/Apply and unrelated L1 contrast/Scale-drag issues remain deferred.
- **Settled by inspection** — Use Systems codec and catalog boundaries, existing JNI APIs, whole destination authoring projection and the L1 command/notice/history paths. Use catalog defaults for Reset. Keep Move Up/Down deferred because current persisted component objects have no meaningful user order. Label stored rotation radians.
- **Proposed choices for review** — Seven sessions, menu-first actions with JSON clipboard interoperability, same-type component value overlay that preserves destination-owned references and legacy fields, and full native destination validation for explicit actions only. These choices are reversible before implementation and keep resource/legacy data handling explicit. No additional design decision is needed before Session 1. Starting implementation requires a separate request. If real asset validation or measured whole-scene latency calls for a contextual JNI validator, record the evidence and propose a focused plan amendment before expanding that boundary.

## F. Final verification and L2 completion

Configure and build the changed targets and suite before executing tests. The focused commands above remain useful for diagnosis.

```powershell
cmake --preset debug -DSLE_BUILD_EDITOR=ON
cmake --build build/debug --config Debug --parallel
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
ctest --test-dir build/debug -C Debug -L integration -LE windowed --output-on-failure
ctest --test-dir build/debug -C Debug -L windowed --output-on-failure
```

From `tools/editor` on an interactive Windows desktop:

```powershell
.\gradlew.bat build --no-daemon --console=plain
.\gradlew.bat run --args=--authoring-smoke
.\gradlew.bat run --args=--authoring-ui-smoke
.\gradlew.bat run --args=--scene-smoke
.\gradlew.bat run --args=--camera-smoke
.\gradlew.bat run --args=--context-menu-smoke
.\gradlew.bat run --args=--editor-appearance-smoke
.\gradlew.bat run --args=--docking-workspace-smoke
.\gradlew.bat run --args=--project-documents-smoke
.\gradlew.bat run --args=--l1-walk-smoke
```

- Walk Scene Details and Entity Template Details against old and newly created documents. Include a hierarchy, a linked instance, a template with descendants, Animator cross-entity references, legacy syntax, packaged and project assets. Compare authored values/references, save and reopen, and boot/load representative files through existing runtime coverage. Verify untouched fields and data shapes survive. Check shared Lua codec regressions through the integration suite.
- For each host, check property and component copy/reset/paste, valid/invalid/empty clipboard, mismatch, busy/unavailable clipboard, no-op, single-entry Undo/Redo, an active draft, numeric drag and keyboard paths, out-of-range typed slider values, grouping/conditional visibility/read-only, color, UV, structured and resource controls. Confirm notice feedback, non-mutating failure and normal text shortcuts. Check Play/Pause gating, mode changes and viewport regression.
- Cross Dark Forest and Light with Compact and Comfortable at normal scale. Exercise UI 75/85/100%, narrow dock under 240 dp and detached Details (focus, menus, picker owner, history and redock). Exercise Windows 150% and mixed-DPI detached migration where available. Record Java2D 1.5× separately if used. Mark blocked or unverified cells and carry forward a named limitation for each. Existing L1 scale-drag and light-theme handle defects remain separate.
- Close L2 after confirming documented extensible metadata, legacy catalog keys, declared enums, typed parsing/fallback, shared Details and registered drawers, property/component actions, L1 history, atomic rejection, representative coverage, compatibility, current verification evidence, and updated schema/help/inventory/versions. Review required checks that could not execute before declaring the milestone complete.

## Progress and handoff

- **Planning** — Inspected the branch, working files, native registry and authoring paths, Kotlin rendering and history, L1 plan, test targets and smoke entry points. No implementation or product tests executed.
| Session | Status | Evidence or handoff |
|---|---|---|
| 1. Catalog and pilot | Implemented | Native, JUnit and JNI checks passed. See desktop evidence below. |
| 2. Shared rows and numeric controls | Implemented and verified on Windows | Editor suite, focused CTest, gallery, Camera and docking smokes passed. See remaining manual checks in the handoff. |
| 3. Property actions | Implemented | Editor tests and JNI smoke passed. Desktop acceptance remains open. |
| 4. Component actions | Implemented | Native, editor, and JNI checks passed. The component-heading desktop walk remains open. |
| 5. Drawer migration | Implemented | Editor tests and Windows UI smoke passed. Detached picker walkthrough remains open. |
| 6. Coverage and guidance | Implemented | Native and editor validation passed. The desktop walkthrough remains open. |
| 7. Milestone verification | In progress | Windows native and JVM suites passed. The Windows smoke suite passed after a context-menu retry. See Session 7 for targeted desktop checks still open. |

For each handoff record the date and session, behavior delivered, files and interfaces affected, test commands with actual results, manual matrix cells, relevant findings, and the next session's starting state. Copy remaining risks forward only when evidence leaves them open.


### Session 1 handoff — 2026-11-18

- Used the existing `feat/project-foundation` checkout and preserved its local edits. The
  catalog keeps legacy defaults and enum order. The four shared enum tables now supply
  both codec validation and editor choices. Camera2D, RigidBody and SpriteRenderer
  supply additive metadata over the existing JNI entry point. The Details renderer uses
  resolved field types and the Zoom tooltip. Kotlin decodes optional metadata by literal
  path and resolves defaults separately from authored values.
- The numeric baseline covers finite input, invalid blur and save blocking, Escape, focused
  Undo, remount, read-only events, and viewport gesture history. The typed exponent spelling
  persists in the editor draft and returns with Redo. JSON serialization can emit the
  equivalent ordinary decimal token. Existing numeric controls and serialization were
  kept in place for this session.
- Linux/WSL Debug build used a separate Ninja directory, cached dependency sources,
  system Python with Jinja2, and JDK 21. Built `sle_editor_native` and the three
  integration targets. The first focused CTest run failed on missing Camera2D metadata
  as expected. The final run of
  `ctest --test-dir /tmp/sle-l2-linux -R '^(integration_entity_authoring_path|integration_camera_scene_path|integration_scene_loader_roundtrip_path)$' --output-on-failure`
  passed 3/3. Coverage includes enum acceptance and rejection, defaults and paths,
  legacy vector and alias normalization, and the exclusive zoom minimum after float
  conversion. Zoom 6 remained valid.
- Gradle 9.5.1 with JDK 21 ran `test run --args=--authoring-smoke -PsleNativeLibrary=/tmp/sle-l2-linux/tools/editor-native/libsle_editor_native.so --no-daemon --console=plain`.
  JUnit passed 333/333. The authoring smoke reported Entity, prefab package and linked prefab
  success. It also exercised pilot catalog resolution, zoom editing and Undo with both
  document owners. The smoke reopens the saved template. The native legacy round-trip
  verified that field metadata stays out of document component values.
- A local 1,000-call Debug timing of catalog construction measured about 2.47 ms/call for
  the old probe implementation and 1.25 ms/call for the new implementation using the
  same dependency build. This measures catalog construction alone.
- Desktop commands used the same Gradle setup and JNI library with `--authoring-ui-smoke`
  and `--camera-smoke`. The Entity Template smoke passed the native preview check
  (1,202 cyan pixels) and failed the desktop capture (0 cyan pixels), then crashed
  during teardown in `Resources::clear`. Both the default renderer and the WSLg
  llvmpipe retry reproduced that result. The llvmpipe Scene smoke reached the first
  viewport frame and failed its active-child-camera centering check after a Skiko
  Linux GL context warning. A replay with the old catalog linked into the JNI
  bridge produced the same camera assertion.
- The manual Details walk remains open in both hosts, including the Zoom tooltip,
  RigidBody choice order and editing through the widgets. The automated JNI smoke
  covers both document owners at the model boundary. Windows desktop and the Windows
  Debug build were unavailable through WSL interop. Complete desktop acceptance on
  the supported Windows configuration before treating Session 1 as fully verified.
  Session 2 starts from the additive decoder and resolved row model.


### Session 2 handoff — 2026-11-18

- Scene and Entity Template Details share the group and field projection. Ordering
  is stable within the declared groups and fallback rows. Visibility follows live
  sibling values, and hiding a field preserves its value and draft. Collapse state
  belongs to the workspace and is keyed by entity, component and group.
- Shared property rows display help and validation, and expose optional action and
  override content. Vector axes resolve their own paths and inherit read-only state.
  Session 3 owns the menu commands and authoritative Reset/Paste validation.
- Camera zoom has a 0.25×–4× slider with quarter-step increments and direct
  entry. Slider and Alt+Up/Alt+Down edits use the owner-cached number transaction.
  The gallery shows the same descriptor behavior in both Details hosts.
- Windows PowerShell and Visual Studio 2022 Debug built `sle_editor_native` and
  `integration_entity_authoring_path`, `integration_camera_scene_path` and
  `integration_scene_loader_roundtrip_path` via `cmake --build build/debug --config Debug --target ...`.
  Windows `ctest --test-dir build/debug -C Debug -R "^<target>$" --output-on-failure --no-tests=error`
  passed each of the three targets.
- Windows JDK 21 and Gradle 9.5.1 ran
  `tools/editor/gradle.ps1 test run --args=--camera-smoke --no-daemon -Pkotlin.incremental=false`.
  JUnit reported 335 passed and two symlink fixture tests skipped. The Camera smoke
  reported `CAMERA_SCENE_PLAY_PASS` on the rebuilt Windows DLL. Disabling incremental
  compilation avoided Kotlin cache paths left by the previous WSL build.
- Windows Gradle ran `test run '--args=--inspector-gallery --capture tools/editor/build/reports/l2-session2-gallery' --no-daemon -Pkotlin.incremental=false`.
  It produced eight gallery captures at the two window widths and font scales in
  both themes.
- A Windows desktop gallery walk entered 6, clicked the Template slider to
  choose 2, then used Undo to restore 6. It collapsed and reopened View and
  toggled Scene Camera Enabled, which hid Zoom and restored it at 6 on
  re-enable. Screenshots are under `tools/editor/build/reports/l2-session2-gallery-walk`.
- On 2026-11-19, the Windows gallery walk also verified Alt+Up from 6 to 6.25,
  invalid exponent feedback, Escape restoring 6, and a read-only View size Y
  field retaining 270 after attempted keyboard input.
- Windows `tools/editor/gradle.ps1 test run --args=--docking-workspace-smoke --no-daemon -Pkotlin.incremental=false`
  passed its native host, group detach/redock, and draft-preservation checks.
  The specific Camera View group detach/reattach sequence and clipboard shortcuts
  in the field remain open manual checks. Session 3 can use the shared row
  action slots and individual axis paths.


### Session 3 handoff — 2026-11-19

- Right-click property labels in Scene or Entity Template Details for registered
  Reset, Copy and Paste actions. A vector axis targets only its own path. Copy
  writes JSON text and attaches property kind to the same clipboard contents.
  Commands recheck the live target, document, drafts, play state and presenter
  before operating. Text editing keeps its ordinary shortcuts.
- Reset and Paste stage one candidate in the destination document, resolve changed
  assets, validate the scene or template tree and publish one named history entry.
  Linked entities also validate their persisted instance override. Project asset
  lookup uses registered identities without creating sidecars. File-backed
  standalone documents use their own path conventions within an active project.
- The Gradle 9.5.1 run used JDK 21, the editor cache and the existing Linux JNI
  bridge. `test run --args=--authoring-smoke --offline --no-daemon
  --console=plain -Pkotlin.incremental=false` with
  `-PsleNativeLibrary=/tmp/sle-l2-linux/tools/editor-native/libsle_editor_native.so`
  passed 346 editor tests and the JNI authoring smoke. The smoke exercised
  enum paste, vector paste, single-axis reset and native rejection in both
  owners. No native source changed in Session 3.
- Ran `run --args=--context-menu-smoke` and `run --args=--l1-walk-smoke` on
  WSL with llvmpipe and the Linux bridge. Both started and reported a Skiko
  Linux GL fallback. Context Menu stopped when Down did not select a File menu
  item. L1 stopped when it could not find the notice chip colour.
- Windows `tools/editor/gradle.ps1 run --args=--context-menu-smoke --no-daemon
  -Pkotlin.incremental=false` passed its appearance and File menu keyboard
  checks. It stopped before the Inspector stage on the viewport click
  placement assertion.
- Windows `tools/editor/gradle.ps1 run --args=--l1-walk-smoke --no-daemon
  -Pkotlin.incremental=false` passed all seven stages, including detached
  Hierarchy and Scene, playback and appearance.
- The desktop property walkthrough remains open, including clipboard interaction,
  stale popups and detached Details on Windows. Session 4 can reuse the target,
  clipboard and candidate publishing paths for component actions.


### Session 4 handoff — 2026-11-19

- Scene and Entity Template component headings use the registered Details Reset,
  Copy, and Paste commands. Component Paste requires a matching `component` and
  overlays the supplied `values` paths. Copy omits legacy-only, read-only, and
  Animator target fields. Reset restores eligible catalog defaults, including
  absent fields where their containing shape permits insertion.
- The existing destination validation and history path handles the complete
  candidate, including linked prefab overrides. The successful edit is one
  `Paste <component>` or `Reset <component>` undo entry. A rejected or unchanged
  candidate leaves the document and history alone.
- Clipboard Script behaviour records using the legacy `asset` field are normalized
  to `scriptAsset` before destination asset resolution. Animator state names
  such as `variables` are valid map keys and their changed clip paths go through
  the same resolver as other state entries.
- WSL Debug built both prefab integration targets via
  `cmake --build /tmp/sle-l2-linux --target integration_prefabs_path integration_prefab_package_path -j 4`.
  Then `ctest --test-dir /tmp/sle-l2-linux -R '^(integration_prefabs_path|integration_prefab_package_path)$' --output-on-failure --no-tests=error`
  passed 2/2.
- Linux Gradle 9.5.1 and JDK 21 ran `test run --args=--authoring-smoke --offline --no-daemon --console=plain -Pkotlin.incremental=false -PsleNativeLibrary=/tmp/sle-l2-linux/tools/editor-native/libsle_editor_native.so -PsleBuildDir=/tmp/sle-l2-s4-editor-build`.
  All 352 editor tests passed. The JNI smoke passed and exercises cross-host and
  cross-entity Camera actions, wrong-type
  rejection, Make Active, Undo/Redo, and linked override save and reopen. Unit
  coverage includes Transform hierarchy, read-only descendants, Animator
  references, template descendants, legacy mass, atomic rejection, and nested
  asset resolution.
- The WSL `run --args=--scene-smoke` run passed Scene tools and multi-selection.
  It stopped at the desktop pixel assertion (`textured=0 cyan=0`) after the Skiko
  Linux GL context fallback. Actual heading menus, platform clipboard, and the
  Edit/Fit/Remove walkthrough remain open on the supported desktop. Session 5
  can reuse both command scopes and their regression cases.


### Session 5 handoff — 2026-11-19

- Both Details hosts resolve the built-in color, UV, resource, texture and
  structured drawers by compatible descriptor ID. Legacy field inference
  supplies captions and the default drawer when lookup fails. Structured
  drawers delegate child rows through the shared inspector, and free-form maps
  retain JSON editing.
- Color selection preserves the authored alpha token. RGBA captions remain
  available when visibility hides a channel, and the chooser requires all four
  visible channels. Explicit descriptor captions keep their authored case.
  Vector child rows share their existing menus and read-only rules. The resource
  drawer delegates browsing to its host with the original target and keeps the
  guarded project selection callback.
- Windows Gradle 9.5.1 with JDK 21 ran `tools/editor/gradle.ps1 test run
  "--args=--inspector-gallery --capture tools/editor/build/reports/l2-session5-gallery"
  --offline --no-daemon "-Pkotlin.incremental=false"`. The editor suite reported
  352 passed and two skipped. The gallery wrote eight captures for Dark, Light,
  both widths and both font scales. The 900-wide captures showed the texture
  preview and RGBA controls in both themes, plus Script variables as JSON.
- Windows ran `tools/editor/gradle.ps1 run "--args=--authoring-smoke"
  --offline --no-daemon "-Pkotlin.incremental=false"`. The JNI authoring smoke
  passed, including native drawer resolution and nested color and UV actions
  in both document owners.
- Windows ran `tools/editor/gradle.ps1 run "--args=--authoring-ui-smoke"
  --offline --no-daemon "-Pkotlin.incremental=false"` with each of
  `JAVA_TOOL_OPTIONS=-Dsle.smokeTheme=Dark` and `-Dsle.smokeTheme=Light`.
  Both reported `ENTITY_EDITOR_UI_SMOKE_PASS`. The WSL runs in both themes
  failed after Skiko's Linux GL fallback, with a native crash in
  `sle::core::Resources::clear()`. WSL gallery captures lacked usable pixels.
- The detached project chooser and target-switch walkthrough remains open.
  `ProjectAssetSelectionTest` covers delayed results after field edits, document
  replacement, component reattachment, context changes and playback. The
  screenshots and automated checks leave interactive picker and menu clicks
  for desktop verification. Sessions 6 and 7 follow this handoff.


### Session 6 handoff — 2026-11-19

- Added descriptors for all 65 default paths across Transform, SpriteRenderer,
  Camera2D, RigidBody and the two colliders, including axes and SpriteRenderer
  region children. Kept the legacy enum tables and also declared Animator,
  UI and ChunkActivation enums as fields. UI binding scope demonstrates
  explicit string metadata. The normalized Lua-shaped test fixture covers an
  explicit read-only text field.
- Integer step gestures emit whole JSON tokens and respect the native layer
  and filter-bit bounds. Numeric typing retains the existing preview and
  save-time validation behavior. Transform rotation displays radians. Camera
  zoom retains its suggested slider interval and visibility rule. The
  shared codec limits distinguish positive geometry, nonnegative damping and
  material values, restitution in 0–1, and finite UV and RGBA channels.
- The [schema topic](../topics/EditorPropertySchema.md) documents all attributes,
  inference, the field inventory, legacy aliases and runtime-only fields. The
  built integration executable's `--dump-catalog` output reported 69
  described paths out of 120 default paths. All declarations resolve to defaults.
- Windows ran `cmake --build build/debug --config Debug --target sle_editor_native integration_entity_authoring_path integration_camera_scene_path --parallel 4`
  and `ctest --test-dir build/debug -C Debug -R '^(integration_entity_authoring_path|integration_camera_scene_path)$' --output-on-failure --no-tests=error`.
  Both focused CTests passed. WSL built the same three targets in
  `/tmp/sle-l2-linux` with `-j 4` and passed both CTests there.
- Windows ran `tools/editor/gradle.ps1 test run '--args=--authoring-smoke' --offline --no-daemon '-Pkotlin.incremental=false'`.
  JUnit reported 353 passes and two skips. The authoring smoke verified both
  Details owners, the checked-in
  `editor_starter.slescene` round trip, a legacy project scene with array
  vectors and preserved mass, and the linked prefab round trip.
- Windows ran `tools/editor/gradle.ps1 test run '--args=--inspector-gallery --capture tools/editor/build/reports/l2-session6-gallery' --offline --no-daemon '-Pkotlin.incremental=false'`.
  Gradle completed and produced eight captures. The inspected capture showed
  the Windows lock screen, so the visual checks remain open. Windows
  `tools/editor/gradle.ps1 run '--args=--project-documents-smoke' --offline --no-daemon '-Pkotlin.incremental=false'`
  stopped at the native canvas focus assertion for Ctrl+S.
- Session 7 remains pending along with the manual review of common Details
  controls and the earlier desktop checks recorded in their handoffs.

### Session 7 handoff — 2026-11-19

- Bumped the engine from 0.18.0 to 0.19.0 and the editor from 0.10.0 to 0.11.0 for this feature. The engine executable reported `SLE engine 0.19.0`. Gradle generated `version=0.11.0` in the editor resources.
- Windows `cmake --preset debug -DSLE_BUILD_EDITOR=ON` and `cmake --build build/debug --config Debug --parallel 4` succeeded with Visual Studio 2022 and SDK 10.0.26100.0. Under `ctest --test-dir build/debug -C Debug ... --output-on-failure`, `-L smoke` passed 2/2, `-L integration -LE windowed` passed 59/59, and `-L windowed` passed 16/16.
- Windows `tools/editor/gradle.ps1 build --offline --no-daemon '-Pkotlin.incremental=false'` succeeded. The JUnit XML reports 355 cases with 353 passed, two skipped, and zero failures or errors. AssetBrowserTest and ProjectAssetImportsTest each skipped a symlink case because the Windows user lacked symlink creation privileges.
- The existing `--authoring-smoke` passed against the rebuilt Windows JNI library. It checks Details property and component actions against document and history state, native rejection, both hosts, starter scene round trips, packaged resources, and legacy and linked prefab scenes. The editor README now describes that coverage.
- Logs for this session are under `tools/editor/build/reports/l2-session7/`.
- The Windows `--authoring-ui-smoke` capture `tools/editor/build/reports/entity-editor.png` shows `SLE Editor 0.11.0 · engine 0.19.0` in the title and the Entity Template Details next to the rendered textured collider. Windows reported Intel graphics, two 1920×1080 displays and an applied desktop DPI of 96.
- The Windows desktop runner passed `--scene-smoke`, `--camera-smoke`, and `--editor-appearance-smoke` in Compact density. The appearance smoke covered Dark and Light, both Details layouts, detached Details, Play read-only behavior and the return to editing. The initial `--context-menu-smoke` reached the viewport Create submenu and timed out waiting for it to open, before reaching Details. Its failure capture shows the adjacent Import submenu highlighted. The cause remains unconfirmed.
- The Windows `--docking-workspace-smoke` and `--project-documents-smoke` passed. Docking exercised draft retention, play/redock and separate monitors. Project Documents passed native-canvas save and the linked prefab edit/save/reopen workflow.
- The unchanged context-menu smoke passed on retry, including positioned creation, the Inspector stage and save shortcuts. The first failure remains recorded as intermittent. The `--l1-walk-smoke` passed all seven steps at Java2D scale 1.0. It exercised Dark and Light in Compact and Comfortable, detached windows, Undo/Redo and Play/Pause gating. Captures are in `tools/editor/build/reports/l1-walk-1/`.
- The editor smoke commands used `tools/editor/gradle.ps1 run --args=--<smoke> --offline --no-daemon`. Help now documents both property-label and component-heading actions. The same Windows Gradle build passed again after the Help change and the new assertion in KeymapTest.
- Windows `tools/editor/gradle.ps1 run "--args=--inspector-gallery --capture tools/editor/build/reports/l2-session7/inspector-gallery" --offline --no-daemon` passed and produced eight captures. The Dark 900 px window at 100% font scale showed the authored `6×` next to a slider at its travel limit. The Light 540 px window at 150% font scale showed truncated component headings in the side-by-side fixture. Gallery font scaling exercises presentation only.
- **Open acceptance** — The focused desktop walkthrough for property and component clipboard actions, stale menus, detached picker ownership, and descriptor-specific numeric gestures is still open. The existing Desktop and JNI smokes cover those surfaces and model behaviors in part. The 75% and 85% editor UI settings, Details dock widths under 240 dp, Windows at 150% DPI and mixed-DPI migration remain unverified. The docking smoke passed migration between the available displays at the recorded scale.
