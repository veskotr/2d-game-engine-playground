# Entity authoring

Goal: create a single entity, add/remove supported components, edit their fields,
and save engine-loadable JSON using the existing single-entity prefab format.
The sandbox remains available in its own tab; entity authoring is the default tab.
This milestone does not add hierarchy editing or connect the authored document to
live scene mutation. Saved files are reusable through PrefabLoader and scenes.

## Plan

- [x] Add a VM/graphics-independent editor authoring API in Systems. Export writable
  component defaults from ComponentRegistry, filter derived/native fields through
  existing validators, include the JSON texture selector, and validate single-entity
  prefab documents using the same component rules as SceneLoader. Reject unsupported
  formats/types rather than silently discarding them. Test engine load compatibility.
- [x] Expose catalog/validation over JNI independently of the viewport session.
- [x] Add a Kotlin document model and atomic file persistence. Unit tests cover
  component edits, invalid JSON/types, preserved values, dirty/reset behavior,
  save/open round trips and failed-save preservation.
- [x] Build the entity workspace with identity fields, add/remove component menu,
  typed scalar/vector/boolean/enum controls and JSON fields for maps/arrays. Preserve
  incomplete input and block saving while invalid. Confirm before discarding edits.
- [x] Add authoring smoke that generates a prefab from the real engine catalog,
  validates and saves/reopens it; load that artifact using the engine loader.
- [x] Review changes, build/check focused targets and editor tests, run full engine
  regressions and the existing viewport GUI smoke. Document actual validation.

## Boundaries

Kotlin owns authored document data and saving, never native runtime handles.
C++ remains authoritative for component defaults and validation. Export stable
entity keys, no runtime IDs. Unknown/unsupported content is rejected on open with
an explanation, not dropped. This first editor handles exactly one entity per
prefab; nested prefabs, parent links and multiple entities remain future work.
Output uses root/entities/key/name/components, accepted by current engine loaders.
Writes replace the destination only after native validation and successful temp
file output. Saving must not export running sandbox state.

Work is in the supplied dirty checkout; preserve all pre-existing edits and avoid
commits/resets. User explicitly authorized implementation of this workflow.


## Implementation and validation (2026-11-11)

- Added EntityAuthoring to Systems: codec-derived writable defaults/enum choices,
  strict single-entity prefab validation and canonical import. Native/derived
  fields are filtered by the existing validators. JSON texture selection and
  legacy mass metadata retain scene adapter semantics.
- JNI catalog/validation/canonicalization are independent of the viewport; JSON
  crosses JNI as UTF-8 bytes so supplementary Unicode names survive.
- Editor defaults to Entity Editor, with entity key/name, Add/Remove Component,
  typed scalar/vector/boolean/enum controls, JSON maps/arrays, JSON preview and
  New/Open/Save/Save As. Document and incomplete field state survive tab switches.
  Native validation precedes atomic temp-file replacement; destructive document
  actions confirm only when data has changed.
- Review found missing controls for defaults omitted by opened prefabs. Fixed
  through engine canonical import using existing scene syntax adaptation and
  defaults; native regression covers partial Transform and legacy Script aliases.
- Windows full CMake Debug build succeeded. Full CTest: 64/64 passed, including
  all 9 windowed tests and native entity authoring/prefab compatibility.
- Gradle build succeeded with 5/5 document/state tests, no failures/skips.
- --authoring-smoke passed: engine catalog, edits, UTF-8 name, atomic replacement,
  failed-save preservation and save/open round trip. Exported artifact:
  tools/entity-editor/build/reports/authored-entity.prefab.json.
- integration_entity_authoring_path.exe loaded that exact editor-generated file
  through PrefabLoader successfully (exit 0), separately from native fixtures.
- --authoring-ui-smoke passed; entity-editor.png was visually inspected.
- Existing --smoke-test passed after sandbox tab extraction, including gameplay
  playback transitions, frozen pause frames and graphics context teardown.
- Updated doc links and authoring source whitespace checked. Native regressions
  and state tests were observed failing for missing APIs before implementation.

Limitations: one entity per prefab; no hierarchy/nested prefab editing, undo/redo,
asset browser or authored viewport connection. Sandbox remains a separate startup
scene. OS file-picker interaction was not automated; the save/open/preservation
logic behind it was tested. Unavailable atomic filesystem replacement is reported
as a failed save rather than falling back to a potentially destructive write.
