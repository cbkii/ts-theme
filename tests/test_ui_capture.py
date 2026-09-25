"""Static safety and provenance checks for the manual UI evidence capture helper."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/termux/capture-ui-evidence.sh"


class UiCaptureScriptTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script = SCRIPT.read_text(encoding="utf-8")

    def test_capture_is_manual_bounded_and_read_only(self):
        self.assertIn("LABEL is required", self.script)
        self.assertIn("timeout -k 1 6 /system/bin/screencap", self.script)
        self.assertIn("uptime_start_ms", self.script)
        self.assertIn("uptime_end_ms", self.script)
        self.assertIn("TS18Media:I TS18Nav:I TS18Launcher:I", self.script)
        for forbidden in (
            "am force-stop", "am task resize", "am task focus", "input keyevent",
            "settings put", "setprop ", "pm clear", "pm disable", "setenforce 0",
        ):
            self.assertNotIn(forbidden, self.script)

    def test_capture_manifest_excludes_self_and_partial_files(self):
        self.assertIn("! -name MANIFEST.sha256 ! -name MANIFEST_VERIFY.txt", self.script)
        self.assertIn("sha256sum -c MANIFEST.sha256 >MANIFEST_VERIFY.txt", self.script)
        self.assertNotIn(".tmp", self.script)


if __name__ == "__main__":
    unittest.main()
