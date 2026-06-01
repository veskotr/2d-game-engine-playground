# Resources Module

## Responsibility

Asset loading, caching, and resource reuse.

## Owns

- Resource cache/pool types
- Shared loading helpers for engine assets

## Dependencies

- Core
- Renderer for texture/shader resource loading in current code

## Important Paths

- `EngineModules/Resources/include/sle/resources/`
- `EngineModules/Resources/src/`
- `EngineModules/Resources/CMakeLists.txt`

## Rules

- Avoid duplicate asset loads.
- Return stable handles or cached resources where possible.
- Keep resource ownership separate from gameplay behavior.

## Update This File When

- Asset cache semantics, supported asset types, or resource lifetime behavior changes.
