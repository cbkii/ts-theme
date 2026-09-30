"""Static safety and provenance checks for bounded UI evidence capture."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/termux/capture-ui-evidence.sh"


class UiCaptureScriptTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script = SCRIPT.read_text(encoding="utf-8")

    def test_manual_capture_remains_bounded_and_read_only(self):
        self.assertIn("LABEL is required in manual mode", self.script)
        self.assertIn("timeout -k 1 6 /system/bin/screencap", self.script)
        self.assertIn("capture_request_uptime_ms", self.script)
        self.assertIn("capture_complete_uptime_ms", self.script)
        self.assertIn("TS18Media:I TS18Nav:I TS18Launcher:I", self.script)
        for forbidden in (
            "am force-stop", "am task resize", "am task focus", "input keyevent",
            "settings put", "setprop ", "pm clear", "pm disable", "setenforce 0",
        ):
            self.assertNotIn(forbidden, self.script)

    def test_watch_mode_starts_at_current_boundary_rejects_stale_and_coalesces(self):
        self.assertIn("--watch", self.script)
        self.assertIn("/system/bin/logcat -T 1 -v epoch", self.script)
        self.assertIn("age > 2", self.script)
        self.assertIn("COALESCED", self.script)
        self.assertIn("duplicate within 2000ms", self.script)
        self.assertIn("source_event_epoch", self.script)
        self.assertIn("source_event=", self.script)
        for trigger in (
            "drawer-first-draw", "startup-complete", "startup-failure",
            "nav-fullscreen", "nav-failure",
        ):
            self.assertIn(trigger, self.script)

    def test_screenshot_happens_before_heavier_correlation_snapshots(self):
        capture = self.script.split("capture_one()", 1)[1].split(
            "if (( watch_seconds == 0 ))", 1
        )[0]
        self.assertLess(capture.index("/system/bin/screencap"), capture.index("/system/bin/dumpsys activity"))
        self.assertIn('if [[ "$trigger" == nav-* || "$trigger" == manual ]]', capture)

    def test_capture_manifest_excludes_self_and_partial_files(self):
        self.assertIn("! -name MANIFEST.sha256 ! -name MANIFEST_VERIFY.txt", self.script)
        self.assertIn("sha256sum -c MANIFEST.sha256 >MANIFEST_VERIFY.txt", self.script)
        self.assertNotIn(".tmp", self.script)


if __name__ == "__main__":
    unittest.main()
