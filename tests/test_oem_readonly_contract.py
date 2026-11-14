"""Static guard: the launcher's Topway adapter stays observation-only."""
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
OEM = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/oem"
MAIN = ROOT / "launcher/src/main/java"


class OemReadOnlyContract(unittest.TestCase):
    def sources(self):
        return {p.name: p.read_text() for p in OEM.glob("*.java")}

    def test_only_status_registration_and_source_query_transactions_exist(self):
        contract = (OEM / "TopwayContract.java").read_text()
        codes = dict(re.findall(r"static final int (\w+) = (\d+);", contract))
        command = {k: v for k, v in codes.items() if not k.startswith("CB_")}
        self.assertEqual({"REGISTER_STATUS_CALLBACK": "1", "UNREGISTER_STATUS_CALLBACK": "2",
                          "EXTENDED_INTERFACE": "67"}, command)
        host = (OEM / "FrameworkTopwayHost.java").read_text()
        transacted = set(re.findall(r"transact\(TopwayContract\.(\w+)", host)) | set(
            re.findall(r"callbackTransaction\(TopwayContract\.(\w+)", host))
        self.assertEqual({"EXTENDED_INTERFACE", "REGISTER_STATUS_CALLBACK", "UNREGISTER_STATUS_CALLBACK"}, transacted)

    def test_extended_interface_only_carries_the_source_query(self):
        host = (OEM / "FrameworkTopwayHost.java").read_text()
        self.assertEqual(1, host.count("TopwayContract.EXTENDED_INTERFACE"))
        self.assertIn("ACTION_SOURCE_REQUEST", host)
        self.assertIn("PROJECT_SYSTEM", host)

    def test_no_writers_music_callback_or_service_creation(self):
        banned = ("TWUtil", "android.tw.john", "radioOpenChannel", "radioSearchNext", "radioSetChannel",
                  "setSystemBrightness", "musicPause", "MUSIC_CALLBACK", "BIND_AUTO_CREATE", "startService")
        for name, text in self.sources().items():
            for token in banned:
                self.assertNotIn(token, text, f"{token} in {name}")
        for path in MAIN.rglob("*.java"):
            if path.parent == OEM:
                continue
            self.assertNotIn("com.tw.service.xt", path.read_text(), str(path))

    def test_probe_is_operator_initiated_only(self):
        callers = [p for p in MAIN.rglob("*.java")
                   if "TopwayCapabilityCheck.run(" in p.read_text() and p.parent != OEM]
        self.assertEqual(["SettingsActivity.java"], [p.name for p in callers])

    def test_observation_needs_a_separate_default_off_acknowledgement(self):
        settings = (MAIN / "com/cbkii/ts18launcher/SettingsActivity.java").read_text()
        self.assertIn('.getBoolean("observe_acknowledged", false)', settings)
        self.assertIn("if (observeAllowed) {", settings)
        guard = settings.split("private void runTopwayCheck(boolean observe) {", 1)[1].split("TopwayCapabilityCheck.run(", 1)[0]
        self.assertIn('getBoolean("observe_acknowledged", false)) return;', guard)

    def test_native_radio_has_no_transport(self):
        text = (OEM / "TopwayNativeRadio.java").read_text()
        self.assertNotIn("IBinder", text)
        self.assertNotIn("transact", text)
        self.assertIn("Capability.Status.BLOCKED", text)


if __name__ == "__main__":
    unittest.main()
