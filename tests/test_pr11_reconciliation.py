import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class Pr11ReconciliationTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_launcher_keeps_drawer_preload_direct_settings_and_background_readiness(self):
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        self.assertIn("appDrawerPanel.preload()", launcher)
        self.assertIn("appsButton.setOnLongClickListener", launcher)
        self.assertIn("new Intent(this, SettingsActivity.class)", launcher)
        self.assertIn("mediaBootstrapper.warmConfiguredSources()", launcher)
        self.assertNotIn("STARTUP_BOOTSTRAP_CLAIMED", launcher)
        self.assertNotIn("StartupBootstrapCoordinator", launcher)
        self.assertNotIn("launchMediaBootstrap", launcher)
        self.assertNotIn("MEDIA_REFRESH_INTERVAL_MS", launcher)
        self.assertNotIn("mediaRefreshPoll", launcher)

    def test_settings_keep_background_media_access_and_non_blocking_navigation_warning(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        compatibility = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/NavigationCompatibilityPolicy.java")
        self.assertIn("Notification access", settings)
        self.assertIn("NavigationCompatibilityPolicy.settingsMessage", settings)
        self.assertIn("UNQUALIFIED", compatibility)
        self.assertNotIn("throw", compatibility)
        self.assertNotIn("Startup splash overlay", settings)
        self.assertNotIn("StartupMaskController", settings)

    def test_drawer_is_visible_before_navigation_suspension_finishes(self):
        controller = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java")
        method = controller.split("void openLauncherOverlay(Runnable show)", 1)[1].split(
            "void cancelLauncherOverlay()", 1)[0]
        self.assertLess(method.index("show.run()"), method.index("suspendForLauncherSurface"))
        self.assertIn("Physical visibility and touch are not inferred", controller)

    def test_fullscreen_uses_same_task_without_reapplying_non_null_bounds(self):
        backend = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java")
        helper = self.read("launcher/src/main/assets/nav/nav-window.sh")
        self.assertIn('helper.run("fullscreen", packageName, Integer.toString(taskId))', backend)
        self.assertIn("WindowManager-owned", backend)
        self.assertNotIn("fullscreenResizeCommand", backend)
        self.assertNotIn('"am task resize " + taskId + " 0 0', backend)
        fullscreen = helper.split("move_task_fullscreen()", 1)[1].split(
            '[ "$(id -u 2>/dev/null)" = 0 ]', 1
        )[0]
        self.assertIn('--windowingMode 1 --task "$wanted_task"', fullscreen)
        self.assertIn('wait_state "$pkg" "$wanted_task" 1 any', fullscreen)
        self.assertNotIn("am task resize ", fullscreen)

    def test_known_navigation_task_is_verified_and_corroborated_before_repair(self):
        backend = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java")
        present = backend.split("@Override public void present", 1)[1].split(
            "@Override public void verify", 1
        )[0]
        self.assertLess(present.index('helper.run("verify-native"'),
                        present.index('helper.run("present-native"'))
        self.assertIn("retryKnownTaskMiss", present)
        self.assertIn("TASK_OBSERVATION_UNCERTAIN", present)
        self.assertIn("return verified", present)
        self.assertIn("dumpsys activity recents", backend)
        self.assertIn("pidof", backend)

    def test_parked_known_task_is_focused_before_reporting_windowed(self):
        controller = self.read("launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java")
        helper = self.read("launcher/src/main/assets/nav/nav-window.sh")
        resume = helper.split("  resume-windowed)", 1)[1].split("  fullscreen)", 1)[0]
        self.assertLess(resume.index('verify_state 5 "$expected"'),
                        resume.index('am task focus "$hint"'))
        self.assertIn('require_task "$home_pkg" "$home_task"', resume)
        self.assertIn('require_handoff_foreground "$home_task" "$hint"', resume)
        self.assertIn('require_foreground_task "$hint" NATIVE_NOT_FOREGROUND', resume)
        self.assertIn('emit_protocol OK RESUMED_NATIVE', resume)
        self.assertIn('backend.resume(pkg, target, taskId, activity.getPackageName(), activity.getTaskId()', controller)
        self.assertIn('startPresent(pkg, component, currentTarget(target), taskId)', controller)


if __name__ == "__main__":
    unittest.main()
