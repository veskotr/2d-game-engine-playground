"""Compare an editor image's shipped resources to Gradle's verified staging tree."""

import argparse
import filecmp
from pathlib import Path


def check_image(image: Path, staged: Path, host: str) -> None:
    windows = host == "windows-x64"
    resources = image / ("app/resources" if windows else "lib/app/resources")
    launcher = image / ("SLE Editor.exe" if windows else "bin/SLE Editor")
    jvm = image / ("runtime/bin/server/jvm.dll" if windows else "lib/runtime/lib/server/libjvm.so")
    bridge = "sle_editor_native.dll" if windows else "libsle_editor_native.so"
    for required in (launcher, jvm, resources / bridge):
        if not required.is_file():
            raise SystemExit(f"Missing editor image file: {required}")
    for required in (
        staged / host / bridge,
        staged / "common/assets/shaders",
        staged / "common/runtime/windows-x64/sle-runtime.json",
        staged / "common/runtime/linux-x64/sle-runtime.json",
    ):
        if not required.exists():
            raise SystemExit(f"Missing staged editor resource: {required}")
    for directory in ("common", host):
        for source in (staged / directory).rglob("*"):
            if source.is_file():
                shipped = resources / source.relative_to(staged / directory)
                if not shipped.is_file() or not filecmp.cmp(source, shipped, shallow=False):
                    raise SystemExit(f"Missing or modified editor resource: {shipped}")
    print(f"Verified {host} image: launcher, JVM, bridge, assets and both game runtimes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("staged", type=Path)
    parser.add_argument("host", choices=("windows-x64", "linux-x64"))
    args = parser.parse_args()
    check_image(args.image, args.staged, args.host)
