"""Source contracts for the experimental N3 virtual-display navigation package."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
N3 = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/navigation/n3"
SHARED = ROOT / "shared/n3/java/com/cbkii/ts18launcher/navigation/n3"
HOST = ROOT / "n3host"


def code(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


class N3ContractTests(unittest.TestCase):
    def test_n3_is_independent_of_legacy_navigation_and_root(self):
        legacy = ("NavigationWindowController", "NavigationRootHelper", "RootNavigationBackend",
                  "RootTaskSession", "NavTaskBridge", "nav-window.sh", "setTaskWindowingMode")
        for path in N3.glob("*.java"):
            text = code(path)
            for symbol in legacy:
                self.assertNotIn(symbol, text, f"{path.name} must not use {symbol}")
            for forbidden in ("Runtime.getRuntime", "\"su\"", "injectInputEvent", "InputManager",
                              "VIRTUAL_DISPLAY_FLAG_PUBLIC", "VIRTUAL_DISPLAY_FLAG_TRUSTED"):
                self.assertNotIn(forbidden, text, f"{path.name} must not contain {forbidden}")
            # The only external start is the user-initiated launch on the owned, non-default display.
            if path.name != "PublicDisplayPath.java":
                self.assertNotIn("startActivity(", text, path.name)
        public = code(N3 / "PublicDisplayPath.java")
        self.assertEqual(1, public.count("startActivity("))
        self.assertIn("setLaunchDisplayId(id)", public)
        self.assertIn("isActivityStartAllowedOnDisplay(app, id, intent)", public)
        self.assertIn("reader.getSurface(), 0,", public)

    def test_probe_is_read_only_and_private(self):
        probe = code(N3 / "N3DisplayProbe.java")
        self.assertIn("isActivityStartAllowedOnDisplay(", probe)
        self.assertIn("reader.getSurface(), 0)", probe)
        self.assertIn("display.release()", probe)

    def test_no_classification_claims_device_success(self):
        feasibility = code(N3 / "N3Feasibility.java")
        self.assertNotIn("DEVICE_FEASIBILITY_PASS", feasibility.split("public enum Result", 1)[1].split("}", 1)[0])
        self.assertIn("LAUNCH_ADMITTED_UNVERIFIED", feasibility)

    def test_settings_exposes_n3_only_behind_explicit_default_off_opt_in(self):
        settings = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java").read_text()
        self.assertIn("Experimental · N3 display host", settings)
        self.assertIn("N3DisplayProbe.run(this, pkg)", settings)
        self.assertIn("if (navFacade.n3Enabled()) {", settings)
        facade = code(ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/navigation/NavigationFeatureFacade.java")
        self.assertIn("getBoolean(KEY_N3_ENABLED, false)", facade)
        self.assertIn("getBoolean(KEY_N3_HOST, false)", facade)
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text()
        self.assertRegex(manifest, r'android:name="\.N3Activity"\s+android:configChanges="[^"]+"\s+android:exported="false"')
        policy = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/HomeNavigationSurfacePolicy.java").read_text()
        self.assertNotIn("n3", policy.lower(), "N3 is not a selectable surface until device feasibility passes")

    def test_optional_host_is_separate_inert_by_default_and_identity_bound(self):
        policy = code(SHARED / "N3HostPolicy.java")
        self.assertIn("Config DISABLED = new Config(false", policy)
        for banned in ("Runtime.getRuntime", "ProcessBuilder", "injectInputEvent", "Intent(", "su "):
            self.assertNotIn(banned, policy)
        # The launcher itself gains no privilege and never runs host admission.
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text()
        for banned in ("INJECT_EVENTS", "ACTIVITY_EMBEDDING", "CAPTURE_SECURE_VIDEO_OUTPUT", "ADD_TRUSTED_DISPLAY",
                       "DisplayHostService"):
            self.assertNotIn(banned, manifest)
        for path in (ROOT / "launcher/src/main/java").rglob("*.java"):
            self.assertNotIn("new N3HostPolicy(", path.read_text(), path.name)
        # The host APK: inert unless built with an explicit identity, no injection or network permission.
        host_manifest = (HOST / "src/main/AndroidManifest.xml").read_text()
        for banned in ("INJECT_EVENTS", "INTERNET", "SYSTEM_ALERT_WINDOW", "WRITE_SECURE_SETTINGS", "sharedUserId"):
            self.assertNotIn(banned, host_manifest)
        host_build = (HOST / "build.gradle.kts").read_text()
        self.assertIn('prop("n3host.enabled") == "true"', host_build)
        service = code(HOST / "src/main/java/com/cbkii/ts18launcher/n3host/DisplayHostService.java")
        for entry in ("open(", "launch(", "motion(", "back(", "setSurface(", "close("):
            body = service.split("@Override public " , 1)[1]
        self.assertEqual(6, service.count("int uid = Binder.getCallingUid();"))
        for banned in ("Runtime.getRuntime", "ProcessBuilder", "injectInputEvent", "\"su\"", "ServerSocket", "Socket("):
            self.assertNotIn(banned, service)
        release = (ROOT / "release-config.json").read_text()
        self.assertNotIn("n3host", release)

    def test_input_mapper_never_routes_through_root_or_freeform(self):
        mapper = code(SHARED / "N3InputMapper.java")
        for banned in ("RootShell", "RootTaskSession", "NavTaskBridge", "windowingMode", "am start"):
            self.assertNotIn(banned, mapper)

    def test_emulator_smoke_runs_the_real_private_display_path(self):
        smoke = (ROOT / "scripts/ci/api29-startup-boundary-smoke.sh").read_text()
        self.assertIn("-e n3_path 1", smoke)
        self.assertIn("grep -Fq 'PASS API29 N3 private display'", smoke)
        probe = code(ROOT / "launcher/src/androidTest/java/com/cbkii/ts18launcher/navigation/n3/N3PathIpcProbe.java")
        self.assertIn("non-embeddable target not refused", probe)
        self.assertIn("no pixels from the launched fixture", probe)


if __name__ == "__main__":
    unittest.main()
