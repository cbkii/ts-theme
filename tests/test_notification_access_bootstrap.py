import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOT = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaNotificationAccessBootstrapper.java").read_text(encoding="utf-8")
APP = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/Ts18LauncherApplication.java").read_text(encoding="utf-8")


class NotificationAccessBootstrapTest(unittest.TestCase):
    def test_root_grant_is_narrow_and_verified(self):
        self.assertIn("cmd notification allow_listener", BOOT)
        self.assertIn("MediaListenerService.hasNotificationAccess", BOOT)
        self.assertIn("NotificationListenerService.requestRebind", BOOT)
        self.assertIn("AndroidUserId.current()", BOOT)
        self.assertNotIn("settings put secure enabled_notification_listeners", BOOT)
        self.assertNotIn("pm grant", BOOT)

    def test_application_runs_listener_access_setup_early(self):
        self.assertIn("MediaNotificationAccessBootstrapper.ensureEarly(this);", APP)


if __name__ == "__main__":
    unittest.main()
