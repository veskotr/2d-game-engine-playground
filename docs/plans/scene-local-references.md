# Stable scene references implementation plan

User-approved scope: stable scene-local keys, parent/reference resolution after
entity creation, and repeat-load regressions. Shared JSON/Lua component codecs and
hierarchy-relative physics remain subsequent work.

Design: optional nonempty, case-sensitive entity `key` strings are separate from
human-facing `name`. `parent` and Animator.targetEntities accept a key string or a
legacy positive 1-based position in the JSON entities array. Numeric values never
refer to runtime IDs. Forward references work. null/absent parent means root.
Duplicate keys, unknown references, invalid types/ranges, and hierarchy cycles
return contextual load errors. SceneManager's staged JSON path preserves the live
scene on failure; direct SceneLoader calls retain their append/nontransactional
contract. Scene owns key lookup/lifetime; Lua gets Engine.findEntityByKey(key),
returning a current runtime ID or nil. Key metadata swaps with staged ECS storage
and is removed on entity/subtree/scene destruction.

Use Scene metadata rather than a new component: identity is structural and must
follow the same lifetime as hierarchy. Resolve references through a per-file map;
existing entities/Tiled IDs cannot accidentally satisfy references in another file.
Keep old unkeyed scenes working; numeric legacy targets resolve within each load.

- [x] Add native reload/hierarchy/target regression; observe failure on old loader.
- [x] Add scene key lifetime/lookup and two-pass loader resolution; validate failures.
- [x] Bind key lookup through ScriptApi and update manifest/stub; exercise real Lua.
- [x] Migrate the demo's raw animator target to a key; document authoring/compatibility.
- [x] Run focused native tests, tooling tests/check, full Debug build and CTest suite.

Preserve existing worktree edits and do not commit/push this task without request.

## Validation (2026-11-11)

The new native regression first failed because parent references were ignored.
Invalid-reference variants then reproduced successful replacement of the live scene
with malformed links. After resolving those, the real Lua test failed on the
missing findEntityByKey wrapper; adding the bridge completed the green cycle.

Final commands actually run:

- Windows CMake: `cmake --build build/debug --config Debug --parallel 4` succeeded.
- Windows CTest: `ctest --test-dir build/debug -C Debug --output-on-failure`
  passed 57/57, including all seven windowed tests.
- `python3 -m unittest discover -s EngineModules/Scripting/tools/tests` passed 21.
- `python3 EngineModules/Scripting/tools/generate_lua_api_stub.py --check` passed.
- Changed-file whitespace and affected documentation links checked.
- Independent read-only review found no concrete correctness/compatibility findings.

Coverage includes repeated keyed/unkeyed loads after prior allocations, forward
parent/animator references, transformless intermediate parents, legacy numeric
remapping, key renaming/duplicates/lifetime, Lua lookup and recursive destruction,
invalid types/ranges/keys/cycles, and preservation of old scene keys/entities after
failed staged loads. The NPC demo now uses keyed animator targets.

Compatibility: numeric references now always mean file positions, never existing
runtime/Tiled IDs; migration is documented in docs/topics/Scenes.md. Physics under
parents, shared JSON/Lua validation, same-name deferred chunk refresh, and
transactional imperative builders remain separate work. No commit/push performed.
