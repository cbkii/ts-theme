import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ROOT_HELPER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationRootHelper.java"
CONTROLLER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java"
SHELL_HELPER = ROOT / "launcher/src/main/assets/nav/nav-window.sh"


class NavigationPermissionRegressionTests(unittest.TestCase):
    def test_warm_suspend_uses_guarded_home_focus_without_restarting_navigation(self):
        helper = ROOT_HELPER.read_text(encoding="utf-8")
        park = helper.split("NavigationHelperResult parkWindowedTask", 1)[1].split(
            "private NavigationHelperResult ensureInstalled", 1
        )[0]
        shell = SHELL_HELPER.read_text(encoding="utf-8")
        shell_park = shell.split("  park-windowed)", 1)[1].split("  *) fail BAD_ACTION", 1)[0]

        self.assertIn('run("park-windowed", packageName, Integer.toString(taskId)', park)
        self.assertIn("require_handoff_foreground", shell_park)
        self.assertIn('am task focus "$home_task"', shell_park)
        self.assertIn("SUSPEND_STATE_CHANGED", shell_park)
        self.assertIn('[ "$WINDOWING_MODE" = 5 ]', shell_park)
        self.assertIn('[ "$TASK_BOUNDS" = "$navigation_bounds" ]', shell_park)
        self.assertNotIn("navigation_component", shell_park)
        self.assertNotIn("am start", shell_park)
        self.assertNotIn("pm grant", shell_park)
        self.assertNotIn("settings put", shell_park)

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

    def test_root_navigation_processes_are_timeout_wrapped_and_force_terminated(self):
        helper = ROOT_HELPER.read_text(encoding="utf-8")
        self.assertIn("/system/bin/toybox timeout -k 1", helper)
        self.assertIn("terminate(process)", helper)
        self.assertIn("process.destroyForcibly()", helper)


if __name__ == "__main__":
    unittest.main()
