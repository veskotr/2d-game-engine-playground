---
name: sle-engine-feature
description: Implement an SLE C++ engine or runtime feature, including ECS, scene data, Lua bindings, or engine capabilities that need an editor authoring or inspection tool. Use the companion editor skill for the editor portion when the requested feature needs one.
---

# Add an SLE engine feature

Read AGENTS.md and docs/CONSTRAINTS.md. Use the documentation index to find the affected module and topic. Trace the normal runtime or authoring entry point and affected CMake link dependencies. Keep component data and system behavior in their existing owners. Systems coordinates modules, and Lua access uses the registered bindings and runtime API.

Establish the feature's runtime behavior, lifecycle, and failure cases. If it changes saved data, exercise loading existing data and saving and reopening new data. Add focused coverage per tests/AGENTS.md and update the affected documentation. Build the changed targets before running CTest.

## Editor handoff

When the requested engine feature needs editor-facing authoring, inspection, or controls, apply the sibling skill at ../sle-editor-feature/SKILL.md. Pass along what the editor displays or edits, the data and operations exposed through the existing editor/native bridge, and which project, document, or session owns them. Include stale-target behavior, refresh timing, and undo/save semantics where relevant. Use the companion's panel workflow when the feature calls for a window.

Verify the engine behavior and the editor's normal entry point together when both are in scope. Runtime-only requests use the engine workflow alone.
