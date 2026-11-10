# Architecture

## Model

SLE is a layered engine. Lower modules provide stable services, while higher modules coordinate behavior.

Module dependencies form an acyclic graph, not a single linear chain. The
`EngineModules/*/CMakeLists.txt` targets are authoritative. Direct engine-module
links (consumer -> dependencies):

- `Core`: no engine-module dependencies.
- `Events`, `Platform`: `Core`.
- `Renderer`: `Core`, `Platform`.
- `Resources`: `Core`, `Renderer`.
- `Scene`: `Core`, `Events`, `Resources`, `Renderer`.
- `Physics`: `Core`, `Scene`, `Events`.
- `Scripting`: `Core`, `Platform`, `Scene`, `Resources`.
- `UI`: `Events`, `Platform`, `Renderer`, `Resources`, `Scene`, `Scripting`.
- `Systems` (`sle::engine`): all other engine modules.

Applications and examples consume these modules.

## Core Boundary Rules

- Lower modules do not depend on higher modules.
- Cross-module access uses public APIs, value types, handles, or runtime context.
- `Systems` is the main integration layer.
- Renderer receives render commands rather than querying ECS.
- Lua reaches engine services through registered bindings and runtime bridge APIs.

## Known Deviations

- `Scene` currently links `Renderer` because `SpriteRenderer` includes `TextureRegion`.
- Some namespaces still reflect older ownership decisions.
- Runtime owns `ScriptEngine`; systems use the narrower `ScriptRuntime` interface.

## Deep References

- `docs/ARCHITECTURE_VERIFIED.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEBASE_ARCHITECTURE_ANALYSIS.md`
