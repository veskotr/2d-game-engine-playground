# Physics

## Model

Physics is backed by Box2D and coordinated by systems.

## Main Flow

1. Physics components describe body, fixture, and sensor data.
2. `PhysicsSystem` creates/syncs Box2D objects.
3. Box2D steps the simulation.
4. Results sync back to local transform/component data.
5. Runtime resolves world transforms after physics, before rendering and UI.
6. Contact and zone events are drained after callbacks.

Chunk/gameplay physics gates now suspend native Box2D bodies and reconcile active
fixtures. Resume synchronizes the latest local pose; see [Chunks](Chunks.md).

## Rules

- Do not dispatch mutating gameplay work directly inside Box2D callbacks.
- Keep debug rendering controlled through runtime state.
- Sync through systems, not through direct cross-module ownership.
- Consume dirty local transform edits before TransformSystem clears dirty flags.
  Animation and external position/rotation edits must reach existing bodies.
- This scheduling contract preserves the existing local-coordinate physics model;
  it does not add support for hierarchy-relative rigid bodies.

## Update This File When

- Physics component semantics, stepping, contact handling, raycasts, zones, or debug rendering change.

## Deep Reference

- `docs/BOX2D_PHYSICS_INTEGRATION_PLAN.md`
