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

    def test_destroyed_browser_session_invalidates_cached_controller(self):
        bootstrap = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java").read_text(encoding="utf-8")
        self.assertIn("attachBrowserController(connection, generation);", bootstrap)
        self.assertIn("browserSessionDestroyed(connection, generation)", bootstrap)
        self.assertIn("connections.remove(packageName);", bootstrap)
        self.assertIn("connection.controller.unregisterCallback(connection.controllerCallback);", bootstrap)
        self.assertIn("queueBrowser(adapter, command);", bootstrap)

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


if __name__ == "__main__":
    unittest.main()
