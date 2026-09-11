import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class AutomotiveUiStateTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_home_drawer_resets_search_and_dismisses_soft_keyboard(self):
        drawer = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerPanel.java")
        self.assertIn("private final EditText search;", drawer)
        self.assertIn('search.setText("")', drawer)
        self.assertIn("search.clearFocus()", drawer)
        self.assertIn("InputMethodManager", drawer)
        self.assertIn("hideSoftInputFromWindow(search.getWindowToken(), 0)", drawer)
        self.assertIn("dismissKeyboard();\n        setVisibility(View.GONE);", drawer)

    def test_quick_slot_settings_report_navigation_and_bluetooth_role_fallbacks(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        self.assertIn("LauncherPrefs.KEY_DRAWER_QUICK_1.equals(key)", settings)
        self.assertIn("pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_NAV);", settings)
        self.assertIn("LauncherPrefs.KEY_DRAWER_QUICK_4.equals(key)", settings)
        self.assertIn("pkg = LauncherPrefs.packageFor(this, LauncherPrefs.KEY_BLUETOOTH);", settings)
        self.assertIn('AppResolver.labelFor(this, pkg, pkg) + " · role fallback"', settings)


if __name__ == "__main__":
    unittest.main()
