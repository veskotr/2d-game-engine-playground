# Scene tree and entity-only scene interaction

User request: visualize a scene tree; Scene Editor selects/works on entities,
while Entity Editor selects/works on components.

- [x] Show an expandable scene root and entity nodes (the authored format is
  currently flat; do not invent parent links or silently expand format scope).
  Tree and viewport selection share one selected entity key.
- [x] Scene picking returns entity identity only. Scene handles, body dragging
  and keyboard nudges move Transform; component hit geometry only identifies the
  owning entity. Draw a whole-entity selection outline. Keep component handles
  and component picking in Entity Editor.
- [x] Replace Scene component inspector with entity name/Transform properties.
  Edit Components opens that live scene entity in Entity Editor, preserves its
  key and the separate prefab document, and applies edits to its scene copy.
  Disable prefab New/Open/Save on this contextual component view. Isolated paused
  preview remaps valid external Animator lookup targets only in preview data,
  never in authored/saved scene content.
- [x] Test tree model, whole-entity picking/dragging, retained component selection,
  linked component edits, external reference projection and actual desktop tree
  navigation. Rebuild/test editor and document workflow; no native format change.

Preserve existing edits and unsaved files. Nested parent/child authoring remains
future work; the tree accurately represents the existing scene root/entities.

## Compact layout steering

The user additionally requested a smaller, denser tool layout. Added a compact
dark theme, smaller headings, tighter spacing, slimmer inspector and a UI scale
menu (75/85/100%, default 85%). Tree/properties share available height so component
fields do not get crowded out. Display density changes never alter authored data.

## Validation

- Windows Gradle `build`: 37/37 editor tests passed. New tests cover tree identity,
  no component nodes, entity-only picking/body movement without modifying sprite
  or collider geometry, retained component selection, isolated reference projection,
  and the smoke color predicate for red/blue animation blends.
- `run --args=--scene-smoke` passed actual tree-row clicks, whole-entity movement,
  unchanged component geometry, live component-editor handoff, valid external
  Animator reference preview without changing authored references, preserved prefab
  workspace, save/open and three-mode switching in one native context.
  UI selection at 75/85/100% passed with no document/dirty-state changes.
  Final log: `/tmp/sle-tree-final-scene.log`; screenshot in editor build reports:
  `scene-tree-window.png`.
- Independent code review found no confirmed feature-scoped correctness issues.
- Standalone prefab component/resize/undo/persistence smoke was run after the
  compact layout change: `/tmp/sle-tree-prefab-final.log`.
- Affected documentation links passed. Native engine/format code is unchanged;
  no new native build or CTest result is claimed for this task.

The earlier cross-mode desktop assertion rejected a correctly rendered purple
player during its red/blue blend. The smoke predicate now accepts the animation's
blend colors while rejecting background/NPC colors, with focused coverage.
