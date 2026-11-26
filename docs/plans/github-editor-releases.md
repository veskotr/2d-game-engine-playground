# GitHub editor releases implementation plan

> Execute task-by-task using superpowers:executing-plans, with parallel agents for independently scoped script and workflow work and one fresh final review.

**Goal:** Publish a release in GitHub's Releases screen and receive five complete application downloads plus SHA-256 checksums automatically.

**Spec:** [Approved design](github-editor-releases-design.md).

**Architecture:** Extend the current two-platform native-build/package workflow. A Python release helper owns version/tag validation, portable ZIPs, normalized asset names and checksums. The workflow builds and tests tagged source, verifies packages, and uploads only in a final job after all packaging succeeds. Manual dispatch remains artifact-only.

**Constraints:** Preserve all existing edits. No push, tag or public release during implementation. Match editor tag `v<editor-version>` and native engine inventory version; target x64. Keep release notes unchanged. ZIPs retain `SLE Editor/`; Linux archives retain executable modes. The upload job alone has contents:write.

## Review focus

- Release tags, including unsafe/unexpected input, must never select different source or enter shell code.
- A missing/stale platform download must fail before any upload; retry must not alter release notes or tag.
- Archives must contain the full app image, JVM, bridge and both runtime bundles.
- Linux launcher, runtime and symlink permissions must survive ZIP/TAR extraction.
- Manual dispatch builds must never publish a release accidentally.

## Task 1: Release packaging helper and regressions

Files: `tools/editor/packaging/release.py`, `tools/editor/packaging/tests/test_release.py`.

- [x] Write focused tests for metadata/tag version mismatch and unsafe tags, release asset completeness, independent SHA-256 checks, archive folder/execute modes, and missing/ambiguous installer input.
- [x] Observe RED before implementation.
- [x] Implement CLI contracts below and pass all helper tests.
- [x] Exercise helper ZIPs against a real Windows image and an available Linux image; extract and run image-content checks.

CLI contracts (paths relative to repo root unless explicitly supplied):

- `metadata --event-name <name> --event <event-json>` reads current editor/engine versions, validates release tag `v<editor-version>` when event is release, and prints `version=<editor-version>` plus `engine_version=<engine-version>` for GITHUB_OUTPUT. Non-release event must be workflow_dispatch.
- `package --host windows-x64|linux-x64 --image <image> --output <directory> --version <version> [--installer-dir <directory>|--linux-packages <directory>]` writes exactly 2 Windows / 3 Linux versioned assets, portable ZIP plus installer or AppImage/TAR.GZ. Checks source artifacts and ZIP contents/modes. Does not publish.
- `checksums --directory <directory> --version <version>` requires exactly five expected versioned payloads, rejects unexpected/missing entries and generates `SHA256SUMS.txt` with sorted filenames. Re-running checksum generation is safe.

## Task 2: Workflow and release instructions

Files: `.github/workflows/editor-distribution.yml`, `tools/editor/README.md`, `CONTRIBUTING.md`; add minimal CI test hook if needed.

- [x] Add release:published trigger, early version validation/job outputs and same-tag checkout.
- [x] Run helper tests and editor tests; preserve native build and packaged-resource validation.
- [x] Generate and verify both portable ZIPs, with Linux extracted execute checks.
- [x] Upload normalized versioned assets as Actions artifacts; retain run-local native intermediates.
- [x] Add final upload job gated on release event and successful tests/packages, complete asset validation, checksums, quoted release tag and repository env variables. No release creation or note rewrite.
- [x] Explain release UI, version bump/tag relationship, five download names/checksums, manual preview builds and retry; distinguish source archives and app archives.
- [x] Run actionlint and shell/parser checks; run the helper suite. No source-text-mirroring tests for workflow YAML.

## Task 3: Final validation and review

- [x] Run actual packaging commands locally where artifacts/tooling exist; record limitations explicitly.
- [x] Fresh whole-change review, fix important findings with regression coverage, and run affected suite.
- [x] Mark approved design implemented and report commands/results. Hosted release publishing remains unexecuted until committed workflow is available on GitHub.

## Progress

Design approved via user reply on 2026-11-26. Existing editor-distribution workflow lint and real Windows package content check passed before implementation.

**Implemented 2026-11-26** — Tasks 1–3 are implemented and validated locally. Nothing was committed, pushed, tagged or published.

Commands actually run on 2026-11-26:

- `python -m unittest discover -s tools/editor/packaging/tests -p 'test_*.py'` on Windows: 9 tests, 2 symlink/mode tests skipped. The same suite on a native WSL Ubuntu filesystem ran all 9 with no skips.
- `.\gradlew.bat test` in `tools/editor`: 475 tests, 0 failures, 3 skipped. Gradle reported the task up to date, so these are results recorded for identical inputs.
- `release.py package --host windows-x64 --version 0.34.1` against the real Windows app image and `SLE Editor-0.34.1.exe` installer. The extracted ZIP passed `check-editor-image.py` against `build/packaged-resources`.
- In WSL, `release.py package --host linux-x64` against a native-ext4 copy of the 0.34.1 Linux image. That copy had mixed 444/644/755 modes and one relative symlink. Its TAR was regenerated the way `package-linux.sh` builds one. The ZIP, TAR and AppImage all extracted, passed `check-editor-image.py`, and kept an executable launcher. ZIP modes, symlinks and contents matched the image exactly. `release.py checksums` over all five assets then passed `sha256sum -c`.
- A parse of the workflow YAML, `bash -n` on all 12 bash steps, and a PowerShell parser check of the Windows packaging step.
- `actionlint` 1.17.1, installed from Scoop's main bucket, which now packages the `kjanat/actionlint` fork. `actionlint -ignore 'node20.+is deprecated' .github/workflows/editor-distribution.yml .github/workflows/ci.yml` exited 0. Across all workflows, its only findings are 24 node20-runtime deprecation warnings.

Findings and decisions:

- The earlier local Linux image had been built on a DrvFs path, which stores every file as mode 0777. The helper correctly rejected the TAR made from it, because extracted modes differed. CI builds on ext4 and is unaffected. Build local Linux packages on a native Linux filesystem.
- A fresh whole-change review found no confirmed bugs. Three of its suggestions were applied:
  - The upload step sends the five binaries first and `SHA256SUMS.txt` last. A failed retry can no longer pair new checksums with old downloads.
  - `GH_TOKEN` is now scoped to the two `gh` steps.
  - Portable ZIP entries keep their modification times, with regression coverage.

Remaining limitations:

- shellcheck is not installed, so actionlint skipped its shell-script pass. `bash -n` covers syntax only.
- All three workflows still use the Node 20 majors of `actions/*`. The 2026-11-26 CI run shows GitHub forcing them onto Node 24 with deprecation warnings, so they run today. Upgrading them is separate repository-wide maintenance.
- `appimagetool` was not available locally, so the AppImage was reused from an earlier local build rather than regenerated.
- A hosted end-to-end release can only run once the workflow is on GitHub.
- Review suggestions not taken:
  - Pin the `appimagetool` download by SHA-256 and the actions by commit SHA.
  - Protect `v*` tags with a ruleset.
  - Normalize Windows-derived ZIP modes (files are stored as 0666).
  - Pin FetchContent tags and the Gradle wrapper checksum.
