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
fixtures. Resume synchronizes the latest composed hierarchy pose; see [Chunks](Chunks.md).

## Rules

- Do not dispatch mutating gameplay work directly inside Box2D callbacks.
- Keep debug rendering controlled through runtime state.
- Sync through systems, not through direct cross-module ownership.
- Authored Transform position/rotation stay local. Native body poses, velocity,
  forces, impulses and physics queries use world space. Physics resolves current
  ancestors directly, including scene roots and transformless nodes; it neither
  depends on WorldTransform caches nor consumes dirty flags.
- Pre-step parent edits and reparenting update existing bodies. After stepping,
  all native world poses are captured before parent-first inverse conversion to
  local coordinates. A dynamic parent and child remain independently simulated
  world bodies; hierarchy does not create a joint.
- Fixture dimensions and offsets inherit ancestor scale (absolute dimensions,
  signed offsets). The body's own Transform scale remains visual dimensions and
  does not multiply explicit collider units, preserving root-body compatibility.
  Scale changes rebuild fixtures while preserving the native body and velocity.
- Uniformly scaled circles stay native circles. Nonuniform circles and circle
  sensors use an inscribed convex eight-vertex ellipse approximation. Rotation
  follows the engine's additive TRS contract; hierarchy composition does not
  introduce shear geometry.
- Missing Transform destroys orphan native bodies. Nonfinite poses or ancestor
  scale magnitudes at or below 1e-6 suspend simulation without changing authored
  enabled flags. Degenerate inherited polygon geometry also suspends: box sides
  must exceed 0.1 world units and ellipse radii must be at least 0.5 world units
  to avoid Box2D precision/assertion limits. Validation inspects converted native
  vertices, rejecting collapsed hulls at large offsets before Box2D construction.
  Only fixtures enabled by gameplay and their chunk subsystem participate in this
  geometry gate. Direct PhysicsWorld fixture creation returns zero for unsafe
  geometry. Valid geometry resumes the same body
  using the current hierarchy pose. Native pose teleports are preserved when
  authored hierarchy poses have not changed.

## Update This File When

- Physics component semantics, stepping, contact handling, raycasts, zones, or debug rendering change.

## Deep Reference

- `docs/BOX2D_PHYSICS_INTEGRATION_PLAN.md`
