import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "launcher/src/main/assets/nav/nav-window.sh"
CONTROLLER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java"
POLICY = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/HomeNavigationSurfacePolicy.java"
SETTINGS = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java"
MANIFEST = ROOT / "launcher/src/main/AndroidManifest.xml"
PROVIDER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/platform/TopwayDesktopWindowProvider.java"
MARKER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/platform/TopwayDesktopWindowMarkerService.java"
QUALIFIER = ROOT / "scripts/termux/qualify-navigation-surfaces.sh"
POLICY_COLLECTOR = ROOT / "scripts/termux/collect-topway-window-policy-evidence.sh"
FIXTURES = ROOT / "tests/fixtures"


class NavigationSurfaceExperimentContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.helper = HELPER.read_text(encoding="utf-8")
        cls.controller = CONTROLLER.read_text(encoding="utf-8")
        cls.policy = POLICY.read_text(encoding="utf-8")
        cls.settings = SETTINGS.read_text(encoding="utf-8")
        cls.manifest = MANIFEST.read_text(encoding="utf-8")
        cls.provider = PROVIDER.read_text(encoding="utf-8")
        cls.marker = MARKER.read_text(encoding="utf-8")
        cls.qualifier = QUALIFIER.read_text(encoding="utf-8")
        cls.policy_collector = POLICY_COLLECTOR.read_text(encoding="utf-8")
        match = re.search(
            r"TASK_SNAPSHOT_AWK='(.*?)'\n\nPINNED_SNAPSHOT_AWK=",
            cls.helper,
            re.DOTALL,
        )
        if match is None:
            raise AssertionError("Production task parser must remain directly fixture-testable")
        cls.task_awk = match.group(1)

    def parse_fixture(self, name, package="app.organicmaps.incar", hint="0"):
        completed = subprocess.run(
            ["awk", "-v", f"pkg={package}", "-v", f"hint={hint}",
             self.task_awk, str(FIXTURES / name)],
            check=True,
            capture_output=True,
            text=True,
        )
        return completed.stdout.strip()

    def test_task_parser_matches_exact_ts18_freeform_hierarchy(self):
        self.assertEqual(
            "FOUND 9257 3 0 5 475,72,1211,459 "
            "app.organicmaps.incar/app.organicmaps.MwmActivity 0",
            self.parse_fixture("nav-activity-freeform.txt"),
        )

    def test_task_parser_matches_exact_ts18_fullscreen_hierarchy(self):
        self.assertEqual(
            "FOUND 9258 4 0 1 0,0,0,0 "
            "app.organicmaps.incar/app.organicmaps.MwmActivity 0",
            self.parse_fixture("nav-activity-fullscreen.txt"),
        )

    def test_package_only_acquisition_fails_ambiguous_tasks_closed(self):
        self.assertEqual("AMBIGUOUS 2", self.parse_fixture("nav-activity-ambiguous.txt"))
        self.assertTrue(self.parse_fixture("nav-activity-ambiguous.txt", hint="1002").startswith(
            "FOUND 1002 8 0 5 200,120,700,520 "))

    def test_parser_uses_task_bounds_not_nested_activity_configuration(self):
        result = self.parse_fixture("nav-activity-freeform.txt")
        self.assertIn(" 5 475,72,1211,459 ", result)
        self.assertNotIn("1280,720", result)

    def test_freeform_and_pip_are_separate_backends(self):
        self.assertIn('presentAction() { return "freeform"; }',
                      (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/RawFreeformTaskBackend.java").read_text())
        self.assertIn('presentAction() { return "pip"; }',
                      (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/AndroidPipBackend.java").read_text())

    def test_pip_uses_android10_four_coordinate_shell_contract(self):
        self.assertIn('am stack move-top-activity-to-pinned-stack "$STACK_ID" \\\n          "$left" "$top" "$right" "$bottom"', self.helper)
        self.assertIn('am stack resize "$STACK_ID" "$left" "$top" "$right" "$bottom"', self.helper)
        self.assertNotIn('move-top-activity-to-pinned-stack "$STACK_ID" "$expected"', self.helper)
        self.assertNotIn('am stack resize "$STACK_ID" "$expected"', self.helper)

    def test_pip_never_enables_global_force_resizable_or_steals_foreign_pip(self):
        self.assertIn("PIP_OCCUPIED_BY_OTHER_APP", self.helper)
        self.assertIn("force_resizable_activities", self.helper)
        self.assertNotRegex(self.helper, r"settings\s+put\s+global\s+force_resizable_activities")

    def test_controller_repairs_known_task_before_relaunch(self):
        self.assertIn("repair the same task", self.controller)
        self.assertIn("TASK_NOT_FOUND", self.controller)
        self.assertIn("backend.present(pkg, target, knownTask", self.controller)

    def test_fullscreen_handoff_does_not_relaunch_without_location_semantics(self):
        self.assertIn("The helper already foregrounded the exact authorised task", self.controller)
        self.assertRegex(
            self.controller,
            r"if \(location != null && !NavigationProvider\.open\(activity, pkg, location\)\)",
        )
        self.assertIn("HOME stopped during intentional fullscreen handoff", self.controller)
        self.assertIn("if (state == State.FULLSCREEN_HANDOFF) state = State.IDLE;", self.controller)

    def test_explicit_launcher_overlay_suspends_exact_task_without_force_stop(self):
        self.assertIn("backend.suspend(pkg, task, activity.getPackageName(), activity.getTaskId()", self.controller)
        self.assertIn("suspend)", self.helper)
        self.assertIn('am task focus "$home_task"', self.helper)
        self.assertNotIn("force-stop", self.helper)

    def test_home_stop_cancels_helper_work_but_keeps_task_authority(self):
        self.assertIn("helper work cancelled, task authority retained", self.controller)
        stop_body = self.controller.split("void onHomeStopped()", 1)[1].split("void onLauncherOverlayOpened()", 1)[0]
        self.assertIn("destroyBackendInstance();", stop_body)
        self.assertNotIn("activeTaskId = -1", stop_body)

    def test_single_surface_policy_has_safe_default_and_true_one_time_legacy_migration(self):
        self.assertIn('static final String FULLSCREEN = "fullscreen"', self.policy)
        self.assertIn("if (prefs.contains(KEY))", self.policy)
        self.assertIn("return isKnown(stored) ? stored : FULLSCREEN;", self.policy)
        new_key_branch = self.policy.split("if (prefs.contains(KEY))", 1)[1].split("// Legacy", 1)[0]
        self.assertNotIn("KEY_MAP_ENABLED", new_key_branch)
        self.assertIn('"HOME navigation surface"', self.settings)

    def test_success_checks_real_mode_display_bounds_component(self):
        for token in ["result.displayId == 0", "result.windowingMode == expectedMode",
                      "target.toString().equals(result.bounds)", "result.component.startsWith(pkg + \"/\")"]:
            self.assertIn(token, self.controller)

    def test_qualification_uses_live_home_authority_and_verified_task_bounds(self):
        self.assertIn('case "$home_component" in', self.qualifier)
        self.assertIn('result BLOCKED home-authority', self.qualifier)
        self.assertIn("TARGET_SOURCE=configured", self.qualifier)
        self.assertIn("TARGET_SOURCE=implicit-organicmaps-fallback", self.qualifier)
        self.assertIn("TARGET_SOURCE=argument-only", self.qualifier)
        self.assertIn("result BLOCKED configuration-authority", self.qualifier)
        self.assertIn('GESTURE_BOUNDS="$bounds"', self.qualifier)
        self.assertIn('input swipe $x $y $x2 $y 250', self.qualifier)
        self.assertNotIn("input swipe 650 360 700 360 250", self.qualifier)
        self.assertIn("launcher-apk-sha256.txt", self.qualifier)

    def test_evidence_archives_verify_immutable_manifest_and_archive_hash(self):
        for script in (self.qualifier, self.policy_collector):
            self.assertIn("! -name SHA256SUMS.txt ! -name MANIFEST_VERIFY.txt", script)
            self.assertIn("sha256sum -c SHA256SUMS.txt", script)
            self.assertIn('sha256sum "$ZIP" >"$ZIP.sha256"', script)
            manifest_start = script.index("sha256sum -c SHA256SUMS.txt")
            self.assertNotIn('>>"$OUT/summary.txt"', script[manifest_start:])
            self.assertNotIn('>>"$OUT/status.tsv"', script[manifest_start:])

    def test_topway_policy_collector_keeps_live_log_narrow_and_read_only(self):
        self.assertIn("live-relevant.txt", self.policy_collector)
        self.assertIn("grep -Ei 'isPipLauncher|sendNaviType|forcepip|windowingMode", self.policy_collector)
        self.assertNotIn('>"$OUT/logs/live.txt"', self.policy_collector)
        for token in ("setprop", "settings put", "am force-stop", "pm disable", "setenforce 0"):
            self.assertNotIn(token, self.policy_collector)

    def test_video_binder_is_not_navigation_transport(self):
        combined = self.controller + self.helper
        self.assertNotIn("FLOATING_WINDOW_SERVER", combined)
        self.assertIn("DESKTOP_WINDOW_SERVICE", self.manifest)
        self.assertIn("return null;", self.marker)
        self.assertIn("zero rows => no WindowInfo", self.provider)
        self.assertIn("UnsupportedOperationException", self.provider)

    def test_helper_has_no_topway_state_writer(self):
        forbidden = ["setprop persist.tw", "setprop sys.tw", ">/data/tw/", "> /data/tw/"]
        for token in forbidden:
            self.assertNotIn(token, self.helper)

    def test_settings_exposes_one_explicit_four_way_surface_selector(self):
        for value in ["Fullscreen only · safe fallback", "Leaflet comparator",
                      "Raw freeform task · experimental", "Android PiP · experimental"]:
            self.assertIn(value, self.settings)

    def test_testing_build_has_exact_source_provenance(self):
        gradle = (ROOT / "launcher/build.gradle.kts").read_text()
        workflow = (ROOT / ".github/workflows/validate.yml").read_text()
        self.assertIn("SOURCE_REVISION", gradle)
        self.assertIn("SOURCE_REF", gradle)
        self.assertIn("source_sha=$SOURCE_SHA", workflow)
        self.assertIn("validate_run_id=$GITHUB_RUN_ID", workflow)


if __name__ == "__main__":
    unittest.main()
