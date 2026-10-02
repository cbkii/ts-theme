import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOT = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaNotificationAccessBootstrapper.java").read_text(encoding="utf-8")
APP = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/Ts18LauncherApplication.java").read_text(encoding="utf-8")
MONITOR = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/ProcessMediaSessionMonitor.java").read_text(encoding="utf-8")


class NotificationAccessBootstrapTest(unittest.TestCase):
    def test_root_grant_is_narrow_and_verified(self):
        self.assertIn("cmd notification allow_listener", BOOT)
        self.assertIn("MediaListenerService.hasNotificationAccess", BOOT)
        self.assertIn("NotificationListenerService.requestRebind", BOOT)
        self.assertIn("AndroidUserId.current()", BOOT)
        self.assertNotIn("settings put secure enabled_notification_listeners", BOOT)
        self.assertNotIn("pm grant", BOOT)

    def test_application_starts_process_session_monitor_early(self):
        self.assertIn("MediaNotificationAccessBootstrapper.ensureEarly(this);", APP)
        self.assertIn("ProcessMediaSessionMonitor.start(this);", APP)
        self.assertIn("ProcessMediaSessionMonitor.refresh(this);", APP)

    def test_grant_refreshes_sessions_without_waiting_for_listener_connection(self):
        self.assertIn("ProcessMediaSessionMonitor.refresh(app);", BOOT)
        self.assertIn("addOnActiveSessionsChangedListener", MONITOR)
        self.assertIn("getActiveSessions(listenerComponent)", MONITOR)


if __name__ == "__main__":
    unittest.main()
