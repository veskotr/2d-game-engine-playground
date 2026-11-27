# Phase 2: tileset v2 and Tileset Editor

Status: implemented and validated 2026-11-27 (uncommitted). Implements T2 / Phase 2 of
[the polish design](polish-tiles-cameras-launcher.md), authorized 2026-11-26.
Existing Phase 0/1 edits and unrelated engine/editor edits must be preserved.
Work in the current checkout: Phase 2 depends on its uncommitted Phase 0/1 code.

## Contract and decisions

- Read v1 unchanged; write new tilesets as v2. V2 adds required `grid` with
  `margin: [x,y]`, `spacing: [x,y]` (nonnegative integers), `columns` (positive
  integer). Pairs preserve the existing slicer's independent axes.
- V2 tile entries optionally add `tags` (unique nonblank strings), `properties`
  (object of string/finite-number/bool), and `animation` (reserved, null only).
  Metadata is validated by editor and runtime; animation playback is out of scope.
- Metadata names/tags use the existing 256 UTF-8 byte name limit; at most 256
  tags/properties per tile; property strings at most 4096 UTF-8 bytes. Unknown
  fields and wrong JSON types are rejected; existing 4096-tile/4-MiB limits stay.
- One on-demand `tilesetEditor` panel, registered once and shared across hosts.
  Atlas zoom and rectangle selection; selected tile(s) name/tags/properties;
  collision handles and Full Cell / From Alpha / None; reslice/add/remove.
- A project-owned session stages edits, supports undo/redo and explicit Save /
  Revert, refuses external-file conflicts, and validates before atomic writes.
  Closing/hiding does not destroy the session; switching assets or projects must
  not silently save/discard dirty edits or let a stale callback write another asset.
- Reslice matches old tile IDs and metadata by grid row/column, including when
  margin/spacing changes. Existing spritesheet regions are retained; append new
  regions so other sprites keep their references. Destructive tile removal is
  explicit and warned about because scenes may reference those tile UUIDs.
- New Tileset keeps the existing slicer entry points and captures its grid;
  creation opens the Tileset Editor. The old Tile Collision action routes to it.
- Collision validation combines scales from registered project scene files and
  the live scene, so Save All at exit and tool Save follow the same rules. The
  tool does not modify scenes that reference removed tiles.
- Atlas zoom is capped by physical canvas size (8190 px per axis), including
  display density, to stay within Compose's packed constraints.
- The existing uncommitted foundation is the implementation baseline. No
  worktree switch, commit, merge, push or publication is part of this task.

## Implementation tasks

1. **Runtime compatibility.** Extend `Systems/src/TileLayerLoader.cpp` strict
   loader for v2 metadata. Regression coverage in the native tilemap scene test:
   v1/v2 both load; malformed grid, metadata and future versions fail.
2. **Editor document and session.** Extend `model/TilesetDocument.kt` with the
   editable source and strict v2 parser/writer. Add a project/assets session for
   metadata, reslice, selection, collision edits, history and conflict-safe save.
   Tests first: preservation, ID stability, invalid metadata, history, conflicts.
3. **Tool UI and entry points.** Register `tilesetEditor`, wire Main and asset
   commands, replace collision modal entry point. Use Little Forest controls and
   notices. Atlas drag selects; tile collision drag stages valid geometry; invalid
   geometry reports an error and preserves the previous shape. Add a desktop smoke
   that creates/opens/edits/saves/reopens and detaches/re-docks the panel.
4. **Validation and docs.** Run editor tests; build and run focused native tests;
   run tool and existing fresh-tilemap smokes. Update editor README, scene format
   reference and tilemap guide. Record actual commands and unavailable checks.

## Review focus

V1 read compatibility; strict validation agreement; stable UUIDs under reslicing;
deleted tiles still referenced by scenes; stale project/session callbacks;
external modification conflicts; history and disk agreement; collision coordinate
orientation and Box2D validation; hidden/detached panel state; new panel handling
in saved layouts. No changes to tilemap placement/streaming (Phases 3/4).

## Validation record

- Native implemented and built on Windows Debug, including the editor JNI bridge.
  `cmake --build build/debug --config Debug --target sle_editor_native
  integration_tilemap_scene_path integration_tilemap_collision_path
  integration_tilemap_render_path --parallel 5` passed.
- Windows `ctest --test-dir build/debug -C Debug -R
  '^integration_tilemap_(scene|collision|render)_path$' --output-on-failure`:
  3/3 passed, including actual v1/v2 pixel parity and collision geometry.
- Linux Release `cmake --build /tmp/sle-runtime-session1 --parallel 6` passed;
  `ctest --test-dir /tmp/sle-runtime-session1 -L integration -LE windowed
  --output-on-failure`: 64/64 passed. Broader `-LE windowed`: 65/66 passed;
  `smoke.runtime_sentinel` could not initialize GLFW on headless Linux.
- First integrated Windows editor `gradle.ps1 test --offline --no-daemon
  -Pkotlin.incremental=false`: 515 tests, 3 skipped, 0 failures/errors. This
  predates final review fixes; rerun required before completion.
- First `--tileset-editor-smoke` failed because a horizontal separator consumed
  the tool body's Row width. Screenshot confirmed only the toolbar was visible.
  Changed that separator to vertical; desktop recheck pending.
- Review identified additional required regressions/fixes: legacy v1 reslice
  grid matching, UUID identity checks, stale callbacks after reload, changed
  atlas dimensions, safe zoom constraints, collision validation at staging and
  quit-time save. Each required fix was subsequently covered and verified.
- These review findings now have focused regressions and fixes. Independent
  model/session/workspace checks passed 22 tests; zoom checks passed 4 tests
  after reproducing the old constraint failures.
- Second desktop attempt reached metadata editing, but its Robot driver pasted
  before Compose applied Select All. The driver now waits for the field selection
  and resulting text. No test success is inferred from this failed attempt.
- A post-reslice desktop regression reproduced a stale Full Cell overlay: its
  cached polygon still had the old cell dimensions. Including the displayed
  polygon in the preview state key and clipping the canvas fixed it. The same
  Robot regression now drags the six-pixel tile's handle and undoes the edit.
- Final Windows command:
  `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/editor/gradle.ps1
  test run --args=--tileset-editor-smoke --offline --no-daemon
  -Pkotlin.incremental=false`: passed. Final JUnit reports: **531 tests, 3 skipped,
  zero failures/errors**. The desktop marker was `TILESET_EDITOR_SMOKE_PASS`.
- `gradle.ps1 run --args=--fresh-tilemap-smoke --offline --no-daemon
  -Pkotlin.incremental=false`: `FRESH_TILEMAP_SMOKE_PASS`.
- `gradle.ps1 run --args=--docking-workspace-smoke --offline --no-daemon
  -Pkotlin.incremental=false`: passed, including interactive docking, overflow
  active-tab visibility, separate-monitor views, playing redock and cleanup.
  The overflow fixture now uses the current native window minimum instead of
  assuming every registered tool can fit 360 px. Its previous run failed at
  the new tool's legitimate 656 px native minimum; no production minimum was
  weakened to satisfy the test.
- Light-theme repetition used the same Tileset Editor smoke with
  `JAVA_TOOL_OPTIONS=-Dsle.smokeTheme=Light`: `TILESET_EDITOR_SMOKE_PASS`.
  The first wrapper attempt had a PowerShell argument-quoting error before app
  launch; the corrected wrapper passed. Both final theme captures were inspected.
  Captures: `tools/editor/build/reports/tileset-editor-{docked,detached}-dark.png`
  and `tileset-editor-{docked,detached}.png` (Light).
- `python3 docs/guide/check_links.py`: 573 references, 13 guide pages, zero broken
  references. Targeted changed-file `git -c core.whitespace=cr-at-eol diff --check`
  and new-source whitespace checks passed. A whole-worktree check still reports
  a pre-existing extra blank line in `ui/EditorDialogAppearance.kt`; left intact.

## Remaining limits

- No separate human/manual editor session, installer or packaged-release run.
  The desktop smokes exercised the actual Windows editor and JNI bridge.
- The Linux GLFW runtime smoke failure above remains an environment limitation;
  the Windows rendering test executed successfully.
- Animation is a reserved null slot. Tilemap assets, multi-tileset layers and
  streaming belong to Phases 3/4 and were not started.
