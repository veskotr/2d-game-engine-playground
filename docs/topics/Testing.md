# Testing

## Test Layout

- `tests/smoke/` - runtime and harness sentinel checks.
- `tests/integration/` - deterministic subsystem behavior.
- `tests/harness/` - shared test helpers.
- `tests/data/` - fixtures for tests.

## Common Commands

```powershell
ctest --test-dir build/debug -C Debug -L smoke --output-on-failure
ctest --test-dir build/debug -C Debug -L integration --output-on-failure
ctest --test-dir build/debug -C Debug --output-on-failure
```

## Labels

- `smoke`
- `integration`
- `windowed`
- subsystem labels such as `ui`, `events`, `physics`, `scripting`, `renderer`, `systems`

## Rules

- Add focused tests for changed behavior.
- Use integration tests for cross-module behavior.
- Keep windowed tests opt-in where CI/headless environments cannot run them.

## Update This File When

- Test labels, commands, harness behavior, or test folder conventions change.
