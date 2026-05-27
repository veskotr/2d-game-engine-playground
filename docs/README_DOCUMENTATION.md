# SLE Documentation Index

This index is the fastest path to the right document.

## Start Here

If you read only three documents, read these in order:

1. `ARCHITECTURE_VERIFIED.md` - verified architecture, module boundaries, and design rules.
2. `IMPLEMENTATION_OVERVIEW.md` - current runtime behavior and what is actually implemented.
3. `ENGINE_MASTER_PLAN.md` - consolidated implementation roadmap and execution plan.

If you are an AI agent and need quick architectural context first:

1. `AGENT_ARCHITECTURE_CONTEXT.md` - compact architecture retrieval pack.
2. Then open the three core docs above.

## Core Documents

| Document | Purpose | When To Use |
|---|---|---|
| `ARCHITECTURE_VERIFIED.md` | Canonical architecture and dependency boundaries | Before any structural engine change |
| `IMPLEMENTATION_OVERVIEW.md` | Current-state map of runtime loop and systems | Before implementation or debugging |
| `ENGINE_MASTER_PLAN.md` | Consolidated roadmap and feature execution order | For planning and vibecoding implementation passes |
| `AGENT_ARCHITECTURE_CONTEXT.md` | Fast architecture context for AI/tooling sessions | First stop when context window is limited |

## Specialized References

| Document | Purpose |
|---|---|
| `UI_PROGRESS.md` | UI implementation status and remaining tasks |
| `COMPONENT_SYSTEM_GUIDE.md` | Component design patterns and extension practices |
| `LUA_IMPLEMENTATION_QUICKSTART.md` | Lua scripting implementation checklist |
| `OPTIMIZATIONS_CURRENT.md` | Implemented optimization summary and rationale |
| `BOX2D_PHYSICS_INTEGRATION_PLAN.md` | Detailed physics integration notes |
| `EVENT_SYSTEM_IMPLEMENTATION_PLAN.md` | Detailed event-system fixes and phased design |

## Planning Workflow

Use this lightweight workflow for new feature work:

1. Confirm architectural fit in `ARCHITECTURE_VERIFIED.md`.
2. Confirm current code reality in `IMPLEMENTATION_OVERVIEW.md`.
3. Pick the next slice from `ENGINE_MASTER_PLAN.md`.
4. Use one specialized reference only if you need subsystem detail.

## Document Roles

To reduce duplicated planning content:

- `ENGINE_MASTER_PLAN.md` is the single roadmap source of truth.
- subsystem plan files remain implementation detail references.
- `ARCHITECTURE.md` is historical/design-background context.

## Quick FAQ

- Where should sprint planning happen?
  - Use `ENGINE_MASTER_PLAN.md`.

- Where should architecture decisions be validated?
  - Use `ARCHITECTURE_VERIFIED.md`.

- Where do I find exact current behavior?
  - Use `IMPLEMENTATION_OVERVIEW.md`.
