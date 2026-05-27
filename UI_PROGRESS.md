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

1. Expand widget layout rules beyond absolute `x/y/width/height`.
2. Add a small UI example scene and layout asset.
3. Add Lua-facing APIs for mutating UI bindings directly.
4. Add keyboard focus and navigation for buttons and fields.
5. Later polish queue: text wrapping, alignment controls, and fallback font chains.

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
