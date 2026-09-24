import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class MediaPlaybackSemanticsTests(unittest.TestCase):
    def test_buffering_and_connecting_use_pause_side_semantics(self):
        media = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java").read_text(encoding="utf-8")
        self.assertIn("private static boolean usesPauseAction(int state)", media)
        self.assertIn("state == PlaybackState.STATE_PLAYING", media)
        self.assertIn("state == PlaybackState.STATE_BUFFERING", media)
        self.assertIn("state == PlaybackState.STATE_CONNECTING", media)
        self.assertIn("this.playing = usesPauseAction(state);", media)
        self.assertIn("boolean pauseSide = state != null && usesPauseAction(state.getState());", media)
        self.assertIn("if (pauseSide) controller.getTransportControls().pause();", media)

    def test_bound_browser_controllers_survive_notification_listener_rebind(self):
        media = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java").read_text(encoding="utf-8")
        self.assertIn("REGISTERED_EXTERNAL", media)
        self.assertIn("attachRegisteredExternalControllers();", media)
        self.assertIn("REGISTERED_EXTERNAL.put(token, controller);", media)
        self.assertIn("forgetRegisteredExternalController(token, controller);", media)
        self.assertIn("forgetExternalController(controller);", media)
        self.assertIn('MediaEventTrace.record("listener", "connected")', media)
        self.assertIn('MediaEventTrace.record("listener", "disconnected"', media)

    def test_destroyed_browser_session_invalidates_cached_controller(self):
        bootstrap = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java").read_text(encoding="utf-8")
        self.assertIn("attachBrowserController(connection, generation);", bootstrap)
        self.assertIn("browserSessionDestroyed(connection, generation)", bootstrap)
        self.assertIn("connections.remove(packageName);", bootstrap)
        self.assertIn("connection.controller.unregisterCallback(connection.controllerCallback);", bootstrap)
        self.assertIn("queueBrowser(adapter, command);", bootstrap)
        self.assertIn('MediaEventTrace.record("session", "bound-destroyed"', bootstrap)

    def test_home_stop_and_media_configuration_change_invalidate_obsolete_commands(self):
        launcher = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java").read_text(encoding="utf-8")
        self.assertIn("reconcileMediaConfiguration();", launcher)
        self.assertIn("boolean commandPending = mediaStatusActive;", launcher)
        self.assertIn("if (commandPending) resetMediaBootstrapper();", launcher)
        self.assertIn("mediaStatusGeneration++;", launcher)
        self.assertIn("mediaBootstrapper.destroy();", launcher)
        self.assertIn("mediaBootstrapper = new MediaSourceBootstrapper(this);", launcher)
        self.assertIn("radioPackage.equals(mediaRadioPackage)", launcher)
        self.assertIn("musicPackage.equals(mediaMusicPackage)", launcher)

    def test_removable_storage_reconciliation_is_mount_only_and_non_playing(self):
        app = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/Ts18LauncherApp.java").read_text(encoding="utf-8")
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('android:name=".Ts18LauncherApp"', manifest)
        self.assertIn("Intent.ACTION_MEDIA_MOUNTED", app)
        self.assertIn("RemovableMediaPolicy.shouldWarm", app)
        self.assertIn("bootstrapper.warm(target);", app)
        self.assertNotIn("bootstrapper.command", app)
        self.assertNotIn("startActivity", app)
        self.assertNotIn("forceStopPackage", app)

    def test_media_trace_is_bounded_local_and_visible_in_settings(self):
        trace = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaEventTrace.java").read_text(encoding="utf-8")
        settings = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java").read_text(encoding="utf-8")
        self.assertIn("MAX_EVENTS = 192", trace)
        self.assertIn('LOG_TAG = "TS18Media"', trace)
        self.assertNotIn("Http", trace)
        self.assertNotIn("Socket", trace)
        self.assertIn("MediaEventTrace.dump(40)", settings)
        self.assertIn("Playback acknowledgement is not audible-output proof", settings)

    def test_background_readiness_never_grows_an_activity_fallback(self):
        bootstrap = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java").read_text(encoding="utf-8")
        adapter = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceAdapter.java").read_text(encoding="utf-8")
        self.assertNotIn("startActivity", bootstrap)
        self.assertNotIn("getLaunchIntentForPackage", bootstrap)
        self.assertIn("return false;", adapter.split("static boolean maskedFallbackQualified()", 1)[1].split("}", 1)[0])


if __name__ == "__main__":
    unittest.main()
