# UI Progress Tracker

## Current State

Architecture has been reworked to favor a component-attached UI document with two space modes, screen and world. The initial UI module scaffold now exists under `EngineModules/UI`, the UI target now builds successfully, documents, fonts, and behavior assets are resolved through the resource system, XML layouts parse into a reactive document tree, and UI components now sync into a runtime-rendered document map. Font-backed label rendering is now live.

## Decision Log

- UI remains a separate module.
- UI is attached to entities through a data-only component.
- The document tree is owned by the UI module, not the ECS registry.
- The same XML can render in screen space or world space.
- Lua handles behavior, XML handles layout.
- UI documents, fonts, and Lua behavior are loaded through the resource system.
- Parsed UI attributes and text keep raw template values plus resolved binding values.
- `UIComponent` entities now create and refresh runtime UI documents automatically.
- UI click handlers emit UI events and invoke Lua global functions from the behavior script.
- Labels and button text now render from the baked font atlas through textured glyph quads.

## Next Steps

1. Add keyboard focus and navigation for buttons and fields.
2. Add font fallback chains.
3. Later polish queue: more layout primitives (padding, flow stacking).

## Status Checklist

- [x] Reworked the UI architecture plan.
- [x] Identified the ownership model.
- [x] Captured the space-mode decision.
- [x] Created the module scaffold.
- [x] Added dependency wiring.
- [x] Implemented runtime shell.
- [x] Implemented resource-backed document loading.
- [x] Implemented document parsing.
- [x] Implemented reactive binding.
- [x] Implemented screen-space and world-space quad rendering.
- [x] Implemented font-backed label and button text rendering.
- [x] Connected `UIComponent` instances to runtime documents.
- [x] Implemented Lua behavior hooks.
- [x] Implemented text alignment (`textAlign="left|center|right"`).
- [x] Implemented word wrapping (`wrap="true"` with element width as max line width).
- [x] Implemented anchor-based positioning (`anchor="topleft|topcenter|topright|middleleft|center|middleright|bottomleft|bottomcenter|bottomright"`).
- [x] Integration test for layout feature attributes.
