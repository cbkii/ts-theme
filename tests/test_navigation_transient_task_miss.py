import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java").read_text(encoding="utf-8")
CONTROLLER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java").read_text(encoding="utf-8")


class NavigationTransientTaskMissTest(unittest.TestCase):
    def test_known_task_not_found_is_rechecked_before_authority_is_cleared(self):
        self.assertIn("KNOWN_TASK_RECHECK_DELAY_MS", BACKEND)
        self.assertIn("retryKnownTaskMiss", BACKEND)
        self.assertIn('"TASK_NOT_FOUND".equals(first.code)', BACKEND)
        self.assertIn("Thread.sleep(KNOWN_TASK_RECHECK_DELAY_MS)", BACKEND)
        self.assertIn("transient TASK_NOT_FOUND", BACKEND)
        # Controller still fails closed if the bounded backend recheck also reports absence.
        self.assertIn('if ("TASK_NOT_FOUND".equals(result.code))', CONTROLLER)
        self.assertIn('activeTaskId = -1', CONTROLLER)

    def test_recheck_covers_verify_and_resume_without_relaunching(self):
        verify = BACKEND.split("@Override public void verify", 1)[1].split("@Override public void resume", 1)[0]
        resume = BACKEND.split("@Override public void resume", 1)[1].split("@Override public void status", 1)[0]
        self.assertIn("retryKnownTaskMiss", verify)
        self.assertIn("retryKnownTaskMiss", resume)
        self.assertNotIn("present-native", verify)
        self.assertNotIn("present-native", resume)


if __name__ == "__main__":
    unittest.main()
