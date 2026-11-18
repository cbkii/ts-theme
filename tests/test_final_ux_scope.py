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

    def test_home_reentry_restores_dashboard_root(self):
        activity = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        self.assertIn("onNewIntent(Intent intent)", activity)
        self.assertIn("restoreDashboardRoot()", activity)
        self.assertIn("clearFocus()", activity)

    def test_offline_and_configuration_boundaries_are_explicit(self):
        codec = self.read("launcher/src/main/java/com/cbkii/ts18launcher/ConfigurationCodec.java")
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        readme = self.read("README.md")
        manifest = self.read("launcher/src/main/AndroidManifest.xml")
        # Core HOME is offline: the launcher itself declares no network permission.
        self.assertNotIn("android.permission.INTERNET", manifest)
        self.assertNotIn("android.permission.ACCESS_NETWORK_STATE", manifest)
        self.assertIn("MAX_BYTES = 64 * 1024", codec)
        self.assertIn("ACTION_CREATE_DOCUMENT", settings)
        self.assertIn("ACTION_OPEN_DOCUMENT", settings)
        self.assertIn("Network availability is optional", readme)


if __name__ == "__main__":
    unittest.main()
