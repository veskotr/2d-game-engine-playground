# UI

## Model

UI is attached to entities through `UIComponent`, but parsed document state is owned by the UI module.

## Current Features

- XML layouts.
- Screen-space and world-space UI.
- Reactive `{{binding}}` text/attribute values.
- Labels, panels, and buttons.
- Font-backed text rendering.
- Click handlers that emit UI events and call Lua globals.
- Text alignment, wrapping, and anchor positioning.

## Rules

- XML describes layout.
- Lua handles behavior.
- UI runtime state should not live inside ECS component pools.

## Update This File When

- Layout attributes, binding behavior, rendering, input/click handling, or `UIComponent` semantics change.

## Deep Reference

- `docs/UI_PROGRESS.md`
- `docs/UI_IMPLEMENTATION_PLAN.md`
