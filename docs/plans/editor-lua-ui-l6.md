# L6 slice 4 — project Lua drawers and panels

Implement roadmap item 3.9 using the trusted L6 host and existing editor controls.
A project script may return a declaration table with commands, drawers and panels.
Existing command lists still work. Drawer declarations bind a component and field
path to a compatible built-in widget. Small project tool windows show labels and
buttons wired to existing project commands. The editor owns widget rendering and
all edits. Close project windows and remove drawer bindings when trust or the
active project changes.

Extend the native declaration driver, validate metadata in the project script
loader, then wire Details and the Windows menu. Cover mixed declarations in the
native integration test and widget validation in editor tests. Build the native
bridge and run Console smoke to check the shared command path.

## Result

Implemented the declaration format and trust-scoped UI. Panels open as separate
tool windows from the Windows menu. Drawer bindings keep the existing property
validation and undo behavior.

Validated with:

- `cmake --build /tmp/sle-l2-linux --target integration_editor_lua_host_path sle_editor_native -j 4`
- `ctest --test-dir /tmp/sle-l2-linux -R '^integration_editor_lua_host_path$' --output-on-failure`
- `bash /tmp/sle-l2-s4-gradle.sh test run --args=--console-smoke` using the cached
  offline Gradle installation and the rebuilt native bridge.

The native integration test, 399 editor tests, and extended Console smoke passed.
The smoke checks JNI discovery, drawer compatibility, panel command resolution,
and undo and redo. Window interaction and trust revocation were reviewed in code.
