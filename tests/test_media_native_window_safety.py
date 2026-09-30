import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class MediaNativeWindowSafetyTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_masked_foreground_prime_is_blocked_before_overlay_for_native_navigation(self):
        mask = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/StartupMaskController.java")
        show = mask.split("boolean show()", 1)[1].split(
            "boolean coversExternalActivities()", 1)[0]
        self.assertIn("HomeNavigationSurfacePolicy.NATIVE_WINDOW", show)
        self.assertIn("native navigation window active; external task ordering unsafe", show)
        self.assertLess(show.index("HomeNavigationSurfacePolicy.NATIVE_WINDOW"),
                        show.index("windowManager.addView"))
        native_guard = show.split("HomeNavigationSurfacePolicy.NATIVE_WINDOW", 1)[1].split(
            "FrameLayout root", 1)[0]
        self.assertIn("return false;", native_guard)

    def test_failed_mask_capability_skips_external_activity_prime(self):
        coordinator = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/StartupBootstrapCoordinator.java")
        begin = coordinator.split("private void begin", 1)[1].split(
            "@Override public void onMediaStateChanged", 1)[0]
        self.assertIn("boolean canMaskExternal = mask.show()", begin)
        self.assertIn("if (!canMaskExternal)", begin)
        self.assertIn("foreground-prime-skipped", begin)
        self.assertTrue("finishCold();" in begin or "completeInteractive" in begin)

    def test_background_readiness_remains_available(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        bootstrapper = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        self.assertIn("mediaBootstrapper.warmConfiguredSources()", launcher)
        self.assertIn("MediaBrowser", bootstrapper)
        self.assertIn("prepareExplicitService", bootstrapper)


if __name__ == "__main__":
    unittest.main()
