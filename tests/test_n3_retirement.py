"""Guard the owner-approved retirement of the N3 private-display experiment.

Historical architecture/audit notes may retain N3 provenance; executable paths may not.
N0 ordinary fullscreen and N1/N2 existing-task native windowing remain supported.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "launcher/src/main"
ANDROID_TEST = ROOT / "launcher/src/androidTest"
JAVA = MAIN / "java/com/cbkii/ts18launcher"


class N3RetirementTests(unittest.TestCase):
    def test_private_display_runtime_and_fixtures_are_absent(self):
        for path in (
            JAVA / "N3Activity.java",
            JAVA / "navigation/NavigationFeatureFacade.java",
            JAVA / "navigation/n3",
            ROOT / "shared/n3",
            ROOT / "launcher/src/test/java/com/cbkii/ts18launcher/navigation/n3",
            ANDROID_TEST / "java/com/cbkii/ts18launcher/navigation/n3",
        ):
            self.assertFalse(path.exists(), f"retired N3 executable path remains: {path}")

    def test_no_n3_activation_in_live_runtime_or_ci(self):
        sources = (
            MAIN / "AndroidManifest.xml",
            ANDROID_TEST / "AndroidManifest.xml",
            MAIN / "res/values/strings.xml",
            JAVA / "SettingsActivity.java",
            JAVA / "SetupActivity.java",
            JAVA / "NavigationRootHelper.java",
            ROOT / "launcher/build.gradle.kts",
            ROOT / "scripts/ci/api29-startup-boundary-smoke.sh",
            ANDROID_TEST / "java/com/cbkii/ts18launcher/MediaSessionApi29Instrumentation.java",
        )
        forbidden = (
            "N3Activity", "N3TrampolineActivity", "NavigationFeatureFacade",
            "N3DisplayProbe", "n3_path", "shared/n3",
            "n3.experimental.enabled", "N3_VIRTUAL_DISPLAY",
        )
        for path in sources:
            source = path.read_text(encoding="utf-8")
            for marker in forbidden:
                self.assertNotIn(marker, source, f"{path} must not expose {marker}")

    def test_automatic_admission_remains_exclusive(self):
        helper = (JAVA / "NavigationRootHelper.java").read_text(encoding="utf-8")
        gate = (JAVA / "navigation/NavigationAutomaticAdmission.java").read_text(encoding="utf-8")
        self.assertIn("NavigationAutomaticAdmission.tryAcquire()", helper)
        self.assertIn("releaseAutomaticLaunchClaim()", helper)
        self.assertIn("NavigationAutomaticAdmission.release(claim)", helper)
        self.assertIn("if (owner != null) return null;", gate)
        self.assertIn("if (claim != null && owner == claim) owner = null;", gate)
        self.assertIn('"automatic".equals(policy)', helper)
        self.assertIn('"manual".equals(policy)', helper)

    def test_no_upgrade_side_effect_on_retired_n3_preference(self):
        for path in (JAVA / "NavigationRootHelper.java",
                     JAVA / "NavigationWindowController.java",
                     JAVA / "SettingsActivity.java",
                     JAVA / "SetupActivity.java"):
            source = path.read_text(encoding="utf-8")
            self.assertNotIn("n3.experimental.enabled", source)
            self.assertNotIn("NavigationFeatureFacade", source)


if __name__ == "__main__":
    unittest.main()
