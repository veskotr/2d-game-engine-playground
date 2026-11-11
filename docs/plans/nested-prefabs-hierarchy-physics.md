# Nested prefabs and hierarchy-relative physics

User authorized both remaining limitations. Preserve pending changes; no commit or
push requested. Work in two independent source areas with root-serialized native
builds and integrated review.

Nested prefabs recursively expand child assets before outer qualification. Paths
are relative to each including file; local child namespaces become outer/child/...
keys. Default nested parent is the enclosing declared root. All references and
outer deep overrides resolve after flattening; numeric authored positions retain
legacy meaning. Canonical file-path stack detects import cycles; depth/entity
budgets bound expansion. Repeated sibling assets are valid. Preserve dynamic
instantiation rollback and failed-scene preservation.

Physics uses world body poses/velocities and local authored transforms. Share TRS
composition/inversion helpers with rendering/Lua. Prestep current ancestors, not
stale cached transforms, determine native placement. Poststep snapshot world body
poses then resolve parent-first and invert parent transforms to write local values.
No implicit joint: parented dynamic bodies simulate independently in world space.
External hierarchy changes teleport/reconcile bodies without rebuilding them.
Singular parent transforms suspend safely and resume without NaNs or authored flag
changes. Chunk activity must still gate native bodies/fixtures.

Compatibility: root collider dimensions remain explicit independent of its visual
Transform.scale; only ancestor scale affects collider geometry. Rebuild fixtures,
not bodies, on inherited geometry changes. Uniform circles stay circles;
nonuniform inherited scales use a documented convex ellipse approximation if
supported cleanly by the pinned Box2D implementation. Current TRS composition is
the engine's existing model; this does not introduce affine shear support.

- [x] Regression RED for nested composition, paths/refs/overrides, cycles and rollback.
- [x] Recursive expansion with bounds, contextual errors and existing compatibility.
- [x] Regression RED for parented physics pose/writeback/edits/scales/gates.
- [x] Implement shared transform contract and native fixture reconciliation.
- [x] Integrated review, full Debug build/CTest, tooling and documentation validation.

## Validation and review (2026-11-11)

RED native tests confirmed nested imports rejected and physics ignored parent
poses/scales/inverse writeback. Recursive prefab composition and hierarchy physics
then passed their primary regressions. Reviews added these reproduced edge cases:

- An original scene numeric reference could accidentally address an appended
  prefab. SceneReferences now receives original authored array length before
  expansion, excluding implicit scene-root/imported slots from numeric lookup.
- Invalid geometry in a chunk-gated-off sensor suspended a healthy collision body.
  Geometry checks now respect Collision/Zones gates independently.
- Finite large ellipse offsets collapsed actual native float vertices and reached
  Box2D polygon_shape.cpp:156 assertion. Shared PhysicsWorld geometry queries now
  validate actual converted vertices/edges/area before Set; direct fixture creation
  rejects unsafe shapes and Systems suspends/recover existing bodies safely.

Independent follow-up review confirmed both physics findings resolved; nested
review found no further recursive-expansion defects after the scene ordinal fix.

Actual final validation:

- Windows `cmake --build build/debug --config Debug --parallel 4`: succeeded.
- Windows `ctest --test-dir build/debug -C Debug --output-on-failure`: 61/61 passed,
  including all seven windowed tests.
- `python3 -m unittest discover -s EngineModules/Scripting/tools/tests`: 21 passed.
- `python3 EngineModules/Scripting/tools/generate_lua_api_stub.py --check`: passed.
- `python3 EngineModules/Scripting/tools/audit_component_bindings.py`: 15 types.
- Affected documentation links, new-file whitespace, and scoped CRLF-aware Git
  whitespace checks passed. Existing unrelated edits/line endings preserved.

Nested tests cover sibling reuse, multi-level relative imports, layered descendant
patches/removal, reference remapping/forward parents, scene/dynamic rollback,
canonical cycles, and shared depth32/entity16384 expansion budgets. Physics tests
cover scene/transparent ancestors, current parent edits with clean caches, local
inverse writeback, reparent/body identity/velocity, singular scale/chunk resume,
independently simulated parent/child bodies, inherited box/circle/ellipse geometry,
root visual-size compatibility, and Transform removal/readdition handle cleanup.

TransformSystem, Lua current-world queries and PhysicsSystem share TransformMath.
Colliders keep explicit own-size units; ancestor scale affects geometry. Parented
bodies remain independent world bodies (no automatic joint). Nonuniform circles
are inscribed 8-vertex ellipses, not exact ellipses; the existing TRS model does not
represent shear. Very small/unrepresentable native geometry suspends until valid.
No Release build or interactive gameplay session was performed in this step.
All changes remain uncommitted, including earlier pending scene/schema/behaviour work.
