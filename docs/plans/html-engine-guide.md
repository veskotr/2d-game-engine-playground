# SLE HTML engine guide implementation plan

Build an offline HTML guide that takes a first-time SLE user from opening the editor to exporting a small playable project. Give C++ contributors a separate route through the same site from scene data to the runtime and validation commands. By the end of the milestone, every current user-facing editor tool, panel, view, and engine capability must have a usage entry. Each session below ends with a usable update and a check another session can repeat.

## Design decisions

Use hand-authored HTML and shared CSS under `docs/guide/` for the first release. This lets someone open `index.html` directly or serve the same folder over HTTP. A Markdown generator would reduce repeated navigation as the site grows, at the cost of adding a build workflow. Keep the existing Markdown as the engineering reference. Add a link from the repository documentation index when the HTML landing page exists.

Adapt Little Forest from the [editor design system](../../tools/editor/UI_DESIGN.md) and [palette implementation](../../tools/editor/src/main/kotlin/sle/editor/ui/theme/EditorTokens.kt). Use neutral surfaces, subtle panel borders, green for active navigation and focus, and generous spacing in articles. Use Roboto when available, a system sans-serif fallback for reading, and monospace for commands and data. Reserve Handjet for the wordmark if bundled for offline use. Start with the editor's dark appearance and check text and control contrast on every surface. The article layout should stack on narrow screens. Put the chapter list in shared markup with a keyboard-accessible skip link, current-page indication, and visible focus. Keep keyboard and browser history navigation working with JavaScript disabled. Add offline search and category filters on the feature index using a deferred local script that filters the visible feature links in place. Search titles, aliases, shortcut names, and keywords. Show an empty-state message and a result count, and keep every feature link visible by default. Use native disclosure controls for optional details and annotated screenshots with equivalent numbered text links. Keep the core guide usable with scripts disabled.

Use one tutorial project, **First Room**, as the thread. Start with a sprite and gameplay camera, then add input and Lua movement, a wall and trigger, and a small HUD. Save and export that same project. Verify each step from a clean project before capturing it. The [existing NPC Zone demo](../../assets/scenes/npc_zone_demo.json) and [its Lua script](../../assets/scripts/npc_zone_demo.lua) are source material for trigger and gameplay examples.

## Sessions

### 1. Build the site shell and quickstart

- [x] Create `docs/guide/index.html`, `quickstart.html`, and `styles.css`. The home page gives users two entry points, **Make your first game** and **Understand the engine**. Add the second link when its target ships. Create `coverage.md` next to this plan as the cross-session inventory. Record the revision, relevant worktree changes, and editor/runtime versions from the build used to confirm features. Seed the inventory from the coverage checklist below and the current registries and runtime entry points.
- [x] Give the quickstart a Windows PowerShell path to build and run the editor using the [contributor setup](../../CONTRIBUTING.md) and [editor instructions](../../tools/editor/README.md). Give users a direct way to run the bundled demo. Check launcher flags against [engine_app/main.cpp](../../engine_app/main.cpp) and the startup scene against [engine.json](../../engine.json).
- [x] Link the guide from the [documentation index](../README_DOCUMENTATION.md). Use relative asset and page URLs. Test home and quickstart over both direct file access and local HTTP.

### 2. Walk through editor authoring

- [x] Add `editor.html` and `first-scene.html`. Explain the Hierarchy, Scene, Details, Assets, and Game views in context. Create the project and a scene, import a sprite, create and position an entity, add or configure the gameplay camera, select the startup scene, save, and enter Play.
- [x] Cross-check names and defaults with the [project guide](../topics/Projects.md), [scene editor guide](../topics/SceneEditor.md), [editor menu](../../tools/editor/src/main/kotlin/sle/editor/ui/EditorMenuStrip.kt), and [panel registry](../../tools/editor/src/main/kotlin/sle/editor/workspace/PanelRegistry.kt). Explain what the reader should see after saving, playing, and reopening the project. Verify the scene opens from a fresh editor launch.
- [x] Explain when to use project `.scene.json` files or portable `.slescene` packages. Link to [scene identity](../topics/Scenes.md), [prefabs](../topics/Prefabs.md), and [prefab packages](../topics/PrefabPackages.md) from the relevant steps.

### 3. Add gameplay and export

- [x] Add `lua-gameplay.html`, `physics-and-ui.html`, and `run-and-export.html`. Document an input action and Lua behaviour on the tutorial entity. Add a wall, a trigger, and an XML HUD. Describe the expected movement, collision, trigger, and HUD effects at the end of each section.
- [x] Verify Lua names and lifecycle against the [generated API stub](../../assets/scripts/engine_api.lua), [Lua behaviour documentation](../topics/Behaviours.md), and current bindings. Use the [physics guide](../topics/Physics.md), [events guide](../topics/Events.md), and [HUD sample](../../assets/ui/fps_hud.xml) to verify the remaining examples.
- [x] Walk through Play, `engine_app --project`, and the Windows Release export workflow. Use the [project guide](../topics/Projects.md) and [game package guide](../topics/GamePackages.md) for output paths and launch behavior. Copy the complete export folder to a different location and launch the copied executable.

### 4. Explain the engine and verify the site

- [x] Add `engine-tour.html` and `reference.html`. Follow a tutorial entity from scene loading through ECS and transform propagation, then show where Lua, physics, events, rendering, and UI fit into the frame. Link chapter concepts to module docs, generated Lua API, and focused validation commands.
- [x] Use the [architecture guide](../topics/Architecture.md) and [constraints](../CONSTRAINTS.md), then verify against [Runtime.cpp](../../EngineModules/Systems/src/Runtime.cpp), [SceneLoader.cpp](../../EngineModules/Systems/src/SceneLoader.cpp), and [Systems CMake targets](../../EngineModules/Systems/CMakeLists.txt). Leave native tile painting and collision for the dedicated feature-reference session, where the walkthrough can be checked against the build selected for the milestone.
- [x] Add a standard-library link checker at `docs/guide/check_links.py`. It should walk the guide HTML, validate relative local URLs in `href` and `src`, verify HTML fragment IDs, report broken references with their source file, and exit nonzero on failure. Verify a renamed target and a stale fragment are caught in a temporary copy. Use `python3 docs/guide/check_links.py` and `git diff --check` on every later update.
- [x] From the repository root, run `python3 -m http.server 8000` and open `http://localhost:8000/docs/guide/`. Serving the repository root keeps links to the engineering docs and samples reachable. Check every chapter from the home page, the next and previous links, keyboard focus, and a 360 px viewport. Repeat navigation by opening `docs/guide/index.html` directly. Confirm that missing screenshots do not interrupt any tutorial step.

### 5. Complete the editor and engine feature reference

- [x] Use `coverage.md` to audit the current [command sources](../../tools/editor/src/main/kotlin/sle/editor/command/), [workspace panels](../../tools/editor/src/main/kotlin/sle/editor/workspace/PanelRegistry.kt), [viewport tools](../../tools/editor/src/main/kotlin/sle/editor/viewport/tools/), inspector schema, and project and asset context menus. For each active user-facing feature, give it a direct HTML anchor and document its purpose, how to open or invoke it, prerequisites or selection context, shortcuts where applicable, and expected result. Group related actions under the relevant workflow.
- [x] Add `editor-tools.html`, `project-assets.html`, and `tilemaps.html` alongside the existing workspace and tutorial material. Cover transient panels, dialogs, modes, and tools as well as the default workspace. Verify tile authoring against the [tilemap room walkthrough](../../examples/tilemap_room/README.md) and the documented build, including spritesheet slicing, layers, painting modes, selections and clipboard, tile collision preview and generation, save/reopen, and export.
- [x] Add `engine-features.html` and map subsystems to real authoring, Lua, configuration, or C++ entry points. Use a small example for each capability or link to a verified example that demonstrates it. For schemas and bindings, link the detailed reference from the usage entry.
- [x] Make `reference.html` the searchable feature index. Add `search.js` and check that searches for a menu label, a shortcut, and an engine term find their chapters. Test a no-match query, clearing a query, switching categories, navigating to a feature by keyboard, reloading a direct anchor, and using the index with JavaScript disabled.
- [x] Reconcile each inventory row with a working guide anchor. Where the selected build lacks a candidate feature, record the version or other evidence in the inventory. Resolve all unknown coverage rows before the milestone is called complete.

### 6. Capture screenshots and add the maintenance skill

- [x] Try to capture the images below from the verified tutorial project using the packaged or matching local editor build. Check interactive desktop access and use window-only capture where available. If blocked, record the exact screen and capture reason in the inventory and hand off that image request. Capture the available images and put them under `docs/guide/images/`. Until an image is available, put the expected screen state in the article as text. Add each image with a figure caption and alt text that explains what the reader should locate. Keep critical button names and values in the article text.
- [x] Add `.agents/skills/sle-docs-page/SKILL.md` using the repository's skill naming conventions. The workspace currently marks `.agents` read-only, so that session needs authorized write access to install the skill there.
- [x] The skill should trigger on requests to add or edit an HTML guide page. It should read the guide and documentation maintenance rules, confirm changed claims against runnable entry points or code, reuse the layout and CSS, preserve existing URLs and heading IDs, update affected navigation, and run the link check. It should capture the filename, UI state, alt text, and capture blocker for any requested screenshots. Keep new page URLs lowercase and hyphenated.
- [x] Validate both workflows on temporary copies of the guide. Add a page with working incoming and outgoing links, then edit a page while preserving its public URL and fragment links. Run the link checker and inspect the diff after each exercise.

## Coverage checklist

These groups seed the inventory. Expand each group into individual rows from the build and source during session 1. Each row records the exact feature name, location, availability or mode, guide anchor, evidence, and verification status.

| Area | Include |
|---|---|
| Editor workspace | Scene, Game, Hierarchy, Details, Assets, Console, Lua Profiler, Entity Template, Tile Palette when enabled, dock and float, focus, saved layout, recovery/reset, theme, density, and UI scale |
| Viewport and playback | Select, Move, Rotate, Scale, Tiles, framing and fit, camera navigation, multi-select, snapping, pivot, Play, Pause, Step, Stop, Run Project, Live Inspector, and Lua reload |
| Editor commands and authoring | File, Edit, Create, View, Tools, window and shortcut menus, context actions, undo/redo, entity hierarchy, component controls, scene cameras, state machines, event areas, and Run Lua |
| Project and assets | Create/open/save, startup scene and window, project settings, sorting and collision layers, tags, input actions, import and UUID identity, sprite regions and filtering, prefabs and overrides, portable packages, and runnable game export |
| Engine and scripting | ECS and transforms, scene loading/switching, chunk activity, rendering and cameras, tile layers, physics/collision/triggers, events and subscriptions, Lua lifecycle/bindings/gameplay components, state machines, animation, audio, XML UI, resources, launch configuration, and package loading |
| Developer workflows | C++ module boundaries, runtime frame order, example apps, editor/engine build paths, and test targets and labels |

## Screenshot capture list

Use the same project, Little Forest dark appearance, and a consistent desktop scale. PNG is preferred for UI text. Crop to the relevant panels while preserving enough context to find them. Use the shipped editor labels from the build being documented.

| File | Chapter | Capture and draft alt text |
|---|---|---|
| `editor-workspace.png` | Editor | Full workspace with First Room open. “Hierarchy on the left, Scene in the center, Details on the right, and Assets visible.” |
| `first-room-entity.png` | First scene | Select the player sprite and show its details. “The Player entity is selected in Hierarchy and Scene, with Transform and Sprite Renderer visible in Details.” |
| `first-room-play.png` | First scene | Enter Play with the expected gameplay camera. “The Game view shows the player sprite framed by the scene camera.” |
| `lua-behaviour.png` | Lua gameplay | Show the selected player's configured Lua behaviour. “Player Details shows the movement script attached to the entity.” |
| `physics-and-hud.png` | Physics and UI | Capture Play at the wall or trigger with the HUD visible. “The player stands against the wall near the trigger, with the HUD visible over the game.” |
| `tiles-and-collision.png` | Tilemaps | Show the Tile Palette and a separate collision detail if needed. “The palette shows the current tileset and the collision preview outlines the selected tile.” |
| `live-debugging.png` | Editor tools | Show a relevant inspection state during Play. “The Game view is running with live entity values visible in Details.” |
| `export-game.png` | Run and export | Show the actual export command and its location. “The editor's File menu shows the Export Game command.” |

## Handoff for a fresh session

The Session 3 First Room project is available locally under
`build/docs-session3/First Room/`, with a copied Release delivery under
`build/docs-session3/Relocated Delivery/`.

Session 6 uses copies of the Session 2 and 3 First Room projects and Tilemap
Room. See [Session 6 results](#session-6-results) and the
[screenshot inventory](coverage.md#session-6-screenshot-inventory).

The milestone is complete when the HTML guide runs locally, the searchable index links to every verified current feature, the First Room walkthrough succeeds from a clean project through relocated export, and the page maintenance skill has been validated for adding and editing pages. Review the inventory against the documented build once more before closing the milestone.

## Session 1 results

- Added `docs/guide/index.html`, `quickstart.html`, and `styles.css`, and linked the guide from `docs/README_DOCUMENTATION.md`. The home page reserves the engine-tour route for Session 4. Chapter navigation uses ordinary relative links, a skip link, and current-page state.
- Added [the coverage inventory](coverage.md) with 195 source-based entries and planned anchors across the milestone. The existing native tile sources and editor work are included. Baseline was `a00b7f3` plus the pre-existing working changes. Gradle declares editor 0.32.0 and CMake declares engine 0.33.0.
- Served the repository root with `python3 -m http.server 8765 --bind 127.0.0.1`. Edge 158.0.4370.33 in headless mode with `--dump-dom` loaded the home and quickstart over both `file:` and `http://localhost:8765/`; the HTML and shared CSS returned HTTP 200. A temporary Python check validated local links, fragments, chapter markup, and the inventory anchors. The 16 text and surface color pairs tested had a minimum contrast of 5.39:1.
- The existing Windows Debug `engine_app.exe --version` reported 0.33.0. `engine_app.exe --config engine.json --frames 120` loaded `npc_zone_demo`, initialized its Lua scripts, completed 120 frames, and exited successfully.
- `git diff --check -- docs/README_DOCUMENTATION.md` and a trailing-whitespace scan of the new files passed. The repository-wide `git diff --check` flagged pre-existing line endings and whitespace in unrelated changes.
- Remaining verification includes a clean Windows editor build and launch, keyboard and narrow-screen visual walkthroughs, and the rest of the First Room workflow. Screenshots, full reference anchors and search, the permanent link checker, and the maintenance skill are scheduled for later sessions.


## Session 2 results

- Added the Editor workspace and First Room chapters, linked them from every chapter, and updated the coverage anchors. The tutorial uses the bundled `tile2.png` at 64×64 world units, a separate root camera centered on Player, and a new `FirstRoom.scene.json` alongside the generated Main scene.
- On Windows, `cmake --build build/debug --config Debug --target sle_editor_native --parallel 4` built the Debug bridge. The first Gradle invocation supplied an extra `--console` option that the wrapper already sets. The subsequent `tools/editor/gradle.ps1 run --args=--project-runtime-smoke --no-daemon` compiled the editor and passed startup selection, Run Project, and Stop. The normal `tools/editor/gradle.ps1 run` window title reported editor **0.32.0** and engine **0.33.0**.
- `tools/editor/gradle.ps1 run --args=--project-documents-smoke` passed the project documents and prefab workflow checks, including save, same-process reopen, and Play. The fixture also reported texture warnings for a sprite with an empty texture path.
- Cross-checked menu labels, panel names, inspector section labels, scene defaults, and file formats against the running editor title, source, and project and scene docs. The startup control is labeled **Startup: …**, and the inspector component headings are **SpriteRenderer** and **Camera2D**.
- Temporary Python checks passed for local links, HTML and Markdown fragments, sidebar navigation, current-page markers, coverage anchors, and trailing whitespace. A loopback HTTP check and direct file reads returned identical bytes for all four chapters, the shared CSS, and the tutorial image.
- The clean First Room walkthrough and explicit fresh-process reopen still need a dedicated Windows desktop session. The shared desktop was in use by another foreground application, so the Windows smoke results cover their own fixtures. Follow First Room from project creation through Play, Stop, editor exit and relaunch, and Play again. Confirm the saved startup choice with Run Project. Record any UI label corrections and results here, then check off the remaining Session 2 item. Capture the tutorial screenshots in Session 6.


### Session 2 closeout

- Created a clean First Room project on Windows using a temporary smoke harness on editor **0.32.0** with the **0.33.0** Debug bridge. The harness called the editor's project, asset import, scene authoring, and document-save paths to build the exact tutorial project, including the image, Player, camera, and startup scene UUID. It verified embedded Play and Stop, both runtime entities, and the 480×270 camera viewport. Verification used editor actions programmatically. The prior Windows smoke tests exercised the startup selector and other UI controls.
- `tools/editor/gradle.ps1 run "--args=--project-runtime-smoke --first-room-create"` and `tools/editor/gradle.ps1 run "--args=--project-runtime-smoke --first-room-reopen"` both reported `FIRST_ROOM_GUIDE_PASS` from distinct process IDs (42316 and 7952). The second process reopened the saved project, recovered First Room with its texture and camera, and repeated Play and Stop. The temporary hook was removed from the editor source and the original file was restored byte for byte.
- `cmake --build build/debug --config Debug --target engine_app --parallel 4` succeeded. `build/debug/engine_app/Debug/engine_app.exe --project "build/docs-session2/First Room/First Room.sleproject" --frames 120` loaded the saved startup UUID and completed all 120 frames. This bounded run verified startup selection for the tutorial project. The existing editor runtime smoke separately verified the Run Project control.
- The earlier desktop limitation is resolved for authoring, Play, and fresh-process recovery through the temporary harness. A manual click-through and screenshot capture remain available for the later visual verification sessions.


## Session 3 results

- Added Lua gameplay, Physics and UI, and Run and export. All seven pages link to the new chapters. The walkthrough binds **A/D** to **MoveLeft/MoveRight** and drives Player's dynamic body at 160 world units/s. A static wall blocks Player, a box event area tracks checkpoint overlap, and a screen-space XML HUD displays that state. Source checks covered the generated Lua stub, registered input and physics functions, behaviour lifecycle, zone payloads, XML binding syntax, and the editor commands.
- Copied the Session 2 project to `build/docs-session3/First Room/` and authored the additions in that copy using the project and scene formats. The tutorial Lua and XML match the copy. The visible editor labels used for attachment are **New Lua Behavior…** and **Create and Attach**. The guide publishes HUD state from `update` so it reaches the UI after the document is created.
- Windows `cmake --build build/release --config Release --target sle_runtime_bundle --parallel 4` succeeded. `tools/editor/gradle.ps1 run "--args=--project-distribution-smoke" --no-daemon` with a temporary First Room hook passed on editor **0.32.0** with engine **0.33.0**. The hook exercised **Edit → Play**, clicked Game for focus, and sent A/D through the desktop. Player stopped at X **371.25** with D still held, moved away on A, and logged checkpoint entry and exit. The two window captures in the build folder show **Checkpoint: Inside** and **Checkpoint: Outside**. The hook also exercised **Edit → Run Project**, Stop, and **File → Export Game…** with the Release bundle chooser. Earlier harness attempts exposed an exact-coordinate assertion, missing Game focus, and an unopened lazy menu. The temporary hook was removed and its source file restored byte for byte.
- `build/debug/engine_app/Debug/engine_app.exe --project "build/docs-session3/First Room/First Room.sleproject" --frames 120` completed 120 frames on engine **0.33.0**. Copied all 47 files in the exported delivery and matched their SHA-256 hashes. From `C:/projects/engine/build`, launched `C:/projects/engine/build/docs-session3/Relocated Delivery/engine_app.exe --frames 120` without a config or project argument. It also reported **0.33.0**, discovered its adjacent project, and completed 120 frames on the same startup scene UUID `096afd7e-160d-4cee-a0a0-862db723201a`. Both standalone checks were bounded startup checks. Gameplay and HUD interaction were checked in editor Play.
- A temporary Python check passed for 111 local links, HTML and Markdown fragments, sidebar links, current-page markers, inventory anchors, and trailing whitespace. `git diff --check -- docs` passed. A loopback HTTP attempt hit the shell sandbox's socket restriction. The browser walkthrough, permanent checker, and full keyboard and narrow-screen checks are Session 4 work. Manual authoring clicks and guide-ready screenshots remain for later visual verification.


## Session 4 results

- Added the [Engine tour](../guide/engine-tour.html) and [Reference](../guide/reference.html) to the sidebar and chapter sequence, with a contributor route on Home. Cross-checked the saved First Room Player against SceneLoader, ProjectRuntime, Registry, TransformSystem, PhysicsSystem, RenderSystem, Runtime, and the Systems CMake target. The reference lists focused Windows validation commands checked against the CMake test definitions. This session used the existing editor 0.32.0 and engine 0.33.0 First Room artifacts as source evidence. No native build or tests were needed for the guide update.
- Published all 12 planned engine tour anchors in the coverage inventory. Search, the full feature reference, native tile authoring, and screenshot capture remain scheduled in Sessions 5 and 6.
- `python3 docs/guide/check_links.py` scanned 199 references across nine pages and reported zero broken links. A temporary fixture returned exit 1 with source and URL diagnostics for a renamed HTML file, a stale HTML fragment, and a missing image. The valid fixture returned exit 0.
- Served the repository root with `python3 -m http.server 8000 --bind 127.0.0.1`. Windows Edge 158.0.4370.33, driven headlessly by `build/docs-session4/browser_check.py`, followed both Home routes and the complete forward and backward chapter sequence over HTTP and direct `file:` URLs. All nine pages fit a 360 px viewport without document-level horizontal overflow. On each page, Tab focused the skip link with a visible outline and Enter moved focus to the main content. The tutorial remains usable from its instructions and expected-state text with screenshots pending.
- `git diff --check -- docs` and a whitespace scan of the touched guide and plan files passed. Repository-wide `git diff --check` reported pre-existing whitespace and line endings in files outside this change.


## Session 5 results

- Added Editor tools, Project and assets, Tilemaps, and Engine features. Linked
  them in the chapter navigation and indexed all 208 linked inventory rows.
  Preserved the existing Reference anchors and links. Recorded reserved shortcuts,
  planned rebinding, and unavailable asset name/type search in the inventory.
  The asset browser visibility and refresh commands are under View.
- Selected Windows editor 0.32.0 (generated Gradle version) and engine 0.33.0
  (`engine_app.exe --version`) from the working snapshot at `a00b7f3` with existing
  edits. `build/debug/engine_app/Debug/engine_app.exe --project examples/tilemap_room/Room.sleproject --frames 8`
  completed eight frames on the room's startup scene.
- Ran `ctest --test-dir build/debug -C Debug -R "^integration_(tilemap_(scene|collision|render)|game_package)_path$" --output-on-failure`
  on Windows. All four passed, including the windowed render and collision
  overlay checks.
- In Windows PowerShell, ran
  `./tools/editor/gradle.ps1 test run --args=--tile-painting-smoke --offline --no-daemon "-Pkotlin.incremental=false"`.
  The editor smoke passed and the Gradle test task was up to date. It covered
  painting, erasing, rectangle, eyedropper, fill, selection, clipboard,
  stamping, undo/redo, save/reopen and Play on a copied fixture. The first
  invocation split the unquoted Kotlin property. The quoted retry passed.
- Ran `./tools/editor/gradle.ps1 test --tests "*TileCollisionTest" --tests "*TilesetCollisionTest" --offline --no-daemon "-Pkotlin.incremental=false"`.
  All 13 passed, including alpha threshold, perforation, geometry validation,
  apply/reopen and undo. The editable collision preview was reviewed against
  its source. A manual fresh-project pass comparing that preview with the
  runtime overlay is still open.
- Ran the same Gradle wrapper with `run --args=--asset-browser-smoke` and
  `run --args=--project-distribution-smoke`, both with
  `--offline --no-daemon "-Pkotlin.incremental=false"`. Both passed. The
  asset smoke covered slicing, region editing, stable IDs and save/reopen.
  The distribution smoke copied and exported Tilemap Room, relocated the
  delivery, and launched it for eight frames from an unrelated working
  directory after moving away the source PNG and sidecar. A second export
  rejected the missing spritesheet UUID without publishing a delivery.
- Ran `python build/docs-session5/browser_check.py` on Windows with Edge
  158.0.4370.33. Checked all 13 chapters at 360 px over HTTP and direct file
  access. Verified menu-label, shortcut and Box2D searches, counts, no
  matches, clear, category switching, keyboard link activation, direct
  fragment reload and all 208 entries with JavaScript disabled.
- Ran `python3 docs/guide/check_links.py` and checked inventory links against the
  index. All 561 guide references and 208 inventory guide targets resolved.
  Repository-wide `git diff --check` reported pre-existing CRLF additions
  outside the guide. The scoped check, a whitespace scan of the untracked
  guide and plan files, and `git -c core.whitespace=cr-at-eol diff --check`
  passed.

## Session 6 results

- Opened copies of the saved pre-Lua First Room, completed First Room, and
  Tilemap Room with Windows editor **0.32.0** and engine **0.33.0**, as shown
  in the window titles. Used `tools/editor/gradle.ps1 run` with
  `--args=--project <copy-path>`, `--offline`, `--no-daemon`, and
  `"-Pkotlin.incremental=false"`. The initial invocation split the path
  containing spaces. Paths without spaces resolved it.
- Added all eight requested screen states and a separate Tile Collision
  figure. Captured the window bounds in Little Forest dark using the
  interactive Windows console. Reused the verified Session 3 HUD capture
  from the same editor, engine, and project. Enlarged the Tile Palette to
  show its paint modes and tile thumbnails, then captured the Slope
  collider in the separate dialog. All requested captures are present.
  The [screenshot inventory](coverage.md#session-6-screenshot-inventory)
  records the exact states and alt text.
- Confirmed **Tile Palette** is the Scene toolbar label and **File → Export
  Game…** is the export command. Updated the Tile Palette article, search
  keywords, and inventory. All 208 feature index entries match the
  feature rows in the inventory.
- Installed [`sle-docs-page`](../../.agents/skills/sle-docs-page/SKILL.md)
  with approved write access to `.agents`. Ran the skill creator's
  `quick_validate.py .agents/skills/sle-docs-page`. In separate temporary
  guide copies, added a chapter with incoming navigation and an outgoing
  link to `first-scene.html#play`, then edited that heading in another copy
  while preserving the page URL and all fragments. Inspected both diffs.
  The link checker passed with 603 references for the add exercise and
  570 for the edit exercise.
- Ran `python3 docs/guide/check_links.py`. All 570 references across 13
  guide pages resolved. The targeted inventory, figure, and whitespace
  checks passed, as did `git -c core.whitespace=cr-at-eol diff --check`.
- Ran `python build/docs-session5/browser_check.py` in Windows Edge
  158.0.4370.33. All 13 chapters loaded at 360 px over HTTP and direct
  file access, including the nine images. Keyboard navigation, search,
  filters, fragment reload, and all 208 links with JavaScript disabled
  passed in both modes.
- The separate manual comparison between the editable collision preview
  and the runtime fixture overlay from Session 5 remains open.
