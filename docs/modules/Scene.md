# Scene Module

## Responsibility

Entity lifetime, component storage, hierarchy, and scene-owned data.

## Owns

- `Scene`
- `Registry`
- `Entity`
- component pool infrastructure
- scene components
- parent/child hierarchy

## Dependencies

- Core
- Events
- Resources
- Renderer in current code because `SpriteRenderer` embeds `TextureRegion`

## Important Paths

- `EngineModules/Scene/include/sle/scene/`
- `EngineModules/Scene/include/sle/scene/components/`
- `EngineModules/Scene/src/`
- `docs/topics/ECS.md`

## Rules

- Scene should stay a data and structure layer.
- Avoid adding behavior to components.
- Preserve hierarchy and transform dirty invariants.
- Treat the Renderer dependency as known architecture debt unless actively removing it.

## Update This File When

- Entity lifetime, component storage, hierarchy behavior, or component definitions change.
