# Preserve the active scene on file-load failures

Goal: file-based loads report errors without replacing the active scene or emitting
successful lifecycle events. Existing void C++ builders retain their API/semantics.

Approach: register file paths distinctly, load once into an isolated staging scene,
catch loader exceptions at the result boundary, then commit ECS/hierarchy data.
Keep the live event-bus address stable and allocate replacement IDs after existing
IDs so runtime subsystems cannot mistake new entities for old ones. Resource-cache
population during staging is not rolled back; active scene contents are preserved.

Validation covers immediate/deferred failures (syntax, missing file, invalid typed
component after partial construction, missing Tiled map), old scene state and events,
then a successful replacement with deferred unload/load events and fresh entity IDs.

- [x] Reproduce with a native integration regression before production changes.
- [x] Implement staging, commit, and file-load error propagation.
- [x] Run focused regression and full Debug suite; review relevant consumers.
- [x] Update Scene/Systems and lifecycle documentation with actual guarantees.

Validation (2026-11-04): the new regression failed against the original implementation
(false success, lost scene data/events); it passes with staging. The full native
Windows Debug build succeeded and CTest passed all 47 tests, including graphics
and the prior transform/physics regression. Existing CRT linker warnings remain.
Independent source review found no correctness issues. Coverage also checks old
queued events are discarded on commit and old subscription tokens cannot remove
new listeners. Changes are uncommitted.
