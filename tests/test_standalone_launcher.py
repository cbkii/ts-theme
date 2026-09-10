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
        alias = next(
            item for item in aliases
            if item.attrib.get(ANDROID_NS + "name") == ".HomeAlias"
        )
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
        java = "\n".join(
            path.read_text(encoding="utf-8")
            for path in sorted(source_root.rglob("*.java"))
        )
        media = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java"
        )
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

    def test_web_map_is_local_lifecycle_bound_and_origin_restricted(self):
        panel = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MapPanel.java")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        html = self.read("launcher/src/main/assets/map/map.html")
        checker = self.read("tools/launcher_apk_check.py")
        self.assertIn('file:///android_asset/map/map.html', panel)
        self.assertIn('tile.openstreetmap.org', panel)
        self.assertIn('tile.openstreetmap.org', html)
        self.assertNotIn("addJavascriptInterface", panel)
        self.assertNotIn("http://", panel + html)
        self.assertIn("LocationManager.GPS_PROVIDER", panel)
        self.assertNotIn("LocationManager.NETWORK_PROVIDER", panel)
        self.assertIn("removeUpdates(this)", panel)
        self.assertIn("pointerdown", html)
        self.assertIn("pointermove", html)
        self.assertIn("pointers=new Map()", html)
        self.assertIn("pinchDistance", html)
        self.assertIn("window.mapHealth", html)
        self.assertIn("tileKey", html)
        self.assertIn("if(key===tileKey)return", html)
        self.assertIn("rebuildTiles(p,tx,ty)", html)
        self.assertIn("onReceivedError(", panel)
        self.assertIn("onReceivedHttpError(", panel)
        self.assertIn("onReceivedSslError(", panel)
        self.assertIn("handler.cancel()", panel)
        self.assertNotIn("handler.proceed()", panel)
        self.assertIn("new MapPanel(", launcher)
        self.assertIn('"assets/map/map.html"', checker)
        self.assertNotIn("ts18launcher/MapPanel;", checker)

    def test_home_has_quick_slots_collapsible_drawer_and_navigation_handoff(self):
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        drawer = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerPanel.java")
        nav = self.read("launcher/src/main/java/com/cbkii/ts18launcher/NavigationProvider.java")
        self.assertIn("KEY_QUICK_1", prefs)
        self.assertIn("KEY_QUICK_4", prefs)
        self.assertIn("QUICK_SLOT_COUNT = 4", launcher)
        self.assertIn("AppDrawerPanel", launcher)
        self.assertIn("toggleAppDrawer()", launcher)
        self.assertIn("mapPanel.stop()", launcher)
        self.assertIn("mapPanel.resumeWebView()", launcher)
        self.assertIn("setVisibility(View.GONE)", drawer)
        self.assertIn("GridView", drawer)
        self.assertIn("NavigationProvider.open", launcher)
        self.assertIn('GOOGLE_MAPS = "com.google.android.apps.maps"', nav)
        self.assertIn('WAZE = "com.waze"', nav)
        self.assertIn('ORGANIC_MAPS = "app.organicmaps"', nav)
        self.assertIn('OSMAND_PLUS = "net.osmand.plus"', nav)
        self.assertIn("geo:", nav)
        self.assertIn("waze://", nav)

    def test_media_selection_is_deterministic_capability_aware_and_radio_separate(self):
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        media = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java")
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        radio = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RadioProvider.java")
        self.assertIn('MEDIA_MODE_AUTO = "auto"', prefs)
        self.assertIn('MEDIA_MODE_PREFER_MUSIC = "prefer_music"', prefs)
        self.assertIn("preferConfigured", media)
        self.assertIn("pickExactPackage(controllers, preferredPackage)", media)
        self.assertIn("RadioProvider.resolvePackage(this)", media)
        self.assertIn("public boolean supports(Command command)", media)
        self.assertIn("PlaybackState.ACTION_PLAY", media)
        self.assertIn("PlaybackState.ACTION_PAUSE", media)
        self.assertIn("sessionDiagnostics", media)
        self.assertIn("MEDIA_REFRESH_INTERVAL_MS = 1000L", launcher)
        self.assertIn("postDelayed(mediaRefreshPoll, MEDIA_REFRESH_INTERVAL_MS)", launcher)
        self.assertIn("removeCallbacks(mediaRefreshPoll)", launcher)
        self.assertIn("setMediaButtonState(mediaPrevious", launcher)
        self.assertIn("setMediaButtonState(playPause", launcher)
        self.assertIn("setMediaButtonState(mediaNext", launcher)
        self.assertIn("radioPlayPause", launcher)
        self.assertIn("sendRadio(MediaListenerService.Command.PLAY_PAUSE)", launcher)
        self.assertNotIn("if (!MediaListenerService.sendRadio", launcher)
        self.assertIn("Generic media selection:", settings)
        self.assertIn("Media session diagnostics", settings)
        self.assertIn('NAVRADIO_PLUS_PACKAGE = "com.navimods.radio"', radio)
        self.assertNotIn("sendBroadcast", radio)
        self.assertNotIn("su -c", radio)

    def test_first_physical_feedback_adjustments_are_guarded(self):
        geometry = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/Ts18Geometry.java"
        )
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java"
        )
        self.assertIn("HOTSEAT_WIDTH = 96", geometry)
        self.assertIn("STRIP_HEIGHT = 72", geometry)
        self.assertIn("RADIO_WIDTH = 350", geometry)
        self.assertIn("MUSIC_WIDTH = 616", geometry)
        self.assertIn("RippleDrawable", launcher)
        self.assertIn("setTextSize(13f)", launcher)
        self.assertIn("setTextSize(28f)", launcher)

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
