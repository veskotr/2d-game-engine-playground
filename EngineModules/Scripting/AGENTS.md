# Scripting changes

Read the root [AGENTS.md](../../AGENTS.md) and [Scripting docs](../../docs/modules/Scripting.md).

- Start from the affected `LuaBindings*.cpp` file and public `ScriptApi.hpp`.
- For runtime-backed APIs, update the interface in this module and the implementation
  in Systems (`ScriptApiImpl`). Follow existing binding registration and error handling.
- Preserve Lua stack balance, argument checks, callback lifetimes, and script teardown.
- Add API coverage under `tests/integration/scripting/` and Lua fixtures under
  `tests/data/scripts/`; register new executables in the integration CMake file.
- `assets/scripts/engine_api.lua` is generated IntelliSense documentation. Update
  binding sources and the generator if needed, then regenerate using:

```powershell
python EngineModules/Scripting/tools/generate_lua_api_stub.py --bindings-dir EngineModules/Scripting/src --output assets/scripts/engine_api.lua
```

- Review the generated diff and update the scripting topic docs for public API changes.

## Maintaining the Lua API

For each gameplay `ScriptApi` addition, register a Lua wrapper and update
[`tools/lua_api.json`](tools/lua_api.json). Declare the actual Lua parameter names,
types, optional arguments, nil returns, and the C++ method called by the wrapper.
Aliases need separate Lua entries pointing to the same method. Signatures are
reviewed declarations; the generator does not infer Lua semantics from C++ types.

Classify runtime-only execution/lifecycle hooks in `internal_methods` with a
reason. Do not expose VM callback references or cleanup machinery as gameplay API.
Add any new binding table to `tables`, and keep constant expressions in `constants`
identical to their registrations. GLFW key codes in the manifest are the stable
public ABI reference; Input enum references are resolved against the current header.
Unsupported constant expressions fail validation instead of emitting placeholders.

From the repository root, run:

```sh
python3 -m unittest discover -s EngineModules/Scripting/tools/tests
python3 EngineModules/Scripting/tools/generate_lua_api_stub.py
python3 EngineModules/Scripting/tools/generate_lua_api_stub.py --check
```

Use `python` on Windows. The generator also retains `--bindings-dir` and `--output`
and accepts `--manifest` for isolated validation. CI checks the committed stub
before CMake can regenerate it. Normal generation warns about newly unmapped
`ScriptApi` methods so staged native builds can proceed; `--check` rejects them.
Registration/manifest mismatches fail both modes. Review the generated diff and execute relevant
runtime integration tests: manifest validation checks registration coverage and
method mappings, not argument handling or runtime behavior.

`--check` also audits component codecs in `Systems/src/lua/components/` against
plain component struct declarations under `EngineModules/*/include`. A new
`*Component` type (or `SpriteRenderer`) needs a codec and descriptor registration,
or a `// Lua-internal-type: TypeName: reason` annotation in its declaration header.
New top-level fields must appear in their snapshot, or have an adjacent codec
`// Lua-internal: field: reason` annotation. Accessor-backed/renamed storage uses
`// Lua-field: cppField -> luaField`; the named output key must exist. Keep each
component's codec in its own header and register it through the descriptor map.

This is a focused declaration/codec audit, not a C++ AST or serialization proof.
It covers direct top-level member snapshots and the existing descriptor idioms;
nested resource internals, validation semantics, field-type conversions, mutation
hooks, and teardown behavior still need code review and runtime tests. Unsupported
component declaration syntax requires extending the scanner with a regression test.
Run `python3 EngineModules/Scripting/tools/audit_component_bindings.py` for a
standalone classification audit.
