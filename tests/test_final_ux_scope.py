import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class FinalUxScopeTests(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_shared_media_authority_and_independent_groups(self):
        activity = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        selection = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaSelection.java")
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        self.assertEqual(1, activity.count("private MediaMetadataView mediaText"))
        self.assertIn("LauncherPrefs.radioOnRight(this)", activity)
        self.assertIn("selectSource(MediaSelection.RADIO)", activity)
        self.assertIn("selectSource(MediaSelection.MUSIC)", activity)
        self.assertIn("if (radioPlaying == musicPlaying) displayed = explicit", selection)
        self.assertIn("KEY_LAST_MUSIC", prefs)

    def test_home_reentry_and_map_recovery_are_bounded(self):
        activity = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        panel = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MapPanel.java")
        recovery = self.read("launcher/src/main/java/com/cbkii/ts18launcher/RendererRecovery.java")
        self.assertIn("onNewIntent(Intent intent)", activity)
        self.assertIn("restoreDashboardRoot()", activity)
        self.assertIn("clearFocus()", activity)
        self.assertIn("onRenderProcessGone", panel)
        self.assertIn("recovery.failed()", panel)
        self.assertIn("retry.setText(\"Retry map\")", panel)
        self.assertIn("One automatic recreation per map lifecycle", recovery)

    def test_offline_and_configuration_boundaries_are_explicit(self):
        tile = self.read("launcher/src/main/java/com/cbkii/ts18launcher/TileBroker.java")
        state = self.read("launcher/src/main/java/com/cbkii/ts18launcher/TileState.java")
        codec = self.read("launcher/src/main/java/com/cbkii/ts18launcher/ConfigurationCodec.java")
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        readme = self.read("README.md")
        self.assertIn("definitelyOffline", tile)
        self.assertIn("offlineResponse", tile)
        for kind in ("NETWORK_OK", "CACHE_FRESH", "CACHE_STALE", "FAILED"):
            self.assertIn(kind, state)
        self.assertIn("MAX_BYTES = 64 * 1024", codec)
        self.assertIn("ACTION_CREATE_DOCUMENT", settings)
        self.assertIn("ACTION_OPEN_DOCUMENT", settings)
        self.assertIn("Network availability is optional", readme)


if __name__ == "__main__":
    unittest.main()
