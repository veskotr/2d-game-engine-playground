# Physics

## Model

Physics is backed by Box2D and coordinated by systems.

## Main Flow

1. Physics components describe body, fixture, and sensor data.
2. `PhysicsSystem` creates/syncs Box2D objects.
3. Box2D steps the simulation.
4. Results sync back to transform/component data.
5. Contact and zone events are drained after callbacks.

## Rules

- Do not dispatch mutating gameplay work directly inside Box2D callbacks.
- Keep debug rendering controlled through runtime state.
- Sync through systems, not through direct cross-module ownership.

## Update This File When

- Physics component semantics, stepping, contact handling, raycasts, zones, or debug rendering change.

## Deep Reference

- `docs/BOX2D_PHYSICS_INTEGRATION_PLAN.md`
