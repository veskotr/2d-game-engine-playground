# Lua-driven gameplay and binding maintenance

The user wants to author games through Lua, including spawning/configuring supported
components, with each component owning its binding description and automatic drift
checks. Scene data must not depend on Lua/Systems. Keep per-component codecs and
validation beside one another under Systems Lua adapters; shared registration and
conversion stays generic. Internal GPU/body handles and derived state stay private.

Add atomic Components has/get/set/remove/find, current transform/hierarchy queries,
missing input/physics controls, and Lua startup/scene-building support where the
existing runtime supports it. Preserve old Lua names. Synchronize resource lifecycle
and physics when fields change; do not expose inert setters. Capture Lua structural
mutation tests. A declarative typed API manifest + executable check validates all
binding/ScriptApi mappings, constants, and generated docs; CI runs check before CMake
can regenerate stale output. Use scoped AGENTS instructions for the maintenance
workflow (no skill installation into the read-only .agents mount).

Validation: real Lua/native bridge tests first, then native full Debug suite;
Python tool tests and deliberate stale/missing-mapping checks, generated docs, and
independent review. All existing changes remain uncommitted and preserved.
