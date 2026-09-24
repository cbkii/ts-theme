import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class Pr11ReconciliationTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_launcher_keeps_cold_bootstrap_drawer_preload_and_direct_settings_access(self):
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        self.assertIn("STARTUP_BOOTSTRAP_CLAIMED", launcher)
        self.assertIn("compareAndSet(false, true)", launcher)
        self.assertIn("new StartupBootstrapCoordinator", launcher)
        self.assertIn("appDrawerPanel.preload()", launcher)
        self.assertIn("appsButton.setOnLongClickListener", launcher)
        self.assertIn("new Intent(this, SettingsActivity.class)", launcher)

    def test_settings_keep_startup_overlay_and_non_blocking_navigation_warning(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        compatibility = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/NavigationCompatibilityPolicy.java")
        self.assertIn("StartupMaskController.hasOverlayAccess(this)", settings)
        self.assertIn("Settings.ACTION_MANAGE_OVERLAY_PERMISSION", settings)
        self.assertIn("NavigationCompatibilityPolicy.settingsMessage", settings)
        self.assertIn("UNQUALIFIED", compatibility)
        self.assertNotIn("throw", compatibility)

    def test_drawer_is_visible_before_navigation_suspension_finishes(self):
        controller = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java")
        method = controller.split("void openLauncherOverlay(Runnable show)", 1)[1].split(
            "void cancelLauncherOverlay()", 1)[0]
        self.assertLess(method.index("show.run()"), method.index("suspendForLauncherSurface"))
        self.assertIn("Physical visibility and touch are not inferred", controller)

    def test_fullscreen_uses_same_task_resize_without_unproven_resizeable_command(self):
        backend = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java")
        self.assertIn('"am task resize " + taskId + " 0 0', backend)
        self.assertNotIn("am task resizeable", backend)
        self.assertIn("FULLSCREEN_BOUNDS_STALE", backend)


if __name__ == "__main__":
    unittest.main()
