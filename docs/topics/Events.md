# Events

## Model

Events provide decoupled communication between systems, scripts, scene lifecycle, physics contacts, UI, and state machines.

## Current Concepts

- Event payload structs live in `EngineModules/Events/include/sle/events/`.
- Subscriptions should have clear lifetime ownership.
- Physics contact events are deferred out of Box2D callbacks.
- Lua can subscribe and emit through `Engine.Events`.

## Rules

- Avoid event callbacks that mutate an active iteration unless dispatch is deferred.
- Keep payloads explicit and small.
- Do not leak subscriptions across scene teardown.

## Lua subscription ownership

`Engine.Events.subscribe(name, callback)` infers its owner from the executing Lua
context. Entity script code and callbacks own subscriptions for that script;
entity UI behavior owns them independently of ScriptComponent. Top-level global
assets use owner 0 and are scoped to the scene.

Stable handles remain valid when other subscriptions are removed. Unsubscribe
releases the Lua registry reference and invalidates copied dispatch closures before
it can be reused. Entity-script listeners respect gameplay enabled state and chunk
suspension. UI-owned listeners respect entity lifetime and the chunk script gate.
Removing a script releases its own listeners without removing UI-owned ones.
Direct C++ entity destruction is reclaimed during the next script synchronization;
Lua destroy APIs clean the entire subtree immediately. Successful scene replacement
and VM shutdown clear all listeners before closing the VM; failed replacements keep
the old listeners. Entity script initialization failure releases partial listeners. Failed UI/global
asset execution rolls back only new registrations from that execution, preserving
older subscriptions sharing the owner. Nested and global-format entity callbacks
retain their entity-script ownership.

## Scene-load outcomes

File-based scene switches stage the replacement first. A failed load returns an
error without queuing unload/load events or clearing the current scene's queue
and subscriptions. Deferred failed requests are consumed rather than retried each
frame. On success, old scene events/subscriptions are cleared and global lifecycle
events remain deferred in `SceneUnloadedEvent`, then `SceneLoadedEvent` order.
The live scene EventBus keeps its address and subscription-token sequence.

## Update This File When

- Event names, payloads, dispatch order, Lua event APIs, or subscription lifetime behavior changes.

## Deep Reference

- `docs/EVENT_SYSTEM_IMPLEMENTATION_PLAN.md`
