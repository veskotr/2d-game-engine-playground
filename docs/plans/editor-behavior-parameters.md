# Editor behavior parameters implementation plan

The goal is to edit named Lua behavior parameters in scene and entity Inspectors
and see the effective values during Play. The
[approved host design](editor-lua-host-l6.md) defines the sandbox and compatibility
requirements.

1. Add a Scripting metadata reader with a shared Lua table decoder and a
   text-only discovery entry point. Exercise malformed types, disabled APIs,
   independent state, deterministic result ordering, and budget exhaustion.
2. Carry optional scalar overrides through Script component serialization and
   ScriptBehaviourSpec. Merge defaults and valid overrides before named
   behavior init, including reload. Extend behavior and scene round-trip
   integration tests to cover saved overrides and legacy input.
3. Connect discovery through EditorBridge and NativeEngine. Resolve the script
   with the existing project asset paths and refresh results on source changes.
   Build attachment controls from the normalized L2 descriptors. Verify
   identity on edits and use the existing document transaction. Cover edit,
   reset, undo and redo in editor tests.
4. Build the native bridge and affected integration targets with the Debug
   preset. Run the focused CTest targets and editor Gradle tests. Smoke-test
   save, reopen and Play in the Inspector.

## Results

The Inspector slice is implemented. Windows Debug CMake builds succeeded for
`sle_editor_native`, `integration_editor_lua_host_path`,
`integration_multiple_behaviours_path`, `integration_scene_loader_roundtrip_path`,
and `integration_entity_authoring_path`. CTest passed all four integration
targets. The Windows JVM loaded the bridge and resolved and discovered a Lua
fixture in a headless JNI smoke test. The editor Gradle `test` task passed,
including override edit/reset/undo and project save/reopen coverage.

The interactive Inspector save/reopen/Play smoke remains unverified.
