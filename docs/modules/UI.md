# UI Module

## Responsibility

XML-driven UI documents, layout parsing, bindings, and UI rendering support.

## Owns

- UI document model
- XML layout parser
- UI resources and font atlas integration
- binding context
- UI events and runtime document state

## Dependencies

- Core
- Events
- Renderer-facing draw support
- Resources
- Scene component integration through systems

## Important Paths

- `EngineModules/UI/include/sle/ui/`
- `EngineModules/UI/src/`
- `EngineModules/UI/CMakeLists.txt`
- `docs/topics/UI.md`

## Rules

- UI documents are owned by the UI module, not by ECS component storage.
- `UIComponent` attaches UI assets and behavior to entities.
- Lua handles behavior; XML handles layout data.

## Update This File When

- UI layout attributes, binding behavior, click behavior, rendering, or document lifecycle changes.
