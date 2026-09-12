from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "launcher" / "src" / "main"
JAVA = LAUNCHER / "java" / "com" / "cbkii" / "ts18launcher"
HELPER = LAUNCHER / "assets" / "nav" / "nav-window.sh"


class NativeNavigationWindowContractTest(unittest.TestCase):
    def test_real_navigation_replaces_leaflet_as_default_home_surface(self):
        source = (JAVA / "LauncherActivity.java").read_text()
        self.assertIn("new NativeNavigationPanel(this)", source)
        self.assertIn("new NavigationWindowController(this, nativeNavigationPanel)", source)
        self.assertIn("boolean experimentalLeaflet = ExperimentalMapPolicy.enabled(this);", source)
        self.assertIn("navigationWindowController.onHomeVisible()", source)
        self.assertIn("mapPanel.resumeWebView()", source)

    def test_controller_uses_public_launch_bounds_then_exact_task_backend(self):
        source = (JAVA / "NavigationWindowController.java").read_text()
        self.assertIn("ActivityOptions.makeBasic()", source)
        self.assertIn("options.setLaunchBounds(target.asRect())", source)
        self.assertIn("backend.showWindowed(pkg, target, taskHint", source)
        self.assertIn("activeTaskId = result.taskId", source)
        self.assertIn("backend.verify(pkg, target, activeTaskId", source)
        self.assertIn("pkg.equals(activePackage)", source)
        self.assertNotIn("FLAG_ACTIVITY_MULTIPLE_TASK", source)

    def test_switching_navigator_foregrounds_home_before_new_task_acquisition(self):
        source = (JAVA / "NavigationWindowController.java").read_text()
        self.assertIn("switchPackage(pkg, target)", source)
        self.assertIn("backend.focus(activity.getPackageName(), -1", source)
        self.assertIn("activeTaskId = -1", source)
        self.assertIn("adoptPackageAndLaunch", source)

    def test_root_helper_is_narrow_and_does_not_write_topway_properties(self):
        helper = HELPER.read_text()
        self.assertIn("am task resizeable", helper)
        self.assertIn("am task resize", helper)
        self.assertIn("--windowingMode 1 --task", helper)
        self.assertIn("getprop persist.tw.forcepip", helper)
        self.assertIn("getprop sys.tw.forcepip", helper)
        self.assertNotIn("setprop", helper)
        self.assertNotIn("force-stop", helper)
        self.assertNotIn("chmod 777", helper)
        self.assertNotIn("setenforce", helper)

    def test_helper_binds_task_selection_to_configured_package_and_task(self):
        helper = HELPER.read_text()
        self.assertIn('index($0, " A=" pkg " ")', helper)
        self.assertIn('task_hint != "0" && task != task_hint', helper)
        self.assertIn("TASK_REPLACED", helper)
        self.assertIn("BOUNDS_MISMATCH", helper)
        self.assertIn("top_component_for_task", helper)
        self.assertNotIn("mResumedActivity", helper)
        self.assertNotIn("mFocusedActivity", helper)

    def test_root_helper_install_is_systemless_private_and_bounded(self):
        source = (JAVA / "NavigationRootHelper.java").read_text()
        self.assertIn('/data/adb/ts18-launcher', source)
        self.assertIn("umask 077", source)
        self.assertIn("chmod 0700", source)
        self.assertIn("COMMAND_TIMEOUT_MS", source)
        self.assertIn('new ProcessBuilder("su", "-c", command)', source)
        self.assertIn('return new ProcessResult(-1, "", true)', source)


if __name__ == "__main__":
    unittest.main()
