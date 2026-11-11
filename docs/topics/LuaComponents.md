# Lua gameplay components

`Engine.Components` exposes entity IDs and copied data snapshots. Component names
are case sensitive. `set(entity, name, patch)` creates or patches a component and
returns `true`, or `false, error`. Validation finishes on a copy before publishing
any changes; unsupported fields, invalid types, nonfinite numbers and invalid
ranges reject the whole patch. Existing runtime handles survive ordinary edits.

`has(entity, name)` returns a boolean. `get` returns a snapshot or `nil`.
`find(name)` returns entity IDs in ascending order. `remove` returns `true`, or
`false, error`; removal of an absent component returns `false, error`.

Vectors are complete objects: `{x=1,y=2}` and `{x=1,y=1,z=1,w=1}`. Map fields replace
that map; nested vectors replace that vector. Enum names match C++ spelling.

| Component | Writable fields |
| --- | --- |
| Transform | position, scale, rotation |
| SpriteRenderer | color, visible, layer, region (uv only) |
| Script | scriptAsset (legacy primary), enabled, behaviours (key/scriptAsset/enabled/order entries) |
| RigidBody | bodyType (Static/Dynamic/Kinematic), linearDamping, angularDamping, gravityScale, velocity, angularVelocity, fixedRotation, enabled |
| BoxCollider | offset, size, friction, restitution, density, categoryBits, maskBits, enabled |
| CircleCollider | offset, radius, friction, restitution, density, categoryBits, maskBits, enabled |
| BoxZone | offset, size, zoneId, categoryBits, maskBits, enabled |
| CircleZone | offset, radius, zoneId, categoryBits, maskBits, enabled |
| Animator | clipAsset, enabled, playing, timeSeconds, speed, overrideLoopMode, loopMode (Once/Loop), stateClipMap, targetEntities, floatParameters |
| StateMachine | definitionAsset, enabled, boolParameters, triggers |
| Audio | assetPath, loop, volume, pitch, playRequested, stopRequested |
| UI | layoutAsset, fontAsset, behaviorAsset, spaceMode (Screen/World), visible, layer, bindingScope |
| ChunkActivation | homeChunkX, homeChunkY, affectsActivation, mode (Inherit/AlwaysActive/EventsOnly/AutoToggle), keepRenderingWhenInactive, keepPhysicsWhenInactive, keepScriptsWhenInactive |

`WorldTransform` (position, rotation, scale) and `ChunkMap` (mapId, enabled,
gridConfig) are read only. StateMachine snapshots also contain currentState,
previousState and stateTimeSeconds. Audio snapshots contain state
(Stopped/Playing/Paused). ChunkActivation snapshots include suspendedSubsystems.
Use the state machine action API to force a state and the hierarchy API to set a
Transform's parent.

Sizes, radii and pitch must be positive. Damping, friction, density and animation
time must be nonnegative. Volume and restitution are in [0,1]. Collision masks
are integers in [0,65535]. Animator targetEntities is a map of names to live
entity IDs. floatParameters, boolParameters, stateClipMap are maps; triggers is
an array of strings; `{}` clears it.

GPU IDs, raw resources, Box2D IDs, audio handles, load-attempt flags, animation
binding diagnostics and state-machine initialization internals are unavailable.
RigidBody.mass is omitted because the physics system derives mass from fixtures.
Texture resources are selected through `Engine.setSpriteTexture`; region.uv uses
{x,y,z,w} for u0,v0,u1,v1. Animator and StateMachine asset edits invalidate their
loaded definitions. Ordinary patches retain loaded resources and runtime state.

JSON scene loading and `Engine.Components.set` now use the same component codecs
and C++ defaults. Unknown/read-only fields on known components, invalid enum values, invalid
vectors and numeric ranges reject rather than silently falling back. Errors include
the field; JSON errors also identify the file/entity/component. Existing runtime
mutation hooks still release/reconfigure native resources before a valid Lua edit.

Asset strings use the same existing-file resolution in both paths. Empty strings
stay empty; UI layout/font/behavior paths trim surrounding whitespace. Missing
paths stay as authored for normal resource loading/error handling. Animator clip
maps use the same asset normalization. This applies to component patches, not a
redesign of every specialized resource API.

JSON additionally adapts legacy asset aliases and vector arrays. See
[scene authoring](Scenes.md) for compatibility exceptions.

The binding definitions live under `EngineModules/Systems/src/lua/components`.
Each component owns its snapshot fields, accepted patch fields and validation.
`ComponentRegistry.cpp` registers them once for the JSON and Lua frontends.
Systems owns resource and runtime lifecycle hooks; Scene remains independent of
Lua and Systems.

See [behaviour ordering and scene scripts](Behaviours.md) for attachment lifetime,
state isolation, and deterministic initialization/update order.
