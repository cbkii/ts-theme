import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts" / "termux"

class TermuxToolkitTests(unittest.TestCase):
    def test_bash_syntax(self):
        paths = list(SCRIPTS.glob("*.sh")) + list((SCRIPTS / "lib").glob("*.sh"))
        for path in paths:
            result = subprocess.run(["bash", "-n", str(path)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

    def test_no_destructive_escape_hatches(self):
        text = "\n".join(path.read_text(encoding="utf-8") for path in SCRIPTS.rglob("*.sh"))
        for forbidden in ("setenforce 0", "chmod 777", "pm clear com.dofun.variety", "/system/", "/vendor/"):
            self.assertNotIn(forbidden, text)
        self.assertNotIn("> '$app_pa/p.l'", text)

    def test_no_root_preflight_is_graceful(self):
        with tempfile.TemporaryDirectory() as td:
            fake = Path(td) / "bin"; fake.mkdir()
            for name, body in {
                "getprop": "#!/bin/sh\nexit 0\n",
                "wm": "#!/bin/sh\necho 'Physical size: 1280x720'\n",
                "dumpsys": "#!/bin/sh\nexit 0\n",
                "cmd": "#!/bin/sh\necho 'com.dofun.variety/com.dofun.overseasvariety.Launcher'\n",
                "su": "#!/bin/sh\nexit 1\n",
            }.items():
                p = fake / name; p.write_text(body, encoding="utf-8"); p.chmod(0o755)
            env = os.environ.copy(); env["PATH"] = f"{fake}:{env['PATH']}"; env["TS18_EXPORT_ROOT"] = str(Path(td)/"out"); env["TS18_PRIVATE_ROOT"] = str(Path(td)/"state")
            result = subprocess.run(["bash", str(SCRIPTS / "ts18-theme-preflight.sh")], env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            reports = list((Path(td)/"out").glob("preflight-*.txt")); self.assertEqual(1, len(reports))
            self.assertIn("root=no", reports[0].read_text(encoding="utf-8"))

if __name__ == "__main__":
    unittest.main()
