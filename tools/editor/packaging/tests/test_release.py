"""Release helper contracts, exercised with complete small application images."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / 'release.py'
NAMES = (
    'SLE-Editor-0.7.2-linux-x64.AppImage',
    'SLE-Editor-0.7.2-linux-x64.tar.gz',
    'SLE-Editor-0.7.2-linux-x64.zip',
    'SLE-Editor-0.7.2-windows-x64-setup.exe',
    'SLE-Editor-0.7.2-windows-x64.zip',
)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / 'output'

    def run_helper(self, *args, script=SCRIPT, success=True):
        result = subprocess.run([sys.executable, str(script), *map(str, args)], capture_output=True, text=True)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout)
        return result

    def image(self, host):
        image = self.root / host / 'image'
        windows = host == 'windows-x64'
        resources = image / ('app/resources' if windows else 'lib/app/resources')
        paths = [
            image / ('SLE Editor.exe' if windows else 'bin/SLE Editor'),
            image / ('runtime/bin/server/jvm.dll' if windows else 'lib/runtime/lib/server/libjvm.so'),
            resources / ('sle_editor_native.dll' if windows else 'libsle_editor_native.so'),
            resources / 'assets/shaders/sprite.vert',
            resources / 'runtime/windows-x64/engine_app.exe',
            resources / 'runtime/linux-x64/engine_app',
            image / ('app/editor.jar' if windows else 'lib/app/editor.jar'),
        ]
        for path in paths:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b'application payload\x00')
            path.chmod(0o755)
        for target in ('windows-x64', 'linux-x64'):
            (resources / f'runtime/{target}/sle-runtime.json').write_text('{"engineVersion":"0.8.1"}')
        return image

    def installer(self):
        installers = self.root / 'installers'
        installers.mkdir()
        (installers / 'SLE Editor-0.7.2.exe').write_bytes(b'MZinstaller')
        return installers

    def linux_packages(self, image):
        directory = self.root / 'linux-packages'
        directory.mkdir()
        (directory / 'SLE-Editor-linux-x64.AppImage').write_bytes(b'\x7fELFAppImage')
        with tarfile.open(directory / 'SLE-Editor-linux-x64.tar.gz', 'w:gz') as archive:
            archive.add(image, arcname='SLE Editor')
        return directory

    def package(self, host, image, packages, success=True, version='0.7.2'):
        return self.run_helper('package', '--host', host, '--image', image,
                               '--output', self.output, '--version', version,
                               '--installer-dir' if host == 'windows-x64' else '--linux-packages', packages,
                               success=success)

    def test_metadata_matches_exact_tag_and_reports_both_versions(self):
        script = self.root / 'tools/editor/packaging/release.py'
        script.parent.mkdir(parents=True)
        shutil.copyfile(SCRIPT, script)
        (self.root / 'tools/editor/build.gradle.kts').write_text('version = "0.7.2"\n')
        (self.root / 'CMakeLists.txt').write_text('project(GameEngine VERSION 0.8.1 LANGUAGES C CXX)\n')
        event = self.root / 'event.json'
        event.write_text('{"release":{"tag_name":"v0.7.2"}}')
        result = self.run_helper('metadata', '--event-name', 'release', '--event', event, script=script)
        self.assertEqual(result.stdout, 'version=0.7.2\nengine_version=0.8.1\n')
        for tag in ('v0.7.1', '0.7.2', 'v0.7.2\n', 'v0.7.2;touch nope', '../v0.7.2'):
            with self.subTest(tag=tag):
                event.write_text(json.dumps({'release': {'tag_name': tag}}))
                self.run_helper('metadata', '--event-name', 'release', '--event', event, script=script, success=False)
        event.write_text('{}')
        result = self.run_helper('metadata', '--event-name', 'workflow_dispatch', '--event', event, script=script)
        self.assertEqual(result.stdout, 'version=0.7.2\nengine_version=0.8.1\n')
        self.run_helper('metadata', '--event-name', 'push', '--event', event, script=script, success=False)

    def test_windows_portable_contains_entire_image_and_normalized_setup(self):
        image = self.image('windows-x64')
        stamp = 1_700_000_000  # 2023-11-14, well inside the DOS date range
        os.utime(image / 'app/editor.jar', (stamp, stamp))
        self.package('windows-x64', image, self.installer())
        self.assertEqual(sorted(p.name for p in self.output.iterdir()), [NAMES[3], NAMES[4]])
        self.assertEqual((self.output / NAMES[3]).read_bytes(), b'MZinstaller')
        with zipfile.ZipFile(self.output / NAMES[4]) as archive:
            self.assertEqual(archive.read('SLE Editor/app/editor.jar'), b'application payload\x00')
            self.assertEqual(archive.getinfo('SLE Editor/app/editor.jar').date_time,
                             time.localtime(stamp)[:6])
            self.assertEqual(archive.read('SLE Editor/runtime/bin/server/jvm.dll'), b'application payload\x00')
            self.assertEqual(archive.read('SLE Editor/app/resources/runtime/linux-x64/engine_app'), b'application payload\x00')
            self.assertTrue(all(n.startswith('SLE Editor/') for n in archive.namelist()))

    @unittest.skipIf(os.name == 'nt', 'requires Unix mode and symlink support')
    def test_linux_zip_retains_executables_and_safe_symlinks(self):
        image = self.image('linux-x64')
        (image / 'lib/runtime/lib/current.so').symlink_to('server/libjvm.so')
        self.package('linux-x64', image, self.linux_packages(image))
        self.assertEqual(sorted(p.name for p in self.output.iterdir()), list(NAMES[:3]))
        with zipfile.ZipFile(self.output / NAMES[2]) as archive:
            for name in ('bin/SLE Editor', 'lib/app/resources/runtime/linux-x64/engine_app'):
                self.assertEqual((archive.getinfo('SLE Editor/' + name).external_attr >> 16) & 0o111, 0o111)
            link = archive.getinfo('SLE Editor/lib/runtime/lib/current.so')
            self.assertTrue(stat.S_ISLNK(link.external_attr >> 16))
            self.assertEqual(archive.read(link), b'server/libjvm.so')

    def test_missing_or_ambiguous_installer_rejected(self):
        image = self.image('windows-x64')
        installers = self.installer()
        (installers / 'extra.exe').write_bytes(b'MZextra')
        self.package('windows-x64', image, installers, success=False)
        (installers / 'extra.exe').unlink()
        (installers / 'SLE Editor-0.7.2.exe').unlink()
        self.package('windows-x64', image, installers, success=False)

    def test_installer_version_must_match_release_version(self):
        image = self.image('windows-x64')
        installers = self.installer()
        (installers / 'SLE Editor-0.7.2.exe').rename(installers / 'SLE Editor-0.7.1.exe')
        self.package('windows-x64', image, installers, success=False)

    def test_incomplete_image_and_unsafe_version_rejected(self):
        image = self.image('windows-x64')
        installers = self.installer()
        self.package('windows-x64', image, installers, success=False, version='../escape')
        (image / 'app/resources/runtime/linux-x64/sle-runtime.json').unlink()
        self.package('windows-x64', image, installers, success=False)
        self.assertFalse(self.output.exists())

    @unittest.skipIf(os.name == 'nt', 'requires Unix symlink support')
    def test_escaping_symlink_rejected(self):
        image = self.image('linux-x64')
        (image / 'outside').symlink_to('../../private')
        self.package('linux-x64', image, self.linux_packages(image), success=False)

    def test_stale_linux_tar_rejected(self):
        image = self.image('linux-x64')
        packages = self.linux_packages(image)
        (image / 'lib/app/editor.jar').write_bytes(b'new content')
        self.package('linux-x64', image, packages, success=False)

    def test_checksums_require_exact_assets_and_hash_actual_contents(self):
        self.output.mkdir()
        for name in NAMES:
            (self.output / name).write_bytes(b'abc')
        self.run_helper('checksums', '--directory', self.output, '--version', '0.7.2')
        expected = ''.join('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad  ' + name + '\n' for name in NAMES)
        self.assertEqual((self.output / 'SHA256SUMS.txt').read_text(), expected)
        (self.output / NAMES[0]).write_bytes(b'different bytes')
        self.run_helper('checksums', '--directory', self.output, '--version', '0.7.2')
        first = (self.output / 'SHA256SUMS.txt').read_text().splitlines()[0]
        self.assertEqual(first.split()[0], hashlib.sha256(b'different bytes').hexdigest())
        (self.output / 'unexpected.zip').write_bytes(b'x')
        self.run_helper('checksums', '--directory', self.output, '--version', '0.7.2', success=False)
        (self.output / 'unexpected.zip').unlink()
        (self.output / NAMES[4]).unlink()
        self.run_helper('checksums', '--directory', self.output, '--version', '0.7.2', success=False)


if __name__ == '__main__':
    unittest.main()
