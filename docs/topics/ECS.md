# ECS And Components

## Model

- `Scene` owns entity lifetime and hierarchy.
- `Registry` owns component pools.
- Components hold data.
- Systems own behavior.

## Common Components

- `TransformComponent`
- `WorldTransformComponent`
- `SpriteRenderer`
- `ScriptComponent`
- `StateMachineComponent`
- `AnimatorComponent`
- `RigidBodyComponent`
- collider and zone components
- `AudioComponent`
- `UIComponent`

## Transform Rule

Transform fields should be changed through existing setters so dirty flags and hierarchy propagation stay correct.

## Adding A Component

1. Add the component type under `EngineModules/Scene/include/sle/scene/components/`.
2. Keep data public unless methods are needed to enforce invariants.
3. Add serialization/loading support where required.
4. Add system behavior outside the component.
5. Update `docs/modules/Scene.md` and this file.

## Deep Reference

- `docs/COMPONENT_SYSTEM_GUIDE.md`
- `docs/SCENE_ECS_CURRENT.md`
