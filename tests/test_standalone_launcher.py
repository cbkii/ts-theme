import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class StandaloneLauncherContractTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_launcher_is_separate_api29_java_module(self):
        settings = self.read("settings.gradle.kts")
        gradle = self.read("launcher/build.gradle.kts")
        self.assertIn('include(":launcher")', settings)
        self.assertIn('applicationId = "com.cbkii.ts18launcher"', gradle)
        self.assertIn("minSdk = 29", gradle)
        self.assertIn("targetSdk = 29", gradle)
        self.assertIn("compileSdk = 29", gradle)
        for forbidden in (
            "androidx.",
            "compose",
            "appcompat",
            "material:",
            "room",
            "datastore",
            "rxjava",
            "dagger",
            "replugin",
        ):
            self.assertNotIn(forbidden, gradle.lower())

    def test_home_alias_is_opt_in_and_dofun_is_not_impersonated(self):
        manifest_path = ROOT / "launcher/src/main/AndroidManifest.xml"
        root = ET.parse(manifest_path).getroot()
        application = root.find("application")
        self.assertIsNotNone(application)
        aliases = application.findall("activity-alias")
        alias = next(
            item for item in aliases
            if item.attrib.get(ANDROID_NS + "name") == ".HomeAlias"
        )
        self.assertEqual("false", alias.attrib.get(ANDROID_NS + "enabled"))
        self.assertEqual("true", alias.attrib.get(ANDROID_NS + "exported"))
        self.assertEqual(".LauncherActivity", alias.attrib.get(ANDROID_NS + "targetActivity"))

        manifest = manifest_path.read_text(encoding="utf-8")
        self.assertNotIn("launcher.variety.theme.plugin.sfp_cbk_black", manifest)
        self.assertNotIn("android.uid.system", manifest)
        self.assertNotIn("QUERY_ALL_PACKAGES", manifest)
        self.assertNotIn("extractNativeLibs", manifest)

    def test_launcher_runtime_stays_small_and_single_authority(self):
        source_root = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"
        java = "\n".join(
            path.read_text(encoding="utf-8")
            for path in sorted(source_root.rglob("*.java"))
        )
        self.assertNotIn("new MediaSession(", java)
        self.assertNotIn("requestAudioFocus", java)
        self.assertNotIn("WakeLock", java)
        self.assertNotIn("startForeground(", java)
        self.assertNotIn("com.qihoo360", java)
        self.assertIn("MediaSessionManager", java)
        self.assertIn("getSessionToken()", java)
        self.assertIn("com.android.server.telecom", java)
        self.assertIn("configuredRadioPackage", java)

    def test_web_map_is_local_lifecycle_bound_and_origin_restricted(self):
        panel = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MapPanel.java")
        html = self.read("launcher/src/main/assets/map/map.html")
        self.assertIn('file:///android_asset/map/map.html', panel)
        self.assertIn('tile.openstreetmap.org', panel)
        self.assertIn('tile.openstreetmap.org', html)
        self.assertNotIn("addJavascriptInterface", panel)
        self.assertNotIn("http://", panel + html)
        self.assertIn("requestLocationUpdates", panel)
        self.assertIn("removeUpdates(this)", panel)
        self.assertIn("pageReady", panel)
        self.assertIn("lastLocation", panel)

    def test_root_policy_is_authorised_but_bounded_and_reversible(self):
        agents = self.read("AGENTS.md")
        installer = self.read("scripts/termux/install-standalone-launcher.sh")
        self.assertIn("Magisk-root superuser access", agents)
        self.assertIn("authorised and safe to use", agents)
        self.assertIn("Root does not grant platform signing", agents)
        self.assertIn("timeout -k 2", installer)
        self.assertIn("--rollback-home", installer)
        self.assertIn("previous-home.txt", installer)
        for forbidden in (
            "pm uninstall com.dofun.variety",
            "pm disable --user 0 com.dofun.variety",
            "setenforce 0",
            "mount -o rw,remount /system",
            "rm -rf /data/user/0/com.dofun.variety",
        ):
            self.assertNotIn(forbidden, installer)

    def test_candidate_workflow_keeps_write_permission_in_publish_job(self):
        workflow = self.read(".github/workflows/launcher-candidate.yml")
        self.assertRegex(workflow, r"permissions:\n  contents: read")
        self.assertRegex(workflow, r"publish:\n    if:.*\n    needs: build")
        self.assertRegex(workflow, r"publish:[\s\S]*?permissions:\n      contents: write")
        self.assertIn("install-standalone-launcher.sh", workflow)
        self.assertIn("measure-standalone-launcher.sh", workflow)
        self.assertIn("STANDALONE_LAUNCHER.md", workflow)
        for match in re.finditer(r"uses:\s+([^\s]+)", workflow):
            action = match.group(1)
            self.assertRegex(action, r"@(?:[0-9a-f]{40})$")


if __name__ == "__main__":
    unittest.main()
