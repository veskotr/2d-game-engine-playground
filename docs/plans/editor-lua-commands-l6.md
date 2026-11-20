# L6 slice 3 — project Lua commands

Implement roadmap item 3.8 under the [approved host design](editor-lua-host-l6.md).

1. Extend the bounded editor Lua host and JNI adapter. Each `Editor/*.editor.lua`
   file returns a dense list of command descriptors. Read declarations separately
   from callbacks. Evaluate predicates against a detached scene snapshot and
   recheck the predicate in the same sandbox as command execution.
2. Discover bounded, direct files under the project's Editor directory. Store the
   user's trust decision outside the project, keyed by project ID and canonical
   descriptor path. Expose trust changes in Project Settings.
3. Register descriptors in the existing editor command registry. Show their
   menu groups and route shortcuts through the normal keymap. Report individual
   registration errors in the Console. Reuse the stopped-console transaction and
   reject results after scene, selection, asset, project or trust changes.
4. Exercise the native host integration, editor unit tests and Console smoke
   where the build environment permits. Document the Lua API and trust controls.

## Result

Implemented the bounded host and JNI entry point, project-local command
registration and editor-local trust controls. Commands load on project open and
use the stopped Scene workspace's Console edit transaction.

Validated on the local Linux build with:

- `cmake --build /tmp/sle-l2-linux --target integration_editor_lua_host_path sle_editor_native -j 4`
- `ctest --test-dir /tmp/sle-l2-linux -R '^integration_editor_lua_host_path$' --output-on-failure`
- `bash gradlew --offline test run --args=--console-smoke -PsleNativeLibrary=/tmp/sle-l2-linux/tools/editor-native/libsle_editor_native.so --console=plain` from `tools/editor` using the cached Gradle home.

The native integration, 397 editor tests, and desktop Console smoke passed.
The smoke exercised Lua discovery, shortcut conflict validation, JNI execution,
and undo and redo together. The interactive trust dialog and custom menu
lifecycle were reviewed in code and remain untested by desktop automation.
