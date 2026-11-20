# L3 asset metadata session plan

Status: Sessions 1 and 3 are implemented. Sessions 2, 4, and 5 are pending. The scope follows the L3 row in
[Editor game workflows](editor-game-workflows.md). Each session should leave
the editor usable and record the checks actually run before handoff.

## 1. Texture filtering end to end

**Start here.** The first deliverable is a texture filter control in the Assets
Details panel. Save it, reopen the project, and verify that the editor viewport,
project Play, and a built game all use the selected sampler.

### Sidecar contract

Keep `format: "sle-asset"`, integer `version: 1`, and the existing UUID. Extend
version 1 with one optional `texture` object. Older SLE versions with strict
sidecar readers cannot open sidecars containing this extension. Existing sidecars
continue to work as-is.

```json
{
  "format": "sle-asset",
  "version": 1,
  "id": "5aa72016-f590-4961-a670-bfcc977908de",
  "texture": { "filter": "nearest" }
}
```

The only stored filter values in this session are `nearest` and `linear`.
The UI offers **Default**, **Nearest**, and **Linear**. Saving Default removes
the texture object and uses the current loader defaults. Reject unknown keys,
nulls, and invalid values, with the sidecar path in the diagnostic. Apply the
same rules in the editor and native project index. A filter applies to both
minification and magnification.

### Implementation order

- [x] **Read and write sidecars.** In
  `tools/editor/src/main/kotlin/sle/editor/project/assets/AssetIdentity.kt`,
  add a typed read returning the UUID and optional filter. Keep the current
  identity read as a wrapper for its callers. Write changes atomically using
  the existing asset-path checks and write helper. Read the current sidecar
  before editing and preserve its UUID. In `AssetIdentityTest`, assert that
  an identity-only sidecar reads as Default, both filter values round-trip,
  clearing the filter restores the identity-only shape, and an invalid
  sidecar stays intact after a rejected write. In `ProjectAssetOperationsTest`,
  assert that a filtered asset keeps its sidecar settings across rename and move.
- [x] **Read the same contract in the runtime.** In
  `EngineModules/Systems/src/ProjectRuntime.cpp`, extend the strict metadata
  validation in `ProjectRuntime::index()`. Add fixtures to
  `tests/integration/systems/integration_project_runtime_path.cpp` for an
  identity-only sidecar, both supported values, and rejection of an invalid
  value. Confirm that folder projects and mounted packages use the same rules.
- [x] **Apply the sampler when loading project sprite textures.** Start at
  `EngineModules/Systems/src/SceneLoader.cpp` and trace how the native editor
  preview, Play, and packaged runtime each reach the sprite texture creation
  call. Carry or look up the filter for the resolved project texture path
  at those entry points, keeping project resolution in Systems. Reuse
  `renderer::TextureLoadOptions` for min and mag. Preserve existing defaults
  for unset filters and non-project paths. Include the effective sampler in
  the texture resource key so reloading after an edit cannot reuse a texture
  created with a different filter. Add a focused check for reload of the same
  file with a different filter. Project sprite textures are the first consumer.
- [x] **Expose the filter in the editor.** Add the control to
  `tools/editor/src/main/kotlin/sle/editor/project/assets/AssetBrowserPanel.kt`
  for registered texture files. Route writes through the existing asset
  operation flow. Refresh Details after a successful save and surface errors
  through the existing status path. Reload the affected preview using the
  existing document or viewport refresh path. If an explicit reload is needed,
  verify it preserves unsaved scene edits.
- [x] **Verify build and reopen.** Extend `ProjectDistributionTest` to assert
  that the packaged asset keeps its sidecar UUID and filter. Use one test
  scene with a sharp pixel-art texture scaled enough to see the difference.
  Save Nearest, reopen, verify viewport and Play, build and launch, then
  switch to Linear and repeat. Clear the override and verify the original
  default behavior. Run the commands under Validation anchors and record
  results and any unavailable checks here.

Session 1 delivers filtering only. Session 2 owns art-style inheritance.
Session 3 owns named rectangles. State-machine and Area authoring belong to
the separate post-polish editor coverage track.

## 2. Project filter defaults, coordinated with L5

Proposed policy: an unset filter follows the project default, and an explicit
filter overrides it. Projects without an art-style setting retain the current
loader default. When the L5 art-style setting is available, wire its texture
default into the same resolution path used by Session 1. Verify that changing
the default affects inherited textures and leaves explicit overrides in effect.
Other art-style settings belong to their own consumers. Sessions 3 through 5
can proceed if this L5 work lands later.

## 3. Named sprite regions

Add stable region IDs scoped to their texture, editable names, and pixel
rectangles. Confirm the coordinate origin and relationship to existing region
data before choosing the serialized shape. Deliver grid slicing and a region
picker with one end-to-end consumer, a scene sprite. Preserve existing
rectangle-based content and preserve region IDs when editing an existing
slice. Define the behavior for a deleted region or an image resized below its
rectangle. Verify rename, save/reopen, viewport, and packaged playback. Plan
manual slicing, pivots, flipbooks, and TileSet authoring with their own consumer
workflows.

## 4. Asset thumbnails

Show texture thumbnails in the asset grid and cache generated files under the
project's `.sle/` directory. Invalidate a thumbnail when its source or relevant
preview settings change. Verify regeneration after cache deletion, refresh
after replacing a source image, and exclusion of generated cache files from
package output.

## 5. Find references

Build an editor-side reverse lookup over the asset UUID references in supported
project document formats. Start with the reference paths already understood by
project packaging and use those readers where practical. Show direct consumers
with navigable document locations. Define whether the first release includes
unsaved documents and report the supported coverage in the UI. Verify updates
after saving a reference change, moving an asset, and deleting a referring
document.

## Validation anchors

- Extend `AssetIdentityTest` and `ProjectAssetOperationsTest` for sidecar changes.
  Use `tools/editor/gradle.ps1 test` for the editor suite.
- Extend `integration_project_runtime_path` where project asset resolution or
  runtime behavior changes. Build the Windows Debug preset and run
  `ctest --test-dir build/debug -C Debug -R 'integration_project_(runtime|play)_path' --output-on-failure`.
- Extend `ProjectDistributionTest` for packaging and use the Windows editor for
  the viewport, Play, and packaged-game checks.

### Session 1 implementation notes

- The asset scan publishes the persisted filter with the registered UUID. The
  Details edit atomically updates the sidecar and refreshes the current viewport
  from its in-memory document, including unsaved edits.
- The native preview reads current sidecars under the active asset root. Project
  Play and built games use filters indexed in their launch snapshots. Script
  sprite reassignment and runtime prefab loads share the scene sprite loader.
- Folder and package indexing use the same strict reader and report the sidecar
  path on validation failure. Lookups accept Windows short paths for indexed
  assets. Renderer texture keys distinguish paths loaded with different samplers.
- Engine version is 0.20.0 and editor version is 0.12.0.

### Session 1 validation — 2026-11-19

- `tools/editor/gradle.ps1 test run --args=--asset-browser-smoke --no-daemon`
  completed with 356 JUnit tests passed and 2 skipped. The desktop smoke
  selected Nearest, Linear, and Default while a sprite scene had unsaved
  edits, then reopened the project and checked the persisted Nearest setting.
- `cmake --build build/debug --config Debug --target sle_editor_native
  integration_project_runtime_path integration_project_play_path
  integration_game_package_path engine_app --parallel 4` passed.
  `ctest --test-dir build/debug -C Debug -R
  "integration_project_(runtime|play)_path|integration_(game_package|package_(discovery|launch))_path"
  --output-on-failure` passed all five tests. The OpenGL tests read both sampler
  parameters for preview, project Play, and mounted package sprites. They also
  verified cache invalidation for the same path after changing its filter.
- `cmake --build build/release --config Release --target sle_runtime_bundle
  --parallel 4` passed. Running `integration_package_launch_path.cmake` against
  the Release bundle executable started the standalone package with Nearest,
  Linear, and Default from an unrelated working directory.
- Visual inspection by hand was not performed. The tests checked OpenGL
  parameters and exercised the dropdown, dirty draft, project reopen, and
  packaged launch.

### Session 3 implementation plan

- Extend the texture sidecar with an optional regions array. Each entry carries
  a UUID, a display name, and an integer rectangle in top-left image coordinates.
  Validate the same shape in Kotlin and C++ and retain regions on filter edits.
- Preserve optional SpriteRenderer.regionId through authoring. Resolve it when
  loading a scene texture and use the loaded dimensions to convert to UVs.
  Missing or out-of-bounds regions render the full texture and report a warning.
  Existing normalized UVs remain the source when no named region is selected.
- Add grid slicing and region editing to Assets Details. Update a region by ID
  and preserve that ID across name and rectangle edits. Add the selector to the
  sprite texture drawer and reuse the texture metadata refresh path.
- Exercise sidecar and grid contracts, scene authoring, viewport and project
  loading, and package preservation. Record current validation below.

### Session 3 validation

- Windows Debug build passed for `sle_editor_native`,
  `integration_project_runtime_path`, `integration_project_play_path`,
  `integration_game_package_path`, and `engine_app`. The five matching CTest
  project and package tests passed. OpenGL checks covered the top-left crop,
  rename and rectangle edits, a missing region, an image resized below a
  region, and package playback.
- `cmake --build build/release --config Release --target sle_runtime_bundle
  --parallel 4` passed.
- Engine version is 0.21.0 and editor version is 0.13.0.
- `tools/editor/gradle.ps1 test --offline --no-daemon` completed with 357
  tests passed and 2 skipped. Coverage includes grid slicing, stable IDs,
  filter/region coexistence, scene save and reopen, and built package metadata.
- `integration_package_launch_path.cmake` passed in launch mode with the
  Release bundle executable. The relocated game reached the scene with the
  named sprite crop with Nearest, Linear, and Default filtering.
- The desktop asset-browser smoke stopped during temporary project creation
  on the first run and at the pre-existing Import chooser step on retry.
  The grid and picker controls did not receive a completed desktop check.

### Session 4 implementation plan

- Keep the existing asset browser rows and load previews for visible texture rows
  on an IO dispatcher. Recheck after file or filter changes and on Refresh.
- Generate aspect-preserving PNGs under `.sle/cache/thumbnails`. Fingerprint the
  source bytes and preview settings. Recreate missing cache files on demand and
  use the file icon when a preview cannot be decoded.
- Test source replacement, cache regeneration, filter changes and exclusion of
  a generated thumbnail from the game package. Run the editor test suite and
  check the asset browser in a graphics environment when available.

### Session 4 validation

- The editor is version 0.14.0. Previews use the editor's ImageIO readers.
- `cmake --build /tmp/sle-l2-linux --target sle_editor_native --parallel 4`
  rebuilt the Linux bridge successfully.
- The offline Gradle `test` task passed 361 tests on Linux. Coverage includes
  thumbnail regeneration, source bytes and filter changes, and the omission
  of a generated thumbnail from a built game archive.
- The Linux `run --args=--asset-browser-smoke` retry with
  `SKIKO_RENDER_API=SOFTWARE` displayed a thumbnail, regenerated it on Refresh,
  and refreshed it after a same-size write that restored the source timestamp.
  The wider smoke stopped at the existing selected-folder Import chooser step.

### Session 5 implementation plan

- Search saved project documents and the saved startup scene setting on demand.
  Reuse the scanner that protects asset operations and preserve its deletion rules.
- Offer Find References for UUID-backed files in Asset Browser details. Show the
  search coverage and open a selected consumer through document or project settings
  controls.
- Test fresh results after a save, asset move, consumer move and consumer deletion.

### Session 5 validation

- The offline Linux Gradle `test` task passed 363 tests, including saved
  reference changes, asset and document moves, document deletion, prefab asset
  fields, and the startup scene setting.
- The Linux `run --args=--asset-browser-smoke` check invoked Find References
  through the editor command, verified the saved consumer in the results dialog,
  and opened the referring scene. The wider smoke stopped at the existing
  selected-folder Import chooser failure. Physical clicks and Windows desktop
  behavior were outside the passing check.
