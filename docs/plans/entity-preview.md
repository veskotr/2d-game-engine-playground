# Live entity preview and viewport startup

User request: visualize authored entities instead of JSON, hide auto-generated
UUID keys behind a name-only editor, and fix slow/white sandbox startup.

- [x] Measure existing viewport initialization and first-frame latency.
- [x] Add UUID identity regressions; new documents get stable random keys and
  renaming changes only the display name. Existing keys are preserved on open.
- [x] Add staged in-memory scene registration and an engine entity-preview path
  that displays authored sprites/colliders/zones without gameplay callbacks.
  Use engine render commands for grid, origin and sprite bounds; fit the camera
  without changing authored transforms or the standalone camera contract.
- [x] Keep one native viewport/context across tab changes. Reuse runtime graphics
  initialization; load the sandbox or current authoring document into it. Defer
  audio initialization until after a visible sandbox frame, and paint an immediate
  dark loading surface rather than leaving the AWT default white background.
- [x] Replace JSON/key UI with name, component inspector and live viewport;
  preserve invalid drafts and retain the last valid visual on validation failure.
- [x] Verify preview pixels and edits, no script execution in preview, UUID save/
  open identity, repeated tab switches, sandbox startup/playback and full suites.

Preserve standalone GLFW executable behavior, authoring files and existing dirty
checkout. Preview does not write temporary authored files or simulate gameplay.
Use existing component validators and staged scene lifetime guarantees.


## Findings and implementation

- Baseline native session creation measured 654 ms; audio device initialization
  accounted for roughly 620–650 ms. Graphics initialization without audio is
  about 7–10 ms in this environment. A dark initial surface is presented before
  engine setup, and audio initialization runs only after a sandbox frame swaps.
- One EngineCanvas/SwingPanel remains mounted across tabs. Preview/sandbox changes
  load scene data into the same graphics session; Stop still resets the Runtime.
- Added staged in-memory scene registration and EntityPreviewSystem. Preview
  renders sprites, collider/zone outlines, grid, origin and sprite bounds without
  script/physics updates. Invalid documents retain the last valid scene.
- Preview editing initially queued gameplay lifecycle events. A focused regression
  reproduced this; preview loads now suppress those notifications and keep normal
  gameplay lifecycle ordering unchanged.
- Added UUID defaults, name-only inspector and name-derived file suggestions.
  Existing keys, saved identity and incomplete field drafts are preserved.
- Desktop captures caught a presentation defect that framebuffer-only tests
  missed: lwjgl3-awt 0.2.3 rendered valid pixels but did not reliably present them
  to the Windows canvas. A plain native-window diagnostic reproduced it without
  Compose. Updating the canvas to stable 0.2.5 and its matching LWJGL 3.4.2 fixed
  the actual desktop output. The new context-current disposeGL hook replaces the
  old manual context-deletion workaround. No frame streaming/readback is used for
  normal presentation.
- Temporary raw-canvas/backend/device-context diagnostics were removed after the
  desktop regression passed. Diagnostic crash reports from the old implementation
  were preserved under tools/entity-editor/build/reports rather than removed.

## Verification (2026-11-12)

- CMake full Debug solution build succeeded after native preview changes.
- Full CTest: 65/65 passed, including 10 windowed tests. New preview integration
  checks collider pixels/edits, invalid edit preservation, no script execution,
  lifecycle isolation and transition back to gameplay in the host context.
- Gradle build: 9/9 document/state/identity/filename tests passed.
- --authoring-ui-smoke passed actual desktop viewport pixel checks and live
  collider edit checks; entity-editor.png was visually inspected.
- --preview-smoke passed repeated entity/sandbox switches, sandbox desktop pixels,
  stable UUID, preserved invalid draft and native context cleanup. One OpenGL
  context stayed alive. Sandbox scene-switch measurements were 30 ms then 6 ms;
  initial context/driver setup remains a separate cold-start cost (~0.5–0.7 s).
- --smoke-test passed Run/Pause/Resume/Stop, frozen pause frames, native and desktop
  sandbox pixels, resize and context cleanup.
- --authoring-smoke passed save/open with generated UUID identity and UTF-8 name;
  the exact export also loaded through integration_entity_authoring_path.exe.
- Independent read-only review found no blocking issues in lifecycle, staging,
  input, UUID and document paths. Fit intentionally caps extreme extents to avoid
  overflow; impractically large finite transforms may not be fully framed.

Remaining scope: one authored entity per file; no hierarchy editor, gizmo dragging,
undo/redo or asset browser. Preview camera zoom/fit never changes authored values.
Do not infer universal latency guarantees from measurements on this machine.
