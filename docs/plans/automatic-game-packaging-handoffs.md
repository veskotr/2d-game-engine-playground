# Automatic game packaging — separate-session handoffs

Paste one prompt per new coding session, in order. Read the [milestone plan](automatic-game-packaging.md) and its Progress section before starting. Each prompt includes the common handoff requirements so it can be pasted on its own.

## Session 1 — Dependency inventory and package ownership

```text
Implement Session 1 only from docs/plans/automatic-game-packaging.md.
Read AGENTS.md and the full plan, then check git status and preserve existing edits.

Build the saved-asset dependency inventory and pure PackagePlanner. Assign an
asset reached by one scene to that scene, and assets reached by multiple scenes
to Shared. Traverse linked and nested templates, overrides, behaviour parameters,
declared assets and tile resources. Group sidecars with payloads. Preserve stable
owners across builds and identify physical file-relative dependencies that need
colocation. Follow the plan's conservative rules for ambiguous references.

Add and run PackagePlannerTest. Cover the shared Enemy template and its texture,
private props, cycles, stable output under input reordering and scene renames,
removed prior owners, tile references and missing UUID diagnostics. Record the
final planner contract, test results and remaining issues in the plan's Progress
section. Stop after Session 1.
```

## Session 2 — Multi-package builds and runtime compatibility

```text
Implement Session 2 only from docs/plans/automatic-game-packaging.md.
Read AGENTS.md, the full plan and the Session 1 handoff in Progress. Check the
prerequisite and git status, and preserve existing edits.

Wire the planner into internal GameBuild. Write deterministic version 1 .slepkg
files, a launch config and release inventory using stable package IDs. Keep
settings with the startup package and texture defaults consistent. Verify native
mounting through prepareGame before publishing, and retain the single-package
fixtures for compatibility.

Prove that the split preserves cross-package UUID references, dynamic relative
paths, Lua require, file-relative XML assets and tile resources. Add colocation
or a focused runtime resolver where the exercised reader needs it. Run the
GameBuildTest and native package tests listed in the plan. Record the finalized
build contract, commands, results and any platform limits in Progress. Stop
after Session 2.
```

## Session 3 — One-click packaged export

```text
Implement Session 3 only from docs/plans/automatic-game-packaging.md.
Read AGENTS.md, the full plan and the preceding handoffs in Progress. Check the
prerequisites and git status, and preserve existing edits.

Route File → Export Game… through automatic package planning and GameBuild.
Keep existing runtime selection, numbered Exports destinations and background
progress. Offer to save dirty documents or export their saved versions. Report
the delivery and its package sizes. The output includes a project-named EXE,
runtime files, config, .slepkg files and the release inventory.

Update the project distribution smoke to export a two-scene tilemap project
with a shared template, relocate it and launch from an unrelated cwd. Exercise
failed export preservation. Run the editor tests and Windows distribution
smoke with a prepared Release bundle where available. Update the user-facing
export docs and record actual commands and any unavailable checks in Progress.
Stop after Session 3.
```

## Session 4 — Content updates and staged apply

```text
Implement Session 4 only from docs/plans/automatic-game-packaging.md.
Read AGENTS.md, the full plan and the preceding handoffs in Progress. Check the
prerequisites and git status, and preserve existing edits.

Implement Export Content Update… and Apply Content Update…. Use the selected
release's inventory as the baseline. Reuse its package ownership, compare
digests, and export changed whole packages, target config and inventory, base
identity and obsolete package paths. A private-to-shared promotion must update
both owners as one operation. Report an unchanged build and reject incompatible
or damaged baselines.

Apply into a staged sibling delivery. Verify the base, the reconstructed target
and its native mount before publishing. Keep the original runnable. Test a
scene-only edit, shared asset edit, ownership promotion, removed scene, config
change, no-op, tampering and failed publication. Run GameContentUpdateTest and
the distribution smoke. Record the release/update contract, results and any
unavailable checks in Progress. Stop after Session 4.
```

## Session 5 — Release acceptance

```text
Execute Session 5 only from docs/plans/automatic-game-packaging.md.
Read AGENTS.md, the full plan and the preceding handoffs in Progress. Check the
prerequisites and git status, and preserve existing edits.

Run the full editor and Windows CTest suites, including windowed tests. Walk a
project with two scenes, a shared template and spritesheet, native tile
collision, Lua modules and XML UI through packaged export, launch, update and
rollback. Check the Windows editor's bundled runtime and launch relocated
deliveries from an unrelated cwd on a machine without the source or developer
tools. Check existing loose and single-package deliveries.

Update the guide, editor README and topic docs. Record every command actually
run and the remaining gaps in Progress. If Windows or a clean test machine is
unavailable, run the available checks and record what remains unverified. Stop
at the release acceptance handoff.
```

## Follow-on milestone — Cross-platform distribution

Use the [cross-platform distribution plan](cross-platform-distribution.md) for the
Windows editor installer, Linux editor AppImage and portable archive, and offline
game export to Windows and Linux from either editor host. Start with its Session 1
after checking the automatic packaging implementation and release acceptance gaps.
