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

## Update This File When

- Event names, payloads, dispatch order, Lua event APIs, or subscription lifetime behavior changes.

## Deep Reference

- `docs/EVENT_SYSTEM_IMPLEMENTATION_PLAN.md`
