import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"


def read(name):
    return (SRC / name).read_text(encoding="utf-8")


class StartupReadinessTests(unittest.TestCase):
    def test_explicit_app_launch_survives_home_stop_and_failed_parking(self):
        gate = read("NavigationOverlayGate.java")
        controller = read("NavigationWindowController.java")
        self.assertIn("void onStopped() { release(); }", gate)
        self.assertNotIn("externalLaunchGeneration++;\n        if (overlayGate", controller)
        on_stopped = controller[controller.index("void onHomeStopped()"):controller.index("void onHomeFocusLost")]
        self.assertNotIn("externalLaunchGeneration++", on_stopped)
        self.assertIn("overlayGate.release()", controller)  # timeout, destroy and latched failure

    def test_navradio_guard_extends_deadline_instead_of_being_dropped(self):
        boot = read("MediaSourceBootstrapper.java")
        self.assertIn("NAVRADIO_STARTUP_CEILING_MS", boot)
        self.assertIn("pending.deadlineMs = Math.min(needed", boot)

    def test_coordinator_is_bounded_single_admission_and_never_autoplays(self):
        coordinator = read("StartupReadinessCoordinator.java")
        policy = read("StartupReadinessPolicy.java")
        for state in ("UNAVAILABLE", "STARTING", "READY", "ACTIVE", "FAILED", "BLOCKED", "UNCERTAIN"):
            self.assertIn(state, policy)
        self.assertIn("markActivityLaunch", coordinator)
        self.assertIn("pauseUnrequestedPlayback", coordinator)
        self.assertIn("foregroundBusy", coordinator)
        self.assertIn("HomeMode.bringLauncherToFront", coordinator)
        self.assertIn("acc=unverified", coordinator)
        for forbidden in ("forceStop", "force-stop", "pm clear", "input tap", "requestAudioFocus",
                          "setenforce", "WakeLock", "startservice"):
            self.assertNotIn(forbidden, coordinator)
        # No unbounded loops or root polling: every poll is deadline-gated.
        self.assertNotIn("while (true)", coordinator)
        self.assertEqual(len(re.findall(r"RootShell\.", coordinator)), 2)

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

    def test_navigation_hold_prevents_duplicate_cold_launch(self):
        controller = read("NavigationWindowController.java")
        self.assertIn("if (startupHold)", controller)
        self.assertIn("void setStartupHold", controller)

    def test_home_yields_on_user_interaction(self):
        self.assertIn("noteUserInteraction", read("LauncherActivity.java"))


if __name__ == "__main__":
    unittest.main()
