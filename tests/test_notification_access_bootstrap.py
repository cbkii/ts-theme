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

    def test_root_grant_requires_stored_consent_and_respects_revocation(self):
        policy = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerAccessPolicy.java").read_text(encoding="utf-8")
        early = BOOT.split("static void ensureEarly(Context context) {", 1)[1].split("static void requestRootGrant", 1)[0]
        self.assertIn("MediaListenerAccessPolicy.decide(", early)
        self.assertIn("UiPersonalizationPrefs.mediaListenerRootGrant(app)", early)
        self.assertIn("case WITHDRAW_CONSENT:", early)
        self.assertIn("UiPersonalizationPrefs.setMediaListenerRootGrant(app, false);", early)
        # The only root calls are the policy-gated grant and the consent-withdrawal undo of
        # exactly our own listener; never directly in process start.
        self.assertNotIn("RootShell", early)
        self.assertEqual(2, BOOT.count("RootShell.runMillis("))
        self.assertIn("cmd notification allow_listener", BOOT)
        self.assertIn("cmd notification disallow_listener", BOOT)
        self.assertIn("if (!rootConsent) return Action.USE_SETTINGS;", policy)
        self.assertIn("if (grantApplied && !freshConsent) return Action.WITHDRAW_CONSENT;", policy)

    def test_consent_withdrawal_cancels_in_flight_root_grant(self):
        # Consent is re-checked before executing and again before committing; an opt-in recorded
        # while access already existed counts as applied so later revocation withdraws consent.
        grant = BOOT.split("private static void rootGrant(", 1)[1]
        self.assertIn('UiPersonalizationPrefs.mediaListenerRootGrant(app)', grant)
        self.assertIn("revokeOwnListener", grant)
        request = BOOT.split("static void requestRootGrant(", 1)[1].split(
            "private static void rootGrant", 1)[0]
        self.assertIn("markMediaListenerRootGrantApplied(app);", request)

    def test_application_initializes_facade_early_and_gates_legacy_monitor(self):
        self.assertIn("MediaNotificationAccessBootstrapper.ensureEarly(this);", APP)
        self.assertIn("mediaFacade(this);", APP)
        self.assertIn("ProcessMediaSessionMonitor.setDemand(app, legacy)", APP)
        self.assertIn("MediaListenerService.setDemand(legacy)", APP)
        self.assertIn("ProcessMediaSessionMonitor.refresh(this);", APP)

    def test_grant_refreshes_sessions_without_waiting_for_listener_connection(self):
        self.assertGreaterEqual(BOOT.count("ProcessMediaSessionMonitor.refresh(app);"), 2)
        verified = BOOT.split('MediaEventTrace.record("listener-access", "root-grant-verified"', 1)[1]
        verified = verified.split("});\n    }", 1)[0]
        self.assertIn("MAIN.post", verified)
        self.assertIn("ProcessMediaSessionMonitor.refresh(app);", verified)
        self.assertIn("addOnActiveSessionsChangedListener", MONITOR)
        self.assertIn("getActiveSessions(listenerComponent)", MONITOR)

    def test_process_monitor_clears_authority_when_access_is_lost(self):
        self.assertIn("MediaListenerService.hasNotificationAccess(context)", MONITOR)
        self.assertIn("else if (!access && registered) detachMembership();", MONITOR)
        unavailable = MONITOR.split("private void invalidate()", 1)[1].split(
            "private static MediaListenerService.Snapshot empty()", 1
        )[0]
        self.assertIn("authoritative = false", unavailable)
        self.assertIn("lastGeneric = empty()", unavailable)
        self.assertIn("lastRadio = empty()", unavailable)
        self.assertIn("notifyObservers()", unavailable)


if __name__ == "__main__":
    unittest.main()
