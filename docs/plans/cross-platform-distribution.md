# Cross-platform distribution implementation plan

**Status** — Sessions 1 and 2 verified on both development hosts. Session 3 editor packaging is implemented and builds locally. Windows installer lifecycle, clean native Linux desktop acceptance, and the Linux release baseline remain open.

**Goal** — Ship installable SLE Editor builds for Windows x64 and Linux x64. On either host, creators can export Windows x64, Linux x64, or both from the same saved project, offline and without a compiler.

**Architecture** — Build each game runtime on its target OS and ship both runtime bundles inside each editor distribution. Keep the existing game-package format. Build project data once per export, assemble it with the selected runtimes, and verify native launch on each target separately.

**Prerequisite** — Read the [automatic game packaging plan](automatic-game-packaging.md) and its [handoff](automatic-game-packaging-handoffs.md). Confirm the current implementation and outstanding acceptance checks before starting. Check git status and preserve existing edits.

## Release contract

- Windows editor x64 ships as an EXE installer. Sign the public installer once release signing is configured. MSI remains an available packaging target.
- Linux editor x64 ships as an AppImage with a portable `.tar.gz` fallback. Keep the configured DEB target. Flatpak and additional distro-native packages are outside the initial release.
- Propose testing Ubuntu LTS, Debian stable, Fedora, and Arch. Record the exact tested versions and the oldest supported build baseline before publishing. WSL development results alone cannot establish desktop compatibility.
- Windows game x64 ships as a runnable folder with a project-named EXE and a ZIP for direct download. Linux game x64 ships as a runnable folder with a native launcher and a TAR.GZ for direct download. Generate the archive automatically and retain the runnable folder.
- The editor can validate content with its own in-process engine. The game launcher is tested on the target OS. Steam delivery can use the unpacked folder with a separately verified Steam Runtime build and is outside this milestone.

## Session 1 — Build and verify the Linux runtime bundle

- [x] Inspect `engine_app/CMakeLists.txt`, `cmake/BuildRuntimeBundle.cmake`, and the Linux development setup in `tools/editor/README.md`. Add a reproducible Linux x64 Release CMake configuration and build a `sle_runtime_bundle` on Linux. Keep the existing Windows bundle contract readable.
- [x] Stage the native executable, required private libraries, assets, notices, and a hashed inventory recording target, engine version, Release configuration, and executable path. Audit dynamic dependencies and record the development build ABI. Use executable-relative library lookup and leave host graphics driver integration to the system.
- [x] Add Linux executable discovery in `engine_app/main.cpp` so startup can find adjacent game data through absolute, relative, PATH, and symlink invocations. Preserve explicit config/project precedence and test relocation to a path with spaces from an unrelated working directory.
- [x] Build `engine_app` and `sle_runtime_bundle` on both development hosts. Run the distribution and package CTest checks and a bounded windowed launch from the relocated Linux bundle on WSLg.
- [ ] Run the bounded relocated launch on a clean Linux desktop with native OpenGL 4.6 and establish the release baseline.

## Session 2 — Export either target from either editor

- [x] In `ProjectDistribution.kt`, parse and verify runtime inventories by declared target, including the executable, file hashes, and expected engine compatibility. Add tests for both targets, wrong-target selection, damaged bundles, and a legacy Windows fixture. Keep repository-run editors able to select prepared local bundles.
- [x] In `GameBuild.kt` and the Export Game controls, separate saved-content packaging from target assembly. Offer Windows x64, Linux x64, and Both. Keep saved-document handling, progress reporting, and numbered export destinations. Build both outputs under one staging root and publish only after all requested folders and archives validate. Preserve previous exports on failure.
- [x] Use each target’s executable naming and file layout. Reject Windows-incompatible shipped paths and case-folding collisions when Windows is selected. Preserve Linux executable modes in the TAR.GZ even when exporting on Windows by writing mode metadata from the verified runtime layout. Test archive extraction, paths with spaces, and a failure during assembly of the second target.
- [x] Check `GameContentUpdate.kt` against the new target-specific deliveries. Preserve existing Windows updates and explicit platform/runtime matching. For this milestone, mark Linux deliveries as full-export only and report that limitation in the editor and guide.
- [x] Run the editor unit suite with `./gradlew test` on Linux and `gradlew.bat test` on Windows. Extend the project distribution smoke to export both targets from each host. Compare the mountable package payloads and check each platform-specific release inventory. Pass all four host/target combinations to Session 4 for native launch verification.

## Session 3 — Package and distribute the editor

- [x] Use separate Windows and Linux release jobs in `.github/workflows/`. Each builds its native bridge and game runtime in Release and publishes an artifact tied to the same source revision. Stage both game runtime artifacts in each editor build, validating their target and version before packaging.
- [x] Update `tools/editor/build.gradle.kts` and bundled-runtime resource lookup to embed both game runtimes under distinct target directories. Package the Windows editor with the configured Compose EXE target. Produce the Linux app image using `createDistributable`, then produce the AppImage and TAR.GZ from that payload. Verify that the Java runtime, bridge, and game runtimes are present and that installed assets can be read from the package.
- [ ] Test Windows install, upgrade, uninstall, and launch without a system JDK. Test the AppImage and portable archive on the Linux desktop matrix, including the embedded OpenGL viewport, project open/save, and exports from a writable project directory. Record exact tested distro releases, runtime dependencies, and any signing limitation.

## Session 4 — End-to-end release acceptance

- [ ] On each packaged editor host, open and save a sample project with multiple scenes and shared assets, disconnect network access, and export Both. Transfer each archive to a clean machine running its target OS. Extract, relocate, and launch it from an unrelated working directory with no checkout, JDK, or compiler present.
- [ ] Confirm that the Linux launcher extracted from a Windows-created TAR.GZ is executable, and that both Windows ZIPs launch after extraction. Verify startup scene, scene changes, assets, and audio in the exported games. Run the Windows content-update regression.
- [ ] Update `tools/editor/README.md`, `docs/topics/GamePackages.md`, `docs/topics/Projects.md`, and `docs/guide/run-and-export.html` alongside the delivered behavior. Record artifact names and hashes, signing status, commands actually run, and results for Windows→Windows, Windows→Linux, Linux→Windows, and Linux→Linux. List any checks that remain pending.

## Progress and next-session handoff

### Session 1 handoff

The Ninja preset `linux-x64-release` builds the standalone launcher with static
engine dependencies and ships a v1 `sle-runtime.json` with `platform: linux-x64`,
`engineVersion: 0.33.0`, `configuration: Release`, and `executable: engine_app`.
The bundle contains the executable with its Linux execute bits, shader, UI and
font assets, dependency notices, and Linux launch instructions. The Linux
staging script inspects ELF dependencies and rejects unexpected shared libraries.
The Windows v1 inventory, MSVC redistributables, executable name, and launch
instructions keep their existing contract.

**Linux commands and results**

```bash
mkdir -p /tmp/sle-runtime-session1/_deps
cp -a "$HOME"/sle-build/release/_deps/*-src /tmp/sle-runtime-session1/_deps/
cmake --preset linux-x64-release -B /tmp/sle-runtime-session1 \
  -DPython_EXECUTABLE=/usr/bin/python3 -DFETCHCONTENT_FULLY_DISCONNECTED=ON
cmake --build /tmp/sle-runtime-session1 --config Release --parallel 6 \
  --target engine_app sle_runtime_bundle integration_game_package_path integration_project_play_path
export XDG_SESSION_TYPE=x11 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.6
ctest --test-dir /tmp/sle-runtime-session1 -C Release \
  -R '^integration_(distribution_launch|game_package|package_discovery|package_launch)_path$' --output-on-failure
bash /tmp/sle-runtime-session1/verify-relocated.sh
python3 /tmp/sle-runtime-session1/verify-inventory.py /tmp/sle-runtime-session1/runtime/Release linux-x64 engine_app
sha256sum /tmp/sle-runtime-session1/runtime/Release/{engine_app,sle-runtime.json}
```

- Ubuntu 26.04 LTS x86-64 under WSL2, CMake 4.2.3, GCC 15.2.0,
  glibc 2.43 and WSLg 1.0.71. The offline configure reused cached source
  trees in a writable build directory. An initial configure against the
  read-only cache failed to generate dependency files. Using the system
  Python with Jinja2 resolved the subsequent GLAD generator failure.
- The new distribution test failed at PATH discovery before the launcher
  change. With `/proc/self/exe` discovery it passed absolute, relative, PATH,
  and symlink invocations from a separate cwd with a decoy `engine.json`,
  plus explicit `--config` and `--project` precedence.
- All four CTest checks passed with the environment shown. The three
  non-windowed checks also passed without the override. The first windowed
  attempts failed with EGL and then GLX context creation errors.
  `glxinfo -B` reported Mesa llvmpipe 26.0.3 with a maximum OpenGL core
  profile of 4.5. The launcher requests 4.6.
- The temporary relocation script copied the staged runtime into
  `Relocated SLE Game`, checked the inventory and executable mode, then
  generated adjacent game packages with
  `integration_game_package_path --fixture "$delivery" nearest`.
  It ran `engine_app --frames 8` via an absolute path, a relative path,
  PATH and a relative symlink, from an unrelated cwd containing a decoy
  config. Each invocation used a 30-second timeout with `LD_LIBRARY_PATH`
  and `LD_PRELOAD` unset, initialized audio, reached the second scene, and
  exited 0 with no engine errors. Final logs are in
  `/tmp/sle-runtime-session1/relocation.Yw53bn/`.
- `file`, `readelf -d`, `ldd`, and `readelf -V` on the staged launcher
  showed an x86-64 ELF PIE with `/lib64/ld-linux-x86-64.so.2`,
  `RUNPATH=$ORIGIN/lib`, and resolved direct dependencies on `libm.so.6`,
  `libstdc++.so.6`, `libgcc_s.so.1`, and `libc.so.6`. The highest symbol
  versions were `GLIBC_2.43`, `GLIBCXX_3.4.32`, `CXXABI_1.3.15`, and
  `GCC_3.0`. The bundle requires no private shared libraries. GLFW and
  miniaudio use host desktop graphics and audio libraries at runtime.
- The Linux inventory verified all 25 shipped files. The launcher SHA-256
  is `b9d4602adb7555d82cee635b6a38d9f0d8a2fcf11d815980d000e7d9f99b38bf`.
  The inventory SHA-256 is
  `94a2262b9062878fc623ea5e513296679af813e0a461421e647d95f49a04e6cb`.

**Windows development-host commands and results**

Run with Windows tooling from the checkout through WSL interop.

```powershell
cmake --build build/release --config Release --target engine_app sle_runtime_bundle integration_game_package_path integration_project_play_path --parallel 4
ctest --test-dir build/release -C Release -R 'integration_(distribution_launch|game_package|package_discovery|package_launch)_path' --output-on-failure
```

- Visual Studio 2022 built the requested Release targets and staged
  `build/release/runtime/Release`. All four CTest checks passed, including
  the windowed packaged-game fixture. The explicit-project test initially
  expected the wrong missing-path diagnostic. The final assertion checks the
  project error prefix and requested descriptor name on both systems.
- From WSL, `python3 /tmp/sle-runtime-session1/verify-inventory.py build/release/runtime/Release windows-x64 engine_app.exe`
  verified all 32 shipped files and the v1 Windows metadata.
- The automatic-packaging Session 5 walkthrough and transferred archive
  launch passed previously on this development host. The separate clean
  Windows x64 check remains deferred and open. See the
  [Session 5 transfer record](automatic-game-packaging.md#2026-11-25--session-5-clean-machine-transfer).

**Remaining acceptance**

- A clean native Linux desktop was unavailable. Verify the relocated game
  with an unmodified OpenGL 4.6 environment, record the tested distro and
  graphics stack, and select and test the oldest supported build baseline
  before release. Ubuntu 26.04 WSLg with a Mesa version override is the
  development evidence for this bundle. Native Wayland and hardware-driver
  launch remain unverified.
- Session 2 receives the Linux v1 inventory and launcher contract alongside
  the Windows bundle. Editor target selection and cross-target assembly
  remain in Session 2.

### Session 2 handoff

- **Export Game…** offers Windows x64, Linux x64, and Both. Repository-run
  editors pick verified Release bundles for the selected targets. Installed
  editors look in `runtime/<target>/` and the existing flat Windows layout.
  Installer assembly with both bundles belongs to Session 3.
- Each numbered export retains a runnable `windows-x64/` and/or `linux-x64/`
  folder and a corresponding ZIP or TAR.GZ. Both targets use byte-identical
  config and package payloads. Each target has a separate release inventory
  with its runtime manifest hash. The editor verifies launcher headers,
  runtime hashes, content compatibility, the staged native package mounts,
  and archive contents before publishing a single directory. The Linux TAR.GZ
  records the project-named launcher as mode `0755` even on Windows.
- Packaged Windows export accepts the version 1 runtime inventory from SLE
  0.16 and later, subject to the saved project's feature requirements. The
  older unversioned Windows inventory remains usable for loose-project
  export. Linux x64 requires 0.33 or later. Both runtimes need 0.33 or later
  for native tile layers. Windows content updates still verify the exact
  baseline runtime. Select the Windows runnable folder as the baseline.
  Linux x64 uses full exports.
- The automatic packaging walkthrough and its clean Windows transfer
  artifact were already recorded. The separate clean Windows desktop
  check remains open alongside the native Linux desktop and baseline
  checks from Session 1.

**Development validation**

- Linux built the current bridge with
  `cmake --preset linux-x64-release -B /tmp/sle-runtime-session1 -DSLE_BUILD_EDITOR=ON -DPython_EXECUTABLE=/usr/bin/python3 -DFETCHCONTENT_FULLY_DISCONNECTED=ON`
  and `cmake --build /tmp/sle-runtime-session1 --target sle_editor_native --parallel 6`.
  Gradle 9.5.1 ran `test run --args=--project-distribution-smoke --offline`
  from `tools/editor`, with a writable cache at `/tmp/sle-cross-gradle`,
  build output at `/tmp/sle-cross-editor-build`, and
  `-PsleNativeLibrary=/tmp/sle-runtime-session1/tools/editor-native/libsle_editor_native.so`.
  The suite passed all 453 tests. Both the project distribution and project
  runtime smokes passed. The test used the prepared Linux runtime via
  `SLE_SMOKE_LINUX_RUNTIME=/tmp/sle-runtime-session1/runtime/Release`.
  WSLg used `XDG_SESSION_TYPE=x11 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.6`.
  Skiko fell back after its GL context attempt. The SLE viewport ran and
  the Linux game completed the launch checks.
- Windows ran `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/editor/gradle.ps1 test run --args=--project-distribution-smoke --offline`
  with the Windows Release bundle in `build/release/runtime/Release` and
  `SLE_SMOKE_LINUX_RUNTIME` set to the staged copy of the Linux Release bundle in
  `build/cross-session2-linux`. The Gradle wrapper reported 450 tests passed
  and three skipped. Both smokes passed, including Windows update and rollback.
- On both editor hosts the smoke selected **Both**, compared per-target
  package and config hashes, and verified each release's target and runtime hash.
  It extracted and launched the host's archive from a directory with spaces
  and an unrelated working directory. The two-scene tilemap fixture with a
  shared template mounted for both targets. A failed export preserved earlier
  deliveries. Unit tests injected a failure during validation of the second
  target and confirmed that no partial export was published.
- `python3 docs/guide/check_links.py` checked 573 references across 13 pages
  with zero broken references. `git -c core.whitespace=cr-at-eol diff --check`
  passed with the checkout's existing CRLF files.

**Archive transfer handoff**

All four archives are staged locally in `build/cross-session2-artifacts/`.
The native and cross-host launches reached `second-scene` at frame 8.

| Editor host | Target | Archive | SHA-256 |
|---|---|---|---|
| Windows | Windows | `windows-editor-windows-x64.zip` | `15527fb106d7042324e10ac34c6509a862af3c489ccf54637349fc6143734037` |
| Windows | Linux | `windows-editor-linux-x64.tar.gz` | `56ecd779605bddf6010e4919102179cf8623a88c3f7969f152526cd705ff8b04` |
| Linux | Windows | `linux-editor-windows-x64.zip` | `85ce8ef331b6cb99c08643896355059520e6557d1cf952ff99c9c8ef414956a6` |
| Linux | Linux | `linux-editor-linux-x64.tar.gz` | `b493c6677bd55a2d18a8fb9ef50921a687f032bfeb0e9462fb23bfcb476f4ab2` |

- Windows → Linux used `tar -xzf`, `stat -c %a` (returned `755`), and the
  extracted `Source Game --frames 8` launcher under WSLg from an unrelated cwd.
  Linux → Windows used
  `powershell.exe -NoProfile -ExecutionPolicy Bypass -File build/cross-session2-artifacts/check-windows.ps1`.
  That driver uses `Expand-Archive` and runs the extracted EXE with
  `--frames 8` from a separate empty directory.
- Session 3 can use the Windows and Linux runtime bundles and the
  `runtime/<target>/` lookup. Session 4 still needs clean target machines
  and offline exports from the installed editors. The supported Linux distro
  and driver baseline still need clean desktop validation.

### Session 3 handoff

- The manual `Editor distribution` workflow builds Release native artifacts on
  Windows Server 2022 and Ubuntu 24.04. Each artifact contains a bridge and
  a runtime bundle. The package jobs download both artifacts for the same
  workflow revision. Gradle verifies each staged bundle's target, exact engine
  version, Release metadata, and file hashes before packaging.
- Compose includes both runtimes in `runtime/<target>/` on each host. The
  Windows EXE uses the configured jpackage/WiX target and is unsigned. Linux
  uses the Compose app image for the AppImage and portable archive. The
  packaging script restores the Linux game launcher mode after Compose
  copies it. It keeps the configured DEB target available in Gradle.
- The Windows app image and both extracted Linux artifacts were checked
  against the Gradle staging tree. The check covers the launcher, bundled
  JVM, JNI bridge, shared editor assets and both game runtimes. The Linux
  launcher was executable in both extracted formats. Negative Gradle checks
  rejected missing, wrong-platform, wrong-version and modified runtime inputs.
- On Linux, cached Gradle 9.5.1 ran `test createDistributable` with the
  Session 2 Release bridge and runtime in `/tmp/sle-runtime-session1` and the
  Windows runtime in `build/release/runtime/Release`. All 453 tests passed.
  `bash tools/editor/packaging/package-linux.sh` built both Linux formats using
  appimagetool 1.9.1. Both passed `--project-distribution-smoke` and the project
  runtime smoke from an unrelated working directory using their packaged JVM
  and runtimes. The AppImage used `APPIMAGE_EXTRACT_AND_RUN=1` because WSLg had
  no `/dev/fuse`. The fixture came from a separate copy of
  `examples/tilemap_room` supplied through `SLE_SMOKE_TILEMAP_PROJECT`.
- Windows ran `tools/editor/gradle.ps1 test createDistributable packageExe
  -PslePackageLinuxRuntimeBundle=../../build/cross-session2-linux
  -PsleBuildDir=../../build/editor-session3-windows`. Of 453 tests, 450 passed
  and three link tests were skipped. JDK 21 and WiX built the unsigned EXE.
  The Windows app image ran `--project-distribution-smoke` from an unrelated
  cwd with a system-only PATH and no `JAVA_HOME`. It exited successfully with
  both the distribution and project-runtime pass markers. The smoke covered
  both exports, content update and native game launch. The launcher output is
  in `build/editor-session3-windows-smoke/stdout.log`.
  Packaged-smoke fixtures use short paths to stay within the Windows native
  path limit and canonicalize the report directory before inventory checks.
  The settings smoke now invokes the Swing menu action directly to avoid a
  desktop-focus race.
- Local Linux was Ubuntu 26.04 with glibc 2.43 under WSL2
  `6.6.87.2-microsoft-standard-WSL2`. The editor viewport ran with Mesa
  llvmpipe and the development OpenGL 4.6 override. Skiko fell back after
  its GL context attempt. Windows was development build `10.0.26300.0`.
  Neither environment establishes the release ABI or desktop support baseline.
  Linux uses host glibc, libstdc++, X11/GL or Wayland and the host graphics
  driver and audio services.
- `actionlint`, `bash -n tools/editor/packaging/package-linux.sh` and
  `python3 docs/guide/check_links.py` passed. The new workflow still needs
  its first GitHub Actions run. Clean Windows install, upgrade, uninstall, and
  launch without a system JDK remain open. Native FUSE launch and the Ubuntu
  LTS, Debian stable, Fedora and Arch desktop matrix remain open.

Local development artifacts and SHA-256 digests:

| Artifact | SHA-256 |
| --- | --- |
| `build/editor-session3-windows/compose/binaries/main/exe/SLE Editor-0.34.0.exe` | `a5b790d157ca9498620093265a4fe4b16d2a01a2109dada1a0452d2338babf94` |
| `build/editor-session3-linux/SLE-Editor-linux-x64.AppImage` | `bf9cefa73d18f4cc015d617ee8960993c5da038e5cc21a9e4330fae414469ac0` |
| `build/editor-session3-linux/SLE-Editor-linux-x64.tar.gz` | `0e27498b8432e1785d8f816b22ffd8886dc7e826d2a4f79f300fd69226321e73` |
