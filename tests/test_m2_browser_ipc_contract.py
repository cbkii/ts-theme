"""Static contract for the API 29 cross-process M2 browser qualification.

The emulator job runs it; this keeps the fixture, probe and smoke wiring from drifting apart.
"""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
TEST_SRC = ROOT / "launcher/src/androidTest"


class M2BrowserIpcContract(unittest.TestCase):
    def test_fixture_is_an_exported_browser_service_in_the_test_apk(self):
        manifest = (TEST_SRC / "AndroidManifest.xml").read_text()
        self.assertIn("media.direct.FixtureBrowserService", manifest)
        self.assertIn('android:exported="true"', manifest)
        self.assertIn("android.media.browse.MediaBrowserService", manifest)
        fixture = (TEST_SRC / "java/com/cbkii/ts18launcher/media/direct/FixtureBrowserService.java").read_text()
        self.assertIn("setSessionToken(session.getSessionToken())", fixture)
        self.assertNotIn("requestAudioFocus", fixture)

    def test_probe_uses_the_production_connector_and_requires_confirmation(self):
        probe = (TEST_SRC / "java/com/cbkii/ts18launcher/media/direct/BrowserStarterIpcProbe.java").read_text()
        self.assertIn("new BrowserSessionStarter.FrameworkConnector(", probe)
        self.assertIn("session.dispatch(DirectOperation.PLAY)", probe)
        self.assertRegex(probe, r"playing\.await\(\d+, TimeUnit\.SECONDS\)")
        self.assertIn("fresh reconnect", probe)
        self.assertIn("session.dispatch(DirectOperation.PREPARE)", probe)
        self.assertIn("PREPARE must not start playback", probe)
        self.assertIn("retry after death", probe)

    def test_smoke_runs_the_mode_and_requires_its_pass_line(self):
        smoke = (ROOT / "scripts/ci/api29-startup-boundary-smoke.sh").read_text()
        self.assertRegex(smoke, r"am instrument -w -e m2_browser 1")
        self.assertIn("grep -Fq 'PASS API29 M2 browser starter'", smoke)
        self.assertIn("grep -Fq 'PREPARE loads without Play'", smoke)
        self.assertIn("grep -Fq 'retry after death'", smoke)
        runner = (TEST_SRC / "java/com/cbkii/ts18launcher/MediaSessionApi29Instrumentation.java").read_text()
        self.assertTrue(re.search(r'"m2_browser"', runner))


if __name__ == "__main__":
    unittest.main()
