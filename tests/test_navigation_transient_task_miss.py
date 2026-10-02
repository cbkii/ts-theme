import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java").read_text(encoding="utf-8")
RESULT = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationHelperResult.java").read_text(encoding="utf-8")
CONTROLLER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java").read_text(encoding="utf-8")


class NavigationTransientTaskMissTest(unittest.TestCase):
    def test_same_parser_miss_requires_independent_absence_corroboration(self):
        self.assertIn("KNOWN_TASK_RECHECK_DELAY_MS", BACKEND)
        self.assertIn("retryKnownTaskMiss", BACKEND)
        self.assertIn('"TASK_NOT_FOUND".equals(first.code)', BACKEND)
        self.assertIn("Thread.sleep(KNOWN_TASK_RECHECK_DELAY_MS)", BACKEND)
        self.assertIn("corroborateKnownTask", BACKEND)
        self.assertIn("dumpsys activity recents", BACKEND)
        self.assertIn("pidof", BACKEND)
        self.assertIn("AbsenceEvidence.ABSENT", BACKEND)
        self.assertIn("TASK_OBSERVATION_UNCERTAIN", BACKEND)
        self.assertIn("withCode", RESULT)

    def test_uncertain_resume_uses_existing_non_mutating_controller_path(self):
        resume = BACKEND.split("@Override public void resume", 1)[1].split(
            "@Override public void status", 1
        )[0]
        self.assertIn("retryKnownTaskMiss", resume)
        policy = BACKEND.split("private NavigationHelperResult retryKnownTaskMiss", 1)[1].split(
            "private AbsenceEvidence corroborateKnownTask", 1
        )[0]
        self.assertIn('"resume".equals(phase)', policy)
        self.assertIn('NavigationHelperResult.failure("FOREGROUND_CHANGED"', policy)
        controller_resume = CONTROLLER.split("private void startResume", 1)[1].split(
            "private void startPresent", 1
        )[0]
        self.assertIn('"FOREGROUND_CHANGED".equals(result.code)', controller_resume)
        self.assertNotIn("activeTaskId = -1", controller_resume.split('"FOREGROUND_CHANGED"', 1)[1].split("return;", 1)[0])

    def test_uncertain_pre_present_does_not_issue_present_native(self):
        present = BACKEND.split("@Override public void present", 1)[1].split(
            "@Override public void verify", 1
        )[0]
        uncertain = present.split('"TASK_OBSERVATION_UNCERTAIN".equals(verified.code)', 1)[1].split(
            'if ("TASK_NOT_FOUND"', 1
        )[0]
        self.assertIn("return verified", uncertain)
        self.assertNotIn('helper.run("present-native"', uncertain)

    def test_absence_can_still_clear_authority_when_independently_proven(self):
        self.assertIn('if (evidence == AbsenceEvidence.ABSENT) return second;', BACKEND)
        self.assertIn('if ("TASK_NOT_FOUND".equals(result.code))', CONTROLLER)
        self.assertIn("activeTaskId = -1", CONTROLLER)


if __name__ == "__main__":
    unittest.main()
