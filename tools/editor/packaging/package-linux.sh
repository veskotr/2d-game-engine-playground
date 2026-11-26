#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 3 ]]; then
    echo "Usage: bash $0 <Compose app image> <output directory> <appimagetool>" >&2
    exit 2
fi
image="$(realpath "$1")"
mkdir -p "$2"
output="$(realpath "$2")"
appimagetool="$(realpath "$3")"
icon="$(dirname "$(realpath "$0")")/../src/main/resources/sle/editor/branding/sle-256.png"

staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT
appdir="$staging/SLE Editor"
cp -a "$image" "$appdir"

# Keep jpackage's relative bin/lib layout for both Linux delivery formats.
chmod +x "$appdir/lib/app/resources/runtime/linux-x64/engine_app"
tar -C "$staging" -czf "$staging/SLE-Editor-linux-x64.tar.gz" 'SLE Editor'
cp "$icon" "$appdir/sle-editor.png"
cat > "$appdir/sle-editor.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=SLE Editor
Comment=SLE scene and entity editor
Exec=sle-editor
Icon=sle-editor
Categories=Development;
Terminal=false
DESKTOP
cat > "$appdir/AppRun" <<'APPRUN'
#!/bin/sh
exec "$(dirname "$0")/bin/SLE Editor" "$@"
APPRUN
chmod +x "$appdir/AppRun"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$appimagetool" "$appdir" "$staging/SLE-Editor-linux-x64.AppImage"
mv "$staging/SLE-Editor-linux-x64.tar.gz" "$staging/SLE-Editor-linux-x64.AppImage" "$output/"
