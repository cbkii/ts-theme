import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BACKEND = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/RootNavigationBackend.java").read_text(encoding="utf-8")
RESULT = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationHelperResult.java").read_text(encoding="utf-8")
CONTROLLER = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java").read_text(encoding="utf-8")


def between(source: str, start: str, end: str) -> str:
    return source.split(start, 1)[1].split(end, 1)[0]


class NavigationTransientTaskMissTest(unittest.TestCase):
    def test_same_parser_miss_requires_independent_absence_corroboration(self):
        policy = between(BACKEND,
                         "private NavigationHelperResult retryKnownTaskMiss",
                         "private AbsenceEvidence corroborateKnownTask")
        corroborate = between(BACKEND,
                              "private AbsenceEvidence corroborateKnownTask",
                              "private static boolean safePackage")
        self.assertIn('"TASK_NOT_FOUND".equals(first.code)', policy)
        self.assertIn("Thread.sleep(KNOWN_TASK_RECHECK_DELAY_MS)", policy)
        self.assertIn("AbsenceEvidence evidence = corroborateKnownTask", policy)
        self.assertIn("if (evidence == AbsenceEvidence.ABSENT) return second;", policy)
        self.assertIn('NavigationHelperResult.withCode(\n                    cached, "TASK_OBSERVATION_UNCERTAIN"', policy)
        self.assertIn("dumpsys activity recents 2>/dev/null", corroborate)
        self.assertIn('" A=" + packageName', corroborate)
        self.assertIn("+ \" ' >/dev/null && exit 0; \"", corroborate)
        self.assertIn("+ \"}' >/dev/null && exit 0; \"", corroborate)
        self.assertIn('"pidof " + packageName', corroborate)

    def test_uncertainty_keeps_identity_but_is_not_success(self):
        with_code = between(RESULT,
                            "static NavigationHelperResult withCode",
                            "private static String lastProtocolLine")
        self.assertIn("new NavigationHelperResult(false, code", with_code)
        retain = between(CONTROLLER,
                         "private void retainObservedTask",
                         "private void markWindowed")
        self.assertIn('"TASK_OBSERVATION_UNCERTAIN".equals(result.code)', retain)
        self.assertIn("result.taskId > 0", retain)

    def test_uncertain_resume_uses_existing_non_mutating_controller_path(self):
        policy = between(BACKEND,
                         "private NavigationHelperResult retryKnownTaskMiss",
                         "private AbsenceEvidence corroborateKnownTask")
        self.assertIn('"resume".equals(phase)', policy)
        self.assertIn('NavigationHelperResult.failure("FOREGROUND_CHANGED"', policy)
        controller_resume = between(CONTROLLER, "private void startResume", "private void startPresent")
        foreground_branch = controller_resume.split('if ("FOREGROUND_CHANGED".equals(result.code))', 1)[1].split(
            'if ("TASK_NOT_FOUND".equals(result.code))', 1
        )[0]
        self.assertIn("needsPresentation = true", foreground_branch)
        self.assertNotIn("activeTaskId = -1", foreground_branch)
        self.assertNotIn("startPresent(", foreground_branch)

    def test_uncertain_verify_and_pre_present_defer_without_repair_or_latch(self):
        verify = between(CONTROLLER, "private void startVerify", "private void startResume")
        present = between(CONTROLLER, "private void startPresent", "private void beginReacquisitionGeneration")
        helper = between(CONTROLLER,
                         "private boolean deferUncertainTaskObservation",
                         "private int beginOperation")
        self.assertIn('deferUncertainTaskObservation(result, pkg, "verify")', verify)
        self.assertIn('deferUncertainTaskObservation(result, pkg, "pre-present")', present)
        self.assertIn('"TASK_OBSERVATION_UNCERTAIN".equals(result.code)', helper)
        self.assertIn("needsValidation = true", helper)
        self.assertIn("needsPresentation = true", helper)
        self.assertNotIn("activeTaskId = -1", helper)
        self.assertNotIn("latchFailure", helper)
        self.assertNotIn("startPresent(", helper)

    def test_present_backend_stops_before_mutation_when_observation_is_uncertain(self):
        present = between(BACKEND, "@Override public void present", "@Override public void verify")
        uncertain = present.split('if ("TASK_OBSERVATION_UNCERTAIN".equals(verified.code))', 1)[1].split(
            'if ("TASK_NOT_FOUND"', 1
        )[0]
        self.assertIn("return verified", uncertain)
        self.assertNotIn('helper.run("present-native"', uncertain)
        self.assertIn("rememberKnownTask(result, packageName, taskId)", present)

    def test_windowed_cache_is_scoped_and_thread_visible(self):
        self.assertIn("private volatile NavigationHelperResult lastKnownTaskObservation", BACKEND)
        remember = between(BACKEND, "private void rememberKnownTask", "private NavigationHelperResult cachedKnownTask")
        cached = between(BACKEND, "private NavigationHelperResult cachedKnownTask", "private static String taskHint")
        self.assertIn("result.windowingMode != 5", remember)
        self.assertIn("cached.windowingMode != 5", cached)
        fullscreen = between(BACKEND, "@Override public void fullscreen", "@Override public void suspend")
        self.assertNotIn("rememberKnownTask", fullscreen)

    def test_independently_proven_absence_can_still_clear_authority(self):
        verify = between(CONTROLLER, "private void startVerify", "private void startResume")
        not_found = verify.split('if ("TASK_NOT_FOUND".equals(result.code))', 1)[1].split(
            'if ("TASK_AMBIGUOUS"', 1
        )[0]
        self.assertIn("activeTaskId = -1", not_found)
        self.assertIn("latchFailure", not_found)


if __name__ == "__main__":
    unittest.main()
