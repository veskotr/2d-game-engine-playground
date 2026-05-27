# UI Implementation Plan

## Goal

Add a UI module that renders through the existing renderer, receives input through the event system, and loads XML layouts plus Lua behavior through the resource and scripting layers.

## Architecture Decision

UI should be entity-attached, component-based, and module-owned.

- The ECS entity is the ownership anchor for a UI document.
- The UI module owns parsing, binding, layout, and widget runtime state.
- The renderer only receives draw commands.
- The scene and event bus remain the integration points with the rest of the engine.

This keeps UI out of the core ECS model while still letting gameplay attach UI to world objects when needed.

## Space Model

UI needs two placement modes:

- Screen space, for HUDs, menus, and overlays.
- World space, for in-world labels, dialog boxes, health bars, and other diegetic UI.

The plan is to represent this at the root document level through a data-only component. Child widgets inherit the space mode from the root.

Planned behavior:

- Screen-space roots resolve against the viewport and UI canvas.
- World-space roots resolve against the owning entity transform and then project into render space.
- The same XML document can be reused in either mode.

## Component Boundary

The UI module should stay component-based, not engine-object based.

Recommended root component:

- `UIComponent`
  - layout XML asset path
  - Lua behavior asset path
  - space mode (`screen` or `world`)
  - visibility and z-order
  - optional binding source name or scope key

The component should remain pure data. The module runtime performs parsing, binding, and updates.

## XML And Binding Model

XML defines structure and declarative attributes, Lua defines behavior.

Planned binding syntax:

- Literal attributes remain plain strings or numbers.
- Reactive attributes use a token syntax inside strings, for example `"Score: {{player.score}}"`.
- The UI runtime parses bindings, subscribes to changes, and marks affected nodes dirty.

Planned binding behavior:

- No manual per-attribute rebinding in gameplay code.
- Changes to bound data propagate through a binding context.
- Dirty widgets recompute only when the source data changes.

This is the default loading path for the current UI shell.

Current shell status:

- XML layouts are parsed into a generic UI element tree.
- Attributes and text keep both raw template values and resolved values.
- Binding updates are refreshed from the document binding context during `UISystem::update()`.
- `UIComponent` entities are synchronized into runtime documents each frame.
- Screen-space and world-space UI quads are submitted through the existing renderer.
- `onClick` handlers emit UI events and invoke Lua global callbacks from the behavior asset.
- Font atlas glyph quads are now submitted for label and button text.

## Dependencies

The UI module should rely on these engine layers only:

- `sle::events` for input and UI interaction events.
- `sle::renderer` for quad and text draw commands.
- `sle::resources` for XML, fonts, and behavior assets.
- `sle::scripting` for Lua behavior callbacks.

External libraries planned:

- Fast XML for XML parsing.
- stb_truetype for font rasterization.

## Implementation Phases

### Phase 1: Module Scaffold

- Add the `EngineModules/UI` module structure.
- Add CMake wiring for the new module.
- Add dependency declarations for XML and font parsing libraries.
- Create the initial UI component and runtime headers.

### Phase 2: Layout And Document Runtime

- Parse XML into an internal widget tree.
- Load XML and Lua assets from the resource system.
- Support widget IDs, inheritance, and simple layout containers.

### Phase 3: Space and Rendering

- Render screen-space documents through a UI canvas path.
- Render world-space documents using the owning entity transform.
- Use the existing renderer commands for quads and text.

Current implementation note:

- Quad submission is live for panels and buttons.
- Text is rendered directly as font atlas glyph quads through the UI system.

### Phase 4: Reactive Binding

- Add a binding context with change notification.
- Parse XML attribute bindings into watched expressions.
- Recompute only dirty widgets.

### Phase 5: Lua Behavior

- Bind UI events to Lua callbacks.
- Let Lua update binding state and react to user input.
- Keep Lua out of renderer and scene internals.

### Phase 6: Validation

- Add example layouts for HUD and world-space dialog.
- Add a small smoke test for XML parsing and binding updates.
- Verify screen and world rendering paths separately.

## Open Questions

- Whether the root UI component should store a direct transform follow mode or always read the owning entity transform when `space = world`.
- Whether bindings should target a flat key space or named data scopes.
- Whether the UI module should own a dedicated font atlas cache per document or a shared cache per font size.

## Working Rule For The Next Steps

Proceed with dependency wiring after the first UI component contract is finalized.
