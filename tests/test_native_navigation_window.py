from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
LAUNCHER = ROOT / "launcher" / "src" / "main"
JAVA = LAUNCHER / "java" / "com" / "cbkii" / "ts18launcher"
PLATFORM = JAVA / "platform"
HELPER = LAUNCHER / "assets" / "nav" / "nav-window.sh"
MANIFEST = LAUNCHER / "AndroidManifest.xml"
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


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

    def test_exact_topway_home_discovery_surface_is_exposed_without_guessed_binder(self):
        root = ET.parse(MANIFEST).getroot()
        application = root.find("application")
        self.assertIsNotNone(application)
        marker = next(
            service for service in application.findall("service")
            if service.attrib.get(ANDROID_NS + "name") == ".platform.TopwayDesktopWindowMarkerService"
        )
        self.assertEqual("true", marker.attrib.get(ANDROID_NS + "exported"))
        actions = {
            action.attrib.get(ANDROID_NS + "name")
            for intent_filter in marker.findall("intent-filter")
            for action in intent_filter.findall("action")
        }
        self.assertIn("cn.cardoor.desktop.window.DESKTOP_WINDOW_SERVICE", actions)

        provider = next(
            item for item in application.findall("provider")
            if item.attrib.get(ANDROID_NS + "name") == ".platform.TopwayDesktopWindowProvider"
        )
        self.assertEqual("com.cbkii.ts18launcher.ExportedProvider",
                         provider.attrib.get(ANDROID_NS + "authorities"))
        self.assertEqual("true", provider.attrib.get(ANDROID_NS + "exported"))

        marker_source = (PLATFORM / "TopwayDesktopWindowMarkerService.java").read_text()
        self.assertIn("return null;", marker_source)
        self.assertNotIn("new Binder(", marker_source)
        self.assertNotIn("transact(", marker_source)

    def test_topway_provider_matches_exact_video_keys_and_publishes_safe_inactive_state(self):
        contract = (PLATFORM / "TopwayDesktopWindowContract.java").read_text()
        provider = (PLATFORM / "TopwayDesktopWindowProvider.java").read_text()
        self.assertIn('MARKER_ACTION = "cn.cardoor.desktop.window.DESKTOP_WINDOW_SERVICE"', contract)
        self.assertIn('PROVIDER_AUTHORITY = "com.cbkii.ts18launcher.ExportedProvider"', contract)
        self.assertIn('KEY_DESKTOP_WINDOW_SETTING = "desktop_window_setting"', contract)
        self.assertIn('KEY_THEME_DESKTOP_WINDOW = "isCurrentUsedThemeInstanceDesktopWindow"', contract)
        self.assertIn("new MatrixCursor(new String[] {VALUE_COLUMN})", provider)
        self.assertIn('cursor.addRow(new Object[] {"false"})', provider)
        self.assertIn("zero rows => no WindowInfo", provider)
        self.assertIn("UnsupportedOperationException", provider)
        # Do not publish a guessed DoFun WindowInfo/windowName merely to make the client non-null.
        self.assertNotIn('"appType"', provider)
        self.assertNotIn('"windowName"', provider)

    def test_cooperative_topway_binder_is_not_used_as_generic_navigation_transport(self):
        source = (JAVA / "NavigationWindowController.java").read_text()
        docs = (ROOT / "docs" / "NATIVE_NAVIGATION_WINDOW.md").read_text()
        self.assertNotIn("FLOATING_WINDOW_SERVER", source)
        self.assertIn("FLOATING_WINDOW_SERVER", docs)
        self.assertIn("Organic Maps, Google Maps, OsmAnd+ and Sygic", docs)
        self.assertIn("user-observed", docs)

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
