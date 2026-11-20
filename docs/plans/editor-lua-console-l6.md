# L6 Lua console implementation plan

Implement roadmap item 3.7 using the existing [editor Lua host design](editor-lua-host-l6.md).

- Extend the isolated editor host with a text-only console entry point. Pass a detached snapshot, collect bounded output, and decode an ordered edit list. Cover output, sandbox limits, and invalid results in `integration_editor_lua_host_path`.
- Stage stopped edits against the captured scene, validate the candidate through the native scene loader, and publish one history entry. Cover multi-edit undo/redo and failure atomicity in the editor unit tests.
- Route Play input through the viewport owner and JNI to the active ScriptEngine. Use the existing console log queue for diagnostics, with coverage for errors and continued runtime operation.
- Add multiline input, bounded history, and the Run Lua command to the editor. Verify the native targets, Gradle tests, and the stopped and Play flows in a console smoke.

## Results

- Stopped commands run in the isolated host and apply scene and generated text asset changes through one scene history entry. The editor compares the captured revision before applying edits.
- Play commands run on the native viewport owner thread in the active game VM. Output and Lua diagnostics go through the existing Console queue.
- **View → Run Lua…** opens the Console and focuses its input. The command is registered with the editor's standard commands. The searchable command palette is tracked separately in roadmap item 1.8.
- Windows Debug builds succeeded for `sle_editor_native`, `integration_editor_lua_host_path`, `integration_multiple_behaviours_path`, and `integration_lua_runtime_lifecycle_path`. The focused CTest run passed all three integration tests.
- `gradlew.bat test run --offline --args=--console-smoke` passed the editor tests and the graphical Console smoke. The smoke exercises stopped execution through JNI, scene Undo, output from the paused game VM, and Play error reporting.
