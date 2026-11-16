"""Behavioural regressions for failures in the supplied physical capture."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ADAPTER = ROOT / 'host-tools/busybox-host.sh'


def fixture(base):
    mod = base / 'mod'
    mod.mkdir()
    text = (ROOT / 'lib.sh').read_text().replace('BB=/data/adb/magisk/busybox', 'BB=' + str(ADAPTER))
    (mod / 'lib.sh').write_text(text)
    return mod


class CaptureRepairTests(unittest.TestCase):
    def test_byte_limit_uses_actual_shell_units(self):
        with tempfile.TemporaryDirectory() as td:
            mod = fixture(Path(td))
            for shell in ('bash', 'dash'):
                r = subprocess.run([shell, '-c', '. "$1"; limit_file_bytes 4096 || exit 9; b awk \'/^Max file size / {print $4}\' /proc/self/limits', 'host', str(mod/'lib.sh')], capture_output=True, text=True, timeout=5)
                self.assertEqual(r.returncode, 0, r.stderr)
                self.assertEqual(r.stdout.strip(), '4096', shell)

    def test_apk_match_does_not_pass_a_failed_hash_or_different_build(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            mod = fixture(base)
            run = base / 'boot-1-aabb'
            (run/'apps').mkdir(parents=True)
            sha = hashlib.sha256(b'expected').hexdigest()
            (mod/'expected-apks.tsv').write_text(f'com.tw.media source {sha}\n')
            source = (ROOT/'verify-apks.sh').read_text()
            start = source.index('case "$OUT" in ')
            end = source.index('\n# An operator', start)
            source = source[:start] + f'case "$OUT" in {run}) ;; *) exit 2;; esac' + source[end:]
            (mod/'verify-apks.sh').write_text(source)
            artifact = run/'apps/com.tw.media-apk-base.apk.sha256.txt'
            for rc, actual, expected in [(0, sha, 'MATCH'), (0, '0'*64, 'MISMATCH'), (124, sha, 'BLOCKED_HASH_NOT_CAPTURED')]:
                artifact.write_text(f'# context\n{actual}  /data/app/base.apk\n')
                Path(str(artifact)+'.rc').write_text(str(rc))
                r = subprocess.run(['bash', str(mod/'verify-apks.sh'), str(run)], capture_output=True, text=True, timeout=5)
                self.assertEqual(r.returncode, 0, r.stderr)
                fields = (run/'APK-MATCH.tsv').read_text().splitlines()[1].split('\t')
                self.assertEqual(fields[2], expected)
            (run.parent/'EXPECTED-APKS.tsv').write_text(f'com.tw.media final-source {"0"*64}\n')
            Path(str(artifact)+'.rc').write_text('0')
            artifact.write_text('0'*64 + '  /data/app/base.apk\n')
            r = subprocess.run(['bash', str(mod/'verify-apks.sh'), str(run)], capture_output=True, text=True, timeout=5)
            self.assertEqual(r.returncode, 0, r.stderr)
            fields = (run/'APK-MATCH.tsv').read_text().splitlines()[1].split('\t')
            self.assertEqual(fields[1:3], ['final-source', 'MATCH'])

    def test_recorder_retains_native_stdout_and_actual_exit_status(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            mod = fixture(base)
            fake = base/'screenrecord'
            fake.write_text('''#!/bin/bash
export PATH=/usr/bin:/bin
if [ "$1" = --help ]; then echo '--time-limit --bit-rate --verbose'; exit 0; fi
printf 'ENCODER_STDOUT_READY\\n'
for last; do :; done
printf 'not-a-movie' > "$last"
exit 7
''')
            fake.chmod(0o755)
            source = (ROOT/'record.sh').read_text()
            for path in ('/system/bin/screenrecord', '/vendor/bin/screenrecord', '/product/bin/screenrecord'):
                source = source.replace(path, str(fake))
            (mod/'record.sh').write_text(source)
            dest = base/'video.mp4'
            r = subprocess.run(['bash', str(mod/'record.sh'), str(dest), '25', '2'], capture_output=True, text=True, timeout=8)
            self.assertEqual(r.returncode, 7, r.stderr)
            self.assertEqual(Path(str(dest)+'.rc').read_text().strip(), '7')
            self.assertIn('ENCODER_STDOUT_READY', Path(str(dest)+'.encoder.txt').read_text())
            self.assertNotIn('STRUCTURE_OK', Path(str(dest)+'.encoder.txt').read_text())

    def test_prior_boot_recovery_labels_partial_and_refuses_current_boot(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            mod = fixture(base)
            root = base/'download'
            run = root/'boot-1-aabb'
            run.mkdir(parents=True)
            (run/'sample.txt').write_text('retained evidence')
            (run/'CAPTURE-IN-PROGRESS.txt').write_text('active')
            # Summary generator is tested separately; isolate recovery ordering here.
            (mod/'ai-summary.sh').write_text('#!/bin/bash\nexit 0\n')
            source = (ROOT/'recover.sh').read_text()
            start = source.index('case "$ROOT" in ')
            end = source.index('\ncase "$OLD"', start)
            source = source[:start]+f'case "$ROOT" in {root}) ;; *) exit 2;; esac'+source[end:]
            (mod/'recover.sh').write_text(source)
            current = Path('/proc/sys/kernel/random/boot_id').read_text().strip()
            r = subprocess.run(['bash', str(mod/'recover.sh'), str(root), current, '1'], capture_output=True, text=True, timeout=8)
            self.assertEqual(r.returncode, 3)
            r = subprocess.run(['bash', str(mod/'recover.sh'), str(root), 'aabb', '1'], capture_output=True, text=True, timeout=8)
            self.assertEqual(r.returncode, 0, r.stderr)
            self.assertIn('PARTIAL_RECOVERED', (run/'SUMMARY.txt').read_text())
            self.assertFalse((run/'CAPTURE-IN-PROGRESS.txt').exists())
            r = subprocess.run(['sha256sum', '-c', 'MANIFEST.sha256'], cwd=run, capture_output=True, text=True, timeout=8)
            self.assertEqual(r.returncode, 0, r.stdout + r.stderr)


class AnalysisCleanupTests(unittest.TestCase):
    def test_indexer_keeps_the_actual_two_column_video_timeline(self):
        import sys
        sys.path.insert(0, str(ROOT / 'host-tools'))
        from analyse_preflight import analyse
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            run = root / 'boot-1-aabb'
            run.mkdir()
            (run / 'video-timeline.tsv').write_text('81.32\tvideo-01.mp4 28 81 profile=3 rc=0 STRUCTURE_OK_DECODE_NOT_RUN\n')
            report = analyse(root)
            segments = report['runs'][0]['video_segments']
            self.assertEqual(len(segments), 1)
            self.assertEqual(segments[0]['uptime'], '81.32')
            self.assertIn('video-01.mp4', segments[0]['detail'])
