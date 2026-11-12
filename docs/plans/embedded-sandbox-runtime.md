# Run the complete sandbox inside the editor

The user clarified that the embedded sandbox must execute normal engine code,
including Lua, physics, animation, audio and XML UI. The previous render-only
milestone was too narrow. Keep that explicit preview API for future edit mode,
but make the editor's default viewport a running sandbox now.

- [x] Add a deterministic hosted runtime regression proving scripts/input,
  animation, physics, UI behaviour, lifecycle events and scene switches execute.
- [x] Extract the existing per-frame system work into one shared Runtime method,
  preserving order and profiling. Standalone owns GLFW events/presentation;
  hosted execution uses the supplied dt/current context and host input events.
- [x] Bridge AWT keyboard/mouse/focus events into the existing Input state; clear
  transient input before applying queued events. Release held input on focus loss.
- [x] Enable embedded audio initialization, run shared systems through JNI and
  verify the sandbox via native regression and real GUI/input smoke.
- [x] Rebuild full native solution, run CTest and Gradle GUI smoke; update docs,
  review the changes and launch the interactive sandbox.

Validation: tests use explicit fixed dt for hosted frames, test-script errors
must fail verification, no GPU readback in normal presentation. Shared update
order remains events/chunks/animation/audio/scripts/state machines/physics/world
transforms/render/XML UI. Preserve pre-existing dirty checkout and standalone
builds. Host frame dt is bounded by the editor to avoid huge resume steps.

No new renderer, Lua API implementation, physics loop or gameplay logic in Kotlin.
The viewport and scripts use the existing engine services. Editor controls such
as edit/play/pause/document snapshots remain a future UI task.


## Playback controls (user steering)

Add Run/Resume, Pause and Stop buttons. Pause keeps the current scene/UI visible,
suspends audio and skips all gameplay/event/UI callback updates. Stop destroys
Runtime in context and reloads the startup scene, paused before its first gameplay
tick. Run after Stop starts that fresh session. Viewport focus filters keyboard
and mouse events; focus loss clears held state. Host event codes are translated
into existing Input identifiers, never synthesized OS key presses.

## Implementation and review

- Implemented a shared Runtime::advanceAndRender sequence for GLFW and embedded
  hosts. Embedded dt is supplied by the host and validated; standalone profiling
  and frame order remain intact.
- AWT listeners feed the same Input state transitions as GLFW callbacks; a JNI
  queue applies events after clearing transient state at each frame boundary.
- Pause uses render-only presentation plus cached/prepared XML UI without clicks
  or behavior execution. Initial stopped UI defers behavior execution until Run.
- Review found unfocused mouse motion and missing stopped entity UI; both were
  fixed. Regression covers stopped UI pixels, behavior initialization and click
  execution only while running. Original magenta-pixel assumption was corrected
  after diagnostics showed the existing untextured panel renders black; the test
  checks panel presence against background instead of changing the renderer.
- The earlier preview-only API is retained; editor default is now live gameplay.

Validation in progress; final results recorded below. Pre-existing edits are
preserved, and no commits/reset/merge actions are part of this work.


## Final verification (2026-11-11)

- Windows CMake full Debug solution build succeeded (`--parallel 4`).
- Full CTest suite: 63/63 passed, including hosted Lua/input/animation/physics,
  paused/resumed state, scene switching, initial stopped UI pixels, deferred UI
  behavior, running clicks and paused click suppression. Nine windowed tests ran.
- Gradle `build run --args=--smoke-test` succeeded with session guard, pause frozen
  image, Paused/Running/Stopped/Running transitions, sandbox pixel and context
  cleanup markers. Scripts initialized and destroyed through the engine lifecycle.
- Documentation links checked. Broader git whitespace output contains pre-existing
  CRLF modifications; no unrelated files were reset or rewritten.
- Interactive editor relaunched after verification. Native pause stops the audio
  mixer; actual audible output and every physical key/mouse combination were not
  manually audited. Wheel/text input remain outside the existing Input API.
