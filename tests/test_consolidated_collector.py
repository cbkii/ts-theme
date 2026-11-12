"""Exercise the collector's streaming cap without Android or a root transport."""
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'scripts/termux/qualify-consolidated-candidate.sh'

class ConsolidatedCollectorTests(unittest.TestCase):
    def test_flooding_capture_is_bounded_and_identified_as_truncated(self):
        text = SCRIPT.read_text()
        function = text.split('capture() {', 1)[1].split('\ncapture identity', 1)[0]
        with tempfile.TemporaryDirectory() as tmp:
            code = 'work=$1; status="$work/STATUS.tsv"; max_capture_bytes=1024\ncapture() {' + function
            code += '\ncapture flood python3 -c "print(chr(120)*100000)"\n'
            subprocess.run(['bash', '-c', code, 'capture-test', tmp], check=True, timeout=12)
            self.assertEqual(1024, (Path(tmp) / 'flood.txt').stat().st_size)
            self.assertIn('WARN_TRUNCATED', (Path(tmp) / 'STATUS.tsv').read_text())

    def run_framework(self, root, command):
        text = SCRIPT.read_text()
        capture = text.split('capture() {', 1)[1].split('\n# Platform tools', 1)[0]
        framework = text.split('framework() {', 1)[1].split('\ncapture identity', 1)[0]
        with tempfile.TemporaryDirectory() as tmp:
            code = ('work=$1; status="$work/STATUS.tsv"; max_capture_bytes=1024; root=%d\n' % root
                    + 'plain=(env)\nsu_bin="$work/su"\n'
                    + 'printf \'#!/bin/sh\\nshift\\nexec sh -c "$1"\\n\' > "$su_bin"; chmod 700 "$su_bin"\n'
                    + 'capture() {' + capture + '\nframework() {' + framework
                    + '\nframework probe "$2"\n')
            subprocess.run(['bash', '-c', code, 'framework-test', tmp, command], check=True, timeout=12)
            return (Path(tmp) / 'STATUS.tsv').read_text(), (Path(tmp) / 'probe.txt').read_text()

    def test_refused_framework_capture_without_root_is_blocked_not_passed(self):
        status, output = self.run_framework(0, "echo \"Permission Denial: can't dump\"")
        self.assertEqual('probe\tBLOCKED\troot-unavailable\n', status)
        self.assertIn('Permission Denial', output)

    def test_permitted_framework_capture_keeps_its_own_result(self):
        status, _ = self.run_framework(0, 'echo ready')
        self.assertEqual('probe\tPASS\t0\n', status)
        status, output = self.run_framework(1, 'echo as-root')
        self.assertEqual('probe\tPASS\t0\n', status)
        self.assertEqual('as-root\n', output)

    def test_other_application_logs_are_never_read_unprivileged(self):
        text = SCRIPT.read_text()
        unprivileged = text.split('\nelse\n', 1)[1].split('\nfi\n', 1)[0]
        self.assertNotIn('logcat', unprivileged.replace('# An unprivileged logcat', ''))
        self.assertIn("timeline\\tBLOCKED\\troot-unavailable", unprivileged)

    def test_root_path_and_archive_integrity_are_explicit(self):
        text = SCRIPT.read_text()
        self.assertEqual(5, text.count('export PATH=/system/bin:'))
        self.assertIn('plain=(env -u LD_PRELOAD -u LD_LIBRARY_PATH PATH=/system/bin:', text)
        self.assertIn('partial="$archive.partial"', text)
        self.assertIn('tar -tzf "$partial"', text)
        self.assertIn('trap cleanup EXIT', text)
