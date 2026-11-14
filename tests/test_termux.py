import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts" / "termux"

class TermuxToolkitTests(unittest.TestCase):
    def test_bash_syntax(self):
        paths=list(SCRIPTS.glob("*.sh"))+list((SCRIPTS/"lib").glob("*.sh"))
        for path in paths:
            result=subprocess.run(["bash","-n",str(path)],capture_output=True,text=True); self.assertEqual(0,result.returncode,result.stderr)
    def test_no_destructive_escape_hatches(self):
        text="\n".join(path.read_text(encoding="utf-8") for path in SCRIPTS.rglob("*.sh"))
        # Read-only collectors legitimately execute binaries and inspect files below /system and
        # /vendor. Reject mutation primitives instead of treating protected-path reads as writes.
        for forbidden in (
            "setenforce 0",
            "chmod 777",
            "pm clear com.dofun.variety",
            "mount -o rw,remount /system",
            "mount -o rw,remount /vendor",
            "> /system/",
            "> /vendor/",
        ):
            self.assertNotIn(forbidden,text)
        self.assertNotIn("> '$app_pa/p.l'",text)
    def test_fast_media_collector_retains_local_timeline_and_storage_evidence(self):
        text=(SCRIPTS/"collect-fast-media-evidence.sh").read_text(encoding="utf-8")
        self.assertIn("TS18Media:I",text); self.assertIn("/proc/mounts",text); self.assertIn("notification-listener.txt",text)
        self.assertNotIn("am start-foreground-service",text)
    def test_navradio_service_qualification_is_explicit_and_bounded(self):
        text=(SCRIPTS/"qualify-navradio-service-start.sh").read_text(encoding="utf-8")
        self.assertIn("--qualify-navradio-service-start",text); self.assertIn("start-foreground-service",text)
        self.assertIn("expected exactly one installed NavRadio MediaSessionService",text)
        self.assertNotIn("am force-stop",text); self.assertNotIn("am stopservice",text)
        self.assertNotIn("input keyevent",text)
    def test_stock_radio_comparator_is_read_only(self):
        text=(SCRIPTS/"collect-stock-radio-compare.sh").read_text(encoding="utf-8")
        self.assertIn("--phase cold|opened",text); self.assertIn("READ ONLY",text)
        self.assertNotIn("am start ",text); self.assertNotIn("am broadcast",text); self.assertNotIn("service call",text)
    def test_lifecycle_collector_is_time_bounded_and_does_not_drive_acc(self):
        text=(SCRIPTS/"collect-media-lifecycle-evidence.sh").read_text(encoding="utf-8")
        self.assertIn("DURATION < 30 || DURATION > 300",text); self.assertIn("timeout -k 2",text)
        self.assertIn("Screen-on/display-on evidence is not classified as ACC",text)
        self.assertNotIn("am broadcast",text); self.assertNotIn("input keyevent",text)
if __name__ == "__main__": unittest.main()
