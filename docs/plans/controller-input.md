# Controller input and local multiplayer

Status: Planned. This supersedes the original digital-button scope in
[L5 session 6](editor-project-settings-l5.md#6-controller-support-moved-to-a-dedicated-plan).

## Acceptance criteria

- Xbox and PlayStation controllers have usable mappings for buttons, sticks,
  and triggers. Players can inspect their active mappings and configure
  bindings using native engine and editor tools.
- One player can move with an analog stick and aim with a mouse at the same
  time. Meaningful activity updates that player's preferred device for prompts
  automatically while both input sources remain available.
- Local multiplayer can assign multiple controllers to distinct player slots.
  Assignment is independent of joystick enumeration order. Test joining,
  disconnecting, and reconnecting with held input cleared on disconnect.
- The same project bindings work in editor Play, standalone launch, and a
  relocated export. Existing keyboard bindings remain readable.
- Controller diagrams and device-specific mapping graphics belong to the
  later UI work. Expose mapping and device information for that work.

## Implementation sequence

1. Design device discovery, standardized layouts, and the player assignment
   and reconnect rules. Audit GLFW and its mapping coverage on supported
   platforms before selecting a backend strategy. Define how unknown devices
   report capabilities, and how device identity behaves for two identical
   controllers. The initial code audit found no controller input path.
2. Feed device snapshots through Platform and the embedded editor bridge.
   Extend Systems input actions with digital and analog values, per-player
   bindings, dead zones, and per-player device activity. Specify focus-loss
   behavior and distinguish stick drift from meaningful activity.
3. Extend the binding schema and native/editor tools for capture, inspection,
   and remapping. Update project readers, Play snapshots, and package handling
   together. Expose active per-player mappings and device family for UI prompts.
4. Add deterministic tests with injected input for mixed mouse/controller
   play, simultaneous players, switching, disconnect, reconnect, and focus
   loss. Exercise Xbox and PlayStation hardware, including a two-controller
   session. Record tested models, transport, and platform.

## Validation

Build the changed CTest targets, `sle_editor_native`, and `engine_app`.
Run the project runtime, project Play, and package integrations, plus the editor
suite and the Windows distribution smoke described in
[CONTRIBUTING.md](../../CONTRIBUTING.md#validation). Record actual results and
hardware coverage as implementation proceeds.
