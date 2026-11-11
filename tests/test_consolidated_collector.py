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

    def test_root_path_and_archive_integrity_are_explicit(self):
        text = SCRIPT.read_text()
        self.assertEqual(3, text.count('export PATH=/system/bin:'))
        self.assertIn('partial="$archive.partial"', text)
        self.assertIn('tar -tzf "$partial"', text)
        self.assertIn('trap cleanup EXIT', text)
