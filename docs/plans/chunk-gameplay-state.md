# Separate chunk suspension from gameplay state

Chunk activation must never assign gameplay enabled/visible flags. Store a runtime
subsystem suspension mask on ChunkActivationComponent and gate each consuming
system with gameplay state AND chunk activity. Recompute masks on every controller
update, including events-only/disabled policies and per-entity opt-outs.

Physics must disable existing Box2D bodies before stepping and reconcile collider
and sensor fixtures with their own gameplay and chunk gates. Resume the same body
and velocity. Keep suspended Lua instances alive; skip their update calls.
Rendering/UI must consume effective visibility without rewriting component flags.
Audio and transform hierarchy traversal keep their existing behavior.

- [x] Reproduce state overwrites and continued Box2D motion with a real integration test.
- [x] Implement separate gates across controller and consumers; update old flag-based tests.
- [x] Verify policy overrides, keep flags, pause/resume, fixture state, and script lifetime.
- [x] Review changes, run full native Debug suite, and update subsystem docs.

Validation (2026-11-04): the original regression failed seven gameplay/lifetime/
physics expectations. Extended tests then reproduced lost local edits on resume
and lost zone IDs during zone-only suspension; both now pass. Full Windows Debug
build succeeded, and all 49 CTest cases passed, including actual framebuffer
checks for sprites and UI and both prior regression tests. Existing CRT linkage
warnings remain. Independent review found the zone issue; follow-up review found
no remaining confirmed regressions. New-file whitespace/Markdown links and the
changed-file diff checks passed. Changes remain uncommitted.
