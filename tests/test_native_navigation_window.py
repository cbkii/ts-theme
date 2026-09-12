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

    def test_controller_uses_public_launch_bounds_then_exact_package_task_backend(self):
        source = (JAVA / "NavigationWindowController.java").read_text()
        self.assertIn("ActivityOptions.makeBasic()", source)
        self.assertIn("options.setLaunchBounds(target.asRect())", source)
        self.assertIn("backend.showWindowed(pkg, target", source)
        self.assertIn("pkg.equals(activePackage)", source)
        self.assertNotIn("FLAG_ACTIVITY_MULTIPLE_TASK", source)

    def test_root_helper_is_narrow_and_does_not_write_topway_properties(self):
        helper = HELPER.read_text()
        self.assertIn("am task resizeable", helper)
        self.assertIn("am task resize", helper)
        self.assertIn("am stack resize-animated", helper)
        self.assertIn("getprop persist.tw.forcepip", helper)
        self.assertIn("getprop sys.tw.forcepip", helper)
        self.assertNotIn("setprop", helper)
        self.assertNotIn("force-stop", helper)
        self.assertNotIn("chmod 777", helper)
        self.assertNotIn("setenforce", helper)

    def test_helper_binds_task_selection_to_configured_package(self):
        helper = HELPER.read_text()
        self.assertIn('index($0, " A=" pkg " ")', helper)
        self.assertIn("TASK_REPLACED", helper)
        self.assertIn("BOUNDS_MISMATCH", helper)
        self.assertNotIn("mResumedActivity", helper)
        self.assertNotIn("mFocusedActivity", helper)

    def test_root_helper_install_is_systemless_private_and_bounded(self):
        source = (JAVA / "NavigationRootHelper.java").read_text()
        self.assertIn('/data/adb/ts18-launcher', source)
        self.assertIn("umask 077", source)
        self.assertIn("chmod 0700", source)
        self.assertIn("COMMAND_TIMEOUT_MS", source)
        self.assertIn('new ProcessBuilder("su", "-c", command)', source)


if __name__ == "__main__":
    unittest.main()
