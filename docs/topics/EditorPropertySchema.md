# Editor property schema

Scene and Entity Template Details share the catalog returned by the editor
bridge. Each component has native `defaults` and legacy string `enums`. The
optional `fields` and `groups` describe presentation. Scene and prefab files
store component values only. Native codecs still validate authoring and Lua
mutations.

## Descriptor attributes

Each field has a unique, nonempty `path` array of literal object keys relative
to the component. For example, `["region","uv","x"]` targets one UV coordinate.
A field inherits its default from the same path in `defaults`. A missing
default differs from a JSON null default.

| Optional attribute | Behavior |
| --- | --- |
| `type` | Supported kinds are `boolean`, `string`, `number`, `integer`, `enum`, `vector`, `resource`, `object`, `array` and `json`. The live value must have a compatible shape. |
| `label`, `help` | Set the visible caption and tooltip. The authored path still identifies the field. |
| `order`, `group` | Order rows within a block and assign a field to a declared group. `groups` is keyed by ID with optional `label` and `order` per group. Ungrouped rows come first. Numbered entries precede unnumbered entries, and ties preserve their incoming order. |
| `choices` | Ordered, unique JSON scalar values for enums. Keep native string enums in `enums` for older consumers. |
| `hard` | Numeric `min` and `max` with optional `minExclusive` and `maxExclusive`. Use the codec's shared constraints. Generated steps honor hard limits. Native validation checks direct entry at save time, including integer syntax, float representation, shape and context. |
| `ui` | Optional `min` and `max` for slider travel, positive `step` for slider increments and Alt+Up/Down, nonnegative `precision` for generated text and slider display, display `unit`, and `slider` to opt in. Suggested ranges never clamp typed values. A caption can identify a unit on rows without sliders. |
| `visibleWhen` | A single-key sibling `path` with either scalar `equals` or a scalar array `in`. A missing or unavailable sibling leaves the field visible. Hidden values stay authored and remain subject to native validation. |
| `readOnly` | Disables editing and mutation actions while allowing Copy. A read-only vector or object makes its descendants read-only. Host restrictions also apply. |
| `drawer`, `axisLabels` | Select a compatible registered drawer by stable ID and optionally map vector keys to captions. |

Malformed optional attributes degrade locally. Duplicate paths fall back to
inference for that path. An unknown type or incompatible value uses the same
inferred control as a missing type. A missing or incompatible drawer ID also
falls back to an inferred drawer. Inference recognizes asset fields, legacy
enums, booleans, text, numbers, vector-shaped objects and fixed nested objects.
Other shapes get JSON editing. Vector axes are individually addressable.

## Adding fields and drawers

1. Find the component's snapshot and validator in
   `EngineModules/Systems/src/lua/components/`. Add the catalog record in
   `EntityAuthoring.cpp` and use the same constraints as the codec. Keep
   interaction suggestions in `ui`. Add explicit child paths for vector axes
   or structured fields whose rules differ from their parent.
2. Reuse `InspectorComponent` and the shared rows. For a specialized control,
   register an ID and compatibility rule in
   `tools/editor/src/main/kotlin/sle/editor/ui/inspector/PropertyDrawers.kt`.
   Delegate nested rows to the inspector and route edits through the owner
   callbacks so both hosts retain history, read-only state, and action menus.
3. Check the native default, declared path, valid boundary and rejection cases
   in `integration_entity_authoring_path`. Add resolved row or drawer coverage
   to `InspectorPropertiesTest` and use `--inspector-gallery` for desktop checks.

Paths index objects only. Visibility can inspect siblings only. Metadata never
inserts a missing authored value during rendering. The editor Lua host
normalizes named behavior property declarations into field descriptors and
defaults using the same JSON contract. The Inspector stores edits as
per-attachment overrides. See [exposed behavior parameters](Scripting.md#exposed-behavior-parameters).

## Field inventory

The table compares catalog defaults with declared paths. `.*` below expands
to the default object's axes. The command after the table checks the comparison
against the current native build.

| Component | Declared paths | Inferred default paths and controls |
| --- | --- | --- |
| Transform | `position`, `position.*`, `rotation`, `scale`, `scale.*` | — |
| SpriteRenderer | `texture`, `color`, `color.*`, `size`, `size.*`, `offset`, `offset.*`, `layer`, `visible`, `region`, `region.uv`, `region.uv.*` | — |
| Camera2D | `enabled`, `primary`, `viewSize`, `viewSize.*`, `zoom` | — |
| RigidBody | `bodyType`, `linearDamping`, `angularDamping`, `gravityScale`, `velocity`, `velocity.*`, `angularVelocity`, `fixedRotation`, `enabled` | — |
| BoxCollider | `size`, `size.*`, `offset`, `offset.*`, `density`, `friction`, `restitution`, `categoryBits`, `maskBits`, `enabled` | — |
| CircleCollider | `radius`, `offset`, `offset.*`, `density`, `friction`, `restitution`, `categoryBits`, `maskBits`, `enabled` | — |
| Animator | `loopMode` | Resource `clipAsset`. Checkboxes `enabled`, `playing`, `overrideLoopMode`. Numbers `speed`, `timeSeconds`. JSON maps `floatParameters`, `stateClipMap`, `targetEntities`. |
| Audio | — | Resource `assetPath`. Checkboxes `loop`, `playRequested`, `stopRequested`. Numbers `pitch`, `volume`. |
| BoxZone | — | Vectors `size`, `offset` with numeric axes. Numbers `categoryBits`, `maskBits`. Checkbox `enabled`. Text `zoneId`. |
| CircleZone | — | Vector `offset` with numeric axes. Numbers `radius`, `categoryBits`, `maskBits`. Checkbox `enabled`. Text `zoneId`. |
| ChunkActivation | `mode` | Numbers `homeChunkX`, `homeChunkY`. Checkboxes `affectsActivation`, `keepPhysicsWhenInactive`, `keepRenderingWhenInactive`, `keepScriptsWhenInactive`. |
| Script | — | Resource `scriptAsset`. Checkbox `enabled`. JSON array `behaviours`. A provider-supplied `variables` map uses JSON editing. |
| StateMachine | — | Resource `definitionAsset`. Checkbox `enabled`. JSON map `boolParameters` and array `triggers`. |
| UI | `spaceMode`, `bindingScope` | Resources `behaviorAsset`, `fontAsset`, `layoutAsset`. Number `layer`. Checkbox `visible`. |

Run the built `integration_entity_authoring_path --dump-catalog` executable
and redirect its stdout to `catalog.json`. To list each default path and
metadata coverage, run the following with Python 3 from the same directory.

```python
import json

def paths(node, parent=()):
    if isinstance(node, dict):
        for key, value in node.items():
            path = (*parent, key)
            yield path
            yield from paths(value, path)

with open("catalog.json", encoding="utf-8") as source:
    for component, entry in json.load(source).items():
        defaults = set(paths(entry["defaults"]))
        described = {tuple(field["path"]) for field in entry.get("fields", [])}
        for path in sorted(defaults | described):
            coverage = ("metadata" if path in defaults else "metadata only") if path in described else "inferred"
            print(component, ".".join(path), coverage)
```

### Compatibility and runtime fields

- The authoring adapter recognizes `Script.asset`, `Script.behaviours[].asset`,
  `Audio.asset`, `UI.layout`, `UI.font`, `UI.behavior`, `Animator.clip` and
  `StateMachine.definition`. These aliases normalize to their catalog resource
  keys. Legacy array vectors normalize to object axes. Behaviour entries
  remain in the editable JSON array.
- `RigidBody.mass` is legacy serialized metadata, retained on load and save.
  An existing value uses the inferred numeric control and is excluded from
  component Reset/Copy. The codec requires a positive value. Box2D derives
  simulation mass from fixtures.
- Runtime-only snapshot fields include `Audio.state`,
  `StateMachine.currentState`, `previousState`, `stateTimeSeconds` and
  `ChunkActivation.suspendedSubsystems`. They have no authored defaults or
  Details rows. Transform dirty state and parent linkage, Box2D handles,
  `WorldTransform` and `ChunkMap` are also outside authored component metadata.
- Transform rotation is stored and shown in radians. Transform scale accepts
  zero and negative values. Collider dimensions and camera dimensions are
  positive. Density and friction can exceed one, and restitution uses 0–1.
  Sprite UV and RGBA channels accept finite float values outside 0–1.
- Sprite layer and collider filter bits generate whole-number tokens in
  one-unit steps. Direct entry retains its numeric preview and save behavior. Filter bits use decimal 0–65535. Camera zoom keeps the 0.25–4 slider
  and quarter-unit step. Other new numeric rows keep direct entry without
  sliders or prescribed steps.
- Native order matches the preferred SpriteRenderer and collider rows. The
  editor retains its ordering and caption fallbacks for older catalogs.
