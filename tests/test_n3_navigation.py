"""Source contracts for the experimental N3 virtual-display navigation package."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
N3 = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/navigation/n3"


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
                              "VIRTUAL_DISPLAY_FLAG_PUBLIC", "startActivity("):
                self.assertNotIn(forbidden, text, f"{path.name} must not contain {forbidden}")

    def test_probe_is_read_only_and_private(self):
        probe = code(N3 / "N3DisplayProbe.java")
        self.assertIn("isActivityStartAllowedOnDisplay(", probe)
        self.assertIn("reader.getSurface(), 0)", probe)
        self.assertIn("display.release()", probe)

    def test_no_classification_claims_device_success(self):
        feasibility = code(N3 / "N3Feasibility.java")
        self.assertNotIn("DEVICE_FEASIBILITY_PASS", feasibility.split("public enum Result", 1)[1].split("}", 1)[0])
        self.assertIn("LAUNCH_ADMITTED_UNVERIFIED", feasibility)

    def test_settings_exposes_only_a_probe(self):
        settings = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java").read_text()
        self.assertIn("Experimental · N3 display host", settings)
        self.assertIn("N3DisplayProbe.run(this, pkg)", settings)
        policy = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/HomeNavigationSurfacePolicy.java").read_text()
        self.assertNotIn("n3", policy.lower(), "N3 is not a selectable surface until device feasibility passes")


if __name__ == "__main__":
    unittest.main()
