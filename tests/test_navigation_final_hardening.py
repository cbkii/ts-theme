import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTROLLER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java").read_text(encoding="utf-8")
ROOT_HELPER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationRootHelper.java").read_text(encoding="utf-8")
HELPER = (ROOT / "launcher/src/main/assets/nav/nav-window.sh").read_text(encoding="utf-8")
BACKEND = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java").read_text(encoding="utf-8")


class NavigationFinalHardeningTest(unittest.TestCase):
    def test_drawer_skips_redundant_root_parking_when_home_already_focused(self):
        overlay = CONTROLLER.split("void openLauncherOverlay(Runnable show)", 1)[1].split(
            "void cancelLauncherOverlay()", 1
        )[0]
        self.assertIn("activity.hasWindowFocus()", overlay)
        self.assertIn("home-focus-already-owned", overlay)
        self.assertIn("needsValidation = true", overlay)
        self.assertIn('suspendForLauncherSurface("launcher overlay")', overlay)

    def test_parking_uses_guarded_helper_instead_of_unconditional_root_focus(self):
        park = ROOT_HELPER.split("parkWindowedTask", 1)[1].split(
            "private NavigationHelperResult ensureInstalled", 1
        )[0]
        self.assertIn('run("park-windowed"', park)
        self.assertNotIn("homeFocusCommand", ROOT_HELPER)
        self.assertIn("require_handoff_foreground", HELPER)
        self.assertIn("FOREGROUND_CHANGED", HELPER)

    def test_routine_parking_does_not_require_same_top_activity_component(self):
        park = HELPER.split("  park-windowed)", 1)[1].split("  *) fail BAD_ACTION", 1)[0]
        self.assertNotIn('TASK_COMPONENT" = "$navigation_component', park)
        self.assertIn("same package/task may legitimately change top Activity", park)

    def test_fullscreen_does_not_reapply_non_null_task_bounds(self):
        fullscreen = HELPER.split("move_task_fullscreen()", 1)[1].split(
            '[ "$(id -u 2>/dev/null)" = 0 ]', 1
        )[0]
        self.assertIn("--windowingMode 1", fullscreen)
        self.assertNotIn("am task resize", fullscreen)
        self.assertNotIn("fullscreenResizeCommand", BACKEND)


if __name__ == "__main__":
    unittest.main()
