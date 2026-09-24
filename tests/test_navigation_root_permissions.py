import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class NavigationRootPermissionTests(unittest.TestCase):
    def test_setting_is_default_off_and_configuration_whitelisted(self):
        prefs = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/UiPersonalizationPrefs.java").read_text()
        codec = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/ConfigurationCodec.java").read_text()
        settings = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java").read_text()
        self.assertIn('KEY_NAV_ROOT_PERMISSION_GRANT = "navigation.permissions.root_grant"', prefs)
        self.assertIn('getBoolean(KEY_NAV_ROOT_PERMISSION_GRANT, false)', prefs)
        self.assertIn('KEY_NAV_ROOT_PERMISSION_GRANT, Type.BOOLEAN', codec)
        self.assertIn('Pre-grant navigation location permissions (root)', settings)

    def test_root_grant_is_narrow_bounded_current_user_and_never_opens_settings(self):
        bootstrap = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationPermissionBootstrapper.java").read_text()
        policy = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationPermissionPolicy.java").read_text()
        self.assertIn('/system/bin/pm grant --user ', bootstrap)
        self.assertIn('ROOT_TIMEOUT_MS = 2200L', bootstrap)
        self.assertIn('Process.myUid() / ANDROID_UID_PER_USER_RANGE', bootstrap)
        self.assertNotIn('pm grant --user 0', bootstrap)
        self.assertNotIn('ACTION_APPLICATION_DETAILS_SETTINGS', bootstrap)
        self.assertNotIn('ACTION_LOCATION_SOURCE_SETTINGS', bootstrap)
        for permission in (
            'ACCESS_COARSE_LOCATION', 'ACCESS_FINE_LOCATION', 'ACCESS_BACKGROUND_LOCATION'
        ):
            self.assertIn(permission, policy)
        self.assertNotIn('CAMERA', policy)
        self.assertNotIn('READ_CONTACTS', policy)

    def test_early_and_assignment_hooks_prepare_permissions(self):
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text()
        application = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/Ts18LauncherApplication.java").read_text()
        drawer = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerActivity.java").read_text()
        self.assertIn('android:name=".Ts18LauncherApplication"', manifest)
        self.assertIn('NavigationPermissionBootstrapper.ensureEarly(this);', application)
        assignment = drawer.split('private void onEntry(Entry entry)', 1)[1].split(
            'private static final class Entry', 1
        )[0]
        self.assertIn('LauncherPrefs.KEY_NAV.equals(pickKey)', assignment)
        self.assertIn('NavigationPermissionBootstrapper.ensureEarly(this);', assignment)

    def test_native_navigation_checks_permission_mitigation_before_task_transition(self):
        backend = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java").read_text()
        present = backend.split('void present(', 1)[1].split('@Override public void verify', 1)[0]
        verify = backend.split('void verify(', 1)[1].split('@Override public void status', 1)[0]
        fullscreen = backend.split('void fullscreen(', 1)[1].split('@Override public void suspend', 1)[0]
        self.assertIn('ensureNavigationPermissions(packageName);', present)
        self.assertIn('ensureNavigationPermissions(packageName);', verify)
        self.assertIn('ensureNavigationPermissions(packageName);', fullscreen)


if __name__ == "__main__":
    unittest.main()
