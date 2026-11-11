# JSON/Lua component parity

Current authorized step: stabilize JSON/Lua component authoring first. Multiple
behaviours per entity, deterministic script ordering, and Unity-like prefabs are
requested follow-ups, not changes to implement in this step. Preserve pending
scene-reference work and all other worktree changes.

Use the existing per-component codecs as one source for writable fields, types,
ranges, enums, defaults, snapshots and resource-cache invalidation. Extract their
descriptor registry from ScriptApiImplComponents.cpp into a shared Systems helper.
Keep codecs next to their component binding definitions; no Scene-to-Systems/Lua
module dependency. Lua runtime mutation/ownership hooks remain in Runtime.

Scene loading adapts legacy JSON asset aliases and exact-length vector arrays to
canonical component fields, resolves scene references to live IDs, then invokes
the same codec as Engine.Components.set. Canonical Lua field names and vector
objects are also accepted in JSON. Duplicate alias/canonical spellings reject.
Missing fields use component defaults. Known malformed/unknown/read-only fields
reject the staged load; unknown component types retain documented warn/skip
compatibility. JSON texture selection stays a resource adapter. Legacy JSON
RigidBody.mass remains accepted/validated/stored for compatibility but remains
inert and unavailable in Lua (Box2D uses fixture density).

Shared resource-path normalization preserves the JSON search behavior for both
paths, treats empty paths as empty, and retains UI path whitespace trimming.
Resolve Animator target references before codec validation, never bypass the
shared live-entity checks. Include asset alias conversion and conflict tests.

- [x] Write a native regression comparing real JSON loading and Lua patching:
  supported components/defaults, legacy/canonical valid inputs, invalid types,
  ranges, unknown/internal fields, asset paths, and failure atomicity.
- [x] Observe baseline failures before extracting registry and loader adapter.
- [x] Share registry/asset normalization; replace duplicated JSON field loaders.
- [x] Update component audit location and maintenance guidance; keep drift checks.
- [x] Run focused regressions, full Debug build/CTest, Python tooling checks and
  independent review. Update affected docs with compatibility and limitations.

No changes to script scheduling or prefab instantiation in this step. No commit
or push requested for this step.

## Validation (2026-11-11)

Baseline regression failed on ignored sprite UVs/Animator/StateMachine fields,
unsupported ChunkActivation, invalid JSON values accepted by old readers, and UI
path differences. The shared-registry implementation passed focused parity,
scene-reference and existing Lua component tests. Further cases cover canonical
JSON field names/objects, exact vector lengths, asset aliases/conflicts, float
bounds, malformed component objects and legacy mass validation.

Actual final validation:

- Windows `cmake --build build/debug --config Debug --parallel 4` succeeded.
- Windows `ctest --test-dir build/debug -C Debug --output-on-failure` passed 58/58,
  including all seven windowed tests.
- `python3 -m unittest discover -s EngineModules/Scripting/tools/tests`: 21 passed.
- `python3 EngineModules/Scripting/tools/generate_lua_api_stub.py --check`: passed.
- `python3 EngineModules/Scripting/tools/audit_component_bindings.py`: all 15 types
  classified against the shared registry location.
- Affected documentation links and new-file whitespace checks passed. Git's default
  whitespace check flags pre-existing CRLF lines in integration/CMakeLists.txt;
  the scoped cr-at-eol-aware check passed for affected tracked files. A whole-tree
  check also reports older whitespace in unrelated files, which was preserved.
- Independent read-only review found no confirmed correctness/compatibility issues.

SceneLoader is now a compatibility/reference adapter over the shared codecs.
Known component fields no longer silently ignore invalid data. Unknown component
classes keep warn/skip behavior, texture selection remains a JSON resource
adapter, and positive finite legacy mass is retained as inert JSON metadata.
Component-path normalization is shared; specialized resource APIs and Tiled's
separate importer were not redesigned. Changes remain uncommitted.

Follow-up request retained: multiple behaviours per entity with explicit,
deterministic execution order; then prefab definitions/instantiation built on
this common component authoring path. No script scheduling behaviour changed here.
