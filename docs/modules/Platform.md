# Platform Module

## Responsibility

Window and input integration.

## Owns

- Window creation and lifecycle
- GLFW integration
- Keyboard and mouse state tracking

## Dependencies

- Core
- Platform/windowing dependencies configured by CMake

## Important Paths

- `EngineModules/Platform/include/sle/platform/`
- `EngineModules/Platform/src/`
- `EngineModules/Platform/CMakeLists.txt`

## Rules

- Input edge state is cleared before polling so pressed/released semantics remain stable.
- Platform should not own renderer resources, scene data, or script state.

## Update This File When

- Window lifecycle, input semantics, or platform dependencies change.
