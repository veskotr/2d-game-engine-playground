---
Title: SLE 2D Engine - Context Files Guide
Version: 1.0 (May 2026)
Audience: Developers and AI Assistants
---

# SLE Engine Context Files - Setup Guide

This document explains the new context files created for consistent AI and developer understanding of SLE.

---

## Overview

Four comprehensive context files have been created to ensure all future AI interactions comply with the same architectural principles:

| File | Purpose | Audience | When to Use |
|------|---------|----------|------------|
| **copilot-instructions.md** | Core architectural principles & development workflows | AI + Developers | Always - foundational reference |
| **ARCHITECTURE_VERIFIED.md** | Complete verified architecture (matched against code) | AI + Developers | Deep dives, understanding design |
| **AI_CODE_GUIDELINES.md** | Strict rules for code generation | AI Tools | Before generating any code |
| **This file** | How to use the context files | Everyone | Onboarding & setup |

---

## File 1: copilot-instructions.md

### Purpose
Primary guide for understanding SLE's architecture, design decisions, and development patterns.

### What It Contains
✅ **Core Architectural Principles**
- Strict module layering (Core → Platform → Renderer → Resources → Scene → Scripting → Systems → Runtime → Sandbox)
- ECS architecture (pure data components, systems-driven logic)
- Single Lua VM strategy
- Command-based rendering

✅ **Module Breakdown**
- Responsibility of each module
- What each module exports vs. doesn't
- How modules communicate (dependency injection via Context)

✅ **Common Workflows**
- How to add a new component
- How to add a Lua API function
- How to create a new system
- How to extend the renderer

✅ **Key Design Decisions & Rationale**
- Why strict layering (vs circular dependencies)
- Why pure ECS (vs OOP hierarchies)
- Why single Lua VM (vs per-entity VMs)
- Why command-based rendering (vs direct API calls)

✅ **Code Style & Conventions**
- Namespace patterns
- Naming conventions (PascalCase, camelCase, etc.)
- Header/source organization
- Error handling patterns

✅ **AI Guardrails**
- What to ask AI to do (preserve layering, keep ECS pure, etc.)
- Examples of GOOD vs BAD AI requests

### How to Use
1. **First time working with SLE**: Read sections 1-3
2. **Adding new component**: Jump to "Common Workflows" → "Adding a New Component"
3. **Extending renderer**: Jump to "Common Workflows" → "Extending Renderer"
4. **Training AI on the project**: Share this file directly with the AI

### Key Quote
> "When using AI to extend SLE: Preserve layering, ECS purity, boundary compliance, API consistency, and doc sync."

---

## File 2: ARCHITECTURE_VERIFIED.md

### Purpose
Comprehensive reference document that reconciles all documentation with actual source code. **This is the source of truth.**

### What It Contains

✅ **Module Architecture** (Part 1)
- Complete dependency chain with ASCII diagram
- Each module's responsibilities, what it owns/doesn't own
- External dependencies per module
- Verification status (all verified ✅)

✅ **Detailed Component & System Design** (Part 2)
- Entity-Component-System pattern explained
- Component types currently implemented with code snippets
- Transform system algorithm (iterative DFS)
- Render system pipeline (frustum culling, batching)
- Script system lifecycle

✅ **Lua Scripting Integration** (Part 3)
- Why single Lua VM
- ScriptApi interface pattern
- Lua bindings registration
- Script lifecycle example

✅ **Rendering Pipeline** (Part 4)
- Two-layer architecture (RenderSystem + Renderer)
- GPU optimization strategies (instancing, batch ordering, ping-pong VBOs)
- Frustum culling details
- Renderer API reference

✅ **Game Loop** (Part 5)
- Exact frame execution order (verified against Runtime::run())
- Profiling measurements
- Frame time budget breakdown

✅ **Verification Against Code** (Part 8)
- Documentation accuracy checklist (all ✅)
- Known limitations & TODOs
- Extension points

✅ **Development Workflows** (Part 10)
- Step-by-step adding a component
- Step-by-step adding a Lua API function
- Step-by-step creating a new system

### How to Use
1. **Understanding frame loop**: Jump to Part 5
2. **Understanding rendering**: Jump to Part 4
3. **Understanding ECS**: Jump to Part 2.1
4. **Verifying documentation accuracy**: Jump to Part 8
5. **Complex refactoring**: Read Part 10 for impact analysis
6. **Training on complete architecture**: Share entire file

### Key Guarantee
> "All information in this document has been verified against actual source code. Last verified May 2026."

---

## File 3: AI_CODE_GUIDELINES.md

### Purpose
**Strict rules** that AI code generation tools must follow. Non-negotiable.

### What It Contains

✅ **12 Mandatory Rules**
1. Respect module dependency layering (NO backward deps)
2. ECS components are pure data (NO methods)
3. Query via Registry views (not manual iteration)
4. Lua scripts access everything through ScriptApi (not direct C++ calls)
5. Rendering via QuadCommand (not direct calls)
6. Transform hierarchy computed once per frame (not directly mutated)
7. No global state (all passed via Context)
8. Documentation must stay in sync with code
9. Error handling via Result<T, E> pattern
10. Input state cleared BEFORE polling (not after)
11. Script lifecycle: init → update → destroy
12. Naming conventions (PascalCase/camelCase)

Each rule includes:
- ❌ FORBIDDEN examples (what NOT to do)
- ✅ CORRECT examples (what TO do)
- Rationale (why the rule exists)

✅ **Compliance Checklist**
- 13-item checklist before generating code
- Quick reference for validation

✅ **Example Requests**
- GOOD request format
- BAD request format
- How to phrase requests for AI

✅ **Exception Process**
- How to document violations
- When exceptions are allowed
- Approval workflow

### How to Use
1. **Before asking AI to code**: Review the 12 rules
2. **AI violates rules**: Show it the relevant rule section
3. **Adding new AI-assisted work**: Paste guidelines into the prompt
4. **Code review**: Use compliance checklist

### Key Action Items
> "Before generating code, verify: dependency chain, components data-only, systems use Registry views, Lua through ScriptApi, rendering via commands, no global state, Result pattern, documentation updated, input cleared first, naming conventions, CMakeLists.txt updated."

---

## File 4: This Summary Document

### Purpose
- Orientation for new developers/AI
- Quick reference for which file to read
- Setup instructions

---

## How to Use These Files

### For Developers

#### First Time Setup
1. Read **copilot-instructions.md** (sections 1-3, ~20 min)
2. Skim **ARCHITECTURE_VERIFIED.md** (overview, ~15 min)
3. Bookmark **AI_CODE_GUIDELINES.md** (reference for later)

#### Adding a Feature
1. Check **AI_CODE_GUIDELINES.md** Rule 12 for naming conventions
2. Read **copilot-instructions.md** "Common Workflows" section
3. Refer to **ARCHITECTURE_VERIFIED.md** Part 10 for detailed steps
4. Verify no violations of 12 rules from **AI_CODE_GUIDELINES.md**

#### Code Review
1. Use compliance checklist from **AI_CODE_GUIDELINES.md**
2. Verify architecture against **copilot-instructions.md** principles
3. Check documentation sync against **ARCHITECTURE_VERIFIED.md**

#### Understanding a System
1. Find system in **ARCHITECTURE_VERIFIED.md** Part 2
2. Read corresponding .md file (RENDERING_CURRENT.md, SCRIPTING_CURRENT.md, etc.)
3. Check code examples in **copilot-instructions.md** "Common Workflows"

---

### For AI Assistants

#### First Interaction
1. Read **copilot-instructions.md** to understand architecture
2. Read **AI_CODE_GUIDELINES.md** rules 1-7 (core rules)
3. Ask clarifying questions if anything is ambiguous

#### Before Generating Code
1. Verify all 13 items in **AI_CODE_GUIDELINES.md** compliance checklist
2. If unsure about pattern: search **ARCHITECTURE_VERIFIED.md** for examples
3. Check **copilot-instructions.md** "Common Workflows" for similar task

#### When Stuck
1. **"Which module should this go in?"** → **ARCHITECTURE_VERIFIED.md** Part 1
2. **"How do I add a Lua function?"** → **copilot-instructions.md** "Common Workflows"
3. **"Is this pattern correct?"** → **AI_CODE_GUIDELINES.md** rules + examples
4. **"What's the frame loop order?"** → **ARCHITECTURE_VERIFIED.md** Part 5
5. **"Can I break the layering?"** → **AI_CODE_GUIDELINES.md** Rule 1 + Exception Process

---

## Integration with Existing Documentation

These new files **complement** (not replace) existing docs:

| Existing File | New File | Relationship |
|---------------|----------|--------------|
| ARCHITECTURE.md | copilot-instructions.md + ARCHITECTURE_VERIFIED.md | Consolidates + verifies |
| IMPLEMENTATION_OVERVIEW.md | ARCHITECTURE_VERIFIED.md | Verified against actual code |
| COMPONENT_SYSTEM_GUIDE.md | copilot-instructions.md | Summarized in workflows |
| SCRIPTING_CURRENT.md | ARCHITECTURE_VERIFIED.md Part 3 | Verified + detailed |
| RENDERING_CURRENT.md | ARCHITECTURE_VERIFIED.md Part 4 | Verified + detailed |
| SCENE_ECS_CURRENT.md | ARCHITECTURE_VERIFIED.md Part 2 | Verified + detailed |
| LUA_IMPLEMENTATION_QUICKSTART.md | ARCHITECTURE_VERIFIED.md Part 10 | Workflows section |

**Action**: Keep existing .md files as source of truth; new files are high-level summaries + verification.

---

## Usage Scenarios

### Scenario 1: Adding a New Component Type

1. **Developer**: "I want to add a HealthComponent"
2. **Find**: copilot-instructions.md → "Common Workflows" → "Adding a New Component"
3. **Follow**: Step-by-step checklist
4. **Verify**: Run through AI_CODE_GUIDELINES.md Rule 2 (components data-only)
5. **Document**: Update COMPONENT_SYSTEM_GUIDE.md

### Scenario 2: Extending Lua API

1. **Developer**: "I want to add Engine.getEntityVelocity()"
2. **Find**: copilot-instructions.md → "Common Workflows" → "Adding a Lua API Function"
3. **Follow**: Step-by-step checklist (ScriptApi → ScriptApiImpl → LuaBindings → docs)
4. **Verify**: Run through AI_CODE_GUIDELINES.md Rule 4 (Lua through ScriptApi)
5. **Document**: Update SCRIPTING_CURRENT.md

### Scenario 3: AI Code Review

1. **Question**: "Does this new rendering feature respect architecture?"
2. **Check**: AI_CODE_GUIDELINES.md Rule 5 (rendering via commands)
3. **Check**: copilot-instructions.md (rendering workflow)
4. **Check**: ARCHITECTURE_VERIFIED.md Part 4 (rendering details)
5. **Verdict**: ✅ or ❌ + feedback

### Scenario 4: Understanding Impact of Changes

1. **Change**: "I want to make Renderer aware of Scene for optimization"
2. **Check**: AI_CODE_GUIDELINES.md Rule 1 → Violates layering
3. **Check**: ARCHITECTURE_VERIFIED.md Part 8 → Known decisions rationale
4. **Conclusion**: Needs exception process or different approach

### Scenario 5: Onboarding New Team Member

1. **Hand them**: copilot-instructions.md
2. **Talk through**: ARCHITECTURE_VERIFIED.md overview
3. **Reference**: AI_CODE_GUIDELINES.md for code patterns
4. **Follow up**: Have them add a simple component using the workflow

---

## Maintaining the Context Files

### When to Update

**Update copilot-instructions.md** when:
- Core architecture changes
- New common workflow emerges
- New AI guardrails needed

**Update ARCHITECTURE_VERIFIED.md** when:
- Adding major new feature/system
- Module responsibilities change
- Frame loop reordered
- New verification gaps found

**Update AI_CODE_GUIDELINES.md** when:
- New mandatory rule discovered
- Existing rule needs clarification
- New exception process documented

**Always Update All** when:
- Documentation lag discovered → sync immediately
- Architecture violation found → document why/fix

### Verification Process

Every 3 months:
1. [ ] Re-verify module dependencies (should be identical)
2. [ ] Re-verify frame loop order (check Runtime::run())
3. [ ] Check for documentation lag (old .md files outdated?)
4. [ ] Review violations/exceptions (any real ones?)
5. [ ] Update version date in files

---

## Files Created Summary

```
c:\projects\engine\
├── copilot-instructions.md          (NEW) Core principles + workflows
├── ARCHITECTURE_VERIFIED.md         (NEW) Complete verified architecture
├── AI_CODE_GUIDELINES.md            (NEW) 12 mandatory rules for code generation
├── CONTEXT_FILES_GUIDE.md           (NEW) This file
│
├── ARCHITECTURE.md                  (EXISTING) Original design doc
├── IMPLEMENTATION_OVERVIEW.md       (EXISTING) Current state map
├── COMPONENT_SYSTEM_GUIDE.md        (EXISTING) Component patterns
├── SCRIPTING_CURRENT.md             (EXISTING) Lua integration details
├── RENDERING_CURRENT.md             (EXISTING) Render pipeline details
├── SCENE_ECS_CURRENT.md             (EXISTING) Entity model details
├── LUA_IMPLEMENTATION_QUICKSTART.md (EXISTING) Lua setup guide
├── OPTIMIZATIONS_CURRENT.md         (EXISTING) Performance work
└── README_DOCUMENTATION.md          (EXISTING) Docs index
```

---

## Quick Reference Card

### Read This For...
- **First time learning SLE** → copilot-instructions.md (sections 1-3)
- **Adding a component** → copilot-instructions.md (workflows) + ARCHITECTURE_VERIFIED.md (Part 10)
- **Adding a Lua function** → copilot-instructions.md (workflows) + ARCHITECTURE_VERIFIED.md (Part 3)
- **Understanding rendering** → ARCHITECTURE_VERIFIED.md (Part 4) + RENDERING_CURRENT.md
- **Understanding transforms** → ARCHITECTURE_VERIFIED.md (Part 2.3) + SCENE_ECS_CURRENT.md
- **Understanding scripts** → ARCHITECTURE_VERIFIED.md (Part 3) + SCRIPTING_CURRENT.md
- **AI checking code** → AI_CODE_GUIDELINES.md (rules + checklist)
- **Code review** → AI_CODE_GUIDELINES.md (compliance checklist)
- **Architecture deep dive** → ARCHITECTURE_VERIFIED.md (full read)

### Must Know Rules
1. No backward dependencies (Rule 1)
2. Components are data-only (Rule 2)
3. Use Registry views (Rule 3)
4. Lua through ScriptApi (Rule 4)
5. Rendering via commands (Rule 5)

---

## Success Criteria

These context files are **successful** when:

✅ New developers onboard in <1 day  
✅ AI generates code that passes first review  
✅ No architecture violations in PRs  
✅ Documentation stays in sync with code  
✅ Team references these files instead of asking "how do I...?"  
✅ Future AI interactions produce consistent, compliant code  

---

## Questions?

- **"How do I add X?"** → copilot-instructions.md "Common Workflows"
- **"Why is X forbidden?"** → AI_CODE_GUIDELINES.md rule rationale
- **"What does X module do?"** → ARCHITECTURE_VERIFIED.md Part 1
- **"Is my code compliant?"** → AI_CODE_GUIDELINES.md checklist
- **"Where's the detailed X docs?"** → Cross-reference at top of each file

---

**Created**: May 2026  
**Verified Against**: All source code + 8 existing .md files  
**Status**: Ready for use  
**Next Review**: August 2026 (3-month verification cycle)

Good luck building SLE! 🚀
