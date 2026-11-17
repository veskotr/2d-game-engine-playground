#!/usr/bin/env bash
# Linux (including WSL2/WSLg) counterpart of run-editor.ps1. Run from any directory:
#   tools/editor/run-editor.sh [editor args...]
# Environment overrides:
#   SLE_BUILD_DIR   CMake build directory (default: ~/sle-build/debug, outside the shared checkout)
#   JAVA_HOME       JDK 21 (default: whatever Gradle's toolchain detection finds)
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
build_dir="${SLE_BUILD_DIR:-$HOME/sle-build/debug}"
gradle_dir="$(dirname "$build_dir")/gradle"

# WSLg: the editor's shared AWT GL contexts crash inside Mesa's d3d12 (GPU) driver (Mesa 26.0),
# so default to llvmpipe (CPU, OpenGL 4.5). engine_app itself runs fine with GALLIUM_DRIVER=d3d12.
if [[ -e /dev/dxg && -z "${GALLIUM_DRIVER:-}" ]]; then
    export GALLIUM_DRIVER=llvmpipe
fi

cmake -S "$repo_root" -B "$build_dir" -G Ninja -DCMAKE_BUILD_TYPE=Debug -DSLE_BUILD_EDITOR=ON
cmake --build "$build_dir" --target sle_editor_native --parallel

gradle_args=(
    -p "$script_dir"
    -g "$gradle_dir/user-home"
    --project-cache-dir "$gradle_dir/project-cache"
    -PsleBuildDir="$gradle_dir/editor-build"
    -PsleNativeLibrary="$build_dir/tools/editor-native/libsle_editor_native.so"
    run --console plain
)
if [[ $# -gt 0 ]]; then
    gradle_args+=("--args=$*")
fi
exec bash "$script_dir/gradlew" "${gradle_args[@]}"
