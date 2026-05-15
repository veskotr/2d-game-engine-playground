# SLE Lua Scripting - Documentation Index & Quick Reference

## 📚 Documentation Structure

This folder contains complete specification and implementation guides for SLE's Lua scripting system.

### Main Documents

| Document | Purpose | Audience | Length |
|----------|---------|----------|--------|
| **ARCHITECTURE.md** | Complete design specification addressing all architectural requirements | Architects, Technical Leads | ~8000 words |
| **IMPLEMENTATION_OVERVIEW.md** | Current-state map of the engine and recent implementations | All contributors | ~3000 words |
| **SCENE_ECS_CURRENT.md** | Current Scene, ECS, transform, and hierarchy model | All contributors | ~2000 words |
| **SCRIPTING_CURRENT.md** | Current Lua VM, API, and script lifecycle implementation | All contributors | ~2500 words |
| **RENDERING_CURRENT.md** | Current render pipeline, culling, batching, and GPU streaming | All contributors | ~2200 words |
| **OPTIMIZATIONS_CURRENT.md** | Summary of implemented engine optimizations 1-6 | All contributors | ~1800 words |
| **SCRIPTING_IMPLEMENTATION_GUIDE.md** | Concrete C++ patterns and code templates for implementation | Developers | ~4000 words |
| **COMPONENT_SYSTEM_GUIDE.md** | Component design patterns and extension guidelines | Component Developers | ~3500 words |
| **LUA_IMPLEMENTATION_QUICKSTART.md** | Step-by-step implementation checklist with tasks and tests | Team Leads, Sprint Planning | ~2000 words |
| **IMPLEMENTATION_REFERENCE.md** | Current-state truth and near-term implementation decisions for scripting and demo readiness | All contributors | ~1500 words |
| **MINIMAL_LUA_API.md** | Locked minimal Lua API v1 surface for immediate implementation | Scripting Implementers | ~1000 words |

---

## 🎯 Quick Decision Tree

**Question:** Where do I start?

- **"I need to understand the overall architecture"** → Read **ARCHITECTURE.md** sections 1-5
- **"I need the current engine state"** → Read **IMPLEMENTATION_OVERVIEW.md**
- **"I need Scene/ECS details"** → Read **SCENE_ECS_CURRENT.md**
- **"I need scripting details"** → Read **SCRIPTING_CURRENT.md**
- **"I need rendering details"** → Read **RENDERING_CURRENT.md**
- **"I need the implemented optimization summary"** → Read **OPTIMIZATIONS_CURRENT.md**
- **"I need the current truth before coding"** → Read **IMPLEMENTATION_REFERENCE.md** first
- **"I need exact API names to implement now"** → Read **MINIMAL_LUA_API.md**
- **"I need to implement the Lua layer"** → Use **LUA_IMPLEMENTATION_QUICKSTART.md**
- **"I need C++ code patterns"** → See **SCRIPTING_IMPLEMENTATION_GUIDE.md** sections 1-2
- **"I need to add a new component"** → Follow **COMPONENT_SYSTEM_GUIDE.md** section 3
- **"I need to debug a Lua issue"** → Check **SCRIPTING_IMPLEMENTATION_GUIDE.md** section 6
- **"I need to plan sprints"** → Use **LUA_IMPLEMENTATION_QUICKSTART.md** phases + **COMPONENT_SYSTEM_GUIDE.md** roadmap

---

## 📋 Document Summaries

### ARCHITECTURE.md

**What it covers:**
- Module dependency chain (clear one-direction flow)
- Single global Lua VM justification
- ScriptComponent lifecycle (init → update → destroy)
- EngineAPI interface with 30+ methods
- Runtime layer orchestration patterns
- ECS-Lua interaction data flows
- Component type registry design
- Data-driven scene loading (JSON format)
- Entity lifetime management patterns
- Memory safety (Lua refs vs raw pointers)
- Component architecture overview
- Implementation priority roadmap (5 phases)
- Design validation checklist (12 items)

### IMPLEMENTATION_OVERVIEW.md

**What it covers:**
- Current frame loop and runtime orchestration
- How Scene, Systems, Renderer, Resources, and Scripting fit together
- Current Lua API surface exposed by the engine
- What the recent optimization work changed and why
- Where to read the concrete implementation files

**When to read:**
- When you want the current codebase truth instead of the older target-state docs
- When you need to understand how the recent implementations fit together

### SCENE_ECS_CURRENT.md

**What it covers:**
- entity, registry, and hierarchy responsibilities
- current component set and their roles
- transform pipeline and dirty propagation
- how the recent script and optimization work depend on Scene

### SCRIPTING_CURRENT.md

**What it covers:**
- ScriptEngine responsibilities and lifecycle
- ScriptApi / ScriptApiImpl current surface
- Lua binding structure and namespaces
- how scripts load resources and interact with entities

### RENDERING_CURRENT.md

**What it covers:**
- RenderSystem command generation
- culling before submit
- Renderer batching and GPU upload strategy
- current state of the OpenGL path

### OPTIMIZATIONS_CURRENT.md

**What it covers:**
- what each optimization changed
- why it exists
- which files implement it
- how the timings relate to the changes

**Key Sections:**
- Section 2: Lua VM strategy (single VM with per-entity scripts)
- Section 3: ScriptComponent design
- Section 4: EngineAPI specification (table format)
- Section 5: Runtime orchestration
- Section 8: Component architecture
- Section 10: Implementation roadmap

**Decision artifacts:**
- Why single VM over per-scene? (Section 2.2)
- How to handle entity destruction during script execution? (Section 7.2)
- How to avoid breaking ECS encapsulation? (Section 6)

---

### IMPLEMENTATION_REFERENCE.md

**What it covers:**
- Current implementation reality vs intended architecture
- Script API v1 scope for a first demo
- Lua global access model for input/window/camera
- Ownership boundaries and file ownership for upcoming scripting work
- Acceptance criteria for demo readiness

**When to read:**
- Before starting any scripting implementation task
- Before adding or changing ScriptApi surface
- Before making architecture-sensitive runtime changes

---

### SCRIPTING_IMPLEMENTATION_GUIDE.md

**What it covers:**
- ScriptApi abstract interface with 30+ methods
- ScriptApiImpl implementation class
- LuaBindings layer with upvalue patterns
- Script lifecycle (load → init → update → destroy)
- Lua protected calls (lua_pcall) for error handling
- Memory safety with Lua registry refs (luaL_ref/luaL_unref)
- Component serialization patterns
- Debugging Lua stack traces
- Common pitfalls and fixes

**Code Examples Included:**
- Complete ScriptApi interface declaration
- ScriptApiImpl method implementations
- LuaBindings with lua_pushcclosure patterns
- ScriptEngine header and core methods
- Error handling with lua_pcall
- Stack trace debugging utilities

**Key Implementation Patterns:**
- Upvalue pattern for accessing C++ API from Lua
- Protected Lua calls with error handling
- Registry references for script function storage
- Component table conversion (C++ ↔ Lua)
- Entity validity checking before API calls

**Migration Checklist:**
- Phase 2a: Basic Infrastructure (10 tasks)
- Phase 2b: Script Lifecycle (7 tasks)
- Phase 2c: Component Access (5 tasks)
- Phase 2d: Input & Engine State (3 tasks)
- Phase 2e: Asset Loading (3 tasks)

---

### COMPONENT_SYSTEM_GUIDE.md

**What it covers:**
- Component design principles (data-only, serializable, independent)
- Existing components (Transform, SpriteRenderer, ScriptComponent)
- How to add new components (5-step process)
- Future components (Physics, Audio, Animator, Collider, Tilemap)
- Component registration patterns
- JSON serialization structure
- Performance considerations (sparse-set vs AoS)
- Physics system complete example
- Extension roadmap (10 phases through phase 10: Editor)
- Checklist for adding components

**Complete Example:**
- HealthComponent from design through Lua usage
- Physics system integration example
- Collider → collision callback patterns

**Future Components:**
- PhysicsComponent (mass, velocity, gravity)
- AudioComponent (playback, volume, pitch)
- AnimatorComponent (animation state machine)
- ColliderComponent (shapes, triggers, collision)
- TilemapComponent (Tiled map support)

**Extension Roadmap:**
Phases 1-10 with estimated scope:
- Phase 1: Core ECS + scripts (current)
- Phase 3: Physics (Box2D)
- Phase 4: Audio (miniaudio)
- Phase 5: Animation
- Phase 6: Tiled maps
- Phase 7-10: Particles, UI, Networking, Editor

---

### LUA_IMPLEMENTATION_QUICKSTART.md

**What it covers:**
- Phase 2a: Lua VM initialization (Task 1-3)
- Phase 2b: Lua bindings (Task 4-5)
- Phase 2c: Script lifecycle (Task 6-7)
- Phase 2d: Test scripts (Task 8-10)
- Phase 2e: Component access (Task 11-12)
- Phase 2f: Input & engine state (Task 13-15)

**Each Task Includes:**
- Files to modify
- Code checklist
- Expected test output
- Success criteria

**Build Instructions:**
```bash
cd c:\projects\engine
cmake -B build/debug
cmake --build build/debug
.\build\debug\Sandbox\Debug\sandbox.exe
```

**Common Issues:**
- "Undefined reference to lua_..." → Link lua::lua
- "Lua error: attempt to index nil" → Check API registered first
- Component changes don't appear → Call Engine.setComponent()
- Script crashes after init → Check Lua ref cleanup

---

## 🔗 Cross-References

### If you're reading ARCHITECTURE.md...

**Want implementation details?** → See SCRIPTING_IMPLEMENTATION_GUIDE.md section matching the architecture section
- Architecture section 2 (Lua VM) → Implementation section 3 (ScriptEngine)
- Architecture section 3 (ScriptComponent) → Implementation section 1-2 (ScriptApi)
- Architecture section 4 (EngineAPI) → Implementation section 2 (LuaBindings)
- Architecture section 5 (Runtime) → Implementation section 4 (Integration)

**Want to build components?** → See COMPONENT_SYSTEM_GUIDE.md
- Architecture section 8 (Component architecture) → Component guide sections 1-3

**Ready to implement?** → Use LUA_IMPLEMENTATION_QUICKSTART.md phases

---

### If you're implementing (Using QUICKSTART.md)...

**Need architecture context?** → ARCHITECTURE.md
- Task 1-3 (Lua VM) → ARCHITECTURE.md sections 1-2
- Task 4-7 (Bindings & lifecycle) → ARCHITECTURE.md sections 3-5
- Task 8-15 (Components) → COMPONENT_SYSTEM_GUIDE.md sections 1-3

**Need exact code patterns?** → SCRIPTING_IMPLEMENTATION_GUIDE.md
- Task 1 → Section 3.1
- Task 4 → Section 2.2
- Task 6 → Section 3.2
- Task 11-12 → Section 1.3

**Need to debug?** → SCRIPTING_IMPLEMENTATION_GUIDE.md section 5-6

---

## ⚙️ Implementation Flow

```
START: Team Planning
   │
   ├─→ ARCHITECTURE.md (Design Review, 1-2 hours)
   │      ├─ Section 1: Module dependencies
   │      ├─ Section 2: Lua VM strategy decision
   │      ├─ Section 3-5: ScriptComponent, EngineAPI, Runtime
   │      └─ Section 10: Implementation roadmap
   │
   ├─→ COMPONENT_SYSTEM_GUIDE.md (Component Planning, 1 hour)
   │      ├─ Section 1-2: Design principles
   │      ├─ Section 3: How to add components
   │      └─ Section 11: Extension roadmap
   │
   ├─→ LUA_IMPLEMENTATION_QUICKSTART.md (Sprint Planning, 1 hour)
   │      ├─ Phase 2a: Infrastructure (Week 1)
   │      ├─ Phase 2b: Bindings (Week 1-2)
   │      ├─ Phase 2c-d: Lifecycle & Testing (Week 2-3)
   │      └─ Phase 2e-f: Components & Input (Week 3-4)
   │
   ├─→ SCRIPTING_IMPLEMENTATION_GUIDE.md (Development Reference)
   │      ├─ Used during Tasks 1-15
   │      ├─ Code copy-paste patterns
   │      ├─ Error handling patterns
   │      └─ Memory safety guidelines
   │
   └─→ Development (4 weeks)
       Phase 2a: Infrastructure → Phase 2f: Input
       ✓ Build & test each phase
       ✓ Update ScriptApiImpl with new components
       ✓ Verify Lua scripts work in-engine
```

---

## 📊 Effort Estimation

| Phase | Duration | Tasks | Key Deliverables |
|-------|----------|-------|-----------------|
| 2a | 3-4 days | 3 | ScriptEngine, ScriptApi, basic LuaBindings |
| 2b | 3-4 days | 2 | Lua bindings (all 30+ methods), script loading |
| 2c | 2-3 days | 2 | Script lifecycle, init/update/destroy |
| 2d | 2-3 days | 3 | Test scripts, JSON scene loading |
| 2e | 2-3 days | 2 | All components getComponent/setComponent |
| 2f | 2-3 days | 2 | Input bindings, engine state access |
| **Total** | **16-20 days** | **15** | **Functional Lua scripting layer** |

---

## ✅ Validation Checklist

**After Phase 2a (Infrastructure):**
- [ ] Lua VM initializes without crash
- [ ] Compiles with no linker errors
- [ ] Can execute simple Lua code from C++

**After Phase 2b (Bindings):**
- [ ] Engine.log() works from Lua
- [ ] Engine.createEntity() returns entity ID
- [ ] Engine.isKeyDown() checks keyboard input

**After Phase 2c (Lifecycle):**
- [ ] Scripts load from .lua files
- [ ] init() called when entity created
- [ ] update() called every frame with correct dt
- [ ] destroy() called when entity destroyed

**After Phase 2d (Testing):**
- [ ] Test script outputs appear in console
- [ ] Scene JSON loads correctly
- [ ] Multiple entities can have scripts simultaneously

**After Phase 2e (Components):**
- [ ] Engine.getComponent() returns correct data
- [ ] Engine.setComponent() persists changes
- [ ] Entity position updates when Lua modifies Transform

**After Phase 2f (Input & State):**
- [ ] Engine.isKeyDown(GLFW_KEY_W) detects input
- [ ] Engine.getDeltaTime() returns frame time
- [ ] Entities can be controlled from Lua scripts

---

## 🛠️ Key File Locations

**To Create:**
```
Scripting/
  include/sle/scripting/
    ScriptApi.hpp                    (30+ methods interface)
    ScriptEngine.hpp                 (Lua VM management)
    LuaBindings.hpp                  (Helper functions)
  src/
    ScriptEngine.cpp                 (VM init/shutdown/load)
    LuaBindings.cpp                  (registerLuaBindings)

Engine/
  include/sle/engine/
    ScriptApiImpl.hpp                 (Implementation class)
  src/
    ScriptApiImpl.cpp                 (All 30+ methods)

Sandbox/
  main.cpp                           (Updated integration)

assets/
  scripts/
    test.lua                         (Test script)
  scenes/
    test.json                        (Test scene)
```

**To Modify:**
```
Scripting/CMakeLists.txt             (Add lua::lua dependency)
Engine/CMakeLists.txt                (Link sle_scripting)
Engine/src/Engine.cpp                (Add script detection in tick())
```

---

## 🔍 Key Concepts Quick Reference

| Concept | Definition | File |
|---------|-----------|------|
| **ScriptApi** | Abstract interface defining what Lua can access | SCRIPTING_IMPLEMENTATION_GUIDE section 1.1 |
| **ScriptApiImpl** | Runtime implementation of ScriptApi | SCRIPTING_IMPLEMENTATION_GUIDE section 1.2 |
| **LuaBindings** | C++ functions that bridge Lua → C++ | SCRIPTING_IMPLEMENTATION_GUIDE section 2 |
| **ScriptEngine** | Manages Lua VM, loads scripts, calls functions | SCRIPTING_IMPLEMENTATION_GUIDE section 3 |
| **ScriptComponent** | ECS component storing Lua refs for an entity's script | ARCHITECTURE section 3 |
| **EntityRef** | Safe opaque handle to entity (uint32_t ID) | ARCHITECTURE section 7.1 |
| **LuaTable** | Simple map<string, string> for component data | SCRIPTING_IMPLEMENTATION_GUIDE section 2.1 |
| **Registry Ref** | Lua reference from luaL_ref (not raw pointer) | SCRIPTING_IMPLEMENTATION_GUIDE section 5.2 |
| **lua_pcall** | Protected Lua call with error handling | SCRIPTING_IMPLEMENTATION_GUIDE section 5.1 |
| **Upvalue** | C++ data accessible to Lua closure | SCRIPTING_IMPLEMENTATION_GUIDE section 2.2 |

---

## 📖 How to Use These Documents

### For Architects
1. Read **ARCHITECTURE.md** sections 1-6 (1-2 hours)
2. Review design decisions in sections 2.2, 3, 5, 7
3. Check implementation roadmap (section 10)
4. Validate against design checklist (section 11)

### For Technical Leads
1. Read **ARCHITECTURE.md** section 10 (Implementation roadmap)
2. Use **LUA_IMPLEMENTATION_QUICKSTART.md** for sprint planning
3. Estimate effort from table in this document
4. Break into 2-week sprints

### For Developers
1. Use **LUA_IMPLEMENTATION_QUICKSTART.md** as primary reference
2. Copy code patterns from **SCRIPTING_IMPLEMENTATION_GUIDE.md**
3. Reference **COMPONENT_SYSTEM_GUIDE.md** when adding components
4. Check **ARCHITECTURE.md** sections 2-5 for design context

### For Component Authors
1. Read **COMPONENT_SYSTEM_GUIDE.md** sections 1-3
2. Follow "Adding New Components" checklist in section 3
3. Use component template from section 4
4. Follow JSON serialization pattern (section 6)

### For Debugging
1. Check **SCRIPTING_IMPLEMENTATION_GUIDE.md** section 5 (Error Handling)
2. Use debugging tips from section 6 (Stack traces)
3. Refer to common issues in **LUA_IMPLEMENTATION_QUICKSTART.md**
4. Check memory safety patterns in **SCRIPTING_IMPLEMENTATION_GUIDE.md** section 5.2

---

## 🚀 Next Steps

**Immediate (Today):**
- [ ] Review ARCHITECTURE.md sections 1-5 as team (1-2 hours)
- [ ] Assign lead architect to validate against requirements
- [ ] Discuss design decisions (why single VM? why ScriptApi interface?)

**Short-term (This week):**
- [ ] Create implementation tasks from LUA_IMPLEMENTATION_QUICKSTART.md
- [ ] Assign Phase 2a tasks to developers
- [ ] Set up development environment (Lua dependency)

**Medium-term (Weeks 1-4):**
- [ ] Execute Phase 2a-2f per sprint plan
- [ ] Build and test each phase
- [ ] Integrate completed features into Sandbox demo

**Long-term (Phases 3-10):**
- [ ] Reference COMPONENT_SYSTEM_GUIDE.md extension roadmap
- [ ] Plan Physics, Audio, Animation, Tiled integration
- [ ] Consider future editor tooling

---

## 📚 Related Resources

**Inside SLE:**
- Scene module (ECS implementation) - for component access patterns
- Renderer module (OpenGL) - for rendering commands
- Input module (GLFW) - for input queries

**External:**
- Lua 5.4 Reference Manual (lua.org)
- Lua C API docs (lua.org)
- Box2D documentation (physics Phase 3)
- Tiled map editor (tilemap Phase 6)

---

## ❓ FAQ

**Q: Can I modify the architecture?**
A: Yes, with stakeholder buy-in. All design decisions are justified in ARCHITECTURE.md section 2-5. Propose alternatives there.

**Q: Which tasks can run in parallel?**
A: Phase 2a (infrastructure) must complete first. Then 2b-2d can run in parallel with coordination.

**Q: How do I know if implementation is correct?**
A: Check validation checklist at end of each phase in QUICKSTART.md. Run provided test scripts.

**Q: Can I add components before Phase 2e is complete?**
A: Yes. Follow section 3 of COMPONENT_SYSTEM_GUIDE.md "Adding New Components". Implement getComponent/setComponent patterns.

**Q: What if I find a bug in the design?**
A: Document it, propose fix, update ARCHITECTURE.md, notify team. Design is iterative until Phase 2 starts.

---

## 📝 Document Maintenance

**When to update:**
- After major design decisions
- When implementation reveals issues
- When requirements change
- When new components are added

**Version tracking:**
- ARCHITECTURE.md - v1.0 (completed design)
- SCRIPTING_IMPLEMENTATION_GUIDE.md - v1.0 (completed reference)
- COMPONENT_SYSTEM_GUIDE.md - v1.0 (completed reference)
- QUICKSTART.md - v1.0 (completed checklist)

---

**Document created:** [Current Date]
**Status:** Ready for implementation
**Approval:** Pending technical review

