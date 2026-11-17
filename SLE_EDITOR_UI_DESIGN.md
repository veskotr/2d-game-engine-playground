# SLE Editor — Little Forest Compact UI Design Guide

**Status:** Approved visual direction; implementation handoff  
**Product:** SLE (Simple Little Engine) Editor  
**Theme name:** Little Forest / Compact Forest  
**Primary modes:** Dark Forest and Light Forest  
**Audience:** Indie game developers authoring scenes, entities, prefabs, assets, Lua scripts, and engine data.

## 1. Implementation brief for Codex

Modernize the **existing SLE Editor UI**, not the engine itself, to use the compact **Little Forest** design system defined here. Preserve every existing workflow, working control, file format, and backend integration. Implement the visual system as reusable components and semantic theme tokens, so future tools and properties automatically match both light and dark modes.

**Before changing code:** inspect the actual repository, determine its UI framework and architecture, locate the existing theme/styles/widgets, and identify how the editor state is managed. Do not assume the UI is Compose Desktop just because it was previously considered; adapt this specification to the framework actually used. Propose a short staged implementation plan based on the real files, then implement with tests and screenshots/previews of both themes.

The screen already has a menu, Scene/Game tabs, a scene tree on the left, viewport in the center, properties on the right, play controls, and an asset browser at the bottom. **Keep that overall arrangement.** The user wants it denser and more professional, with *more useful information visible at once* and less empty space, while retaining charming indie personality.

## 2. Visual identity

SLE should feel like a **small, welcoming indie game engine with serious development-tool ergonomics**. Think a comfortable workshop, not a game launcher, marketing dashboard, or toy UI.

- Soft forest greens are the brand signature. Use green mainly for selected items, focus, primary actions, enabled state, active tabs, and component icons.
- Base surfaces are restrained warm charcoal or warm ivory, with a subtle green undertone.
- Tight spacing, crisp separators, restrained corner radii, and compact typography convey a tool-like feel.
- Avoid giant buttons, oversized cards, large blank gaps, persistent visual noise, glass effects, gradients, neon colors, or heavy drop shadows.
- Do not tint every panel green; leave most workspace surfaces neutral. Content is more important than decoration.
- Use a **consistent icon family** (prefer the one already in the project). Recommended icon size: 14–16 dp. Never use emoji as production UI icons; the flower shown in the mockup was a temporary placeholder.
- Keep an eventual SLE brand icon separate from generic component icons. The icon concept is a small geometric game tile combined with a sprout/leaf, but its final artwork is a separate deliverable.

## 3. Semantic color tokens

Create a single, typed theme system. **Components must use semantic tokens, never literal colors sprinkled through feature code.** The hex values below are starting values; visually verify text/control contrast and adjust only at the token source.

| Token | Dark Forest | Light Forest | Usage |
|---|---|---|---|
| `background` | `#1B241E` | `#F2F6EF` | Editor/workspace background |
| `surface` | `#232E26` | `#FFFEFA` | Tree/inspector/asset panels |
| `surfaceRaised` | `#2A382D` | `#E8F1E5` | Headers, nested panels, hovered neutral elements |
| `inputBackground` | `#18211B` | `#F7FAF5` | Fields, combo boxes, numeric editors |
| `viewportBackground` | `#121B16` | `#E7EEE5` | Editor grid backdrop (not game content) |
| `textPrimary` | `#E4EEE5` | `#283D2D` | Main labels and values |
| `textSecondary` | `#9BAEA0` | `#718476` | Secondary labels and metadata |
| `border` | `#3A4A3D` | `#C9D8CB` | Separators and control outlines |
| `accent` | `#88CB91` | `#328653` | Focus/active indicators/icons/primary actions |
| `selectedBackground` | `#355541` | `#DBEEE0` | Selection in hierarchy, lists, tabs |
| `accentOnPrimary` | `#17251A` | `#FFFFFF` | Foreground on accent-filled actions |

Additional required *semantic* tokens (choose and verify both theme values before use):

- `hoverBackground`, `pressedBackground`, `focusRing`
- `disabledText`, `disabledBackground`, `placeholderText`
- `error`, `warning`, `success`, `info` and, where needed, matching subdued backgrounds
- `separator`, `tooltipBackground`, `tooltipText`
- `viewportGridMinor`, `viewportGridMajor`, `viewportXAxis`, `viewportYAxis`, `selectionOutline`

Do **not** use brand green as the only way to distinguish destructive actions, validation errors, or warnings. Check at least WCAG AA contrast for essential small text (4.5:1) and meaningful non-text boundaries/icons (3:1 where applicable), including hover, disabled, focus, and selected states.

Theme switching requirements:

1. Both themes have the **same layout, component states, and behaviors**.
2. The setting changes the complete editor without restarting; no mixed dark/light widgets.
3. Persist the user's choice. Support System / Dark / Light if the platform supports OS preference detection; otherwise Dark / Light is sufficient.
4. Design for desktop DPI scaling and font scaling. Colors must not be embedded in scene data or game output.

## 4. Density and typography

The default editor density is **Compact**, not touch-first. Use platform-independent dp/pt equivalents, accounting for the actual UI framework's scaling rules.

| Element | Compact target | Notes |
|---|---|---|
| Main toolbar | 30–32 dp high | Group tools; avoid vertical blank padding |
| Regular toolbar button | 26–28 dp high | Icons 14–16 dp; concise text optional |
| Input / dropdown / small button | 24–26 dp high | Visually aligned across field types |
| Scene tree row | 22–24 dp high | Indentation and expander without wasted width |
| Asset list row | 22–24 dp high | Show name/type/status in columns |
| Inspector property row | 28 dp typical | Single-line label + editor |
| Collapsible component header | 26 dp high | Small expander and trailing actions |
| Panel inner padding | 6–8 dp | Avoid nested large card padding |
| Default gap between controls | 4 dp | Use 4 dp spacing rhythm |
| Main content text | 12–13 sp | Readable at 100% and scaled UI |
| Secondary/meta text | 11–12 sp | Do not use tiny text for critical information |
| Icons | 14–16 dp | Same visual stroke weight |
| Corner radius | 3–5 dp controls; 5–7 dp larger surfaces | Mostly square/compact |
| Borders | 1 dp subtle | Prefer borders over heavy shadow |

Define metrics centrally (for example, `EditorDensity.Compact` and `EditorDensity.Comfortable`). **Comfortable** should enlarge text, controls, and row heights consistently without introducing a separate set of widgets. Do not let compact mode override accessibility scaling or clip text.

Use a restrained hierarchy of typography: clear panel headings, readable property labels, slightly muted metadata, and monospace only where beneficial (coordinates, code, paths, numeric values). Do not use uppercase for every label.

## 5. Layout: preserve structure, use space better

The existing layout is a good foundation:

```text
Menu / main toolbar
Scene / Game tabs
┌─────────────────┬──────────────────────────┬──────────────────┐
│ Scene hierarchy │ Scene / Game viewport    │ Inspector        │
│                 │ (fills remaining area)   │                  │
├─────────────────┴──────────────────────────┴──────────────────┤
│ Assets browser (resizable bottom pane)                         │
└────────────────────────────────────────────────────────────────┘
Status bar
```

The header, tabs and playback controls should occupy **a compact horizontal band**, rather than leaving a large empty strip above the viewport. Prefer putting Play / Pause / Stop / Run Project together. Use brief labels or tooltip-backed icon buttons where intuitive.

Suggested starting widths at normal desktop scale:

- Left hierarchy: **220–260 dp** (resizable).
- Right inspector: **300–360 dp** (resizable).
- Center viewport: flexes to fill remaining space; prioritize available canvas area.
- Assets pane: **160–240 dp** initially, user-resizable and collapsible.

Respect existing window sizes and small-screen constraints. Panels should have stable minimum sizes and **splitters** if supported. Save user panel widths/heights when feasible. Make the panel system ready for future docking, but **do not implement a complex new docking engine** just for this reskin.

### Scene hierarchy

- Compact list/tree rows with consistent indentation, expand/collapse arrows, and clear active selection.
- Distinct, consistent icons for scene, entity, camera, sprite, collider, script, container, and prefab instance; use semantic/icon tokens, not hardcoded symbols.
- Support existing click, expansion, keyboard, and selection workflows unchanged.
- Add action slots to the reusable row model for future hide/show, lock, rename, context actions, and prefab indicators. Only connect actions to the engine once those capabilities exist.
- When future features add nested components or child entities, use the same tree row component.
- Show hover and keyboard focus separately from selection; avoid relying on green color alone.

### Central viewport

- Give the canvas the space recovered from oversized toolbar/header areas.
- Treat canvas grid, axes, scene overlay and editor selection handles separately from UI chrome.
- Toolbars can sit just above/around the canvas with tight 26–28 dp controls for selection, pan, zoom, fit, grid and play where these functions exist.
- UI theme must **not change rendered game pixels**; scene/game rendering has its own background/content.

### Inspector

The inspector is the most important density improvement. Use a consistent two-column property row: **label column ~34%, editor column ~66%**, with responsive resizing and ellipsis/tooltip for long labels. Labels and values remain vertically centered. Avoid the old pattern of a label *above* each input unless a property genuinely needs a multi-line editor.

Example:

```text
Player                                   [⋯]
Name       [Player                      ]
Enabled    [✓]
▾ Transform                              [↺⋮]
Position   [X 128         ][Y 64         ]
Rotation   [0 °                         ]
Scale      [X 1.0         ][Y 1.0        ]
▾ Sprite Renderer                        [ ⋮]
Texture    [player.png               ▾  ]
Z Index    [2                           ]
▸ Lua Scripts                            [ ⋮]
[+ Add Component]
```

- Component sections collapse and expand; remember section state per editor session, and optionally across sessions.
- Keep headers ~26 dp and rows ~28 dp; no large card or form padding.
- Inspector components should be generated from reusable property definitions/widgets rather than hand-building a new style in each feature.
- A property row can have optional help, default/reset, validation, read-only, and prefab override indicators without changing its basic alignment.
- For prefabs: reserve space for override markers and reset actions. Wire them up only when prefab semantics are supported; do not pretend unsupported operations work.
- When narrow, progressively adapt (for example, editor in next line for unusually complex properties) rather than clipping.

### Assets browser

- Default to a **compact list/details view**: name, type, and optional size/modified/status, with a small type icon.
- Support a future icon/grid view without building a separate theme system.
- Use compact folder tree, import/new folder/search tools and context menus where those functions exist.
- Share hierarchy row, selection and icon styles with other tree/list views.

## 6. Reusable editor component library

Implement or consolidate a small library of widgets with consistent dimensions, states, colors and keyboard behavior. Use project naming conventions; conceptual component names below are examples, not required exact class names.

| Conceptual component | Responsibility |
|---|---|
| `EditorTheme` / `ThemeTokens` | Semantic colors, typography, dimensions, densities, shapes |
| `EditorPanel` / `PanelHeader` | Consistent pane container, heading and action slots |
| `EditorButton` / `IconButton` | Primary, neutral, toggle, disabled, warning/destructive variants |
| `EditorTextField` | Text edit, placeholder, validation, read-only/disabled states |
| `EditorNumberField` | Numeric entry, constraints, decimal precision, step control |
| `EditorVector2Field` / `Vector3Field` | Aligned X/Y(/Z) numeric editors with axis labels |
| `EditorCheckBox` / `EditorSwitch` | Boolean state; clear focus/disabled behavior |
| `EditorSelect` / `ComboBox` | Enum and resource selection; search where applicable |
| `EditorPropertyRow` | Shared label/editor grid and help/override/error action slots |
| `EditorInspectorSection` | Collapsible component header and group content |
| `EditorTreeRow` / `EditorTree` | Hierarchical scene, assets, or other nested entities |
| `EditorTabs` | Selected/hovered/keyboard tab states |
| `EditorTooltip`, `ContextMenu` | Consistent auxiliary UI |
| `EditorSplitPane` | Resizable panes without feature-specific sizing code |
| `EditorStatusBar` | Lightweight feedback and async task status |

### State model for every control

At minimum: `normal`, `hovered`, `pressed`, `focused`, `selected` (where applicable), `disabled`, and `invalid` (where applicable). Some states overlap, e.g. focused + selected. Design these intentionally and test keyboard navigation.

Do not implement decorative controls that *look clickable* but do nothing. Preserve existing commands/actions. For capabilities not yet supported, render disabled with a useful tooltip, or omit until implemented.

### Numeric editor behavior

- Click/focus and type a value; Enter commits, Escape reverts uncommitted edits; defined focus-loss behavior (commit if valid, otherwise restore or show validation).
- Respect negative values, floats, minimum/maximum, precision, and locale-independent serialization to JSON.
- Keep the display responsive during editing: editing intermediate states like `-`, `0.`, and an empty selection should not corrupt engine state.
- Support stepping with keyboard (and optionally label-drag) only if easy to implement safely; configurable step size and optional Shift/Alt modifiers. Avoid accidentally writing a new undo action for every mouse pixel.
- Group edits into sensible undo/redo transactions if the engine/editor has an undo stack.

## 7. Architecture rules for new features

The UI guide must remain useful when SLE gains a prefab editor, animation editor, scripting panel, settings, material/resource inspector, scene graph operations, and debugging tools.

1. **One source of truth for tokens.** No arbitrary hex codes, ad hoc control heights, or duplicated light/dark branches inside features.
2. **Separate UI from editor/engine logic.** Widgets emit intents/commands and observe state. They must not directly own serialization, Lua runtime state, renderer lifetime, or file locking.
3. **Data-driven property rendering.** Reuse property metadata for labels, types, ranges, validation and editor selection. It should be possible to add a new component type without copying the entire inspector layout.
4. **Preserve source files as truth.** SLE game definitions remain JSON/XML/Lua. UI editing must preserve valid fields, unknown/forward-compatible keys where appropriate, prefab relationships and file integrity.
5. **Support read-only, dirty, overridden and validation states.** These are independent states used by many future tools; reserve a consistent icon/marker system.
6. **Reusable interactions.** Tooltips, context menus, search, selection, hotkeys, undo/redo integration and async operations should follow shared patterns rather than per-panel inventions.
7. **Feature modules should not import concrete global colors.** Consume a theme/token service or the framework's equivalent.
8. **Compatibility first.** Do not replace functioning scene logic or editor-to-engine communications during UI migration unless required, tested, and separately approved.
9. **No speculative full feature implementations.** It is useful to create extension points for future systems, but do not pretend prefab overrides, docking, drag-drop, or advanced numeric editing exist until they do.

If the editor uses Compose Desktop, implement the theme and density via stable reusable design-system composables and appropriately scoped theme locals/state. If it uses another toolkit, achieve the **same semantics through its idiomatic style/theme mechanism**. Avoid adding a new UI framework just to implement this design.

## 8. Suggested implementation sequence

Codex should map the actual project files first and adjust steps to their existing architecture. This is a *phased guide*, not an instruction to rewrite the editor all at once.

1. **Audit current UI:** identify framework, screens, style literals, component reuse, current interactions, persistence and tests. Capture baseline screenshots; inventory what must remain functional.
2. **Establish theme tokens:** implement dark/light semantic colors, a shared density/typography scale, and a working theme switch. Migrate existing roots/panels without altering data flows.
3. **Build/consolidate reusable controls:** buttons, text/number/vector fields, checkboxes, selects, section headers, property rows, tree/list rows and tooltips. Add tests for critical control behavior.
4. **Compact current layout:** reduce giant header gaps, align play controls, make viewport fill space, use compact tree and assets rows, and implement two-column inspector fields. Preserve commands.
5. **Migrate existing inspector properties:** use reusable property definitions and common control states. Avoid regressions in serialization or undo behavior.
6. **Polish and future-proof:** keyboard focus, hover/selected/disabled styling, scaling, error presentation, persistence of theme/layout/sections, and a short developer README explaining how to add new themed features.

Request review after each coherent phase; avoid a massive styling rewrite touching unrelated engine code.

## 9. Acceptance criteria

- [ ] Existing editor workflows still function (scene selection, play controls, asset actions, inspector editing, tabs, rendering) wherever they already worked before.
- [ ] Both **Dark Forest** and **Light Forest** fully theme all visible editor chrome, fields, trees, lists, dialogs and menus that are in scope.
- [ ] Theme switching is consistent and preference persistence works.
- [ ] Default Compact density meets the dimension targets; a future Comfortable density is possible from shared metrics rather than a rewrite.
- [ ] Most property labels sit next to their inputs; a typical Transform group shows position, rotation and scale with minimal wasted vertical space.
- [ ] A 1366×768 window remains usable without obvious control clipping or giant blank regions. The layout also works at 1920×1080 and common desktop scaling factors (100%, 125%, 150%, 200%).
- [ ] Tree and inspector selection, hover, focus, disabled and validation states are visually distinct and keyboard-accessible.
- [ ] New controls can be added without selecting separate hex values, padding and hover styles by hand.
- [ ] UI-level tests and relevant existing tests pass. Screenshot or manually inspect both themes and key control states before claiming completion.
- [ ] The repository contains a concise `UI_DESIGN.md` / design-system reference and a `HOW_TO_ADD_UI_FEATURES.md` (or appropriate equivalent) showing extension patterns, examples and do/don't guidance.

## 10. Future-feature checklist for contributors and Codex

For **every new editor feature** (dialog, dock, inspector group, tree, wizard or editor tab):

- [ ] Use `EditorTheme` semantic tokens and density metrics only.
- [ ] Prefer existing components; create a reusable widget if a genuinely new interaction is required.
- [ ] Verify dark/light, hover/focus/disabled/error, keyboard use and desktop scaling.
- [ ] Keep controls compact, labels horizontal, and free space for primary content.
- [ ] Reuse standard selection, validation, tooltips, context menu and command/undo patterns.
- [ ] Keep serialization, editor state and engine processes outside the view components.
- [ ] Document new reusable patterns in the UI guide and add a small representative example/test.

**Design summary:** Little Forest is **green and friendly in identity; compact, legible, and capable in operation**. Preserve SLE's lightweight spirit while making the editor comfortable for hours of actual game development.
