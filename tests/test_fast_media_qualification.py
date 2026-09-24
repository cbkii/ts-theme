import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class FastMediaQualificationTests(unittest.TestCase):
    def read(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_media_trace_is_bounded_process_local_and_monotonic(self):
        trace = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaEventTrace.java")
        buffer = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaTraceBuffer.java")
        self.assertIn("SystemClock.uptimeMillis()", trace)
        self.assertIn("new MediaTraceBuffer(96)", trace)
        self.assertIn('LOG_TAG = "TS18MediaTrace"', trace)
        self.assertNotIn("FileOutputStream", trace)
        self.assertIn("while (entries.size() >= capacity) entries.removeFirst()", buffer)

    def test_storage_reconciliation_is_event_driven_non_playing_and_home_scoped(self):
        app = self.read("launcher/src/main/java/com/cbkii/ts18launcher/Ts18LauncherApplication.java")
        storage = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaStorageReconciler.java")
        manifest = self.read("launcher/src/main/AndroidManifest.xml")
        self.assertIn('android:name=".Ts18LauncherApplication"', manifest)
        for action in ("ACTION_MEDIA_MOUNTED", "ACTION_MEDIA_UNMOUNTED", "ACTION_MEDIA_EJECT", "ACTION_MEDIA_REMOVED"):
            self.assertIn(action, app)
        self.assertIn("if (startedHomeActivities == 1) registerStorageReceiver()", app)
        self.assertIn("if (startedHomeActivities == 0)", app)
        self.assertIn('MediaStorageReconciler.cancel("HOME stopped")', app)
        self.assertIn("MediaBrowser", storage)
        self.assertIn("MediaListenerService.observeExternalController", storage)
        self.assertNotIn("RootShell", storage)
        self.assertNotIn("TransportControls", storage)
        self.assertNotIn(".play()", storage)
        self.assertNotIn("listFiles", storage)
        self.assertNotIn("java.io.File", storage)

    def test_root_work_remains_worker_thread_and_activity_fallback_stays_absent(self):
        bootstrap = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java")
        adapter = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceAdapter.java")
        self.assertIn("rootExecutor.execute", bootstrap)
        self.assertIn("RootShell.runMillis", bootstrap)
        self.assertIn("service.fallback", bootstrap)
        self.assertNotIn("startActivity(", bootstrap)
        self.assertNotIn("AppResolver", bootstrap)
        self.assertIn("boolean maskedFallbackQualified()", adapter)
        self.assertIn("return false;", adapter.split("boolean maskedFallbackQualified()", 1)[1].split("}", 1)[0])
        self.assertIn("passive warm", adapter.lower())

    def test_listener_and_metadata_use_real_session_and_semantic_change_policies(self):
        listener = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java")
        metadata = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaMetadataView.java")
        marquee = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SlowMarqueeTextView.java")
        self.assertIn("MediaMetadataPolicy.sameToken", listener)
        self.assertIn("MediaMetadataPolicy.firstNonBlank", listener)
        self.assertIn("REGISTERED_EXTERNAL", listener)
        self.assertIn("listener.rebind", listener)
        self.assertIn("MarqueePolicy.primaryChanged", metadata)
        self.assertIn("MarqueePolicy.durationMs", marquee)
        self.assertIn("TextUtils.equals(getText(), safe)", marquee)

    def test_settings_diagnostics_include_routes_sessions_and_event_timeline(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        diagnostics = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaDiagnostics.java")
        self.assertIn("MediaDiagnostics.build(this)", settings)
        self.assertIn("Configured sources", diagnostics)
        self.assertIn("Active/bound controllers", diagnostics)
        self.assertIn("Readiness/event timeline", diagnostics)
        self.assertIn("MediaEventTrace.dump()", diagnostics)


if __name__ == "__main__":
    unittest.main()
