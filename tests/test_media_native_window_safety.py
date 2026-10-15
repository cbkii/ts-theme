import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class MediaNativeWindowSafetyTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_foreground_media_readiness_code_is_removed(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        self.assertFalse((ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/StartupBootstrapCoordinator.java").exists())
        self.assertFalse((ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/StartupMaskController.java").exists())
        for source in (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher").glob("*.java"):
            text = source.read_text(encoding="utf-8")
            self.assertNotIn("StartupBootstrapCoordinator", text, source.name)
            self.assertNotIn("StartupMaskController", text, source.name)
        self.assertNotIn("launchMediaBootstrap", launcher)
        self.assertNotIn("cold-foreground-prime", launcher)

    def test_background_readiness_remains_available(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        bootstrapper = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        self.assertIn("mediaBootstrapper.warmConfiguredSources()", launcher)
        self.assertIn("MediaBrowser", bootstrapper)
        self.assertIn("prepareExplicitService", bootstrapper)

    def test_failed_background_readiness_does_not_retry_through_activity_prime(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        method = launcher.split("private void dispatchSourceCommand(", 1)[1].split(
            "private void settleSourceCommandFailure", 1)[0]
        self.assertIn("if (success)", method)
        self.assertIn('"background-only-command-failed"', method)
        self.assertIn("settleSourceCommandFailure", method)
        self.assertNotIn("primeForCommand", method)
        self.assertNotIn("cold-foreground-prime-request", method)

    def test_process_monitor_updates_home_without_listener_connection_callback(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        monitor = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/ProcessMediaSessionMonitor.java")
        self.assertIn("ProcessMediaSessionMonitor.Observer", launcher)
        self.assertIn("onProcessMediaStateChanged", launcher)
        self.assertIn("MediaSessionManager.OnActiveSessionsChangedListener", monitor)
        self.assertIn("getActiveSessions(listenerComponent)", monitor)
        self.assertIn("boolean authoritative", monitor)
        self.assertIn("main.post(() -> reconcile", monitor)

    def test_geometry_does_not_reapply_identical_layout_params(self):
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        place = launcher.split("private void place(View view", 1)[1].split(
            "private void placeCard", 1
        )[0]
        comparison = place.index("current.width == desiredWidth")
        guarded_return = place.index("return;", comparison)
        set_params = place.index("view.setLayoutParams(lp)")
        self.assertLess(comparison, guarded_return)
        self.assertLess(guarded_return, set_params)

    def test_process_and_listener_media_contracts_are_aligned(self):
        monitor = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/ProcessMediaSessionMonitor.java")
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        self.assertIn('"com.android.server.telecom"', monitor)
        self.assertIn('"com.android.dialer"', monitor)
        self.assertIn("MediaSelection.pick(candidates, preferred, preferConfigured, remembered)", monitor)
        self.assertIn("PlaybackState.STATE_BUFFERING", monitor)
        self.assertIn("PlaybackState.STATE_CONNECTING", monitor)
        self.assertIn("MediaListenerService.stabiliseSnapshot", monitor)
        self.assertIn("left.sessionIdentity", monitor)
        merge = launcher.split("private static MediaListenerService.Snapshot mergeSessionSnapshot", 1)[1].split(
            "private static MediaListenerService.Snapshot emptyMediaSnapshot", 1
        )[0]
        self.assertIn("if (!coreAuthoritative) return secondary;", merge)
        self.assertIn("if (primary.packageName.isEmpty()) return primary;", merge)
        self.assertIn("primary.sessionIdentity", merge)
        self.assertIn("secondary.sessionIdentity", merge)

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


if __name__ == "__main__":
    unittest.main()
