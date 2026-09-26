import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAWER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerPanel.java").read_text(encoding="utf-8")
STARTUP = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/StartupBootstrapCoordinator.java").read_text(encoding="utf-8")
LAUNCHER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java").read_text(encoding="utf-8")
ADAPTER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceAdapter.java").read_text(encoding="utf-8")
AGENTS = (ROOT / "AGENTS.md").read_text(encoding="utf-8")


class FinalPassHardeningTest(unittest.TestCase):
    def test_startup_prime_has_one_owner_without_dead_bootstrap_dependency(self):
        self.assertIn("StartupBootstrapCoordinator(Activity activity)", STARTUP)
        self.assertNotIn("ignoredBootstrapper", STARTUP)
        self.assertIn("new StartupBootstrapCoordinator(this)", LAUNCHER)
        self.assertNotIn("new StartupBootstrapCoordinator(this, mediaBootstrapper)", LAUNCHER)

    def test_auxio_root_startservice_route_stays_removed(self):
        auxio = ADAPTER.split("if (AUXIO_PACKAGE.equals(packageName))", 1)[1].split(
            "if (NAVRADIO_PACKAGE.equals(packageName))", 1
        )[0]
        self.assertIn("false, false, true", auxio)
        self.assertNotIn("rootPrime = true", auxio)
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


if __name__ == "__main__":
    unittest.main()
