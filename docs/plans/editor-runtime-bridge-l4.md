# L4 runtime bridge session plan

Status: Sessions 1–6 implemented and verified on Linux.
This plan implements the L4 scope in the [editor workflow roadmap](editor-game-workflows.md#5-build-order-shared-base-layers-then-feature-tracks). Each session delivers a runnable editor workflow and records its own validation.

## Design

Systems owns runtime observations and simulation controls. The existing native
viewport owner calls JNI on its owner thread and publishes immutable data to
Compose. Use bounded polling for messages and profiling samples, and request
entity details for the current selection. Keep file paths, entity IDs and
behaviour keys as structured fields when available. Runtime observations are
scoped to a Play run and scene instance. They expire on scene replacement,
Stop or project close. The Console can retain copied messages after Stop.

[Runtime frame order](../topics/Runtime.md) documents separate advance and
render calls. `advanceEmbeddedFrame` currently exits early while paused.
`sceneTransforms()` already exports keyed transforms for a subset of entities.
The new inspection path can use the same scene identity and transform access,
with explicit support for runtime-spawned entities. Authoring mutations stay
in editor transactions. Existing scene and project formats remain compatible.

## Session 1 — Engine Console and source-linked Lua errors

- [x] Extend `EngineModules/Core/include/sle/core/Log.hpp` with an optional
  typed sink while retaining console output. Make sink installation and
  teardown safe during logging. Forward entries into a bounded editor queue
  with an observable dropped-entry count. The producer must never call JVM code.
- [x] Capture structured diagnostics at Lua load and protected execution
  boundaries. Trace behaviour, event callback and UI paths through
  `ScriptEngine`, the behaviour runtime, and registered callbacks. Preserve
  Lua error handling and stack balance. Resolve asset references via existing
  project resolution. Keep message and traceback visible even when no source
  line is available. A single error should appear once when the outer layer
  adds context.
- [x] Drain small batches through `tools/editor-native/EditorBridge.cpp` and
  `NativeEngine.kt` from the viewport owner. Make queue lifetime independent
  of panel visibility. Add a dockable, read-only Console with severity filters,
  text search, repeat counts, Clear, and a Clear on Play preference. Clear
  before starting the new run so boot errors appear. Open resolvable source
  entries at line through the configured external editor. Treat file and
  line as arguments, including paths with spaces.
- [x] Verify a syntax error, an update error, and an event callback error.
  Cover buffer overflow, reopening a hidden panel, repeated messages, and
  starting a second Play run. Verify the source action against a local script
  whose path contains spaces.

### Session 1 validation

- Built `sle_editor_native` and the five integration targets below in the
  Linux build directory `/tmp/sle-l2-linux`.
- `ctest --test-dir /tmp/sle-l2-linux -R 'integration_(editor_log_queue|multiple_behaviours|lua_event_ownership)_path' --output-on-failure`
  passed all three tests. These cover FIFO batches, overflow and Clear, syntax
  errors, behaviour initialization and update errors, and event callback errors.
- `MESA_GL_VERSION_OVERRIDE=4.6 ctest --test-dir /tmp/sle-l2-linux -R 'integration_(project_play|lua_runtime_lifecycle)_path' --output-on-failure`
  passed both windowed tests. The Lua lifecycle test verifies one report when
  a pending Lua scene builder fails.
- `bash /tmp/sle-l2-s4-gradle.sh test run --args=--console-smoke` passed the
  editor suite and the desktop smoke using the rebuilt Linux bridge. The tests
  cover launching an editor executable with a source path containing spaces,
  capture while the panel is hidden, reopening the panel, repeat counts, and
  stopping and playing again. Windows launch and packaging remain unverified.

## Session 2 — Step and pause on Lua error

- [x] Add a one-shot step request for paused embedded Play. Reuse the normal
  simulation body with a deterministic 1/60 s delta, including the UI update.
  Leave playback and audio paused. Clear queued steps on resume, Stop or
  session replacement. Keep free-running timing and standalone behavior.
- [x] Expose Step through `PlayCommands.kt` and the toolbar. Add a Pause on Lua
  Error preference using the diagnostic path from Session 1. Pause at the
  owner frame boundary after a recoverable failure, surface the first error
  from that frame and highlight the entity when its identity is still valid.
- [x] Assert that a paused frame leaves a counter unchanged, one step changes
  it exactly once, and subsequent renders leave it unchanged. Cover stale
  step requests and pause decisions with the preference enabled and disabled.

Session 2 implementation order:

1. Extend the embedded multiview regression and add a one-shot request to
   `Runtime`. Consume it in the existing advance path with fixed delta.
2. Scope first-error capture to each Play start or advance JNI call and pause
   the runtime after it returns. Carry the message to the editor and resolve
   its runtime entity against the current registry for viewport highlighting.
3. Wire the Step command and preference, extend the Console desktop smoke, and
   record the native and editor validation results here.

### Session 2 validation

- Built `sle_editor_native`, `engine_app` and the seven focused native
  integration targets in `/tmp/sle-l2-linux`.
- `MESA_GL_VERSION_OVERRIDE=4.6 ctest --test-dir /tmp/sle-l2-linux -R 'integration_(embedded_multiview|runtime_scene_play|runtime_embedded_play|project_play|lua_runtime_lifecycle|editor_log_queue|multiple_behaviours)_path' --output-on-failure`
  passed all seven. The multiview regression checks fixed delta, the UI
  update, paused redraws, and stale steps after resume or replacement.
- `MESA_GL_VERSION_OVERRIDE=4.6 bash /tmp/sle-l2-s4-gradle.sh test run --args=--console-smoke`
  passed the editor suite and desktop smoke. The smoke covers both
  preference settings, pausing with the Console hidden, one step with a
  repeated failure, entity-position resolution, and Stop clearing the
  pause details.
- `MESA_GL_VERSION_OVERRIDE=4.6 /tmp/sle-l2-linux/engine_app/engine_app --frames 4`
  completed all four standalone frames. Windows execution and visual
  placement of the error marker remain unverified.

## Session 3 — Read-only Live Inspector

- [x] Introduce a runtime hierarchy snapshot and a separate selected-entity
  component snapshot. Include scene/run identity, runtime entity ID, optional
  authored key, and parent identity. Include spawned entities and query registry
  data only on the owner thread. Start with Transform, SpriteRenderer and
  behaviour attachment identity/status. Reuse L2 presentation for supported
  fields where their types and labels match.
- [x] Use an explicit Live mode for hierarchy and Details during embedded Play.
  Keep runtime selection separate from authoring. Bound refresh frequency,
  refresh after Step, and clear selection when the entity dies, the scene
  switches or the run ends.
- [x] Verify moving and spawned entities update during Play, paused data stays
  readable, and a deleted entity or reused ID cannot show stale Details.
  Verify a runtime transform still uses local coordinates.

Session 3 implementation order:

1. Extend the embedded multiview test with hierarchy, local transform, behaviour,
   deletion and same-name scene replacement assertions.
2. Build hierarchy and selected-component snapshots in Runtime. Validate the
   scene instance and entity before reading Details. Use the JNI session token
   as Play-run identity, since Stop destroys and recreates that session.
3. Poll on the viewport owner every 100 ms during Play and after Step. Show
   Live hierarchy and Details in the existing docks with a separate selection.
4. Rebuild the bridge and multiview target, run the focused CTest and editor
   tests, and document the workflow in SceneEditor.

### Session 3 validation

- Built the native editor bridge, standalone app and nine integration targets
  with `cmake --build /tmp/sle-l2-linux --target … -j 4`.
- `MESA_GL_VERSION_OVERRIDE=4.6 ctest --test-dir /tmp/sle-l2-linux -R '^integration_(embedded_multiview|runtime_scene_play|runtime_embedded_play|project_play|lua_runtime_lifecycle|editor_log_queue|multiple_behaviours|scene_loader_roundtrip|prefabs)_path$' --output-on-failure`
  passed all nine. The multiview test covers authored names and keys, a keyed
  runtime spawn with a parent, local position under a translated parent,
  attachment status, a deleted entity, paused snapshots, Step, and stale
  selection across same-name scene replacement.
- `MESA_GL_VERSION_OVERRIDE=4.6 bash /tmp/sle-l2-s4-gradle.sh test run --args=--console-smoke`
  passed 367 editor tests and the desktop smoke. The smoke selects and reads a
  live attachment while paused, steps it, and checks that Stop clears the
  live snapshot and another Play uses a new run identity.
- `MESA_GL_VERSION_OVERRIDE=4.6 /tmp/sle-l2-linux/engine_app/engine_app --frames 4`
  completed four standalone frames. Validation used the Linux build and display.

## Session 4 — Keep selected Play changes

- [x] Offer Keep Changes while paused. Start with Transform as the first
  eligible component and show a preview of changed fields. Keep an authored
  baseline at Play start. Require the same scene document and authored key,
  check that the entity descends from that authored entry, and reject stale or
  conflicting fields before writing. Exclude spawned entities, runtime-only
  state and unresolved prefab mappings.
- [x] Use existing authored setters and a single L1 undo transaction for a
  selected component or entity. Make field validation and conflict rejection
  atomic. Normal Stop continues to discard runtime state.
- [x] Verify save/reopen and Undo/Redo, conflict with an authored edit after
  Play began, deleted entities, and a scene switch with a matching key in a
  different document.

Session 4 implementation order:

1. Add model regressions for a paused Play Scene preview, one-step Undo/Redo,
   save/reopen, field conflicts, and stale authored/runtime identities.
2. Capture the authored entity owners and Transform fields at Play start.
   Build a local field diff and apply a validated property candidate through
   the existing scene history. Keep linked instances out of the first version
   until their overrides can be mapped explicitly.
3. Tag the viewport snapshot with the Play Scene revision only for its
   original native scene instance. Show the diff in paused Live Details and
   read a fresh snapshot on apply before comparing it to the displayed diff.
4. Run the editor suite and exercise the embedded workflow with the Console
   desktop smoke. Document the Play Scene scope and validation results.

### Session 4 validation

- `MESA_GL_VERSION_OVERRIDE=4.6 bash /tmp/sle-l2-s4-gradle.sh test run --args=--console-smoke`
  passed 371 editor tests and the desktop smoke. The model tests verify
  save/reopen, one-step undo/redo, preservation of an independent authored
  axis, atomic rejection of conflicts and invalid floats, and stale selections
  after deletion or a scene change. The smoke applies a Lua-driven Transform
  after one Step while paused, exercises Undo and Redo, and stops Play.
- `cmake --build /tmp/sle-l2-linux --target integration_embedded_multiview_path -j 4`
  confirmed the native test target was current.
  `MESA_GL_VERSION_OVERRIDE=4.6 ctest --test-dir /tmp/sle-l2-linux -R '^integration_embedded_multiview_path$' --output-on-failure`
  passed with display access, including same-name scene replacement and the
  deletion and spawn provenance checks.
- Validation used Linux. The desktop smoke drove the viewport and apply
  methods directly. Visual inspection of the Live Details control and
  Windows execution remain unverified.

## Session 5 — Explicit Lua behaviour reload

- [x] Add Reload Script during embedded Play for the selected saved behaviour
  source. Resolve it through the active project. Stage compilation before
  replacing a live attachment. Reload each affected attachment at a frame
  boundary and report each result in the Console. Start with explicit
  behaviour restart on successful reload. Preserve the prior attachment on
  syntax/top-level failure. Use existing ownership teardown for old event
  subscriptions and queued callbacks. Audit staging against game API side
  effects before promising rollback for execution or init failures.
- [x] Verify two entities using the same asset, a broken edit followed by a
  corrected edit, reload of an attachment that previously failed to initialize,
  and callbacks before and after reload. Keep automatic watching and state
  migration for a later workflow.

Session 5 implementation order:

1. Extend the multiple-behaviours integration test with syntax and top-level
   failure, restart, callback ownership, and retry after init failure.
2. Stage in a private Lua environment with pure Lua helpers. Enable runtime
   access after staging, retire the old generation through its existing
   teardown, then run the new init. Init can change game state, so an init
   failure leaves the attachment unloaded and eligible for another reload.
3. Resolve the selected attachment in the active project, read the saved
   source once, and reload matching active attachments on the viewport owner
   before simulation. Support Run Project and project-backed Play Scene.
   Reject stale play, scene and attachment selections. Use the staged revision
   for new attachments to that source during the same run.
4. Add Reload Script in Live Details and exercise the embedded workflow.
   Record native and editor validation here.

### Session 5 validation

- Built `sle_editor_native`, `engine_app` and the seven focused integration
  targets with `cmake --build /tmp/sle-l2-linux --target … -j 5`.
- `MESA_GL_VERSION_OVERRIDE=4.6 ctest --test-dir /tmp/sle-l2-linux -R '^integration_(multiple_behaviours|project_play|embedded_multiview|lua_event_ownership|lua_runtime_lifecycle|runtime_scene_play|state_machine_lua_guard_transition)_path$' --output-on-failure`
  passed all seven. Coverage includes multiple users of the same saved source,
  stale scene identity, syntax and top-level errors, init failure and retry,
  subscription turnover with queued events, later attachments, and retired
  legacy guards.
- `MESA_GL_VERSION_OVERRIDE=4.6 /tmp/sle-l2-linux/engine_app/engine_app --frames 4`
  completed four standalone frames.
- `MESA_GL_VERSION_OVERRIDE=4.6 bash /tmp/sle-l2-s4-gradle.sh test run --args=--console-smoke`
  passed 371 editor tests and the desktop Console smoke. The smoke verifies
  paused Play Scene reload through the viewport owner and JNI with a path
  containing spaces. It checks that a broken save leaves the attachment
  loaded and a corrected save runs the new init. Windows execution and direct
  interaction with the Live Details button remain unverified.
- Engine version advanced to 0.24.0 and editor version to 0.18.0.

## Session 6 — Lua profiler

- [x] Collect opt-in timings and call counts for init, update and callbacks,
  attributed to the executing behaviour. Preserve attribution across nested
  callbacks. Record allocator activity in the current execution scope and
  provide VM-wide memory and measurable GC timings. Define how to label GC
  work embedded within Lua calls when it cannot be independently timed.
- [x] Publish bounded frame samples. Show a frame graph and behaviour rows
  sorted by execution time, with entity and source navigation. Clear live
  samples when the run ends. The Lua API performance pass uses measurements
  from this panel and has its own implementation scope.
- [x] Verify attribution with two behaviours and a nested callback, bounded
  sample retention, VM-wide memory accounting, and the disabled capture path.
  Measure overhead with capture on and off using the same scene.

### Session 6 implementation order

1. Instrument ScriptEngine's protected calls using the current execution
   owner. Subtract nested calls from parent wall time and attribute allocator
   traffic to the innermost call. Time explicit Lua collection and step
   requests. Include automatic GC in Lua call time.
2. Publish one native frame snapshot and retain the most recent 120 samples
   in the viewport owner. Reset on scene replacement and Stop. Add a dock
   panel with a capture toggle and entity/source actions.
3. Extend the scripting integration test and run it alongside the native
   bridge and editor checks. Compare capture-on and capture-off timings.

### Session 6 validation

- Built `sle_editor_native`, `engine_app`, and five integration targets with
  `cmake --build /tmp/sle-l2-linux --target … -j 5`.
- `MESA_GL_VERSION_OVERRIDE=4.6 SLE_PROFILE_BENCHMARK=1 ctest --test-dir /tmp/sle-l2-linux -R '^integration_(multiple_behaviours|lua_event_ownership|lua_runtime_lifecycle|embedded_multiview|project_play)_path$' -V`
  passed all five. The multiple-behaviour test covers init and update counts,
  nested callback attribution, scoped allocations, retained VM memory,
  explicit GC timing, and disabled capture.
- The Linux Debug benchmark averaged 0.78 µs per frame with capture off and
  3.84 µs with capture on over 20,000 frames of the same two no-op behaviours.
  This measures native call and sample collection overhead. JNI and panel
  rendering are outside the benchmark.
- `MESA_GL_VERSION_OVERRIDE=4.6 /tmp/sle-l2-linux/engine_app/engine_app --frames 4`
  completed four standalone frames.
- `MESA_GL_VERSION_OVERRIDE=4.6 bash /tmp/sle-l2-s4-gradle.sh test run --args=--console-smoke`
  passed 372 editor tests and the desktop smoke. Editor coverage includes
  bounded retention, scene reset, time ordering, Play/Stop and single-step
  capture, and source path metadata. Windows and direct interaction with the
  profiler panel controls remain unverified.
- Engine version advanced to 0.25.0 and editor version to 0.19.0.

## Validation and handoff

- Follow [native and editor build guidance](../../CONTRIBUTING.md#validation).
  Build `sle_editor_native` and each affected test target before running CTest.
  Start with the affected scripting, embedded runtime and project Play
  integration tests. Use explicit assertions and deterministic frame counts.
- Run `tools/editor/gradle.ps1 test` on Windows for editor regression coverage.
  Exercise new functionality with a desktop smoke or manual walkthrough and
  record unavailable checks. Test existing standalone output and normal
  stop/restart behavior while changing shared runtime paths.
- Update the affected Runtime, Scripting or Scene Editor topic as the contracts
  land. Record commands, results and limitations at the end of each session.
