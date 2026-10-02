import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class MediaNativeWindowSafetyTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_foreground_media_readiness_is_retired(self):
        coordinator = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/StartupBootstrapCoordinator.java")
        self.assertIn('"foreground-prime-disabled"', coordinator)
        self.assertNotIn("launchMediaBootstrap", coordinator)
        self.assertNotIn("AppResolver.launchPackage", coordinator)
        self.assertNotIn("startActivity(", coordinator)
        self.assertNotIn("StartupMaskController", coordinator)

    def test_background_readiness_remains_available(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        bootstrapper = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        self.assertIn("mediaBootstrapper.warmConfiguredSources()", launcher)
        self.assertIn("MediaBrowser", bootstrapper)
        self.assertIn("prepareExplicitService", bootstrapper)

    def test_failed_background_readiness_never_retries_transport_after_activity_prime(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        method = launcher.split("private void dispatchSourceCommand(", 1)[1].split(
            "private void settleSourceCommandFailure", 1)[0]
        self.assertIn("if (success)", method)
        self.assertIn('"cold-background-only-prime-unavailable"', method)
        self.assertIn("settleSourceCommandFailure", method)
        self.assertNotIn("generation, false", method)
        self.assertNotIn("cold-foreground-prime-request", method)

    def test_geometry_does_not_reapply_identical_layout_params(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        place = launcher.split("private void place(View view", 1)[1].split(
            "private void placeCard", 1
        )[0]
        self.assertIn("view.getLayoutParams()", place)
        self.assertIn("current.width == desiredWidth", place)
        self.assertIn("current.height == desiredHeight", place)
        self.assertIn("current.leftMargin == desiredLeft", place)
        self.assertIn("current.topMargin == desiredTop", place)

    def test_unconfirmed_pause_is_recorded_without_second_transport_dispatch(self):
        bootstrapper = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        ack = bootstrapper.split("private void awaitAcknowledgement", 1)[1].split(
            "private void completeAcknowledged", 1)[0]
        self.assertIn('"dispatched-unconfirmed"', ack)
        self.assertIn("Phase.DISPATCHED_UNCONFIRMED", ack)
        self.assertNotIn("sendDesired(", ack)

    def test_navigation_failure_without_managed_task_does_not_block_background_readiness(self):
        controller = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java")
        method = controller.split("void whenHomePresented", 1)[1].split(
            "private void finishPresentationCallback", 1)[0]
        self.assertIn("state == State.FAILED", method)
        self.assertIn("callback.accept(!hasManagedNativeTask())", method)
        self.assertIn("finishPresentationCallback(!hasManagedNativeTask())", method)
        failure = controller.split("private void latchFailure", 1)[1].split(
            "private boolean isFailureLatched", 1)[0]
        self.assertIn("finishPresentationCallback(!hasManagedNativeTask())", failure)


if __name__ == "__main__":
    unittest.main()
