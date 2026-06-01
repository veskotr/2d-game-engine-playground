# Current Task

## Active Task

Continue the v1 final stage plan with the standalone engine entry point (`Stream B`) on top of the already in-flight JSON scene loader work (`Stream A`).

## Status

Complete.

## Scope

- Preserve existing SceneLoader and scene-registration changes already present in the worktree.
- Add a standalone `engine_app` target that reads `engine.json`, initializes `Runtime`, registers JSON scenes, loads the start scene, and runs.
- Wire the new target into top-level CMake without disturbing unrelated example or test targets.
- Keep handoff docs current as Stream A transitions into Stream B.

## Decisions

- Treat the existing `SceneLoader`, JSON demo scene, and scene-loader integration tests as active user work and build on them rather than rewriting them.
- Keep `engine_app` as a thin root-level executable, matching the v1 plan.
- Resolve `engine.json` by walking up from the current working directory; resolve scene files relative to the config file first, then by the same parent-walk fallback.

## Files Changed For This Task

- `CMakeLists.txt`
- `engine_app/CMakeLists.txt`
- `engine_app/main.cpp`
- `engine.json`
- `README.md`
- `docs/CURRENT_TASK.md`
- `docs/modules/Systems.md`
- `docs/topics/Runtime.md`

## Validation

- `cmake --preset debug` succeeded on June 1, 2026.
- `cmake --build build/debug --config Debug --target engine_app` succeeded on June 1, 2026.
- `engine_app` runtime execution was not exercised in this pass because it is an interactive windowed target.

## Next Steps

1. Run the standalone app path against `engine.json`.
2. If runtime issues appear, tighten config-path and scene-path handling around the new bootstrap flow.
3. Continue with Stream C (Tiled map integration) or Stream D once the standalone bootstrap is validated interactively.
