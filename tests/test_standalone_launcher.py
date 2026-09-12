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
        for forbidden in (
            "androidx.", "compose", "appcompat", "material:", "room",
            "datastore", "rxjava", "dagger", "replugin",
        ):
            self.assertNotIn(forbidden, gradle.lower())

    def test_home_alias_is_opt_in_and_dofun_is_not_impersonated(self):
        manifest_path = ROOT / "launcher/src/main/AndroidManifest.xml"
        root = ET.parse(manifest_path).getroot()
        application = root.find("application")
        self.assertIsNotNone(application)
        aliases = application.findall("activity-alias")
        alias = next(item for item in aliases if item.attrib.get(ANDROID_NS + "name") == ".HomeAlias")
        self.assertEqual("false", alias.attrib.get(ANDROID_NS + "enabled"))
        self.assertEqual("true", alias.attrib.get(ANDROID_NS + "exported"))
        self.assertEqual(".LauncherActivity", alias.attrib.get(ANDROID_NS + "targetActivity"))
        manifest = manifest_path.read_text(encoding="utf-8")
        self.assertNotIn("launcher.variety.theme.plugin.sfp_cbk_black", manifest)
        self.assertNotIn("android.uid.system", manifest)
        self.assertNotIn("QUERY_ALL_PACKAGES", manifest)
        self.assertNotIn("extractNativeLibs", manifest)

    def test_launcher_runtime_stays_small_and_single_authority(self):
        source_root = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"
        java = "\n".join(path.read_text(encoding="utf-8") for path in sorted(source_root.rglob("*.java")))
        media = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java")
        bootstrap = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        self.assertNotIn("new MediaSession(", java)
        self.assertNotIn("requestAudioFocus", java)
        self.assertNotIn("WakeLock", java)
        self.assertNotIn("startForeground(", java)
        self.assertNotIn("com.qihoo360", java)
        self.assertIn("MediaSessionManager", media)
        self.assertIn("requestRebind(", media)
        self.assertIn("PlaybackState.ACTION_SKIP_TO_PREVIOUS", media)
        self.assertIn("PlaybackState.ACTION_SKIP_TO_NEXT", media)
        self.assertIn("PlaybackState.ACTION_PLAY_PAUSE", media)
        self.assertIn("publishEmpty();", media)
        self.assertIn("MediaBrowser", bootstrap)
        self.assertNotIn("new MediaSession(", bootstrap)
        self.assertNotIn("requestAudioFocus", bootstrap)

    def test_release_runtime_dependency_and_apk_gates_are_real(self):
        workflow = self.read(".github/workflows/validate.yml")
        checker = self.read("tools/launcher_apk_check.py")
        self.assertIn("--configuration releaseRuntimeClasspath", workflow)
        self.assertIn("No dependencies", workflow)
        self.assertIn("launcher_apk_check.py", workflow)
        self.assertIn("apksigner", workflow)
        for marker in ("Lkotlin/", "Lkotlinx/", "Landroidx/", "Lcom/qihoo360/"):
            self.assertIn(marker, checker)
        self.assertIn('dex_files != ["classes.dex"]', checker)
        self.assertIn('name.startswith("lib/")', checker)
        self.assertIn('"assets/map/vendor/leaflet.js"', checker)
        self.assertIn('"assets/map/vendor/leaflet.css"', checker)
        self.assertIn('"assets/map/vendor/LEAFLET-LICENSE.txt"', checker)
        self.assertIn('"assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt"', checker)

    def test_leaflet_map_is_local_feature_rich_and_tile_networking_is_native(self):
        gradle = self.read("launcher/build.gradle.kts")
        fetcher = self.read("tools/fetch_leaflet.py")
        panel = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MapPanel.java")
        broker = self.read("launcher/src/main/java/com/cbkii/ts18launcher/TileBroker.java")
        html = self.read("launcher/src/main/assets/map/map.html")
        policy = self.read("launcher/src/main/java/com/cbkii/ts18launcher/ExperimentalMapPolicy.java")
        self.assertIn("fetchLeafletAssets", gradle)
        self.assertIn('VERSION = "1.9.4"', fetcher)
        self.assertIn("db49d009c841f5ca34a888c96511ae936fd9f5533e90d8b2c4d57596f4e5641a", fetcher)
        self.assertIn("a7837102824184820dfa198d1ebcd109ff6d0ff9a2672a074b9a1b4d147d04c6", fetcher)
        self.assertIn('vendor/leaflet.js', html)
        self.assertIn('vendor/leaflet.css', html)
        self.assertIn('L.map("map"', html)
        self.assertIn('L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png"', html)
        self.assertIn("touchZoom:true", html)
        self.assertIn("doubleClickZoom:true", html)
        self.assertIn("inertia:true", html)
        self.assertIn("L.circle", html)
        self.assertIn("vehicle-bearing", html)
        self.assertIn("setLocation", html)
        self.assertIn("map.panTo", html)
        self.assertIn("setMapAppearance", html)
        self.assertIn('follow?"follow":"free"', html)
        self.assertIn("TileBroker.isTileUri", panel)
        self.assertIn("tileBroker.intercept(uri)", panel)
        self.assertIn("mapControlsEnabled", panel)
        self.assertIn("AppearanceController.resolvedMode", panel)
        self.assertIn('"Map offline"', panel)
        self.assertIn("boolean controlsRight = !railRight", panel)
        self.assertNotIn("openNavigation", panel)
        self.assertIn('TILE_HOST = "tile.openstreetmap.org"', broker)
        self.assertIn("FALLBACK_CACHE_TTL_MS", broker)
        self.assertIn("MAX_CACHE_BYTES = 64L * 1024L * 1024L", broker)
        self.assertIn('setRequestProperty("User-Agent", userAgent)', broker)
        self.assertIn('setRequestProperty("If-None-Match"', broker)
        self.assertIn('setRequestProperty("If-Modified-Since"', broker)
        self.assertIn("HttpURLConnection.HTTP_NOT_MODIFIED", broker)
        self.assertIn("staleOrError", broker)
        self.assertIn("LocationManager.GPS_PROVIDER", panel)
        self.assertNotIn("LocationManager.NETWORK_PROVIDER", panel)
        self.assertIn("removeUpdates(this)", panel)
        self.assertNotIn("addJavascriptInterface", panel)
        self.assertNotIn("setAllowUniversalAccessFromFileURLs(true)", panel)
        self.assertNotIn("http://", panel + broker + html)
        self.assertIn("if (!LauncherPrefs.prefs(context).contains(LauncherPrefs.KEY_MAP_ENABLED)) return false", policy)
        self.assertIn("return LauncherPrefs.mapEnabled(context)", policy)

    def test_home_has_fixed_endpoints_configurable_middle_and_navigation_handoff(self):
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        roles = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RoleIconCatalog.java")
        drawer = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerPanel.java")
        nav = self.read("launcher/src/main/java/com/cbkii/ts18launcher/NavigationProvider.java")
        self.assertIn("KEY_QUICK_1", prefs)
        self.assertIn("KEY_QUICK_6", prefs)
        self.assertIn("KEY_QUICK_COUNT", prefs)
        self.assertIn("KEY_RAIL_POSITION", prefs)
        self.assertIn("KEY_DRAWER_QUICK_5", prefs)
        self.assertIn("QUICK_ROLE_KEYS", prefs)
        self.assertIn("Math.max(3, Math.min(6, value))", prefs)
        self.assertIn("MAX_QUICK_SLOTS = 6", launcher)
        self.assertIn("ImageButton[] quickButtons", launcher)
        self.assertLess(launcher.index("rail.addView(appsButton"), launcher.index("rail.addView(quickRail"))
        self.assertLess(launcher.index("rail.addView(quickRail"), launcher.index("rail.addView(navigationButton"))
        self.assertIn("case 0: return SETTINGS", roles)
        self.assertIn("styleRailButton(this, navigationButton, true)", launcher)
        self.assertIn("LauncherPrefs.railOnRight(this)", launcher)
        self.assertIn("AppDrawerPanel", launcher)
        self.assertIn("toggleAppDrawer()", launcher)
        self.assertIn("mapPanel.stop()", launcher)
        self.assertIn("mapPanel.resumeWebView()", launcher)
        self.assertIn("GridView", drawer)
        self.assertIn('search.setHint("Search apps")', drawer)
        self.assertIn("VoiceSearch.available(activity)", drawer)
        self.assertIn("DRAWER_QUICK_KEYS", drawer)
        self.assertIn("R.drawable.ic_settings", drawer)
        self.assertIn("R.drawable.ic_close", drawer)
        self.assertIn("NavigationProvider.open", launcher)
        self.assertIn('GOOGLE_MAPS = "com.google.android.apps.maps"', nav)
        self.assertIn('WAZE = "com.waze"', nav)
        self.assertIn('ORGANIC_MAPS = "app.organicmaps"', nav)
        self.assertIn('OSMAND_PLUS = "net.osmand.plus"', nav)
        self.assertIn("geo:", nav)
        self.assertIn("waze://", nav)

    def test_automotive_ui_uses_local_vectors_semantic_dimensions_and_inert_date(self):
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        metadata = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaMetadataView.java")
        ui = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AutomotiveUi.java")
        geometry = self.read("launcher/src/main/java/com/cbkii/ts18launcher/Ts18Geometry.java")
        dimens = self.read("launcher/src/main/res/values/dimens.xml")
        drawables = {path.name for path in (ROOT / "launcher/src/main/res/drawable").glob("*.xml")}
        for required in (
            "ic_navigation.xml", "ic_radio.xml", "ic_music.xml", "ic_bluetooth.xml",
            "ic_apps.xml", "ic_settings.xml", "ic_previous.xml", "ic_play.xml",
            "ic_pause.xml", "ic_next.xml", "ic_zoom_in.xml", "ic_zoom_out.xml",
            "ic_my_location.xml", "ic_close.xml", "ic_search.xml", "ic_mic.xml",
            "ic_phone.xml", "ic_star.xml", "ic_utility.xml", "ic_shortcut.xml",
            "ic_car.xml", "ic_map.xml", "ic_route.xml", "ic_equalizer.xml",
            "ic_podcast.xml", "ic_usb.xml", "ic_folder.xml", "ic_wifi.xml",
            "ic_download.xml", "ic_volume.xml", "ic_power.xml", "ic_dashboard.xml",
            "ic_lightbulb.xml", "ic_notifications.xml", "ic_palette.xml",
        ):
            self.assertIn(required, drawables)
        self.assertIn("HOTSEAT_WIDTH = 96", geometry)
        self.assertIn("STRIP_HEIGHT = 88", geometry)
        self.assertIn("GRID_COLUMNS = 12", geometry)
        for token in (
            "ui_metadata_text", "ui_metadata_secondary_text", "ui_drawer_icon", "ui_settings_row_height",
            "driver_target_min", "driver_target_primary", "driver_gap", "driver_gap_large",
            "ui_play_visual", "ui_icon_button_padding", "ui_corner_radius", "ui_settings_shortcut_editor_height",
        ):
            self.assertIn(token, dimens)
        self.assertIn("STATE_CROSSFADE_MS = 120L", ui)
        self.assertIn("FEEDBACK_MS = 140L", ui)
        self.assertIn("PANEL_REVEAL_MS = 160L", ui)
        self.assertIn("DRAWER_MS = 180L", ui)
        self.assertIn("state_focused", ui)
        self.assertIn("state_pressed", ui)
        self.assertIn("linkVertical", ui)
        self.assertIn("linkHorizontal", ui)
        self.assertIn("primaryTransportBackground", ui)
        self.assertIn("MediaMetadataView", launcher)
        self.assertIn("SlowMarqueeTextView", metadata)
        self.assertIn("setIconWithCrossfade", launcher)
        self.assertIn("dateView.setBackgroundColor(android.graphics.Color.TRANSPARENT)", launcher)
        self.assertIn("dateView.setFocusable(false)", launcher)
        self.assertIn("dateView.setClickable(false)", launcher)
        self.assertNotIn("dateView.setOnClickListener", launcher)

    def test_settings_cover_shortcut_editor_accent_warmup_and_advanced_home(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        picker = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerActivity.java")
        self.assertIn("new Switch(this)", settings)
        self.assertIn('addSection("Appearance")', settings)
        self.assertIn('addSection("Advanced HOME / recovery")', settings)
        self.assertIn('"Map controls"', settings)
        self.assertIn('"Experimental Leaflet map"', settings)
        self.assertIn('"Display appearance"', settings)
        self.assertIn('"Accent hue"', settings)
        self.assertIn('"Auto appearance source"', settings)
        self.assertIn('"Middle quick slots"', settings)
        self.assertIn('"Rail position"', settings)
        self.assertIn('"Radio / Music sides"', settings)
        self.assertIn('"Warm media sources on HOME start"', settings)
        self.assertIn('shortcutEditorCell("APP"', settings)
        self.assertIn('shortcutEditorCell("ICON"', settings)
        self.assertIn("SlotIconCatalog.LABELS", settings)
        self.assertIn("TimePickerDialog", settings)
        self.assertIn("setSingleChoiceItems", settings)
        self.assertIn("DRAWER_QUICK_KEYS", settings)
        self.assertIn('setHint("Search apps")', picker)
        self.assertIn("R.drawable.ic_mic", picker)
        self.assertNotIn("androidx.preference", settings)

    def test_media_selection_is_deterministic_radio_separate_and_controls_are_bootstrapped(self):
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        media = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        bootstrap = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        radio = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RadioProvider.java")
        self.assertIn('MEDIA_MODE_AUTO = "auto"', prefs)
        self.assertIn('MEDIA_MODE_PREFER_MUSIC = "prefer_music"', prefs)
        self.assertIn("preferConfigured", media)
        self.assertIn("pickExactPackage(controllers, preferredPackage)", media)
        self.assertIn("RadioProvider.resolvePackage(this)", media)
        self.assertIn("sessionDiagnostics", media)
        self.assertIn("MEDIA_REFRESH_INTERVAL_MS = 1000L", launcher)
        self.assertIn("setMediaButtonsReady(radioPrevious, radioPlayPause, radioNext)", launcher)
        self.assertIn("setMediaButtonsReady(mediaPrevious, playPause, mediaNext)", launcher)
        self.assertIn("mediaBootstrapper.pausePackage", launcher)
        self.assertIn("warmConfiguredSources", launcher)
        self.assertIn('MEDIA_BROWSER_ACTION = "android.media.browse.MediaBrowserService"', bootstrap)
        self.assertIn("COMMAND_TIMEOUT_MS = 4500L", bootstrap)
        self.assertIn("fallbackLaunch(pending)", bootstrap)
        self.assertIn("Generic media selection", settings)
        self.assertIn("Media session diagnostics", settings)
        self.assertIn('NAVRADIO_PLUS_PACKAGE = "com.navimods.radio"', radio)
        self.assertNotIn("sendBroadcast", radio)
        self.assertNotIn("su -c", radio)

    def test_topway_adapter_does_not_guess_stock_radio_contract(self):
        topway = self.read("launcher/src/main/java/com/cbkii/ts18launcher/platform/TopwayAdapter.java")
        self.assertIn('STOCK_MUSIC_PACKAGE = "com.tw.music"', topway)
        self.assertNotIn("STOCK_RADIO_PACKAGE", topway)
        self.assertNotIn("sendBroadcast", topway)
        self.assertNotIn("bindService", topway)

    def test_root_policy_is_bounded_reversible_and_prevalidated(self):
        installer = self.read("scripts/termux/install-standalone-launcher.sh")
        self.assertIn("timeout -k 2", installer)
        self.assertIn("--rollback-home", installer)
        self.assertIn("previous-home.txt", installer)
        self.assertIn("capture_rollback_home_for_change", installer)
        self.assertIn("aapt/aapt2 is required", installer)
        self.assertIn("apksigner is required", installer)
        self.assertIn("SHA256SUMS.txt", installer)
        self.assertIn("APK application ID mismatch", installer)
        self.assertIn("FAILED: rollback did not complete cleanly", installer)
        for forbidden in (
            "pm uninstall com.dofun.variety",
            "pm disable --user 0 com.dofun.variety",
            "setenforce 0",
            "mount -o rw,remount /system",
            "rm -rf /data/user/0/com.dofun.variety",
        ):
            self.assertNotIn(forbidden, installer)

    def test_measurement_reports_partial_capture_and_cleans_partial_archives(self):
        measure = self.read("scripts/termux/measure-standalone-launcher.sh")
        self.assertIn("capture exited with status", measure)
        self.assertIn("SHA-256 generation failed", measure)
        self.assertIn('rm -f -- "$archive" "$digest"', measure)
        self.assertIn("COMPLETED WITH WARNINGS", measure)

    def test_candidate_workflow_keeps_write_permission_in_publish_job(self):
        workflow = self.read(".github/workflows/launcher-candidate.yml")
        self.assertRegex(workflow, r"permissions:\n  contents: read")
        self.assertRegex(workflow, r"publish:\n    if:.*\n    needs: build")
        self.assertRegex(workflow, r"publish:[\s\S]*?permissions:\n      contents: write")
        self.assertIn("install-standalone-launcher.sh", workflow)
        self.assertIn("measure-standalone-launcher.sh", workflow)
        self.assertIn("STANDALONE_LAUNCHER.md", workflow)
        self.assertIn("qualified/BUILD_INFO.txt", workflow)
        self.assertIn("Unexpected candidate bundle.", workflow)
        self.assertIn("mapfile -t actual", workflow)
        self.assertIn("GH_REPO: ${{ github.repository }}", workflow)
        self.assertNotIn('gh release create "$tag" qualified/*', workflow)
        for match in re.finditer(r"uses:\s+([^\s]+)", workflow):
            action = match.group(1)
            self.assertRegex(action, r"@(?:[0-9a-f]{40})$")


if __name__ == "__main__":
    unittest.main()
