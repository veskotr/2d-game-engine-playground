"""Validate release metadata and assemble complete, versioned editor downloads.

Uses only the Python standard library. No command publishes or modifies a release.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import tarfile
import tempfile
import time
import zipfile


HOSTS = ('windows-x64', 'linux-x64')


def version_string(value):
    if not isinstance(value, str) or not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', value):
        raise ValueError('Version must be three nonnegative integers separated by dots')
    return value


def asset_names(version):
    prefix = f'SLE-Editor-{version_string(version)}'
    return sorted((f'{prefix}-windows-x64-setup.exe', f'{prefix}-windows-x64.zip',
                   f'{prefix}-linux-x64.AppImage', f'{prefix}-linux-x64.zip',
                   f'{prefix}-linux-x64.tar.gz'))


def metadata(args):
    root = Path(__file__).resolve().parents[3]
    editor = re.findall(r'^version\s*=\s*"([^"]+)"\s*$',
                        (root / 'tools/editor/build.gradle.kts').read_text(), re.MULTILINE)
    engine = re.findall(r'project\(GameEngine\s+VERSION\s+(\S+)',
                        (root / 'CMakeLists.txt').read_text())
    if len(editor) != 1 or len(engine) != 1:
        raise ValueError('Expected exactly one editor and engine version declaration')
    editor, engine = version_string(editor[0]), version_string(engine[0])
    event = json.loads(args.event.read_text())
    if args.event_name == 'release':
        if event.get('release', {}).get('tag_name') != f'v{editor}':
            raise ValueError(f'Release tag must match editor version exactly: v{editor}')
    elif args.event_name != 'workflow_dispatch':
        raise ValueError('Only release and workflow_dispatch events are supported')
    print(f'version={editor}\nengine_version={engine}')


def digest(stream):
    checksum = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        checksum.update(block)
    return checksum.hexdigest()


def image_entries(image):
    """Enumerate without following links; reject links outside the portable image."""
    if not image.is_dir() or image.is_symlink():
        raise ValueError(f'Not an application image directory: {image}')
    entries = [image]
    for directory, directories, files in os.walk(image, followlinks=False):
        entries.extend(Path(directory) / name for name in directories + files)
    for path in entries:
        if '\\' in path.relative_to(image).as_posix():
            raise ValueError(f'Nonportable image filename: {path}')
        if path.is_symlink():
            target = os.readlink(path)
            if os.path.isabs(target) or '\\' in target or not path.resolve().is_relative_to(image.resolve()) or not path.exists():
                raise ValueError(f'Unsafe or broken image symlink: {path}')
        elif not (path.is_file() or path.is_dir()):
            raise ValueError(f'Unsupported image entry: {path}')
    return sorted(entries)


def check_image(image, host):
    entries = image_entries(image)
    windows = host == 'windows-x64'
    resources = image / ('app/resources' if windows else 'lib/app/resources')
    required = [
        image / ('SLE Editor.exe' if windows else 'bin/SLE Editor'),
        image / ('runtime/bin/server/jvm.dll' if windows else 'lib/runtime/lib/server/libjvm.so'),
        resources / ('sle_editor_native.dll' if windows else 'libsle_editor_native.so'),
        resources / 'runtime/windows-x64/sle-runtime.json',
        resources / 'runtime/linux-x64/sle-runtime.json',
        resources / 'runtime/windows-x64/engine_app.exe',
        resources / 'runtime/linux-x64/engine_app',
    ]
    for path in required:
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f'Missing or empty application image file: {path}')
    for host_runtime in HOSTS:
        json.loads((resources / f'runtime/{host_runtime}/sle-runtime.json').read_text())
    if not any(p.is_file() for p in (resources / 'assets/shaders').rglob('*')):
        raise ValueError('Application image has no engine shaders')
    app = image / ('app' if windows else 'lib/app')
    if not any(app.glob('*.jar')):
        raise ValueError('Application image has no editor JARs')
    if not windows:
        for path in (required[0], required[-1]):
            if path.stat().st_mode & 0o111 != 0o111:
                raise ValueError(f'Linux application image file is not executable: {path}')
    return entries


def archive_name(path, image):
    relative = path.relative_to(image).as_posix()
    return 'SLE Editor' + ('/' + relative if relative != '.' else '')


def zip_time(path):
    """ZIP stores local DOS times from 1980 onward; keep each entry's real modification time."""
    return max(time.localtime(path.lstat().st_mtime)[:6], (1980, 1, 1, 0, 0, 0))


def portable_zip(destination, image, entries):
    with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in entries:
            info = zipfile.ZipInfo(archive_name(path, image) + ('/' if path.is_dir() and not path.is_symlink() else ''),
                                   date_time=zip_time(path))
            info.create_system = 3
            info.external_attr = path.lstat().st_mode << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            if path.is_symlink():
                archive.writestr(info, os.readlink(path).encode('utf-8'))
            elif path.is_dir():
                info.external_attr |= 0x10
                archive.writestr(info, b'')
            else:
                with path.open('rb') as source, archive.open(info, 'w', force_zip64=True) as target:
                    shutil.copyfileobj(source, target)
    with zipfile.ZipFile(destination) as archive:
        if archive.testzip() is not None or len(archive.infolist()) != len(entries):
            raise ValueError('Portable ZIP validation failed')
        for path in entries:
            name = archive_name(path, image) + ('/' if path.is_dir() and not path.is_symlink() else '')
            info = archive.getinfo(name)
            if info.external_attr >> 16 != path.lstat().st_mode:
                raise ValueError(f'ZIP lost mode: {name}')
            if path.is_symlink():
                if archive.read(info) != os.readlink(path).encode('utf-8'):
                    raise ValueError(f'ZIP lost symlink: {name}')
            elif path.is_file():
                with archive.open(info) as shipped, path.open('rb') as source:
                    if digest(shipped) != digest(source):
                        raise ValueError(f'ZIP content mismatch: {name}')


def check_tar(path, image, entries):
    expected = {archive_name(p, image): p for p in entries}
    with tarfile.open(path, 'r:gz') as archive:
        members = archive.getmembers()
        if len(members) != len(expected) or {m.name.rstrip('/') for m in members} != set(expected):
            raise ValueError('Linux TAR does not contain the complete application image')
        for member in members:
            source = expected[member.name.rstrip('/')]
            if source.is_symlink():
                if not member.issym() or member.linkname != os.readlink(source):
                    raise ValueError(f'TAR symlink mismatch: {member.name}')
            elif source.is_dir():
                if not member.isdir():
                    raise ValueError(f'TAR directory mismatch: {member.name}')
            else:
                if not member.isfile() or member.mode != stat.S_IMODE(source.stat().st_mode):
                    raise ValueError(f'TAR file or mode mismatch: {member.name}')
                with source.open('rb') as original, archive.extractfile(member) as shipped:
                    if digest(original) != digest(shipped):
                        raise ValueError(f'Stale TAR content: {member.name}')


def require_file(path, magic):
    if not path.is_file() or path.is_symlink():
        raise ValueError(f'Missing package: {path}')
    with path.open('rb') as stream:
        if stream.read(len(magic)) != magic:
            raise ValueError(f'Invalid package format: {path}')


def package(args):
    version = version_string(args.version)
    image = args.image.resolve()
    entries = check_image(image, args.host)
    prefix = f'SLE-Editor-{version}-{args.host}'
    if args.host == 'windows-x64':
        if args.installer_dir is None or args.linux_packages is not None:
            raise ValueError('Windows packaging requires --installer-dir')
        installers = list(args.installer_dir.glob('*.exe'))
        if len(installers) != 1:
            raise ValueError('Expected exactly one EXE installer')
        if installers[0].name != f'SLE Editor-{version}.exe':
            raise ValueError('Installer filename does not match release version')
        require_file(installers[0], b'MZ')
        copies = [(installers[0], f'{prefix}-setup.exe')]
    else:
        if args.linux_packages is None or args.installer_dir is not None:
            raise ValueError('Linux packaging requires --linux-packages')
        appimage = args.linux_packages / 'SLE-Editor-linux-x64.AppImage'
        tar = args.linux_packages / 'SLE-Editor-linux-x64.tar.gz'
        require_file(appimage, b'\x7fELF')
        require_file(tar, b'\x1f\x8b')
        check_tar(tar, image, entries)
        copies = [(appimage, f'{prefix}.AppImage'), (tar, f'{prefix}.tar.gz')]
    output = args.output.resolve()
    if output == image or output.is_relative_to(image):
        raise ValueError('Output directory must be outside the application image')
    expected = {f'{prefix}.zip', *(name for _, name in copies)}
    if output.exists() and set(p.name for p in output.iterdir()) - expected:
        raise ValueError('Package output contains unexpected entries; use a dedicated directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sle-release-', dir=output.parent) as temporary:
        staging = Path(temporary)
        portable_zip(staging / f'{prefix}.zip', image, entries)
        for source, name in copies:
            shutil.copy2(source, staging / name)
        if args.host == 'linux-x64':
            (staging / f'{prefix}.AppImage').chmod(0o755)
        output.mkdir(exist_ok=True)
        for path in staging.iterdir():
            path.replace(output / path.name)
    print('\n'.join(sorted(expected)))


def checksums(args):
    expected = asset_names(args.version)
    if not args.directory.is_dir():
        raise ValueError('Missing release asset directory')
    actual = {p.name for p in args.directory.iterdir()} - {'SHA256SUMS.txt'}
    if actual != set(expected):
        raise ValueError(f'Release assets mismatch: missing={sorted(set(expected) - actual)}, unexpected={sorted(actual - set(expected))}')
    lines = []
    for name in expected:
        path = args.directory / name
        if not path.is_file() or path.is_symlink() or not path.stat().st_size:
            raise ValueError(f'Invalid release payload: {name}')
        with path.open('rb') as source:
            lines.append(f'{digest(source)}  {name}\n')
    destination = args.directory / 'SHA256SUMS.txt'
    if destination.is_symlink() or (destination.exists() and not destination.is_file()):
        raise ValueError('Checksum output must be a regular file')
    destination.write_text(''.join(lines), encoding='utf-8', newline='\n')
    print(destination.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    meta = commands.add_parser('metadata')
    meta.add_argument('--event-name', required=True)
    meta.add_argument('--event', type=Path, required=True)
    meta.set_defaults(run=metadata)
    pack = commands.add_parser('package')
    pack.add_argument('--host', choices=HOSTS, required=True)
    for name in ('image', 'output'):
        pack.add_argument(f'--{name}', type=Path, required=True)
    pack.add_argument('--version', required=True)
    inputs = pack.add_mutually_exclusive_group(required=True)
    inputs.add_argument('--installer-dir', type=Path)
    inputs.add_argument('--linux-packages', type=Path)
    pack.set_defaults(run=package)
    sums = commands.add_parser('checksums')
    sums.add_argument('--directory', type=Path, required=True)
    sums.add_argument('--version', required=True)
    sums.set_defaults(run=checksums)
    args = parser.parse_args()
    try:
        args.run(args)
    except (ValueError, OSError, KeyError, TypeError, AttributeError, tarfile.TarError, zipfile.BadZipFile) as error:
        parser.exit(1, f'error: {error}\n')


if __name__ == '__main__':
    main()
