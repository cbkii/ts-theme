"""Static contract: setup is optional, offered once on new installs and never grants anything itself."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"


def code(path):
    text = path.read_text(encoding="utf-8")
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", text, flags=re.S))


class SetupFlowContract(unittest.TestCase):
    def test_setup_never_takes_home_or_grants_access_itself(self):
        setup = code(SRC / "SetupActivity.java")
        for banned in ("RootShell", "setHomeWithRoot", "Settings.Secure.put", "putString(\"enabled_notification_listeners\"",
                       "setMediaListenerRootGrant", "setN3Enabled", "Runtime.getRuntime"):
            self.assertNotIn(banned, setup)
        self.assertIn("HomeMode.requestHomeRole(this)", setup)
        self.assertIn("Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS", setup)

    def test_offered_once_durably_and_only_on_new_installs_after_home_draws(self):
        setup = code(SRC / "SetupActivity.java")
        self.assertIn("if (prefs.getBoolean(KEY_OFFERED, false)) return false;", setup)
        self.assertLess(setup.index("putBoolean(KEY_OFFERED, true).commit()"), setup.index("startActivity(new Intent(home, SetupActivity.class))"))
        home = code(SRC / "LauncherActivity.java")
        offer = home.split('StartupTimeline.mark("home.first_draw")', 1)[1].split("addOnDrawListener", 1)[0]
        for key in ("KEY_MUSIC", "KEY_RADIO", "KEY_NAV"):
            self.assertIn(f"LauncherPrefs.packageFor(this, LauncherPrefs.{key}).isEmpty()", offer)
        self.assertIn("SetupActivity.offerOnce(this)", offer)

    def test_setup_is_repeatable_and_not_exported(self):
        settings = code(SRC / "SettingsActivity.java")
        self.assertIn("new Intent(this, SetupActivity.class)", settings)
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text()
        self.assertRegex(manifest, r'android:name="\.SetupActivity"\s+android:configChanges="[^"]+"\s+android:exported="false"')


if __name__ == "__main__":
    unittest.main()
