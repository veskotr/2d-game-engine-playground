# L6 editor Lua host design

Status — The Inspector, Lua console, [project commands](editor-lua-commands-l6.md), and [project UI](editor-lua-ui-l6.md) slices are implemented.

The [editor workflow roadmap](editor-game-workflows.md) assigns L6 four consumers.
The first is exposed behavior parameters in the Inspector. The Lua console is the second slice. Project editor commands are the third slice. The fourth slice adds declarative Details drawers and project panels backed by existing widgets and commands.

## Host

The native editor bridge owns the editor Lua 5.4 host. It has a separate heap and
lifetime from the game ScriptEngine. Each metadata request starts with fresh Lua
state, consumes source text and a diagnostic filename, and returns a normalized
schema or a diagnostic. Cache results by project, resolved asset and source
revision. Discard results after project switches or when the source revision
changes.

Load text chunks only. For discovery, expose selected base functions
(`assert`, `error`, `ipairs`, `next`, `pairs`, `select`, `tonumber`,
`tostring`, `type`) and the string, table, math and UTF-8 libraries.
Exclude `string.dump`, string pattern matching and `table.move` to bound work in native calls. Omit coroutine, package, debug, os and io libraries,
dynamic loading, protected calls, and all engine bindings. Omitting protected
calls makes a Lua instruction-budget error terminal for the request.

Use a capped allocator, a Lua instruction hook with a monotonic deadline,
and bounded source and result sizes. Start with 256 KiB of source, 8 MiB of Lua
allocations and 200,000 Lua instructions. Check elapsed time at hook intervals.
Native library calls can run between hook checks, so the deadline is cooperative.
Keep evaluation off the UI thread and restrict work to the currently requested
schemas. Read returned tables with raw access, limit field counts and string
sizes, and contain compilation, execution, memory and validation errors.
Only top-level declarations and computations execute. Behavior callbacks are
retained by the game runtime and never invoked by discovery.

Host discovery can run for an untrusted project under those restrictions. Before
automatically loading project editor scripts, prompt per project
and persist the choice in editor-local settings keyed to project identity and
canonical path. A declined project still supports Inspector discovery. Revoke
trust from project settings. Trust authorizes editor command and UI registration and command execution
within the host capabilities.

## Inspector slice

Lua behaviors declare metadata on their returned table.

```lua
return {
    properties = {
        speed = { type = "number", default = 3, label = "Speed" },
        patrol = { type = "boolean", default = true },
        greeting = { type = "string", default = "Hello" },
    },
    init = function(entity, parameters)
        -- Read parameters.speed, parameters.patrol and parameters.greeting.
    end,
}
```

Support finite numbers, booleans and strings in the first slice. Require a
matching default for each field. Support labels and an optional integer order,
with key ordering as the tie break. Normalize to the existing
[L2 descriptors and defaults](../topics/EditorPropertySchema.md).
A returned value without a properties table represents a behavior with no fields.
An explicitly supplied malformed properties value produces a diagnostic.
A discovery error affects only that attachment and preserves its stored values.

Show typed fields under each named behavior on scene and entity documents.
Use the existing property renderer. Read the authored value first and fall
back to the script default. A committed edit patches the current attachment
by key and verifies its asset identity before using the existing component edit
transaction. Reset removes the override. Persist overrides in
`Script.behaviours[].parameters` through the existing Script component
serialization. Older documents and the legacy primary script remain compatible.
Keep saved keys when a declaration disappears or changes type. The runtime uses
the new declaration's default when a saved value is incompatible, and the
Inspector shows the effective value and allows the user to replace or reset
the saved value.

Pass a fresh parameters table as the second init argument for named behaviors
with declared fields,
including successful reloads. Behaviors without declared fields retain the existing init call signature.
Use the same native schema validation and merge rules for discovery and runtime,
with the runtime reading the returned table it already obtains when loading
the behavior. Preserve the current arguments of update and destroy. Runtime
changes to the parameters table stay local to that instance.

## Console and project editor commands

Expose a detached snapshot of the current project and document revision,
selected entity IDs, entity/component values and asset records. Entity IDs are
document-local. Asset records carry a project-relative identity and kind.
An editor script returns ordered edits described by operation records.
The vocabulary covers setting a component field by path, adding and removing
a component, creating and deleting an entity, reparenting an entity, and
creating a project-relative asset. Temporary IDs can refer to entities created
earlier in the edit list. Validate the entire list against the current document
revision and project root before applying it through L1 as one undo transaction.

The stopped console uses the editor host with the snapshot and edit-list
contract. During Play, the L4 bridge sends console input to the active game
runtime and routes its output to the Console panel. The editor host remains
independent of Play session lifetime.

## First-slice verification

- Native discovery coverage should include isolated evaluations, compile and
  execution errors, missing schema, unavailable APIs, instruction and memory
  limits, and deterministic normalized field ordering.
- Scene round-trip and behavior integration coverage should prove per-attachment
  persistence, legacy compatibility, default fallback and init values after
  reload.
- Editor tests should cover typed values, attachment identity, reset, undo and
  redo. An Inspector smoke should load a behavior, edit an override, save and
  reopen the document, then verify the value in Play.
