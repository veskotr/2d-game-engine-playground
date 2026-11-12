# Independent sprite geometry and transparent picking

User report: collider interiors intercept sprite clicks; sprite resize currently
edits shared Transform and scales colliders. Fix both behaviors in editor and engine.

- [x] Regression tests: overlapping collider interiors pick SpriteRenderer;
  outlines/selected handles pick collider. Sprite move/resize changes only its
  own offset/size, preserving Transform and collider values. Undo/persistence.
- [x] Add SpriteRenderer positive size (default 1,1) and local offset (default 0,0)
  to shared component codecs, rendering/culling and preview fit/outlines. Existing
  documents without these fields keep identical behavior.
- [x] Update gizmos to use sprite-local size/offset and screen-space outline
  picking; selected handles take precedence, collider interiors do not.
- [x] Verify Kotlin tests, native tests and actual desktop drag/persistence;
  update docs and reopen editor without discarding unsaved user changes.

Shared Transform continues to affect every component intentionally. SpriteRenderer
handles now edit visual geometry only. RigidBody has no drawable/pickable shape.

## Results (2026-11-12)

Windows native debug build succeeded. Full CTest: 66/66 passed, including pixel
checks for independently resized/offset sprites, culling of sprites whose centers
are outside the viewport, unchanged collider/Transform geometry, and JSON/Lua
codec parity with invalid size rejection. Final log:
`/tmp/sle-sprite-fix-final-native.log`.

Windows Gradle build: 26/26 unit tests passed, including transparent interior,
outline and disabled-collider picking. Final desktop Robot regression passed
sprite clicks through collider interiors, collider edge selection, independent
sprite resize, undo/cancel and package save/reopen of size/offset. Logs:
`/tmp/sle-sprite-fix-final-editor.log`, `/tmp/sle-sprite-fix-final-desktop.log`.
`python3 EngineModules/Scripting/tools/generate_lua_api_stub.py --check` passed.

Independent review caught disabled outlines participating in automatic picking;
fixed and covered. Initial engine test failures were corrected test setup: new
Lua parity cases needed matching fixture entries; textured sprite pixel checks
needed a texture because the shader samples a bound image. No untextured sprite
behavior was changed. Explicitly selected handles still win picking so a chosen
collider can be manipulated over a sprite.
