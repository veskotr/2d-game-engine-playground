# Multiple behaviours and prefabs

User authorized implementing both now, after shared JSON/Lua validation. Preserve
all prior pending changes. No commit/push requested.

## Multiple scripts

Extend ScriptComponent with behaviours: ordered entries {key, scriptAsset, enabled,
order}. Unique nonempty keys identify instances independently of asset paths and
array indices. Keep legacy scriptAsset/enabled, treating the nonempty legacy asset
as a reserved primary behaviour. Keyed entries have isolated Lua environments and
function refs even when the same asset is attached repeatedly. Explicit global
_G is still shared for intentional app-level communication; ordinary script globals
are instance-local. Backward-compatible global lifecycle scripts remain supported.

Each frame takes a stable task snapshot sorted by order ascending, entity ID,
then authored entry position. Initialize all due tasks before the update pass.
Mutations are rechecked before invoking each callback; newly added work starts the
next frame. Component enabled gates everything; per-entry enabled pauses its
updates/events without restarting its state. Chunk suspension retains instances.
Changing/removing an entry only tears down that entry and its event refs; asset
changes recreate it. Entry failures do not disable sibling behaviours. Entity
callbacks fan out deterministically; boolean guards aggregate deliberately and
legacy global guards continue working. Never keep iterators/component references
across Lua. Own event subscriptions by entity + behaviour key, with generation
identity to prevent stale scheduled/event callbacks after replacement.

## Prefabs

Prefab JSON: {root: "root", entities: [...keyed entities...]}. Scene JSON can add
prefabs:[{file:"relative/path.prefab.json",key:"enemyA",parent:optionalSceneKey,
 overrides:{root:{Transform:{position:[100,50]}},child:{Audio:null}}}].
Instance root key is enemyA; child keys enemyA/child. Rewrite internal keyed and
legacy numeric references before scene loading. Merge component overrides (objects
recursively, arrays replace; null component removes). Keep identity/hierarchy
controlled by the prefab and instance parent. Reject duplicate/missing keys,
malformed roots/references/overrides and recursive imports with contextual errors.
Resolve prefab paths relative to their including file. Use the shared SceneLoader
component pipeline and staged scene validation.

C++ PrefabLoader::instantiate(path,scene,key,overrides,parent) appends one instance,
returns Result<Entity>, rolls back newly created entities on failure. Lua
Engine.instantiatePrefab(path,key,overrides?,parent?) returns rootID or nil,error.
Expose Engine.getBehaviourKey() for a script to identify its current attachment.

## Steps

- [x] Regressions first for ordering/state isolation/lifetime/event ownership.
- [x] Implement behaviour schema/runtime/scheduler and shared JSON/Lua support.
- [x] Implement prefab expansion, reusable loadData pipeline and runtime instantiate.
- [x] Bind APIs/update manifest/stub/audit and add combined prefab+behaviour coverage.
- [x] Independent review, native full Debug build/CTest, tooling checks, affected docs.

## Additional approved steering: scene behaves like an entity

Scene owns an optional lazy scene-root Entity, exposed as Engine.getSceneEntity().
Top-level scene JSON components use this same entity/component pipeline, including
multiple behaviours. Reserved key @scene identifies it. Existing roots/new entities
are parented under it when present; absent it, legacy scenes retain their root
structure. Scene-level transforms therefore affect descendants. The anchor follows
scene replacement/destruction and uses the same scheduler/event ownership as all
other entities. No second scene-script loop or VM is introduced.

## Validation and review (2026-11-11)

RED: prefab regression initially failed to compile because PrefabLoader.hpp was
absent. Multi-behaviour regression failed with unknown Script.behaviours field.
After implementation, focused prefab/behaviour tests passed. Full compatibility
run passed all old tests; additional review regressions then reproduced stale
same-frame named callbacks after pausing, and a removed sibling surviving a
reentrant destroy callback. Immediate configuration gating plus postcommit
synchronization against final ECS data fixed both. Reviewer confirmed the final
reentrancy fix; no additional important findings remained in that follow-up.

Final commands actually run:

- Windows `cmake --build build/debug --config Debug --parallel 4`: succeeded.
- Windows `ctest --test-dir build/debug -C Debug --output-on-failure`: 60/60 passed,
  including all seven windowed tests.
- `python3 -m unittest discover -s EngineModules/Scripting/tools/tests`: 21 passed.
- `python3 EngineModules/Scripting/tools/generate_lua_api_stub.py --check`: passed.
- `python3 EngineModules/Scripting/tools/audit_component_bindings.py`: 15 component
  types classified, including the Script.behaviours field.
- Scoped CRLF-aware Git whitespace checks passed, affected doc links resolved.

Coverage: deterministic cross-entity init/update sorting and authored tie breaks;
state isolation for repeated assets; entry pause/resume; same-frame callback/event
gates; per-attachment event cleanup; initialization failure isolation/no retry
storm; entity callback fanout; reentrant sibling removal; top-level scene
components/multiple scripts and scene-root transforms/staged replacement; scene
and Lua prefab instantiation, independent overrides/ref remapping, invalid subtree
validation, recursive import rejection and failed-scene preservation.

Implemented scene root is an ordinary registry entity with reserved @scene key,
created only by top-level components or explicit request. Synthetic loader entry
is appended so pre-existing authored numeric positions do not shift. Prefab
imports are deliberately one level: nested imports fail explicitly. Dynamic
instantiation prevalidates then appends with rollback of only new entities.
Physics remains in local coordinates: keep rigid bodies' parent transforms at
identity until hierarchy-relative physics is implemented. Named environments are
isolated; the legacy primary keeps shared-global compatibility. All changes remain
uncommitted, with earlier pending scene-reference/schema work preserved. Release
configuration was not rebuilt for this step; validation above is Debug.
