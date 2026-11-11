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

`ChunkActivationComponent` stores authored policy plus a derived runtime suspension mask.
Gameplay component enabled/visible fields remain independent; see [Chunks](../topics/Chunks.md).

## Entity and hierarchy invariants

Use Scene APIs for Scene-owned entity lifetime/hierarchy. `setParent` returns false
for dead children/parents, self-parenting, or cycles, before changing roots, links,
or dirty state. Passing the default entity detaches a live child. Transform parent
accessors stay synchronized, including transforms inserted after parenting.
`Registry::addComponent` requires a live entity and throws `std::invalid_argument`
otherwise. Entity handles remain registry-local IDs.

Staged replacement reserves candidate IDs before old-scene Lua teardown and retains
the highest allocated ID cursor on commit. Entities created by destroy callbacks
must never alias replacement entities or future allocations.

## Authored keys

`setEntityKey(entity, key)` assigns/renames a nonempty unique key on a live entity.
`findEntityByKey(key)` returns the current entity or an invalid handle. Keys follow
Scene-owned subtree destruction, scene destruction and staged replacement; they
are independent of runtime IDs and diagnostic names. See [scene authoring](../topics/Scenes.md).

## Rules

- Scene should stay a data and structure layer.
- Avoid adding behavior to components.
- Preserve hierarchy and transform dirty invariants.
- `createStagingScene()` creates isolated empty storage with entity IDs continuing
  the live registry sequence. Use it synchronously: do not mutate the live scene
  while a replacement is being prepared.
- `replaceWith()` commits staged ECS/hierarchy storage, clearing the live scene's
  old events/subscriptions while preserving the EventBus object's address and token
  sequence. Do not move/swap EventBus; its queued callbacks capture its address.
- Treat the Renderer dependency as known architecture debt unless actively removing it.

## Update This File When

- Entity lifetime, component storage, hierarchy behavior, or component definitions change.

Scene may own a lazy ordinary ECS root entity (`ensureSceneEntity`/`getSceneEntity`,
reserved key `@scene`). Existing/new roots attach beneath it. Its component data,
multiple script behaviours, destruction and staged identity use the normal entity
paths; see [behaviours](../topics/Behaviours.md).
