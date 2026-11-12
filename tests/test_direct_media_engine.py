"""Source contracts for the direct (M2) media engine: independence and non-ownership."""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"
DIRECT = MAIN / "media/direct"
CONTRACT = MAIN / "media"


def strip_comments(source: str) -> str:
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    return re.sub(r"//[^\n]*", "", source)


class DirectMediaEngineContractTests(unittest.TestCase):
    def sources(self, directory: Path):
        return {path.name: path.read_text() for path in sorted(directory.glob("*.java"))}

    def test_direct_engine_uses_no_legacy_machinery(self):
        legacy = (
            "MediaSourceBootstrapper", "MediaCommandPolicy", "ProcessMediaSessionMonitor",
            "MediaListenerService", "MediaSelection", "MediaSourceAdapter", "MediaItemObservation",
            "SessionControllerRegistry", "StartupReadinessCoordinator", "LegacyMediaBackend",
        )
        direct = self.sources(DIRECT)
        self.assertGreaterEqual(len(direct), 7)
        for name, source in direct.items():
            code = strip_comments(source)
            for symbol in legacy:
                self.assertNotIn(symbol, code, f"{name} must not use legacy {symbol}")
            # It may depend only on the engine contract package and the platform.
            for imported in re.findall(r"^import (com\.cbkii\.[\w.]+);", source, flags=re.M):
                self.assertTrue(imported.startswith("com.cbkii.ts18launcher.media."), f"{name}: {imported}")

    def test_direct_engine_never_owns_playback_or_starts_anything(self):
        forbidden = (
            "new MediaSession(", "requestAudioFocus", "AudioManager", "MediaPlayer",
            "startActivity", "startService", "startForegroundService", "bindService",
            "dispatchMediaButtonEvent", "sendBroadcast", "Runtime.getRuntime",
            "RootShell", "playFromMediaId", "playFromSearch", "prepareFromMediaId", "prepareFromSearch",
            "prepareFromUri", "Thread.sleep",
        )
        for name, source in self.sources(DIRECT).items():
            code = strip_comments(source)
            for marker in forbidden:
                self.assertNotIn(marker, code, f"{name} must not contain {marker}")
            # Only the cold-start adapter may talk to a source's media browser service.
            if name != "BrowserSessionStarter.java":
                self.assertNotIn("MediaBrowser", code, name)

    def test_cold_start_is_source_owned_and_sends_no_transport_command(self):
        starter = strip_comments((DIRECT / "BrowserSessionStarter.java").read_text())
        engine = strip_comments((DIRECT / "DirectSourceEngine.java").read_text())
        self.assertIn('"android.media.browse.MediaBrowserService"', starter)
        self.assertIn("service.enabled && service.exported", starter)
        self.assertIn("if (!packageName.equals(sessionPackage))", starter)
        self.assertIn("callbacks.onConnected(controller.getPackageName(),", starter)
        # Discovery never guesses between several exported services.
        self.assertIn("candidates.size() == 1 ? candidates.get(0) : null", starter)
        for marker in ("getTransportControls", ".play()", "Intent.ACTION_MAIN", "FLAG_ACTIVITY", "su "):
            self.assertNotIn(marker, starter)
        # A start is requested only for a Play tap, and repeated taps join the one attempt.
        self.assertIn("if ((action == MediaUserAction.PLAY_PAUSE || action == MediaUserAction.PLAY) && startable()) { requestStart(callback, deadlineElapsedMs); return; }", engine)
        # Two owners may ask: a Play tap, and passive readiness (only while startable, once per epoch).
        self.assertEqual(2, engine.count("starter.start(key, packageName)"))
        passive = engine.split("private void tryPassivePrepare()", 1)[1].split("\n    }\n", 1)[0]
        self.assertIn("!starter.canPreparePassive(packageName)", passive)
        self.assertIn("if (!startable() || starter == null || !starter.canPreparePassive(packageName)) return;", passive)
        self.assertIn("decidedPrepareEpoch = epoch;", passive)
        self.assertNotIn("DirectOperation.PLAY", passive)
        # A timed-out attempt is abandoned so the next tap opens a fresh connection.
        self.assertIn("if (starter != null) starter.abandon(key, packageName);", engine)
        # Play after a start is the engine's normal single dispatch; PREPARE is the passive one.
        self.assertIn("transport.dispatchDeadline(token, deadline)", engine)

    def test_transport_is_directional_and_single_dispatch(self):
        operation = (DIRECT / "DirectOperation.java").read_text()
        framework = (DIRECT / "FrameworkSessions.java").read_text()
        engine = strip_comments((DIRECT / "DirectSourceEngine.java").read_text())
        self.assertIn("PLAY, PAUSE, NEXT, PREVIOUS, PREPARE;", operation)
        self.assertNotIn("TOGGLE", operation)
        for call in ("controls.play()", "controls.pause()", "controls.skipToNext()", "controls.skipToPrevious()",
                     "controls.prepare()"):
            self.assertEqual(1, framework.count(call), call)
        # Driver action, the one Play after a requested start, the handover pause and passive PREPARE.
        self.assertEqual(3, engine.count("transport.dispatch("))
        self.assertEqual(1, engine.count("transport.dispatchDeadline(token, deadline)"))
        self.assertEqual(1, engine.count("transport.dispatch(pinnedToken, DirectOperation.PREPARE)"))
        command = engine.split("public void commandUntil(", 1)[1].split("\n    }\n", 1)[0]
        self.assertNotIn("PREPARE", command, "a user tap never sends PREPARE")
        self.assertIn("transport.dispatch(pinnedToken, DirectOperation.PAUSE)", engine)

    def test_sessions_come_only_from_the_existing_listener_component(self):
        framework = (DIRECT / "FrameworkSessions.java").read_text()
        engines = (MAIN / "MediaEngines.java").read_text()
        manifest = (ROOT / "launcher/src/main/AndroidManifest.xml").read_text()
        self.assertIn("manager.getActiveSessions(listenerComponent)", framework)
        self.assertIn("addOnActiveSessionsChangedListener(membership, listenerComponent, handler)", framework)
        self.assertIn("new ComponentName(app, MediaListenerService.class)", engines)
        self.assertEqual(1, manifest.count("android.service.notification.NotificationListenerService"))

    def test_engine_contract_stays_free_of_android_and_launcher_types(self):
        for name, source in self.sources(CONTRACT).items():
            self.assertNotIn("import android.", source, name)
            for imported in re.findall(r"^import (com\.cbkii\.[\w.]+);", source, flags=re.M):
                self.fail(f"{name} imports {imported}")

    def test_direct_defaults_keep_explicit_legacy_rollback(self):
        selector = (MAIN / "MediaEngineSelector.java").read_text()
        engine = (CONTRACT / "MediaEngine.java").read_text()
        settings = (MAIN / "SettingsActivity.java").read_text()
        self.assertIn("return DIRECT.stored.equals(value) ? DIRECT : LEGACY;", engine)
        self.assertIn('getString(prefKey(key), "m2")', selector)
        self.assertIn('"Legacy · compatibility"', settings)
        self.assertIn("Direct · TESTING default", settings)

    def test_home_renders_each_source_from_its_selected_engine_only(self):
        launcher = (MAIN / "LauncherActivity.java").read_text()
        self.assertIn("? DirectMediaPresentation.snapshot(facade.state(MediaSourceKey.MUSIC))", launcher)
        self.assertIn("? DirectMediaPresentation.snapshot(facade.state(MediaSourceKey.RADIO))", launcher)
        self.assertIn("mediaFacade().setStateListener(null);", launcher)
        self.assertIn("R.drawable.ic_media_unavailable", launcher)


if __name__ == "__main__":
    unittest.main()
