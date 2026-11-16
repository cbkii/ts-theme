"""Exercise the delivered physical collector and its immutable-selection packager."""
from pathlib import Path
import hashlib
import importlib.util
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MODULE = ROOT / 'scripts/diagnostics/physical-validation'
SPEC = importlib.util.spec_from_file_location('physical_builder', ROOT / 'scripts/diagnostics/build-physical-validation.py')
BUILDER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BUILDER)


class PhysicalValidationTest(unittest.TestCase):
    def test_module_host_boundaries(self):
        result = subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s',
                                 str(MODULE / 'host-tests'), '-q'], capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        for script in MODULE.glob('*.sh'):
            subprocess.run(['sh', '-n', str(script)], check=True)

    def test_packaging_requires_pinned_set_and_preserves_existing_output(self):
        with tempfile.TemporaryDirectory() as td:
            base = Path(td)
            selection = base / 'selection.tsv'
            selection.write_text((MODULE / 'expected-apks.tsv').read_text())
            output = base / 'module.zip'
            with self.assertRaises(ValueError):
                BUILDER.build(selection, output, 'a' * 40)
            self.assertFalse(output.exists())
            selection.write_text(''.join(f'{p} {"b" * 40} {"c" * 64}\n' for p in sorted(BUILDER.PACKAGES)))
            digest = BUILDER.build(selection, output, 'a' * 40)
            self.assertEqual(digest, hashlib.sha256(output.read_bytes()).hexdigest())
            with zipfile.ZipFile(output) as archive:
                for line in archive.read('SOURCES.sha256').decode().splitlines():
                    expected, name = line.split('  ', 1)
                    self.assertEqual(expected, hashlib.sha256(archive.read(name)).hexdigest())
                mode = archive.getinfo('service.sh').external_attr >> 16
                self.assertTrue(mode & 0o111)
            with self.assertRaises(FileExistsError):
                BUILDER.build(selection, output, 'a' * 40)
            self.assertEqual(digest, hashlib.sha256(output.read_bytes()).hexdigest())
