# Editor docking — separate-session handoffs

Use the prompts below in order, one per new session. Each prompt authorizes only
its milestone; the plan itself does not authorize later milestones. If an earlier
milestone is incomplete, finish that prerequisite instead of assuming it passed.

Read the [design](editor-docking-design.md) and
[implementation plan](editor-docking-plan.md). The plan's progress ledger and
dated completion records are the source of current status. All milestones are
**not started** when this document is created, 2026-11-13. Planned test names and
commands do not constitute validation.

Every session must inspect `git status`, preserve all existing changes, and use
the repository instructions. No automatic commits, pushes or cleanup. Follow
the applicable implementation skills, but do not restart product brainstorming:
the user has agreed to the behavior in the design. Revisit only material discoveries
that cannot be resolved within the approved approach. Stop after the requested
milestone and update the completion record.

## Session 1: graphics and tool-window feasibility

```text
Implement session 1 only from docs/plans/editor-docking-plan.md.
Read docs/plans/editor-docking-design.md and the session-1 section first.

The approved goal is one main editor with dockable content and auxiliary windows;
Scene and Game must eventually show one simulation simultaneously. This session
is the bounded opt-in graphics/window feasibility harness, not the full feature.
Preserve all pre-existing worktree changes and ordinary editor startup.

Prove a durable owner GL context independent of visible presenters, two separately
closable windows, bounded frame publication, resize/minimize/peer-recreation and
owner-context shutdown. Measure CPU image presentation first against the plan's
target; try shared-context GPU presentation only if needed. Never create another
Runtime for another view. Verify minimal native tool-window behavior with no app
menu. Record actual hardware, timings, buffer behavior and context identities.

Build/run the opt-in feasibility smoke and ordinary build. Select and document
the presentation mechanism and exact threading/lifetime contract for session 4.
If neither mechanism works, record the reproducible blocker and revised proposal;
do not label feasibility passed. Update the plan ledger and dated completion record,
then stop. Do not implement session 2 in this session.
```

## Session 2: layout model and registration contracts

```text
Implement session 2 only from docs/plans/editor-docking-plan.md.
Read the design, session-1 findings and session-2 requirements. Confirm session 1
has passed; otherwise report/resolve that prerequisite without claiming it passed.

Create the pure docking layout, stable panel/host/group IDs, panel descriptors,
atomic move/tab/split/hide/minimize/restore/group operations and minimum sizing.
Keep Compose factories and native objects out of layout values. Cover uniqueness,
empty-group normalization, canceled/invalid moves, active tabs, restore placement,
synthetic future panel registration and small-display sizing with focused tests.
Leave the default editor shell unchanged.

Use the proposed signatures in the plan and record any finalized constructor
fields/types needed downstream. Run model/sizing tests and the full editor build.
Preserve pre-existing work. Update the plan ledger with actual files, contracts,
commands/results and remaining work; stop after this milestone, without native
runtime refactoring or session-3 implementation.
```

## Session 3: separate runtime advance from draw

```text
Implement session 3 only from docs/plans/editor-docking-plan.md.
Read the design and current milestone records. Check prerequisites in the plan;
preserve every existing runtime/project change in the dirty worktree.

Separate embedded simulation advancement from Scene/Game view drawing in Systems
Runtime and JNI. Keep legacy single-view APIs as compatibility wrappers. Inspect
actual system/UI/event update ordering before extracting: simulation, input and
deferred scene changes run once per owner frame; render-only calls never run
scripts, physics, audio updates or duplicate UI interaction/events. Drawing order
and Scene panel dimensions must not change gameplay camera/chunk behavior.

Add the planned windowed native regression for one advance plus multiple draws,
reverse draw order, pause, invalid arguments, deferred scene switches, camera/
letterboxing independence and wrapper compatibility. Build changed native targets
and the bridge before tests. Run focused CTest and existing camera/project-runtime
desktop smokes with the rebuilt DLL. Record unavailable checks honestly.

Document final native/JNI contracts, update order and actual validation in the
plan ledger/completion record. Stop before implementing session 4.
```

## Session 4: one durable session, simultaneous Scene/Game

```text
Implement session 4 only from docs/plans/editor-docking-plan.md.
Read the design, session-1 graphics decision and session-3 final APIs/evidence.
Do not proceed if those prerequisites are incomplete.

Create the workspace-owned EditorEngineSession using the validated GL-owner and
frame transport mechanism. Visible presenters must not own/create/destroy the
Runtime. One scheduler advances once then draws requested views, keeps Play going
with no visible views, and publishes bounded latest frames with resize generations
and per-view camera/input metrics. Preserve authoring gizmos, Game focus/input,
project launch, audio, Pause/Stop and owner-context cleanup.

Provide independent Scene/Game presenters in the current shell for this milestone;
do not build the full docking UI yet. Add fake-backend ownership/scheduling tests
and the actual --multi-viewport-smoke. Dispose/recreate either presenter while
playing, verify one session and two views, inspect captures and run existing
camera/gizmo/scene/project-runtime compatibility checks.

Record final ViewRequest/ViewFrame/ViewInput and session methods, threading,
buffer cleanup, actual commands/results and limits. Update the ledger and stop
before session 5. Preserve all earlier unrelated changes.
```

## Session 5: main workspace, tool hosts and Windows menu

```text
Implement session 5 only from docs/plans/editor-docking-plan.md.
Read the design and completion records for layout model and durable session.
Confirm the session-5 prerequisites and preserve the dirty worktree.

Move shared state ownership out of panel lifetime into one EditorWorkspace.
Register Hierarchy, Inspector, Assets, Scene, Game and Entity panels using existing
content and commands. Build the main host and auxiliary tool hosts with native
decorations but no duplicated app menus. Add the main Windows menu to show,
restore, focus and hide panels, focus hosts and restore the default layout.
Provide command-based panel/group movement; drag interaction comes next session.

Maintain existing scene-copy/prefab Save routing, dirty confirmations, project
switching and asset services. Closing a tool host hides panels without stopping
Play; canceled home exit preserves everything; accepted exit cleans up once.
Add lifecycle regressions and --docking-workspace-smoke, adapting existing smoke
hooks without removing their behavioral checks. Run build and relevant smokes.

Record panel-context/command routing, native host ownership, close behavior,
actual validation and limitations in the plan. Stop before session 6.
```

## Session 6: drag, tabs, splits, size limits and minimize

```text
Implement session 6 only from docs/plans/editor-docking-plan.md.
Read the design and completed workspace-shell handoff. Preserve all existing edits.

Add screen-coordinate drag/drop across hosts, tab reorder/merge, edge splits,
drop-outside detach, whole-group moves and Escape/capture-loss cancellation.
Use atomic existing layout operations and previews that remain visible around
native view content. Add draggable splitters and recursive panel/host minimums,
including UI-scale changes and fallback to tabs on an undersized display.
Add panel minimize/restore strips and keep those distinct from OS window minimize.

Test unequal DPI/negative coordinate conversion, stale/invalid/self drops,
uniqueness and active-tab/order preservation. Actual desktop smoke must exercise
cross-window drags, whole-group detach, redock Game during Play, minimum resizing,
minimize/restore, canceled drag, retained drafts/undo and released held input.
Run focused tests, build, docking and multiview smokes. Record mixed-monitor manual
validation or clearly identify unavailable checks.

Update the plan ledger/completion record with coordinate/overlay contracts,
captures and actual commands/results. Stop before persistence work in session 7.
```

## Session 7: persist and recover workspace layouts

```text
Implement session 7 only from docs/plans/editor-docking-plan.md.
Read the design and latest interactive-docking handoff. Preserve existing files.

Implement versioned, bounded workspace-layout.json storage separately from the
existing UI-scale settings: project .sle storage plus per-user standalone storage.
Use background atomic writes with captured project keys and revision ordering.
Persist panel/group/host placements and logical window geometry; recover invalid
settings and display changes to accessible windows/defaults without touching
documents. Windows → Restore Default Layout changes layout only.

Test format/version/size/tree limits, unknown/duplicate/new panel IDs, interrupted
and out-of-order saves, project switch during IO, negative monitor coordinates,
removed monitors, DPI changes and displays smaller than minimum content sizes.
Extend desktop smoke for fresh-process restore, per-project isolation, standalone,
reset/corruption and display topology recovery. Run focused tests, build, docking
and project smokes; record real monitor unplug tests or their unavailability.

Record exact schema/paths, revision/recovery contracts, commands/results and
remaining limitations in the plan. Stop before session 8.
```

## Session 8: acceptance, compatibility and operating guide

```text
Complete session 8 only from docs/plans/editor-docking-plan.md.
Read design plus all milestone completion records; verify prerequisites against
current code and evidence. Do not redo passed milestones without a new reason.

Review and validate the full acceptance matrix: every panel operation, one main
menu/workspace, two independent views of one simulation, focused Game input,
zero/one/two visible views, lifetime/cleanup, drafts/undo, source-save/project
compatibility, persisted layouts and display recovery. Reproduce/fix confirmed
regressions with focused coverage. Preserve all unrelated worktree changes.

Build affected native targets and tests before CTest, run full editor build and
the planned docking/multiview and existing scene/camera/gizmo/menu/project/assets/
documents/runtime desktop regressions. Adapt hooks without dropping assertions.
Inspect captures. Report actual monitor/DPI checks and skipped cases honestly.

Update editor/user/module docs affected by the implemented behavior. Fill the
acceptance matrix and final ledger with commands/results, artifacts, limitations,
unfinished work and commit state. Stop at a reviewable result; do not automatically
commit, push or clean this shared worktree.
```

## Required completion record for every session

Append to the implementation plan rather than replacing earlier evidence:

```markdown
### Session N completion / partial handoff — YYYY-MM-DD

- Status: implemented and validated / implemented with unavailable checks / incomplete.
- Files changed in this milestone:
- Final contracts and significant decisions:
- Commands actually run and their results:
- Desktop captures/reports and manual observations:
- Checks not run and why:
- Remaining problems and exact next action:
- Next milestone and prerequisite readiness:
- Git/commit state; pre-existing edits preserved:
```

If interrupted partway through, keep the milestone incomplete and specify the
last passing check, current failing check, and next action. A new session should
resume that record, not infer completion from the presence of source files.

## Latest completion — session 5, Monday 2026-11-16

Session 5 is implemented and its final automated checks passed. Read
[Session 5 completion / final handoff](editor-docking-plan.md#session-5-completion--final-handoff--2026-11-16)
for ownership/context rules, actual commands/results and remaining manual limits.
The full build reported 127 JVM tests with zero failures/errors and two existing
skips. Docking workspace (including real canceled home exit), camera and context-menu
smokes passed on Monday. Friday compatibility results are separately recorded.

Preserve the existing dirty worktree. Session 6 has not started; use its prompt
above only when the user requests that milestone.


## Latest handoff — session 6, 2026-11-16

Session 6 is implemented and automated Windows validation passed; see the final
completion record in `editor-docking-plan.md`. This supersedes the session-5
"session 6 has not started" resume note above. Preserve the dirty worktree. The
next milestone is session 7 only when requested. Interactive dragging, splitters,
host minima, restore strips and Game input release are implemented; controlled
mixed-DPI/monitor and physical capture-loss validation remains outstanding.


## Latest completion: session 7 (2026-11-16)

Layout persistence/display recovery implemented. See the session-7 completion
record in [the plan](editor-docking-plan.md#session-7-completion--handoff--2026-11-16)
for schema, storage paths, revision/atomic-write rules and actual evidence.
Final build: 154 JVM tests, zero failures/errors, two existing skips. Fresh-process
persistence, interactive docking and project desktop smokes passed. Physical
monitor/DPI transitions remain unavailable. Next work is session 8 only when
requested; preserve the shared checkout and its pre-existing changes.


## Session 8 final handoff — 2026-11-16

Automated acceptance is complete with manual hardware limits. See the
[session 8 final record](editor-docking-plan.md#session-8-completion--final-acceptance--2026-11-16)
for commands, captures, remaining checks and commit state. Native 73/73 and JVM
156 tests (two existing skips); all required desktop compatibility smokes passed,
including separate-display Scene/Game. Game F3 and future-panel binding regressions
were reproduced and fixed. Controlled mixed-DPI/unplug checks remain manual.
All existing worktree changes remain uncommitted; no automatic next milestone.
