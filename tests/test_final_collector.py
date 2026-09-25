"""Exercise collector status and archive behaviour with controlled Android commands."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
COLLECTOR = ROOT / "scripts/termux/collect-final-qualification.sh"


class FinalCollectorTest(unittest.TestCase):
    def collect(self, user="0", wm_status=0):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            binaries = base / "bin"
            binaries.mkdir()
            for name in ("cmd", "am", "getprop", "wm", "dumpsys", "logcat"):
                command = binaries / name
                command.write_text("#!/bin/sh\n"
                    "case \"${0##*/}\" in\n"
                    "cmd|am) printf '%s\\n' \"$TEST_ANDROID_USER\" ;;\n"
                    "wm) if [ \"$1\" = size ] && [ \"$TEST_WM_STATUS\" != 0 ]; "
                    "then exit \"$TEST_WM_STATUS\"; fi; echo 'Physical size: 1280x720' ;;\n"
                    "getprop) echo 'test.build' ;;\n"
                    "dumpsys) echo 'test snapshot' ;;\n"
                    "logcat) echo 'test event' ;;\n"
                    "esac\n")
                command.chmod(0o700)
            env = {**os.environ, "TS18_TERMUX_BIN": str(binaries),
                   "TS18_ANDROID_PATH": "/usr/bin:/bin", "TEST_ANDROID_USER": user,
                   "TEST_WM_STATUS": str(wm_status)}
            result = subprocess.run(["bash", str(COLLECTOR), "--no-root", "--out-base", str(base)],
                                    env=env, capture_output=True, text=True, timeout=25)
            exported = next(base.glob("final-*/STATUS.tsv"))
            rows = exported.read_text()
            archive = next(base.glob("final-*.zip"))
            with zipfile.ZipFile(archive) as z:
                archive_ok = z.testzip() is None
            verified = (exported.parent / "MANIFEST_VERIFY.txt").read_text()
            return result, rows, archive_ok, verified

    def test_pass_keeps_optional_root_block_separate_and_verifies_archive(self):
        result, rows, archive_ok, verified = self.collect()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("display/wm-size.txt\tREQUIRED\tPASS\t0", rows)
        self.assertIn("identity/root.txt\tOPTIONAL\tBLOCKED\t0", rows)
        self.assertTrue(archive_ok)
        self.assertIn("OK", verified)

    def test_required_command_not_found_cannot_pass(self):
        result, rows, archive_ok, _ = self.collect(wm_status=127)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("display/wm-size.txt\tREQUIRED\tFAIL\t127", rows)
        self.assertTrue(archive_ok)

    def test_ambiguous_android_user_blocks_dependent_evidence(self):
        result, rows, archive_ok, _ = self.collect(user="Current user: 0\nuser: 10")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("identity/android-user.txt\tREQUIRED\tBLOCKED\t1", rows)
        self.assertIn("packages-BLOCKED.txt\tOPTIONAL\tBLOCKED\t1", rows)
        self.assertIn("display/wm-size.txt\tREQUIRED\tPASS\t0", rows)
        self.assertTrue(archive_ok)


if __name__ == "__main__":
    unittest.main()
