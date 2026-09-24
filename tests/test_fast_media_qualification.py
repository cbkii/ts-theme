import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class FastMediaQualificationTests(unittest.TestCase):
    def read(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_process_local_diagnostics_are_bounded_and_non_telemetric(self):
        diagnostics = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaDiagnostics.java"
        )
        self.assertIn("MAX_EVENTS = 96", diagnostics)
        self.assertIn('LOG_TAG = "TS18MediaTrace"', diagnostics)
        self.assertIn("SystemClock.uptimeMillis()", diagnostics)
        self.assertIn("Audible output: NOT inferred from playback state", diagnostics)
        for forbidden in ("HttpURLConnection", "Socket(", "FileOutputStream", "Analytics"):
            self.assertNotIn(forbidden, diagnostics)

    def test_storage_reconciliation_is_mount_event_driven_and_never_plays(self):
        reconciler = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/RemovableMediaReconciler.java"
        )
        launcher = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java"
        )
        self.assertIn("Intent.ACTION_MEDIA_MOUNTED", reconciler)
        self.assertIn("Intent.ACTION_MEDIA_UNMOUNTED", reconciler)
        self.assertIn("context.registerReceiver(receiver, filter)", reconciler)
        self.assertIn("context.unregisterReceiver(receiver)", reconciler)
        self.assertNotIn("TransportControls", reconciler)
        self.assertNotIn("startActivity", reconciler)
        self.assertIn("mediaBootstrapper.warm(musicPackage)", launcher)
        storage_block = launcher.split("onRemovableMediaAvailable", 1)[1].split(
            "scheduleMediaReadiness", 1
        )[0]
        self.assertNotIn('mediaBootstrapper.command("Music"', storage_block)

    def test_root_and_async_policy_is_used_by_production_coordinator(self):
        bootstrap = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java"
        )
        self.assertIn("MediaPreparationPolicy.afterRoot", bootstrap)
        self.assertIn("MediaPreparationPolicy.shouldDispatch", bootstrap)
        self.assertIn("MediaPreparationPolicy.shouldCoalesce", bootstrap)
        self.assertIn("MediaPreparationPolicy.shouldCommitOppositePause", bootstrap)
        self.assertIn("rootExecutor.execute", bootstrap)
        self.assertIn("audible=UNVERIFIED", bootstrap)
        self.assertNotIn("startActivity(", bootstrap)

    def test_listener_diagnostics_preserve_real_session_token_dedup(self):
        listener = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java"
        )
        self.assertIn("Map<MediaSession.Token, MediaController>", listener)
        self.assertIn("merged.containsKey(entry.getKey())", listener)
        self.assertIn("MediaDiagnostics.report(context)", listener)
        self.assertIn("MediaMetadataPolicy.firstNonBlank", listener)

    def test_navradio_qualification_requires_explicit_mutation_flag(self):
        script = self.read("scripts/termux/qualify-navradio-service-start.sh")
        self.assertIn("--qualify-navradio-service-start", script)
        self.assertEqual(1, script.count("am start-foreground-service"))
        self.assertNotIn("media dispatch", script)
        self.assertNotIn("am force-stop", script)
        self.assertNotIn("input keyevent", script)
        self.assertIn("human_audible_observation=REQUIRED", script)

    def test_stock_radio_and_acc_collectors_remain_read_only(self):
        stock = self.read("scripts/termux/collect-stock-radio-evidence.sh")
        lifecycle = self.read("scripts/termux/collect-media-lifecycle-evidence.sh")
        for text in (stock, lifecycle):
            self.assertNotIn("am broadcast", text)
            self.assertNotIn("service call", text)
            self.assertNotIn("setprop ", text)
            self.assertNotIn("am force-stop", text)
        self.assertNotIn("su -c", stock)
        self.assertIn("Screen-on is not treated as proof of ACC", lifecycle)

    def test_masked_activity_fallback_stays_disabled(self):
        adapter = self.read(
            "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceAdapter.java"
        )
        self.assertIn("boolean maskedFallbackQualified()", adapter)
        method = adapter.split("boolean maskedFallbackQualified()", 1)[1]
        self.assertIn("return false;", method)


if __name__ == "__main__":
    unittest.main()
