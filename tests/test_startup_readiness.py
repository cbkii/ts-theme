import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"


def read(name):
    return (SRC / name).read_text(encoding="utf-8")


class StartupReadinessTests(unittest.TestCase):
    def test_startup_uses_existing_media_and_map_owners(self):
        coordinator = read("StartupReadinessCoordinator.java")
        self.assertIn("Ts18LauncherApplication.mediaSources(context)", coordinator)
        self.assertNotIn("new MediaSourceBootstrapper", coordinator)
        self.assertIn("navigation.reconcile()", coordinator)
        self.assertNotIn("NAVIGATION_DWELL_MS", coordinator)
        self.assertNotIn("holdNavigation", coordinator)

    def test_staging_has_no_mutating_root_fallback(self):
        coordinator = read("StartupReadinessCoordinator.java")
        self.assertIn("StartupForegroundPolicy.parse", coordinator)
        self.assertIn("moveTaskToFront(homeTask, 0)", coordinator)
        self.assertIn("pauseUnrequestedPlayback", coordinator)
        self.assertIn("acc=unverified", coordinator)
        for forbidden in ("am start", "startservice", "force-stop", "requestAudioFocus", "setenforce"):
            self.assertNotIn(forbidden, coordinator)

    def test_boot_receiver_signals_only(self):
        receiver = read("StartupBootReceiver.java")
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("startActivity", receiver)
        self.assertIn("BOOT_COMPLETED", manifest)
        self.assertIn("RECEIVE_BOOT_COMPLETED", manifest)

    def test_background_media_layer_still_has_no_activity_launch(self):
        self.assertNotIn("startActivity", read("MediaSourceBootstrapper.java"))

    def test_startup_modes_exposed_in_testing_methods(self):
        settings = read("SettingsActivity.java")
        profiles = read("TestingProfiles.java")
        for label in ("Background only", "Automatic fallback", "Aggressive"):
            self.assertIn(label, settings)
        self.assertIn('"background", "auto", "aggressive"', profiles)

    def test_navigation_ready_requires_verified_presentation(self):
        controller = read("NavigationWindowController.java")
        self.assertIn("state == State.WINDOWED && !needsValidation && !needsPresentation", controller)

    def test_home_yields_on_user_interaction(self):
        self.assertIn("noteUserInteraction", read("LauncherActivity.java"))


if __name__ == "__main__":
    unittest.main()
