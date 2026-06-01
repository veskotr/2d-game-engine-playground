# Architecture

## Model

SLE is a layered engine. Lower modules provide stable services, while higher modules coordinate behavior.

Target module order:

`Core -> Events -> Platform -> Renderer -> Resources -> Scene -> Physics -> Scripting -> UI -> Systems -> examples`

## Core Boundary Rules

- Lower modules do not depend on higher modules.
- Cross-module access uses public APIs, value types, handles, or runtime context.
- `Systems` is the main integration layer.
- Renderer receives render commands rather than querying ECS.
- Lua reaches engine services through registered bindings and runtime bridge APIs.

## Known Deviations

- `Scene` currently links `Renderer` because `SpriteRenderer` includes `TextureRegion`.
- Some namespaces still reflect older ownership decisions.
- Some systems still call `ScriptEngine` directly; this should move toward narrower script runtime interfaces.

## Deep References

- `docs/ARCHITECTURE_VERIFIED.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEBASE_ARCHITECTURE_ANALYSIS.md`
