# Events Module

## Responsibility

Shared event infrastructure and event payload types.

## Owns

- `EventBus`
- event payload structs
- scoped subscription helpers
- propagation/cancellation helpers where applicable

## Dependencies

- Core
- Small shared value types only

## Important Paths

- `EngineModules/Events/include/sle/events/`
- `EngineModules/Events/CMakeLists.txt`
- Event integration tests under `tests/integration/events/`

## Rules

- Prefer deferred event dispatch when callbacks could mutate active systems.
- Avoid importing whole namespaces from public forwarding headers.
- Keep payloads lightweight and explicit.

## Update This File When

- Event names, payloads, dispatch order, or subscription lifetime behavior changes.
