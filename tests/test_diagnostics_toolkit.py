"""Meaningful host failure tests. No claim of Android/Magisk/device qualification."""
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'scripts/diagnostics'
if not TOOLS.is_dir():
    TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('diagnostics', TOOLS / 'analyse.py')
analyser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analyser)


def sealed(files, complete=b'producer=COMPLETE\n', seal=b'PASS\n'):
    files = dict(files, **{'COMPLETE.txt': complete})
    files['MANIFEST.sha256'] = ''.join(
        f'{hashlib.sha256(data).hexdigest()}  ./{name}\n'
        for name, data in sorted(files.items())).encode()
    files['SEALED.txt'] = seal
    return files


class AnalysisTests(unittest.TestCase):
    def sample(self, status=b'PASS'):
        return sealed({
            'UID_MAP_SOURCE.txt': b'commands/packages-ready-2\n',
            'commands/packages-ready-2/meta.txt': b'result=' + status + b'\nproducer_rc=0\n',
            'commands/packages-ready-2/output.txt': b'package:a uid:10148\npackage:b uid:10148\npackage:c uid:10186\n',
            'commands/live-log/output.txt': (
                b'Cmd send remountUidExternalStorage uid 10148 mode 1\n'
                b'Cmd send remountUidExternalStorage end uid 10148 mode 1\n'
                b'START remountUidExternalStorage uid 10186\n'
                b'END remountUidExternalStorage uid 10186\n'
                b'unknown remountUidExternalStorage uid 10148\n'),
        })

    def test_starts_once_shared_uid(self):
        result = analyser.analyse(self.sample())
        self.assertEqual(result['integrity'], 'PASS')
        self.assertEqual(result['uids']['10148'], ['a', 'b'])
        self.assertEqual(result['remount_starts'], {'10148': 1, '10186': 1})
        self.assertEqual(result['remount_unclassified_lines'], 1)

    def test_failed_pm_never_absence(self):
        result = analyser.analyse(self.sample(b'WARN_TIMEOUT'))
        self.assertEqual(result['uid_map'], 'UNKNOWN')
        self.assertEqual(result['uids'], {})

    def test_tampering_and_unlisted_fail(self):
        for name in ('commands/packages-ready-2/output.txt', 'extra'):
            files = self.sample()
            files[name] = b'tampered'
            result = analyser.analyse(files)
            self.assertEqual(result['integrity'], 'FAIL')
            self.assertEqual(result['uid_map'], 'UNKNOWN')

    def test_forced_marker_fails_even_when_manifested(self):
        base = self.sample()
        base.pop('MANIFEST.sha256')
        base.pop('SEALED.txt')
        base['FORCED_STOP.txt'] = b'forced\n'
        files = sealed(base)
        result = analyser.analyse(files)
        self.assertEqual(result['integrity'], 'FAIL')
        self.assertIn('partial/forced run', result['warnings'])

    def test_invalid_completion_and_seal_sentinels_fail(self):
        self.assertEqual(analyser.analyse(sealed({}, complete=b'producer=FAIL\n'))['integrity'], 'FAIL')
        self.assertEqual(analyser.analyse(sealed({}, seal=b'FAIL\n'))['integrity'], 'FAIL')

    def test_tar_rejects_traversal_links_devices_and_duplicate(self):
        for name, kind in [('../bad', tarfile.REGTYPE), ('run/link', tarfile.SYMTYPE),
                           ('run/hard', tarfile.LNKTYPE), ('run/dev', tarfile.CHRTYPE)]:
            with tempfile.TemporaryDirectory() as temp:
                path = Path(temp) / 'bad.tar.gz'
                with tarfile.open(path, 'w:gz') as archive:
                    member = tarfile.TarInfo(name); member.type = kind
                    member.linkname = '/tmp/outside'
                    archive.addfile(member, io.BytesIO())
                with self.assertRaises(ValueError):
                    analyser.load(path)
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'duplicate.tar.gz'
            with tarfile.open(path, 'w:gz') as archive:
                for payload in (b'a', b'b'):
                    member = tarfile.TarInfo('run/file'); member.size = len(payload)
                    archive.addfile(member, io.BytesIO(payload))
            with self.assertRaisesRegex(ValueError, 'duplicate archive member'):
                analyser.load(path)

    def test_directory_rejects_fifo(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            os.mkfifo(path / 'pipe')
            with self.assertRaises(ValueError):
                analyser.load(path)

    def test_expansion_budget(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'huge.tar.gz'
            with tarfile.open(path, 'w:gz') as archive:
                member = tarfile.TarInfo('run/file'); member.size = 4096
                archive.addfile(member, io.BytesIO(b'x' * 4096))
            old = analyser.MAX_BYTES
            try:
                analyser.MAX_BYTES = 1024
                with self.assertRaises(ValueError):
                    analyser.load(path)
            finally:
                analyser.MAX_BYTES = old

    def test_numeric_marker_order_and_repeated_phase_occurrences(self):
        files = {
            'CONTEXT.txt': b'start_epoch=1000\nstart_uptime=0\n',
            'marker-a.txt': b'10 AUXIO_PLAY\n',
            'marker-b.txt': b'2 IDLE\n',
            'marker-c.txt': b'20 AUXIO_PLAY\n',
            'commands/live-log/output.txt': (
                b'1003.0 tag get_presentation_position: Operation not permitted\n'
                b'1011.0 tag get_presentation_position: Operation not permitted\n'
                b'1021.0 tag get_presentation_position: Operation not permitted\n'),
        }
        result = analyser.analyse(sealed(files))
        self.assertEqual(result['action_markers_uptime'], ['2 IDLE', '10 AUXIO_PLAY', '20 AUXIO_PLAY'])
        self.assertIn('AUXIO_PLAY#1', result['audio_observed_rates'])
        self.assertIn('AUXIO_PLAY#2', result['audio_observed_rates'])
        self.assertEqual(result['audio_position_lines']['AUXIO_PLAY#1'], 1)
        self.assertEqual(result['audio_position_lines']['AUXIO_PLAY#2'], 1)


class BundleTests(unittest.TestCase):
    def test_bundle_is_reproducible_and_refuses_input_collision(self):
        with tempfile.TemporaryDirectory() as temp:
            fixture = Path(temp) / 'diagnostics'
            shutil.copytree(TOOLS, fixture)
            first = Path(temp) / 'one.zip'
            second = Path(temp) / 'two.zip'
            for output in (first, second):
                subprocess.run(['python3', str(fixture / 'build-bundle.py'), str(output)], check=True,
                               capture_output=True, text=True)
            self.assertEqual(hashlib.sha256(first.read_bytes()).digest(),
                             hashlib.sha256(second.read_bytes()).digest())
            readme = fixture / 'README.md'
            before = readme.read_bytes()
            collision = subprocess.run(['python3', str(fixture / 'build-bundle.py'), str(readme)],
                                       capture_output=True, text=True)
            self.assertNotEqual(collision.returncode, 0)
            self.assertEqual(readme.read_bytes(), before)


class SyntaxTests(unittest.TestCase):
    def test_shell_syntax(self):
        for script in TOOLS.glob('*.sh'):
            subprocess.run(['sh', '-n', str(script)], check=True)


@unittest.skipUnless(int(Path('/proc/self/stat').read_text().split()[0]) == os.getpid(),
                     'Host /proc PID namespace mismatch; run process-group tests in canonical Linux CI')
class CaptureTests(unittest.TestCase):
    def run_capture(self, command, limit=2048, timeout=2):
        kill = shutil.which('kill')
        if kill is None:
            self.skipTest('host kill utility unavailable')
        with tempfile.TemporaryDirectory() as temp:
            p = Path(temp)
            bb = p / 'bb'
            bb.write_text(f'''#!/bin/sh
if [ "$1" = kill ]; then shift; exec "{kill}" "$1" -- "$2"; fi
exec "$@"
'''); bb.chmod(0o700)
            lib = TOOLS / 'capture-lib.sh'
            entry = p / 'entry'
            entry.write_text(f'#!/bin/sh\nBB="{bb}"\n. "{lib}"\nshift\nproduce "$@"\n')
            script = p / 'test.sh'
            script.write_text(f'''
BB='{bb}'
SELF='{entry}'
OUT='{p}/run'
. '{lib}'
mkdir -p "$OUT/commands"
END=$(( $(uptime_s) + 10 ))
capture sample {timeout} {limit} sh -c '{command}'
seal
''')
            begin = time.monotonic()
            subprocess.run(['sh', str(script)], check=True, timeout=15, capture_output=True)
            elapsed = time.monotonic() - begin
            files = analyser.load(p / 'run')
            return files, elapsed

    def test_success_failure_and_sealing(self):
        for command, rc, status in [('printf okay', '0', 'PASS'), ('printf partial; exit 7', '7', 'WARN')]:
            files, _ = self.run_capture(command)
            meta = analyser.metadata(files['commands/sample/meta.txt'])
            self.assertEqual(meta['producer_rc'], rc)
            self.assertEqual(meta['result'], status)
            self.assertEqual(analyser.analyse(files)['integrity'], 'PASS')

    def test_hanging_descendant_is_bounded_and_partial(self):
        files, elapsed = self.run_capture('printf partial; sleep 60 & wait')
        self.assertLess(elapsed, 6)
        self.assertEqual(files['commands/sample/output.txt'], b'partial')
        self.assertEqual(analyser.metadata(files['commands/sample/meta.txt'])['result'], 'WARN_TIMEOUT')
        self.assertEqual(analyser.analyse(files)['integrity'], 'PASS')

    def test_output_flood_is_bounded(self):
        files, elapsed = self.run_capture('yes payload', limit=1024)
        self.assertLess(elapsed, 6)
        self.assertEqual(len(files['commands/sample/output.txt']), 1024)
        self.assertEqual(analyser.metadata(files['commands/sample/meta.txt'])['result'], 'WARN_TRUNCATED')
