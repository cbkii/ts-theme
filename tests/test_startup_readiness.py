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

    def test_readiness_uses_one_bounded_owner_without_playback_authority(self):
        coordinator = read("StartupReadinessCoordinator.java")
        for forbidden in ("startActivity", "moveTaskToFront", "StartupRootFallback",
                          "new MediaSourceBootstrapper", "requestAudioFocus"):
            self.assertNotIn(forbidden, coordinator)
        self.assertIn("prepareDirectSource(context, key, ++epoch)", coordinator)
        self.assertIn("MAX_ATTEMPTS_PER_BOOT", coordinator)
        self.assertIn("new MaskedSourcePreparation", coordinator)
        self.assertIn("navigation.deferAutomaticPresentation(false)", coordinator)

    def test_boot_receiver_signals_only(self):
        receiver = read("StartupBootReceiver.java")
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("startActivity", receiver)
        self.assertIn("BOOT_COMPLETED", manifest)
        self.assertIn("RECEIVE_BOOT_COMPLETED", manifest)

    def test_background_media_layer_still_has_no_activity_launch(self):
        self.assertNotIn("startActivity", read("MediaSourceBootstrapper.java"))

    def test_n2_default_preserves_explicit_n0_n1_fallbacks(self):
        settings = read("SettingsActivity.java")
        profiles = read("TestingProfiles.java")
        self.assertIn("N2 task reuse / fullscreen recovery", settings)
        self.assertNotIn('chooseTesting("departure"', settings)
        self.assertNotIn('chooseTesting("navigation"', settings)
        self.assertIn('choice(c, "navigation", "N2", "N0", "N1", "N2")', profiles)
        self.assertIn('TestingProfiles.setNavigation(this, value)', settings)
        self.assertIn('static boolean retainCompact(Context c) { return false; }', profiles)

    def test_navigation_ready_requires_verified_presentation(self):
        controller = read("NavigationWindowController.java")
        self.assertIn("state == State.WINDOWED && !needsValidation && !needsPresentation", controller)

    def test_home_yields_on_user_interaction(self):
        self.assertIn("noteUserInteraction", read("LauncherActivity.java"))


if __name__ == "__main__":
    unittest.main()
