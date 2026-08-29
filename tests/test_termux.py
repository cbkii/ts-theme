import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts" / "termux"

class TermuxToolkitTests(unittest.TestCase):
    def bash(self, code, *, input_text="", env=None):
        return subprocess.run(["bash","-c",code],input=input_text,capture_output=True,text=True,env=env,timeout=10)
    def test_bash_syntax(self):
        paths=list(SCRIPTS.glob("*.sh"))+list((SCRIPTS/"lib").glob("*.sh"))
        for path in paths:
            result=subprocess.run(["bash","-n",str(path)],capture_output=True,text=True); self.assertEqual(0,result.returncode,result.stderr)
    def test_no_destructive_escape_hatches(self):
        text="\n".join(path.read_text(encoding="utf-8") for path in SCRIPTS.rglob("*.sh"))
        for forbidden in ("setenforce 0","chmod 777","pm clear com.dofun.variety","/system/","/vendor/"):
            self.assertNotIn(forbidden,text)
        self.assertNotIn("> '$app_pa/p.l'",text)
    def test_no_root_preflight_is_graceful(self):
        with tempfile.TemporaryDirectory() as td:
            fake=Path(td)/"bin"; fake.mkdir()
            for name,body in {"getprop":"#!/bin/sh\nexit 0\n","wm":"#!/bin/sh\necho 'Physical size: 1280x720'\n","dumpsys":"#!/bin/sh\nexit 0\n","cmd":"#!/bin/sh\necho 'com.dofun.variety/com.dofun.overseasvariety.Launcher'\n","su":"#!/bin/sh\nexit 1\n"}.items():
                p=fake/name; p.write_text(body,encoding="utf-8"); p.chmod(0o755)
            env=os.environ.copy(); env["PATH"]=f"{fake}:{env['PATH']}"; env["TS18_EXPORT_ROOT"]=str(Path(td)/"out"); env["TS18_PRIVATE_ROOT"]=str(Path(td)/"state")
            result=subprocess.run(["bash",str(SCRIPTS/"ts18-theme-preflight.sh")],env=env,capture_output=True,text=True,timeout=10); self.assertEqual(0,result.returncode,result.stderr)
            reports=list((Path(td)/"out").glob("preflight-*.txt")); self.assertEqual(1,len(reports)); self.assertIn("root=no",reports[0].read_text(encoding="utf-8"))
    def test_escaped_pl_paths_are_parsed_and_ranked(self):
        payload='[{"path":"\\/data\\/user\\/0\\/com.dofun.variety\\/app_p_a\\/333.jar","pkgname":"launcher.variety.theme.plugin.sfp_other"},{"path":"\\/data\\/user\\/0\\/com.dofun.variety\\/app_p_a\\/111.jar","pkgname":"launcher.variety.theme.plugin.sfp_fyd18"},{"path":"/data/user/0/com.dofun.variety/app_p_a/222.jar","pkgname":"launcher.variety.theme.plugin.sfp_ts10s"}]'
        result=self.bash('. "$1"; parse_donor_records',input_text=payload,env=os.environ.copy() | {"DUMMY":"1"}) if False else subprocess.run(["bash","-c",'. "$1"; parse_donor_records',"_",str(SCRIPTS/"lib/dofun.sh")],input=payload,capture_output=True,text=True,timeout=10)
        self.assertEqual(0,result.returncode,result.stderr); self.assertEqual(["launcher.variety.theme.plugin.sfp_fyd18|111.jar","launcher.variety.theme.plugin.sfp_ts10s|222.jar","launcher.variety.theme.plugin.sfp_other|333.jar"],result.stdout.splitlines())
    def test_malformed_pl_has_no_candidates(self):
        result=subprocess.run(["bash","-c",'. "$1"; parse_donor_records',"_",str(SCRIPTS/"lib/dofun.sh")],input='not json and no donor path',capture_output=True,text=True,timeout=10); self.assertEqual("",result.stdout.strip())
    def test_missing_app_pa_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            fake=Path(td)/"su"; fake.write_text("#!/bin/sh\nexit 1\n",encoding="utf-8"); fake.chmod(0o755)
            env=os.environ.copy(); env["PATH"]=f"{td}:{env['PATH']}"
            result=subprocess.run(["bash","-c",'. "$1"; find_app_pa',"_",str(SCRIPTS/"lib/dofun.sh")],env=env,capture_output=True,text=True,timeout=10); self.assertNotEqual(0,result.returncode)
    def test_donor_path_guard_and_recovery_contract_are_present(self):
        common=(SCRIPTS/"lib/common.sh").read_text(encoding="utf-8"); install=(SCRIPTS/"ts18-theme-install.sh").read_text(encoding="utf-8"); rollback=(SCRIPTS/"ts18-theme-rollback.sh").read_text(encoding="utf-8")
        self.assertIn('^[0-9]+\\.jar$',common); self.assertIn("test ! -L",install); self.assertIn("test ! -L",rollback); self.assertIn("restore_interrupted_donor",install)
        self.assertLess(install.index("donor-original.jar"),install.index("cat '$TS18_FIXED_STAGE' > '$DONOR_TARGET'")); self.assertIn("p.l-sha256.txt",install); self.assertIn('[[ "$pl_after" == "$pl_before" ]]',install)
if __name__ == "__main__": unittest.main()
