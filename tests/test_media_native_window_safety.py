import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class MediaNativeWindowSafetyTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_native_foreground_prime_uses_guarded_navigation_handoff(self):
        mask = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/StartupMaskController.java")
        show = mask.split("boolean show()", 1)[1].split(
            "boolean coversExternalActivities()", 1)[0]
        coordinator = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/StartupBootstrapCoordinator.java")
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        self.assertIn("hasOverlayAccess(activity)", show)
        self.assertIn("launchMediaBootstrap(packageName", coordinator)
        self.assertIn("afterMediaBootstrapHomeRestored(restored", coordinator)
        self.assertIn("navigationWindowController.launchAfterSuspension(launch)", launcher)
        self.assertLess(coordinator.index("if (!canMaskExternal)"),
                        coordinator.index("private void primeNext()"))

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

    def test_cold_fallback_cannot_double_dispatch_a_warm_command(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        method = launcher.split("private void dispatchSourceCommand(", 1)[1].split(
            "private void settleSourceCommandFailure", 1)[0]
        self.assertIn("if (success)", method)
        self.assertIn("allowColdPrime && command == MediaListenerService.Command.PLAY_PAUSE", method)
        self.assertIn("!mediaBootstrapper.hasUsableController(packageName)", method)
        self.assertIn("generation, false", method)

    def test_unconfirmed_pause_is_recorded_without_second_transport_dispatch(self):
        bootstrapper = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        ack = bootstrapper.split("private void awaitAcknowledgement", 1)[1].split(
            "private void completeAcknowledged", 1)[0]
        self.assertIn('"dispatched-unconfirmed"', ack)
        self.assertIn("Phase.DISPATCHED_UNCONFIRMED", ack)
        self.assertNotIn("sendDesired(", ack)


if __name__ == "__main__":
    unittest.main()
