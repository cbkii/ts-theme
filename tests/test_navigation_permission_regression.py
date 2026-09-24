import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ROOT_HELPER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationRootHelper.java"
CONTROLLER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java"
SHELL_HELPER = ROOT / "launcher/src/main/assets/nav/nav-window.sh"


class NavigationPermissionRegressionTests(unittest.TestCase):
    def test_warm_suspend_focuses_home_without_restarting_navigation(self):
        helper = ROOT_HELPER.read_text(encoding="utf-8")
        shell = SHELL_HELPER.read_text(encoding="utf-8")
        body = shell.split("  park-windowed)", 1)[1].split("  *) fail BAD_ACTION", 1)[0]

        self.assertIn("am task focus", body)
        self.assertNotIn("am start", body)
        self.assertNotIn("pm grant", body)
        self.assertNotIn("settings put", body)
        self.assertIn("SUSPEND_STATE_CHANGED", body)
        self.assertIn('[ "$WINDOWING_MODE" = 5 ]', body)
        self.assertIn('run("park-windowed"', helper)

    def test_home_stop_retains_existing_navigation_task_authority(self):
        controller = CONTROLLER.read_text(encoding="utf-8")
        body = controller.split("void onHomeStopped()", 1)[1].split(
            "void onLauncherOverlayOpened()", 1
        )[0]

        self.assertIn("task authority retained without relaunch", body)
        self.assertNotIn("activeTaskId = -1", body)
        self.assertNotIn("startActivity(", body)
        self.assertNotIn("authorityGeneration++", body)

    def test_cold_acquisition_remains_the_only_direct_navigation_launch(self):
        shell = SHELL_HELPER.read_text(encoding="utf-8")
        cold = shell.split("launch_freeform_once()", 1)[1].split(
            "move_task_fullscreen()", 1
        )[0]

        self.assertEqual(1, cold.count("am start "))
        self.assertIn('--user "$ANDROID_USER" --display 0 --windowingMode 5', cold)
        self.assertIn("-a android.intent.action.MAIN -c android.intent.category.LAUNCHER", cold)

    def test_explicit_mode_changes_remain_separate_activity_transactions(self):
        shell = SHELL_HELPER.read_text(encoding="utf-8")
        fullscreen = shell.split("move_task_fullscreen()", 1)[1].split(
            '[ "$(id -u 2>/dev/null)" = 0 ]', 1
        )[0]
        present = shell.split("  present-native)", 1)[1].split("  verify-native)", 1)[0]

        self.assertIn('am start --user "$ANDROID_USER"', fullscreen)
        self.assertIn('am start --user "$ANDROID_USER"', present)
        self.assertIn("FREEFORM_TRANSITION_FAILED", present)


if __name__ == "__main__":
    unittest.main()
