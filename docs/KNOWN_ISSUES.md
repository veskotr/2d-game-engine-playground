# Known Issues

This file tracks code-level issues that require refactoring. Documentation-only inaccuracies have already been corrected. The items below need code changes.

---

## Issue 1 — Namespace mismatches across modules

**Severity:** Medium  
**Files affected:**
- `EngineModules/Renderer/include/sle/renderer/Camera2D.hpp` — declares `namespace sle::core`
- `EngineModules/Renderer/include/sle/renderer/GLDebug.hpp` — declares `namespace sle::core`
- `EngineModules/Platform/include/sle/platform/Window.hpp` — declares `namespace sle::core`
- `EngineModules/Resources/include/sle/resources/ResourceManager.hpp` (and related) — declares `namespace sle::core`
- `EngineModules/Platform/include/sle/platform/Input.hpp` — declares `namespace sle::input` (inconsistent with other Platform types that use `sle::core`)
- All Systems/Runtime types use flat `namespace sle` instead of a sub-namespace matching their include path (`sle/engine/`)

**Description:**  
Classes are declared in `sle::core` regardless of which physical module they live in. This makes it impossible to tell from the namespace alone which module owns a type. Each module should own a matching sub-namespace (`sle::platform`, `sle::renderer`, `sle::resources`, `sle::systems`).

**Suggested fix:**  
Rename namespaces to match their module: `sle::renderer` for Camera2D/GLDebug, `sle::platform` for Window/Input, `sle::resources` for ResourceManager, `sle::systems` (or `sle::engine`) for Runtime types. Update all call sites accordingly.

---

## Issue 2 — Camera2D lives in the Renderer module, not Platform

**Severity:** Low (architectural clarity)  
**Files affected:**
- `EngineModules/Renderer/include/sle/renderer/Camera2D.hpp`
- `EngineModules/Renderer/src/Camera2D.cpp`

**Description:**  
Camera2D is built on top of projection matrices (a Renderer concern) and has no dependency on Platform. It correctly lives in the Renderer module and is linked only there. No code change is strictly required, but the namespace should be updated from `sle::core` to `sle::renderer` as part of Issue 1.

---

## Issue 3 — ScriptApi boundary violated by engine systems

**Severity:** High  
**Files affected:**
- `EngineModules/Systems/src/StateMachineSystem.cpp` — calls `ScriptEngine::callGlobalBoolFunction()` and `callGlobalFunction()` directly
- `EngineModules/Systems/src/UISystem.cpp` — stores `ScriptEngine*` and calls `executeScriptAsset()` and `callGlobalFunction()` directly
- `EngineModules/Systems/src/ScriptSystem.cpp` — calls `ScriptEngine::ensureScript()` directly

**Description:**  
The `ScriptApi` abstract interface was introduced to decouple engine systems from the Lua VM. However, all three system classes bypass `ScriptApi` and call `ScriptEngine` directly. This tightly couples Systems to the Lua implementation and makes the Scripting module non-swappable.

**Suggested fix:**  
Extend `ScriptApi` (or a sub-interface) to expose `callGlobalFunction`, `callGlobalBoolFunction`, `executeScriptAsset`, and `ensureScript`. Update `ScriptApiImpl` to implement these. Change `StateMachineSystem`, `UISystem`, and `ScriptSystem` to depend on `ScriptApi*` rather than `ScriptEngine*`.

---

## Issue 4 — `using namespace` in public headers (Scene event forwarding headers)

**Severity:** Medium  
**Files affected:**
- `EngineModules/Scene/include/sle/scene/events/CollisionEvents.hpp`
- `EngineModules/Scene/include/sle/scene/events/ZoneEvents.hpp`

**Description:**  
Both forwarding headers include the corresponding `sle/events/` header and then emit `using namespace sle::events;` at global scope inside the header. Any translation unit that includes these headers silently imports the entire `sle::events` namespace, risking name collisions and obscuring which module owns each type.

**Suggested fix:**  
Remove the `using namespace sle::events;` lines. Update all call sites that relied on the implicit import to use fully-qualified names (`sle::events::CollisionBeginEvent`, etc.) or add explicit `using` declarations in `.cpp` files only.

---

## Issue 5 — Stale step comment in `Runtime.cpp`

**Severity:** Low  
**Files affected:**
- `EngineModules/Systems/src/Runtime.cpp` — comment `// 4. Submit sprite renders...` before `renderSystem.update(ctx)`

**Description:**  
The render phase is actually step ~14 in the current frame loop, but the comment still says step 4 (from an earlier simpler loop). This is misleading when reading the code.

**Suggested fix:**  
Update the comment to reflect the actual step number or rewrite as a plain description without a step number.

---

## Issue 6 — SpriteRenderer (Scene module) couples Scene to Renderer

**Severity:** Medium  
**Files affected:**
- `EngineModules/Scene/include/sle/scene/components/SpriteRenderer.hpp` — includes `<sle/renderer/TextureRegion.hpp>`
- `EngineModules/Scene/CMakeLists.txt` — links `sle::renderer` as a result

**Description:**  
`SpriteRenderer` embeds a `renderer::TextureRegion` value directly. This forces the Scene module to depend on the Renderer module, which breaks the intended one-way dependency chain (`Renderer` should depend on `Scene`, not the other way around).

**Suggested fix:**  
Options in increasing invasiveness:
1. Move `TextureRegion` (or an abstract `TextureHandle`) into the `Resources` module, which both Scene and Renderer already depend on.
2. Store only a resource handle (e.g. `std::string path` or a typed `ResourceHandle<Texture>`) in `SpriteRenderer` and let `RenderSystem` resolve it to a `TextureRegion` at render time.
3. Move `SpriteRenderer` into the Renderer module as a render-data type, keeping Scene free of renderer coupling.
