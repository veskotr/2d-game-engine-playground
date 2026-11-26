# GitHub editor releases

Status: implemented and validated locally; hosted release run pending. Date: 2026-11-26. See the [implementation plan](github-editor-releases.md#progress).

## User flow and scope

Use GitHub **Releases → Draft a new release**. Enter a new `v<editor-version>`
tag, select the source branch/commit, write the title and release notes, then
publish (including prereleases). GitHub creates the selected new tag. Actions
builds that tag and attaches the downloads to that same release when both
platform builds and package checks succeed. Release notes remain unchanged.
The source must already contain the intended editor and engine versions.

## Approaches

The user selected the GitHub Releases screen over an Actions form that creates
and publishes a release. Reuse `.github/workflows/editor-distribution.yml` and
its Windows/Linux native-build and packaging jobs. Keep manual dispatch for
artifact-only test builds; only release events upload public release assets.

## Downloads

All artifacts contain the editor, Java runtime, host-native engine bridge,
engine assets, and Windows/Linux game export runtimes. No system JDK or compiler
is required. Target x64 for both platforms, matching existing packaging.

- `SLE-Editor-<version>-windows-x64-setup.exe`
- `SLE-Editor-<version>-windows-x64.zip`
- `SLE-Editor-<version>-linux-x64.AppImage`
- `SLE-Editor-<version>-linux-x64.zip`
- `SLE-Editor-<version>-linux-x64.tar.gz`
- `SHA256SUMS.txt`

Portable archives retain a top-level `SLE Editor` folder; Linux archives preserve
launcher/runtime execute permissions. AppImage and TAR.GZ reuse the existing
Linux packaging script. ZIPs contain the complete app image, not GitHub's source
archives. Windows signing remains the existing unsigned configuration.

## Reliability

Validate release tags against the editor version before expensive builds; each
native inventory must also match the engine version. Build both platforms from
the same tagged source. Run editor tests and existing package-content checks.
Verify extracted portable archives before publication. Publish only after both
platform packages succeed and all five expected artifacts and checksums exist.
Use narrowly scoped write permission only for the final upload job. Treat release
names/notes/tags as data, without interpolating them into shell code. Retrying a
failed workflow must not create a different release, move its tag, or change notes.

GitHub's built-in source ZIP/TAR links remain separate. Downloads appear after
Actions finishes, so a newly published release initially has no app downloads.
This flow requires mutable release assets; GitHub immutable releases require a
draft-build-publish flow instead. No public release, tag, commit, or push will be
created during implementation; the user will publish after reviewing the changes.

## Validation

Add focused executable tests for release metadata, expected assets, checksums,
and portable archive contents/modes. Lint the Actions workflow. Exercise local
archive generation and package verification against real built app images where
available. Document the click-by-click release process, download names, required
permissions, version bumps, and failed-build retry. A hosted end-to-end release
run can only be validated after these workflow changes reach GitHub.
