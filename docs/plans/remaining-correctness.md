# Remaining correctness cleanup

Scope approved by user: GPU shutdown ordering; entity/hierarchy validity; Lua event
subscription ownership; Release-test reliability; stale architecture/build guidance,
pinned dependencies, and CRT linkage warnings. Preserve all prior uncommitted work.

1. Reproduce invalid hierarchy/ghost component behavior, then enforce invariants.
2. Reproduce GPU destruction after context loss, then release runtime references.
3. Trace entity execution context into Lua event subscriptions; add lifecycle coverage
   for pause, unsubscribe, destruction, and scene replacement. Release Lua callback
   references and invalidate callbacks safely even during EventBus snapshot dispatch.
4. Replace assert-based test actions/checks with always-executed checks. Pin existing
   dependency revisions and diagnose CRT mismatch at its actual source.
5. Reconcile touched current docs with source. Build/test integrated Debug and relevant
   Release targets, review the combined change, record exact results and limitations.

No commits or publication are part of this task. Native Windows builds run serially.

## Implementation and validation (2026-11-10)

- Hierarchy regression first rejected the old ghost-component behavior. Validated
  bool-return parenting now rejects dead IDs/cycles atomically; late-added Transform
  data follows one authoritative parent map. Lifetime stays owned by Scene APIs.
- Actual GL deletion hooks reproduced quad=1/debug=2 and deletion without context.
  Runtime now releases UI caches/shader references before GL context teardown;
  Renderer no longer deletes a borrowed debug program. External shared resource
  owners must release their references while the context is alive.
- Lua regression reproduced unstable handles, leaked references, delivery during
  suspension, missing UI ownership, failed initialization rollback, callback snapshot
  reuse, invalid-destroy side effects, and ID aliasing during teardown. Stable handles,
  execution-source context, lifecycle cleanup, checkpoint rollback, and ID reservation
  cover these cases. Failed scene replacements preserve active subscriptions.
- Release baseline: the original assert-based audio test exited 0 with its fixture
  absent. Corrected Release executable exits 0 with its fixture, exits 1 for a missing
  fixture, and exits 1 (volume mismatch) for a deliberately altered fixture. Repository
  fixtures were never changed for these negative checks.
- Dependencies previously tracking master are pinned to existing cached commits.
  Root project enables C and C++. The generated GLAD project now selects /MDd in
  Debug; object scan found 227 MSVCRTD entries, no LIBCMT offenders (previously GLAD
  gl.obj was LIBCMT). No /NODEFAULTLIB suppression was introduced.
- Native full Debug build succeeded. Focused regressions passed after fixes. Full
  suite and final file checks are recorded at completion below. Independent source
  reviews identified the ownership edge cases above; targeted follow-up review
  confirmed their fixes. Legacy architecture docs are marked historical and current
  dependency/interface documentation reconciled.

All work remains uncommitted, including earlier cleanup and interaction fixes.

Completion: final native Debug build succeeded; `ctest --test-dir build/debug -C
Debug --output-on-failure` passed all 52 tests, including all five graphics tests.
Corrected Release audio build and normal execution passed; missing/mutated fixture
negative checks failed as expected with clear diagnostics. Changed-file whitespace,
new regression files, updated Markdown links, and removal of assert-dependent test
execution were checked. No remaining confirmed findings within the reviewed scope.
