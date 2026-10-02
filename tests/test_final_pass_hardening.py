import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAWER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerPanel.java").read_text(encoding="utf-8")
LAUNCHER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java").read_text(encoding="utf-8")
ADAPTER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceAdapter.java").read_text(encoding="utf-8")
PROCESS_MONITOR = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/ProcessMediaSessionMonitor.java").read_text(encoding="utf-8")
AGENTS = (ROOT / "AGENTS.md").read_text(encoding="utf-8")


class FinalPassHardeningTest(unittest.TestCase):
    def test_foreground_startup_bootstrap_is_removed(self):
        self.assertFalse((ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/StartupBootstrapCoordinator.java").exists())
        self.assertFalse((ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/StartupMaskController.java").exists())
        self.assertNotIn("launchMediaBootstrap", LAUNCHER)
        self.assertNotIn("cold-foreground-prime", LAUNCHER)
        self.assertIn("mediaBootstrapper.warmConfiguredSources()", LAUNCHER)

    def test_process_media_session_monitor_is_primary_cold_boot_observer(self):
        self.assertIn("ProcessMediaSessionMonitor.Observer", LAUNCHER)
        self.assertIn("ProcessMediaSessionMonitor.addObserver(this, this)", LAUNCHER)
        self.assertIn("onProcessMediaStateChanged", LAUNCHER)
        self.assertIn("addOnActiveSessionsChangedListener", PROCESS_MONITOR)
        self.assertIn("getActiveSessions(listenerComponent)", PROCESS_MONITOR)
        self.assertIn("listener service receiving onListenerConnected", PROCESS_MONITOR)

    def test_geometry_application_is_idempotent(self):
        place = LAUNCHER.split("private void place(View view", 1)[1].split(
            "private void placeCard", 1
        )[0]
        self.assertIn("view.getLayoutParams()", place)
        self.assertIn("current.width == desiredWidth", place)
        self.assertIn("current.height == desiredHeight", place)
        self.assertIn("current.leftMargin == desiredLeft", place)
        self.assertIn("current.topMargin == desiredTop", place)
        self.assertLess(place.index("return;"), place.index("view.setLayoutParams(lp)"))

    def test_auxio_root_startservice_route_stays_removed(self):
        browser = ADAPTER.split(
            "ComponentName browser = findExportedService(context, packageName, MEDIA_BROWSER_ACTION);", 1
        )[1].split("return sessionOnly(packageName", 1)[0]
        self.assertIn("known-ineffective mutation", browser)
        self.assertIn(
            "new MediaSourceAdapter(packageName, Kind.MEDIA_BROWSER, browser,\n"
            "                    MEDIA_BROWSER_ACTION, false, false, true, \"\")",
            browser,
        )
        self.assertNotIn("MEDIA_BROWSER_ACTION, true, false, true", browser)
        self.assertIn("must not retry the rejected Auxio root `startservice` route", AGENTS)
        self.assertNotIn("Auxio-TS may use bounded Magisk-root service priming", AGENTS)

    def test_drawer_quick_row_is_rebuilt_only_when_its_configuration_changes(self):
        self.assertIn('private String quickSignature = "";', DRAWER)
        self.assertIn("String signature = quickPreferenceSignature();", DRAWER)
        self.assertIn("if (signature.equals(quickSignature)) return;", DRAWER)
        self.assertIn("LauncherPrefs.drawerQuickRole(activity, i)", DRAWER)
        self.assertIn("LauncherPrefs.packageFor(activity, key)", DRAWER)
        self.assertIn("ShortcutSlot.iconAppearance(activity, key)", DRAWER)
        self.assertIn('quickSignature = "";', DRAWER)

    def test_drawer_grid_long_press_opens_android_app_info(self):
        self.assertIn("grid.setOnItemLongClickListener", DRAWER)
        self.assertIn("android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS", DRAWER)
        self.assertIn('android.net.Uri.parse("package:" + entry.packageName)', DRAWER)
        self.assertIn("openAppInfo(visibleEntries.get(position))", DRAWER)
        self.assertIn("button.setOnLongClickListener(v -> { openPicker(", DRAWER)


if __name__ == "__main__":
    unittest.main()
