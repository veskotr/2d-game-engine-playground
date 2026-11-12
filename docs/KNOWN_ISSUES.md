# Known Issues

Current debt checked against the module targets and public interfaces. Historical
issues that have been resolved are listed separately below.

## Namespace ownership

Some public types use legacy namespaces: `Camera2D`, `GLDebug`, `Window`, and
`Resources` are declared in `sle::core`; Systems types use `sle`. This makes module
ownership less obvious. Renaming these namespaces is a separate compatibility
change, not a build reliability fix.

## Scene-to-Renderer coupling

`Scene` links `sle::renderer` because `SpriteRenderer` embeds
`renderer::TextureRegion`. The dependency graph is acyclic, but this couples ECS
data to renderer types. A future resource-handle boundary could remove that
coupling; Renderer should continue consuming commands without querying Scene.

## Resolved historical entries

- `Camera2D` belongs to Renderer; its location is intentional. Its legacy namespace
  is covered above.
- `StateMachineSystem`, `ScriptSystem`, and `UISystem` now use `ScriptRuntime` for
  calls into scripting. `ScriptApi` serves the opposite direction: Lua bindings
  calling engine services. The old proposal to merge these responsibilities is
  obsolete. UI implementation lives under `EngineModules/UI/src/UISystem.cpp`.
- The Scene event forwarding headers cited by the old global `using namespace`
  issue no longer exist. Canonical event declarations live under `sle/events/`.
- The render submission comment in `Runtime.cpp` no longer has the stale step
  number.
