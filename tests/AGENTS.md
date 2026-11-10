# Test changes

Follow the root [AGENTS.md](../AGENTS.md) and [testing topic](../docs/topics/Testing.md).

- Integration tests are standalone executables registered using
  `sle_add_integration_test` in `integration/CMakeLists.txt`.
- Match adjacent assertions, exit codes, fixtures, and link dependencies.
- Cover an observable regression or contract; avoid tests that merely copy implementation.
- Use `data/` for fixtures and the existing `harness/` helpers when appropriate.
- Assign the subsystem label and mark graphics-window tests `windowed`.
- Set the test working directory explicitly when fixtures depend on repository-relative paths.
- Keep event ordering and teardown tests deterministic; avoid timing sleeps.
- Build the relevant target before CTest. Use an anchored `-R` expression for one
  test, then the appropriate subsystem suite. See [CONTRIBUTING.md](../CONTRIBUTING.md).
