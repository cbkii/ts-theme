import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


class StandaloneLauncherContractTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_launcher_is_separate_api29_java_module(self):
        settings = self.read("settings.gradle.kts")
        gradle = self.read("launcher/build.gradle.kts")
        self.assertIn('include(":launcher")', settings)
        self.assertIn('applicationId = "com.cbkii.ts18launcher"', gradle)
        self.assertIn("minSdk = 29", gradle)
        self.assertIn("targetSdk = 29", gradle)
        self.assertIn("compileSdk = 29", gradle)
        for forbidden in ("androidx.", "compose", "appcompat", "material:", "room",
                          "datastore", "rxjava", "dagger", "replugin"):
            self.assertNotIn(forbidden, gradle.lower())

    def test_home_alias_is_opt_in_and_dofun_is_not_impersonated(self):
        manifest_path = ROOT / "launcher/src/main/AndroidManifest.xml"
        root = ET.parse(manifest_path).getroot()
        application = root.find("application")
        alias = next(item for item in application.findall("activity-alias")
                     if item.attrib.get(ANDROID_NS + "name") == ".HomeAlias")
        self.assertEqual("false", alias.attrib.get(ANDROID_NS + "enabled"))
        self.assertEqual(".LauncherActivity", alias.attrib.get(ANDROID_NS + "targetActivity"))
        manifest = manifest_path.read_text(encoding="utf-8")
        self.assertNotIn("android.uid.system", manifest)
        self.assertNotIn("QUERY_ALL_PACKAGES", manifest)

    def test_launcher_runtime_stays_small_and_single_authority(self):
        source_root = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"
        java = "\n".join(path.read_text(encoding="utf-8") for path in sorted(source_root.rglob("*.java")))
        media = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java")
        for forbidden in ("new MediaSession(", "requestAudioFocus", "WakeLock", "startForeground(", "com.qihoo360"):
            self.assertNotIn(forbidden, java)
        self.assertIn("MediaSessionManager", media)
        self.assertIn("requestRebind(", media)
        self.assertIn("PlaybackState.ACTION_PLAY_PAUSE", media)
        self.assertIn("publishEmpty();", media)

    def test_release_runtime_dependency_and_apk_gates_are_real(self):
        workflow = self.read(".github/workflows/validate.yml")
        checker = self.read("tools/launcher_apk_check.py")
        self.assertIn("--configuration releaseRuntimeClasspath", workflow)
        self.assertIn("No dependencies", workflow)
        self.assertIn("launcher_apk_check.py", workflow)
        self.assertIn("apksigner", workflow)
        self.assertIn('dex_files != ["classes.dex"]', checker)
        self.assertIn('name.startswith("lib/")', checker)
        for asset in ("assets/map/vendor/leaflet.js", "assets/map/vendor/leaflet.css",
                      "assets/map/vendor/LEAFLET-LICENSE.txt"):
            self.assertIn(asset, checker)

    def test_leaflet_map_is_local_feature_rich_and_tile_networking_is_native(self):
        panel = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MapPanel.java")
        broker = self.read("launcher/src/main/java/com/cbkii/ts18launcher/TileBroker.java")
        html = self.read("launcher/src/main/assets/map/map.html")
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        self.assertIn('vendor/leaflet.js', html)
        self.assertIn('L.map("map"', html)
        self.assertIn('L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png"', html)
        for marker in ("touchZoom:true", "doubleClickZoom:true", "inertia:true", "L.circle",
                       "vehicle-bearing", "setLocation", "map.panTo", "setMapAppearance"):
            self.assertIn(marker, html)
        self.assertIn('follow?"follow":"free"', html)
        self.assertIn("TileBroker.isTileUri", panel)
        self.assertIn("tileBroker.intercept(uri)", panel)
        self.assertIn("mapControlsEnabled", panel)
        self.assertIn("AppearanceController.resolvedMode", panel)
        self.assertIn('"Map offline"', panel)
        self.assertIn('TILE_HOST = "tile.openstreetmap.org"', broker)
        self.assertIn("MAX_CACHE_BYTES = 64L * 1024L * 1024L", broker)
        self.assertIn("KEY_APPEARANCE_MODE", prefs)
        self.assertIn("LocationManager.GPS_PROVIDER", panel)
        self.assertNotIn("LocationManager.NETWORK_PROVIDER", panel)
        self.assertNotIn("addJavascriptInterface", panel)
        self.assertNotIn("setAllowUniversalAccessFromFileURLs(true)", panel)
        self.assertNotIn("http://", panel + broker + html)

    def test_home_has_fixed_endpoints_configurable_role_middle_drawer_and_navigation_handoff(self):
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        roles = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RoleIconCatalog.java")
        drawer = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerPanel.java")
        nav = self.read("launcher/src/main/java/com/cbkii/ts18launcher/NavigationProvider.java")
        self.assertIn("KEY_QUICK_6", prefs)
        self.assertIn("QUICK_ROLE_KEYS", prefs)
        self.assertIn("Math.max(3, Math.min(6, value))", prefs)
        self.assertIn("MAX_QUICK_SLOTS = 6", launcher)
        self.assertLess(launcher.index("rail.addView(appsButton"), launcher.index("rail.addView(quickRail"))
        self.assertLess(launcher.index("rail.addView(quickRail"), launcher.index("rail.addView(navigationButton"))
        self.assertIn("RoleIconCatalog.icon(role)", launcher)
        self.assertIn("PHONE", roles)
        self.assertIn("UTILITY", roles)
        self.assertIn("GENERIC", roles)
        self.assertIn("VoiceSearch.available(activity)", drawer)
        self.assertIn("DRAWER_QUICK_KEYS", drawer)
        self.assertIn("GridView", drawer)
        self.assertIn("NavigationProvider.open", launcher)
        for provider in ("com.google.android.apps.maps", "com.waze", "app.organicmaps", "net.osmand.plus"):
            self.assertIn(provider, nav)

    def test_automotive_ui_uses_semantic_dimensions_focus_and_limited_palette(self):
        ui = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AutomotiveUi.java")
        geometry = self.read("launcher/src/main/java/com/cbkii/ts18launcher/Ts18Geometry.java")
        dimens = self.read("launcher/src/main/res/values/dimens.xml")
        colors = self.read("launcher/src/main/res/values/colors.xml")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        self.assertIn("HOTSEAT_WIDTH = 96", geometry)
        self.assertIn("STRIP_HEIGHT = 88", geometry)
        self.assertIn("GRID_COLUMNS = 12", geometry)
        for token in ("driver_target_min", "driver_target_primary", "driver_icon_primary",
                      "driver_icon_secondary", "driver_gap", "driver_section_gap", "driver_radius"):
            self.assertIn(token, dimens)
        self.assertIn("#FF7043", colors)
        self.assertIn("ui_high_text", colors)
        self.assertIn("FEEDBACK_MS = 140L", ui)
        self.assertIn("linkVertical", ui)
        self.assertIn("linkHorizontal", ui)
        self.assertIn("primaryTransportBackground", ui)
        self.assertIn("SlowMarqueeTextView", launcher)
        self.assertIn("dateView.setOnClickListener(v -> openSettings())", launcher)

    def test_settings_cover_appearance_schedule_roles_switches_and_advanced_home(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        picker = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerActivity.java")
        self.assertIn("new Switch(this)", settings)
        self.assertIn('addSection("Appearance")', settings)
        self.assertIn('addSection("Advanced HOME / recovery")', settings)
        self.assertIn('"Map controls"', settings)
        self.assertIn('"Display appearance"', settings)
        self.assertIn('"Auto appearance source"', settings)
        self.assertIn('"Middle quick slots"', settings)
        self.assertIn("TimePickerDialog", settings)
        self.assertIn("RoleIconCatalog.LABELS", settings)
        self.assertIn("setSingleChoiceItems", settings)
        self.assertIn('setHint("Search apps")', picker)
        self.assertIn("R.drawable.ic_mic", picker)
        self.assertNotIn("androidx.preference", settings)

    def test_media_selection_is_capability_aware_and_radio_separate(self):
        media = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        radio = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RadioProvider.java")
        self.assertIn("pickExactPackage(controllers, preferredPackage)", media)
        self.assertIn("RadioProvider.resolvePackage(this)", media)
        self.assertIn("sessionDiagnostics", media)
        self.assertIn("MEDIA_REFRESH_INTERVAL_MS = 1000L", launcher)
        self.assertIn('NAVRADIO_PLUS_PACKAGE = "com.navimods.radio"', radio)
        self.assertNotIn("sendBroadcast", radio)

    def test_topway_adapter_does_not_guess_stock_radio_contract(self):
        topway = self.read("launcher/src/main/java/com/cbkii/ts18launcher/platform/TopwayAdapter.java")
        self.assertIn('STOCK_MUSIC_PACKAGE = "com.tw.music"', topway)
        self.assertNotIn("STOCK_RADIO_PACKAGE", topway)
        self.assertNotIn("sendBroadcast", topway)
        self.assertNotIn("bindService", topway)

    def test_root_policy_is_bounded_reversible_and_prevalidated(self):
        installer = self.read("scripts/termux/install-standalone-launcher.sh")
        for required in ("timeout -k 2", "--rollback-home", "previous-home.txt",
                         "capture_rollback_home_for_change", "aapt/aapt2 is required",
                         "apksigner is required", "SHA256SUMS.txt"):
            self.assertIn(required, installer)
        for forbidden in ("pm uninstall com.dofun.variety", "setenforce 0",
                          "mount -o rw,remount /system", "rm -rf /data/user/0/com.dofun.variety"):
            self.assertNotIn(forbidden, installer)

    def test_candidate_workflow_keeps_write_permission_in_publish_job(self):
        workflow = self.read(".github/workflows/launcher-candidate.yml")
        self.assertRegex(workflow, r"permissions:\n  contents: read")
        self.assertRegex(workflow, r"publish:[\s\S]*?permissions:\n      contents: write")
        self.assertIn("STANDALONE_LAUNCHER.md", workflow)
        self.assertIn("GH_REPO: ${{ github.repository }}", workflow)
        for match in re.finditer(r"uses:\s+([^\s]+)", workflow):
            self.assertRegex(match.group(1), r"@(?:[0-9a-f]{40})$")


if __name__ == "__main__":
    unittest.main()
