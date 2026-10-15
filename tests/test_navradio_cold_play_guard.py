import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOTSTRAP = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaSourceBootstrapper.java").read_text(encoding="utf-8")


class NavRadioColdPlayGuardTest(unittest.TestCase):
    def test_cold_navradio_play_waits_for_service_settle_then_dispatches_once(self):
        self.assertIn("NAVRADIO_COLD_PLAY_NOT_BEFORE_MS = 3200L", BOOTSTRAP)
        self.assertIn("coldPlayNotBeforeMs", BOOTSTRAP)
        self.assertIn("pending.coldPlayGuarded = true", BOOTSTRAP)
        self.assertIn('MediaEventTrace.record("service", "cold-play-guard"', BOOTSTRAP)
        self.assertIn("boolean guardedColdPlay = pending.coldPlayGuarded", BOOTSTRAP)
        self.assertIn("if (!guardedColdPlay && MediaCommandPolicy.acknowledged", BOOTSTRAP)
        self.assertIn("if (!sendDesired(controller, pending.desired))", BOOTSTRAP)
        self.assertIn("pending.dispatched = true", BOOTSTRAP)
        self.assertNotIn("sendDesired(controller, pending.desired);\n        sendDesired", BOOTSTRAP)

    def test_guard_is_exact_navradio_play_only(self):
        method = BOOTSTRAP.split("private long coldPlayNotBeforeMs", 1)[1].split(
            "private void dispatchToController", 1
        )[0]
        self.assertIn("MediaCommandPolicy.Desired.PLAY", method)
        self.assertIn("MediaSourceAdapter.NAVRADIO_PACKAGE", method)
        self.assertIn("ServiceStart start = serviceStarts.get", method)


if __name__ == "__main__":
    unittest.main()
