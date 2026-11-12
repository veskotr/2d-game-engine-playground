# Embedded editor viewport implementation plan

Goal: Render the sandbox startup scene directly inside Compose through a native
OpenGL canvas and JNI, retaining the standalone GLFW executable.

Approved architecture: Compose hosts an AWT OpenGL canvas; the canvas owns its
context and buffer presentation. An optional CMake JNI shared-library target links
SLE. Runtime gains an externally hosted, render-only initialization/frame path.
No frame streaming, sockets, native-window reparenting, or competing renderer.
Windows x64/JDK 21 is the first validated platform. This phase is edit preview,
not Play mode: scripts, animation, audio and physics do not advance, and runtime UI
behaviours are not executed. Sandbox means its current startup JSON scene,
assets/scenes/npc_zone_demo.json, rather than copying its imperative scene builders.

## Tasks

- [x] Prepare editor: pinned AWT/LWJGL dependencies, JNI declarations, native
  library discovery, a Compose-hosted canvas, visible failure reporting, and a
  finite smoke mode. All context operations and destruction use the AWT event
  thread; destroy SLE before the canvas peer/context is removed.
- [x] Prepare engine: optional SLE_BUILD_EDITOR target (OFF by default), reuse
  Runtime services with a borrowed current context, explicit viewport sizing and
  render-only frames. Standalone init/run remain available without Java.
- [x] Add integration coverage: externally created context, sandbox load,
  framebuffer pixels, resize, disabled simulation, invalid dimensions and repeated
  teardown/recreation. JNI checks session handles, thread ownership and exceptions.
- [x] Build editor and native target; run focused CTest plus existing runtime
  regression/smoke tests, and finite embedded GUI smoke. Verify actual pixels,
  not just a successful DLL load. Record failures and unavailable checks.
- [x] Review lifecycle/build changes and document commands and remaining limits.

## Interfaces and responsibilities

Runtime::initEmbedded() borrows an already-current, GLAD-loaded context.
Runtime::renderEmbeddedFrame(int width, int height) renders with physical-pixel
viewport dimensions. It never polls GLFW or swaps buffers. Runtime owns resources,
not the host context. Its destructor must run while that context remains current.
The preview uses a logical 480x270 camera for the existing sandbox startup scene.
JNI provides create/render/destroy with opaque validated session tokens; only one
session is allowed because Resources/Input currently have process-wide state.
Kotlin hosts its OpenGL surface through SwingPanel and AWTGLCanvas. A Swing timer
requests rendering without a second render thread. Errors stop rendering and are
shown to the user. Native calls must not escape C++ exceptions across JNI.

## Review focus

- Partial initialization and scene-load failure must release GPU objects with a
  current context; repeated close/reopen must not retain native sessions.
- Zero-size/minimized surfaces do not render; resize uses physical pixel sizes.
- Removing the AWT peer releases Runtime first; no stale render timer callbacks.
- Dev DLL support is opt-in; ordinary engine builds require no JDK/JNI.
- Edit preview must not execute scene scripts or mutate authored physics state.

## Validation

Windows CMake configure/build with SLE_BUILD_EDITOR=ON and JAVA_HOME pointing to
JDK 21; focused integration_runtime_embedded_preview_path test; existing runtime
transform regression and smoke label. Gradle build and run --args=--smoke-test
from tools/entity-editor. GUI smoke requires a Windows desktop and OpenGL 4.3.

## Progress

Implementation is complete. Existing worktree contains broad unrelated edits;
work is in place to preserve and build against that actual source, without commits
or resetting any files. Plan approval is supplied by the user's instruction to
wire the agreed direct-rendering design now.


## Validation results (2026-11-11)

- Windows CMake configure: `-DSLE_BUILD_EDITOR=ON -DJAVA_HOME=C:/java/jdk-21.0.2`.
- Built `sle_editor_native`, embedded regression, standalone transform/physics
  regression, smoke sentinels, `example_sandbox`, `engine_app`, then the full
  Debug solution with `--parallel 4`; builds succeeded.
- Missing embedded Runtime API was observed as the native regression's initial
  compile failure before implementation.
- Focused embedded/standalone regressions: 2/2 passed. Smoke label: 2/2 passed.
- Full `ctest --test-dir build/debug -C Debug --output-on-failure`: 62/62 passed,
  including 8 windowed tests, no skips.
- Initial Compose GUI smoke rendered/resized the sandbox successfully and
  captured `tools/entity-editor/build/reports/embedded-smoke.png`.
- Stronger GUI lifecycle test reproduced an OpenGL context leak in the pinned
  lwjgl3-awt 0.2.3 teardown: WGL accepted deletion after AWT peer removal.
  Explicit host context deletion was added after SLE destruction and before the
  library clears its context handle. Final verification recorded below.
- JNI smoke now checks duplicate sessions, invalid dimensions, wrong-thread
  calls, stale handles, double destruction and same-context session recreation.
- Independent read-only review found no confirmed engine/JNI correctness issues.
  An unconfirmed context-loss concern remains: if cleanup itself throws, it can
  bypass the original error display. Ordinary lifecycle paths are tested.

Decisions: work in the supplied dirty checkout so actual current source and assets
are used; no commits or worktree reset. Scope stays render-only as planned.
No UI layout framework, gameplay control API or entity persistence is added yet.

Final verification: Gradle `build run --args=--smoke-test` passed after the
context cleanup fix. Output included `EMBEDDED_SESSION_GUARDS_PASS`,
`EMBEDDED_SMOKE_PASS 952x499 red=2783 green=3063`, and
`EMBEDDED_CONTEXT_CLEANUP_PASS`. The framebuffer PNG was visually inspected.
