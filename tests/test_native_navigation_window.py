import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "launcher/src/main/assets/nav/nav-window.sh"
CONTROLLER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowController.java"
ROOT_HELPER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationRootHelper.java"
LAUNCHER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java"
NAV_PROVIDER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationProvider.java"
POLICY = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/HomeNavigationSurfacePolicy.java"
SETTINGS = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java"
PANEL = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NativeNavigationPanel.java"
MANIFEST = ROOT / "launcher/src/main/AndroidManifest.xml"
PROVIDER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/platform/TopwayDesktopWindowProvider.java"
MARKER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/platform/TopwayDesktopWindowMarkerService.java"
COLLECTOR = ROOT / "scripts/termux/collect-navigation-window-evidence.sh"
POLICY_COLLECTOR = ROOT / "scripts/termux/collect-topway-window-policy-evidence.sh"
PLAYBOOK = ROOT / "docs/NAVIGATION_WINDOW_PHYSICAL_PLAYBOOK.md"
ROADMAP = ROOT / "docs/NAVIGATION_SURFACE_ROADMAP.md"
FIXTURES = ROOT / "tests/fixtures"


class NativeNavigationWindowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.helper = HELPER.read_text(encoding="utf-8")
        cls.controller = CONTROLLER.read_text(encoding="utf-8")
        cls.root_helper = ROOT_HELPER.read_text(encoding="utf-8")
        cls.launcher = LAUNCHER.read_text(encoding="utf-8")
        cls.nav_provider = NAV_PROVIDER.read_text(encoding="utf-8")
        cls.policy = POLICY.read_text(encoding="utf-8")
        cls.settings = SETTINGS.read_text(encoding="utf-8")
        cls.panel = PANEL.read_text(encoding="utf-8")
        cls.manifest = MANIFEST.read_text(encoding="utf-8")
        cls.provider = PROVIDER.read_text(encoding="utf-8")
        cls.marker = MARKER.read_text(encoding="utf-8")
        cls.collector = COLLECTOR.read_text(encoding="utf-8")
        cls.policy_collector = POLICY_COLLECTOR.read_text(encoding="utf-8")
        cls.playbook = PLAYBOOK.read_text(encoding="utf-8")
        cls.roadmap = ROADMAP.read_text(encoding="utf-8")
        match = re.search(
            r"TASK_SNAPSHOT_AWK='(.*?)'\n\nFOREGROUND_TASK_AWK=",
            cls.helper,
            re.DOTALL,
        )
        if match is None:
            raise AssertionError("Production task parser must remain directly fixture-testable")
        cls.task_awk = match.group(1)
        focus_match = re.search(
            r"FOREGROUND_TASK_AWK='(.*?)'\n\nPKG=unknown",
            cls.helper,
            re.DOTALL,
        )
        if focus_match is None:
            raise AssertionError("Foreground task parser must remain directly fixture-testable")
        cls.focus_awk = focus_match.group(1)

    def parse_fixture(self, name, package="app.organicmaps.incar", hint="0", user="0"):
        completed = subprocess.run(
            ["awk", "-v", f"pkg={package}", "-v", f"hint={hint}",
             "-v", f"target_user={user}",
             self.task_awk, str(FIXTURES / name)],
            check=True,
            capture_output=True,
            text=True,
        )
        return completed.stdout.strip()

    def parse_foreground_fixture(self, name):
        completed = subprocess.run(
            ["awk", self.focus_awk, str(FIXTURES / name)],
            check=True,
            capture_output=True,
            text=True,
        )
        return completed.stdout.strip()

    def run_helper(self, args, *, help_text, help_exit, start_output="", start_exit=0,
                   activity_text="Display #0\n  Stack #0: type=home mode=fullscreen\n"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            helper_root = root / "helper"
            bin_dir.mkdir()
            helper_root.mkdir()
            for command in ("awk", "cat", "cut", "grep", "head", "rm", "sleep", "tr"):
                resolved = shutil.which(command)
                if resolved is None:
                    self.fail(f"required test command is unavailable: {command}")
                os.symlink(resolved, bin_dir / command)
            (bin_dir / "id").write_text("#!/bin/sh\nprintf '0\\n'\n", encoding="utf-8")
            (bin_dir / "getprop").write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            activity = root / "activity.txt"
            activity.write_text(activity_text, encoding="utf-8")
            (bin_dir / "dumpsys").write_text(
                "#!/bin/sh\ncat \"$FAKE_ACTIVITY_FILE\"\n", encoding="utf-8"
            )
            (bin_dir / "am").write_text(
                "#!/bin/sh\n"
                "case \"${1:-}\" in\n"
                "  help) printf '%s\\n' \"$FAKE_AM_HELP\"; exit \"$FAKE_AM_HELP_EXIT\" ;;\n"
                "  start) printf '%s\\n' \"$FAKE_AM_START_OUTPUT\"; "
                "exit \"$FAKE_AM_START_EXIT\" ;;\n"
                "  *) exit 0 ;;\n"
                "esac\n",
                encoding="utf-8",
            )
            for fake in ("id", "getprop", "dumpsys", "am"):
                (bin_dir / fake).chmod(0o700)

            test_helper = root / "nav-window.sh"
            source = self.helper.replace(
                "PATH=/system/bin:/system/xbin:/vendor/bin",
                f"PATH={bin_dir}",
                1,
            ).replace(
                "ROOT_DIR=/data/adb/ts18-launcher",
                f"ROOT_DIR={helper_root}",
                1,
            )
            test_helper.write_text(source, encoding="utf-8")
            environment = os.environ.copy()
            environment.update({
                "FAKE_ACTIVITY_FILE": str(activity),
                "FAKE_AM_HELP": help_text,
                "FAKE_AM_HELP_EXIT": str(help_exit),
                "FAKE_AM_START_OUTPUT": start_output,
                "FAKE_AM_START_EXIT": str(start_exit),
            })
            return subprocess.run(
                ["/bin/sh", str(test_helper), *args],
                check=False,
                capture_output=True,
                text=True,
                env=environment,
            )

    def test_task_parser_matches_exact_ts18_freeform_hierarchy(self):
        self.assertEqual(
            "FOUND 9257 3 0 5 475,72,1211,459 "
            "app.organicmaps.incar/app.organicmaps.MwmActivity 0",
            self.parse_fixture("nav-activity-freeform.txt"),
        )

    def test_task_parser_reads_component_from_physical_hist_line(self):
        self.assertEqual(
            "FOUND 9071 12 0 1 0,0,0,0 "
            "app.organicmaps.incar/app.organicmaps.MwmActivity unknown",
            self.parse_fixture("nav-activity-physical-hist.txt"),
        )

    def test_task_parser_ignores_nested_task_record_references(self):
        self.assertEqual(
            "FOUND 9133 4 0 1 0,0,0,0 "
            "app.organicmaps.incar/app.organicmaps.MwmActivity 0",
            self.parse_fixture("nav-activity-physical-nested-task.txt"),
        )
        self.assertIn(
            'in_task && !matched && /^[[:space:]]*\\* TaskRecord\\{/',
            self.helper,
        )
        self.assertIn('matched && /^[[:space:]]*\\*?[[:space:]]*Hist #0:/', self.helper)

    def test_foreground_parser_reads_resumed_task_identity(self):
        self.assertEqual("9258", self.parse_foreground_fixture("nav-activity-fullscreen.txt"))

    def test_task_parser_preserves_component_unknown_as_observation(self):
        self.assertEqual(
            "FOUND 9078 14 0 5 475,72,1211,459 unknown unknown",
            self.parse_fixture("nav-activity-component-unknown.txt"),
        )
        component_check = self.helper.split("validate_observed_component()", 1)[1].split(
            "require_task()", 1)[0]
        self.assertIn("unknown|'') return 0", component_check)
        self.assertIn("*) fail COMPONENT_MISMATCH", component_check)

    def test_task_parser_normalises_same_package_shorthand(self):
        self.assertEqual(
            "FOUND 9079 15 0 5 475,72,1211,459 "
            "app.organicmaps.incar/app.organicmaps.incar.MwmActivity unknown",
            self.parse_fixture("nav-activity-shorthand.txt"),
        )

    def test_package_only_acquisition_fails_ambiguous_tasks_closed(self):
        self.assertEqual("AMBIGUOUS 2", self.parse_fixture("nav-activity-ambiguous.txt"))
        self.assertTrue(self.parse_fixture("nav-activity-ambiguous.txt", hint="1002").startswith(
            "FOUND 1002 8 0 5 200,120,700,520 "))

    def test_task_parser_rejects_a_task_owned_by_another_android_user(self):
        self.assertEqual("NONE", self.parse_fixture("nav-activity-freeform.txt", user="10"))

    def test_parser_uses_task_bounds_not_nested_activity_configuration(self):
        result = self.parse_fixture("nav-activity-freeform.txt")
        self.assertIn(" 5 475,72,1211,459 ", result)
        self.assertNotIn("1280,720", result)

    def test_helper_owns_one_mode5_acquire_launch_resize_verify_transaction(self):
        present = self.helper.split("  present-native)", 1)[1].split("  verify-native)", 1)[0]
        self.assertIn("present-native)", self.helper)
        self.assertIn('read_task_once "$PKG" 0', self.helper)
        self.assertIn('2) fail TASK_AMBIGUOUS', self.helper)
        cold_launch = self.helper.split("launch_freeform_once()", 1)[1].split("move_task_fullscreen()", 1)[0]
        self.assertEqual(1, cold_launch.count(
            'am start --user "$ANDROID_USER" --display 0 --windowingMode 5'))
        self.assertIn("-a android.intent.action.MAIN -c android.intent.category.LAUNCHER", self.helper)
        self.assertIn('am task resizeable "$wanted_task" 2', self.helper)
        self.assertIn('am task resize "$wanted_task" "$left" "$top" "$right" "$bottom"', self.helper)
        self.assertIn('verify_state 5 "$expected"', self.helper)
        self.assertIn("launched=%s transaction=%s", self.helper)
        self.assertIn("helpExit=%s helpWindowingMode=%s helpDisplay=%s launchExit=%s", self.helper)
        self.assertIn("logCapabilityEvidence(result)", self.controller)
        self.assertRegex(
            present,
            r"0\) validate_observed_component ;;\s*1\)\s*launch_freeform_once",
        )
        self.assertEqual(1, present.count('launch_freeform_once "$launch_component"'))
        self.assertRegex(present, r"2\) fail TASK_AMBIGUOUS.*;;")

    def test_help_flags_are_authoritative_even_when_help_exits_255(self):
        completed = self.run_helper(
            ["probe", "0"],
            help_text="usage: am start [--display DISPLAY_ID] [--windowingMode WINDOWING_MODE]",
            help_exit=255,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("nativeLaunch=1", completed.stdout)
        self.assertIn("helpExit=255", completed.stdout)
        self.assertIn("helpWindowingMode=1", completed.stdout)
        self.assertIn("helpDisplay=1", completed.stdout)

    def test_help_missing_either_flag_is_unsupported(self):
        for help_text in (
            "usage: am start [--display DISPLAY_ID]",
            "usage: am start [--windowingMode WINDOWING_MODE]",
        ):
            with self.subTest(help_text=help_text):
                completed = self.run_helper(["probe", "0"], help_text=help_text, help_exit=0)
                self.assertEqual(0, completed.returncode, completed.stderr)
                self.assertIn("nativeLaunch=0", completed.stdout)

    def test_actual_unknown_option_launch_is_classified_unsupported(self):
        completed = self.run_helper(
            ["present-native", "0", "app.organicmaps.incar",
             "app.organicmaps.incar/app.organicmaps.MwmActivity",
             "524", "77", "1174", "453", "0", "1"],
            help_text="usage: am start [--display DISPLAY_ID] [--windowingMode WINDOWING_MODE]",
            help_exit=255,
            start_output="Error: Unknown option: --windowingMode",
            start_exit=64,
        )
        self.assertEqual(1, completed.returncode)
        self.assertIn("code=FREEFORM_LAUNCH_UNSUPPORTED", completed.stdout)
        self.assertIn("helpExit=255", completed.stdout)
        self.assertIn("launchExit=64", completed.stdout)

    def test_other_actual_launch_failure_is_classified_failed(self):
        completed = self.run_helper(
            ["present-native", "0", "app.organicmaps.incar",
             "app.organicmaps.incar/app.organicmaps.MwmActivity",
             "524", "77", "1174", "453", "0", "1"],
            help_text="usage: am start [--display DISPLAY_ID] [--windowingMode WINDOWING_MODE]",
            help_exit=255,
            start_output="Error: Activity not started, unable to resolve Intent",
            start_exit=1,
        )
        self.assertEqual(1, completed.returncode)
        self.assertIn("code=FREEFORM_LAUNCH_FAILED", completed.stdout)
        self.assertIn("launchExit=1", completed.stdout)

    def test_standard_android_pip_is_absent_from_production_path(self):
        self.assertFalse((ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/AndroidPipBackend.java").exists())
        for token in ("verify-pip", "move-top-activity-to-pinned-stack", "PIP_OCCUPIED_BY_OTHER_APP"):
            self.assertNotIn(token, self.helper)
        self.assertNotIn("ANDROID_PIP", self.controller + self.policy + self.settings)

    def test_java_resolves_exported_launcher_component_before_root_transaction(self):
        self.assertIn("resolveLaunchComponent(configuredPackage)", self.controller)
        self.assertIn("getLaunchIntentForPackage(pkg)", self.controller)
        self.assertIn("!info.exported", self.controller)
        self.assertIn("component.flattenToString()", self.controller)
        self.assertIn("backend.present(pkg, component, target, taskHint, operation", self.controller)
        self.assertIn("COMPONENT", self.root_helper)
        self.assertIn("singleQuote(arg)", self.root_helper)

    def test_controller_never_performs_fullscreen_first_java_launch(self):
        self.assertNotIn("ActivityOptions", self.controller)
        self.assertNotIn("startActivity(", self.controller)
        self.assertNotIn("POST_LAUNCH_RECONCILE", self.controller)
        self.assertNotIn("POST_LAUNCH_RECONCILE", self.controller)
        self.assertIn("park-timeout", self.controller)

    def test_home_stop_preserves_inflight_transaction_and_backend(self):
        stop_body = self.controller.split("void onHomeStopped()", 1)[1].split(
            "void onLauncherOverlayOpened()", 1)[0]
        self.assertIn("transaction retained", stop_body)
        self.assertIn("task authority retained without relaunch", stop_body)
        self.assertNotIn("destroyBackendInstance", stop_body)
        self.assertNotIn("authorityGeneration++", stop_body)
        self.assertNotIn("activeTaskId = -1", stop_body)

    def test_home_return_clears_drawer_overlay_and_stale_suspend(self):
        home_body = self.controller.split("void onHomeVisible()", 1)[1].split(
            "void onHomeStopped()", 1
        )[0]
        overlay_body = self.controller.split("void onLauncherOverlayOpened()", 1)[1].split(
            "void suspendForExperimentalMap()", 1
        )[0]
        ui_state = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/NavigationWindowUiState.java").read_text()
        ui_test = (ROOT / "launcher/src/test/java/com/cbkii/ts18launcher/NavigationWindowUiStateTest.java").read_text()

        self.assertIn("uiState.onHomeVisible()", home_body)
        self.assertIn('pendingSuspendReason = "";', home_body)
        self.assertIn("!uiState.onLauncherOverlayOpened()", overlay_body)
        self.assertIn("launcherOverlayOpen = false", ui_state)
        self.assertIn("homeReturnClearsDrawerOverlaySuppression", ui_test)
        self.assertNotIn("void onLauncherOverlayClosed()", self.controller)

    def test_inflight_callbacks_coalesce_and_cannot_launch_again(self):
        self.assertIn("if (activeOperationId != 0)", self.controller)
        self.assertIn("pendingReconcile = true", self.controller)
        self.assertIn("acquisitionAttemptGeneration == authorityGeneration", self.controller)
        self.assertIn("Acquisition already attempted; use Retry", self.controller)
        self.assertIn("beginReacquisitionGeneration()", self.controller)

    def test_failure_latch_is_explicit_retry_and_fullscreen_only(self):
        self.assertIn("failureGeneration == authorityGeneration", self.controller)
        self.assertIn("panel.showFailure", self.controller)
        self.assertIn('setActions("Retry",retry,"Open fullscreen",openFullscreen)', self.panel)
        self.assertIn("private void retry()", self.controller)
        self.assertNotIn("NavigationProvider.open(activity, pkg, null));\n        Log.w(TAG, \"native navigation failed", self.controller)

    def test_success_requires_current_user_component_task_display_mode_and_bounds(self):
        for token in (
            "result.userId != AndroidUserId.current()",
            "result.taskId != expectedTask",
            '"unknown".equals(component)',
            "result.displayId == 0",
            "result.windowingMode == 5",
            "target.toString().equals(result.bounds)",
        ):
            self.assertIn(token, self.controller)
        self.assertIn('&& component.startsWith(pkg + "/")', self.controller)
        self.assertIn("panel.showConfigured", self.controller)
        self.assertIn("Physical visibility and touch are not inferred", self.controller)
        self.assertNotIn("showReady", self.controller + self.panel)

    def test_fullscreen_handoff_and_home_return_preserve_task_authority(self):
        self.assertIn("backend.fullscreen(pkg, task", self.controller)
        self.assertIn("activeTaskId = result.taskId", self.controller)
        self.assertIn("if (state == State.FULLSCREEN_HANDOFF)", self.controller)
        self.assertIn("needsValidation = true", self.controller)
        self.assertIn("navigationWindowController.openFullscreen(location)) return;", self.launcher)
        self.assertIn('require_foreground_task "$wanted_task" FULLSCREEN_NOT_FOREGROUND', self.helper)
        self.assertIn('require_foreground_task "$home_task" HOME_NOT_FOREGROUND', self.helper)
        self.assertIn('[ "$component" != unknown ] || fail COMPONENT_UNKNOWN', self.helper)

    def test_native_surface_is_primary_and_leaflet_is_explicit_legacy_fallback(self):
        self.assertIn('static final String NATIVE_WINDOW = "native_window"', self.policy)
        self.assertIn('private static final String LEGACY_RAW_FREEFORM = "raw_freeform"', self.policy)
        self.assertIn("return NATIVE_WINDOW;", self.policy)
        for value in (
            "Native navigation window · TESTING",
            "Fullscreen only · safe fallback",
            "Legacy online map fallback · Internet required",
        ):
            self.assertIn(value, self.settings)
        self.assertNotIn("Android PiP · experimental", self.settings)

    def test_navigation_owned_incar_fallback_uses_real_launcher_availability(self):
        self.assertIn('ORGANIC_MAPS_INCAR = "app.organicmaps.incar"', self.nav_provider)
        self.assertIn("static boolean hasLauncherActivity", self.nav_provider)
        self.assertIn("getLaunchIntentForPackage(packageName) != null", self.nav_provider)
        self.assertIn(
            "NavigationProvider.hasLauncherActivity(activity, NavigationProvider.ORGANIC_MAPS_INCAR)",
            self.controller,
        )

    def test_event_driven_collector_is_read_only_and_seals_on_ctrl_c(self):
        self.assertIn("OBSERVATION READY", self.collector)
        self.assertIn("state-change", self.collector)
        self.assertIn("trap request_stop INT TERM HUP", self.collector)
        self.assertNotIn("--observe-seconds", self.collector)
        for token in (
            "am force-stop", "am task resize", "am task focus", "input keyevent",
            "input swipe", "settings put", "setprop ", "pm disable", "pm clear",
        ):
            self.assertNotIn(token, self.collector)
        for token in (
            "launcher-apk-sha256", "resolved-launch-component", "am-help.txt",
            "classpaths.txt", "system-server-maps.txt", "anchor-strings.txt",
            "magisk-lsposed-metadata", "state-initial.txt", "state-final.txt",
            "final-activity.txt", "final-window.txt", "final-input.txt",
            "final-surfaceflinger.txt", "status-final.txt",
        ):
            self.assertIn(token, self.collector)
        for token in (
            "dumpsys input", "InputDispatcher", "surface/checkpoints",
            "dumpsys SurfaceFlinger", "discovery/APPROACH_MATRIX.txt",
            "ShellTaskOrganizer", "WindowContainerTransaction", "VirtualDisplay",
            "DESKTOP_WINDOW_SERVICE", "DESKTOP_FLOATING_APP_SERVICE",
            "FLOATING_WINDOW_SERVER", "broad-discovery",
        ):
            self.assertIn(token, self.collector)
        self.assertIn("am_help_exit=%s", self.collector)
        self.assertIn("cmd_activity_help_exit=%s", self.collector)

    def test_evidence_archives_verify_immutable_manifest_and_archive_hash(self):
        for script in (self.collector, self.policy_collector):
            self.assertIn("! -name SHA256SUMS.txt ! -name MANIFEST_VERIFY.txt", script)
            self.assertIn("sha256sum -c SHA256SUMS.txt", script)
            self.assertIn('sha256sum "$ZIP" >"$ZIP.sha256"', script)
            manifest_start = script.index("sha256sum -c SHA256SUMS.txt")
            self.assertNotIn('>>"$OUT/summary.txt"', script[manifest_start:])

    def test_playbook_and_roadmap_keep_oem_policy_behind_physical_gate(self):
        self.assertIn("at your own pace", self.playbook)
        self.assertIn("Press Ctrl-C once", self.playbook)
        self.assertIn("Mode 5 and exact bounds", self.playbook)
        self.assertIn("initial bounded rendering demonstrated on `PR11-5aea8bc`", self.roadmap)
        self.assertIn("no mode-5 launch or resize occurred", self.playbook)
        self.assertIn("Phase 3 - conditional Topway policy recovery", self.roadmap)
        self.assertIn("log-only, exact-build-hash-gated LSPosed trace", self.roadmap)
        self.assertIn("Do not fabricate", self.roadmap)

    def test_video_binder_is_not_navigation_transport(self):
        combined = self.controller + self.helper
        self.assertNotIn("FLOATING_WINDOW_SERVER", combined)
        self.assertIn("DESKTOP_WINDOW_SERVICE", self.manifest)
        self.assertIn("return null;", self.marker)
        self.assertIn("zero rows => no WindowInfo", self.provider)
        self.assertIn("UnsupportedOperationException", self.provider)

    def test_helper_has_no_topway_state_writer(self):
        for token in ("setprop persist.tw", "setprop sys.tw", ">/data/tw/", "> /data/tw/"):
            self.assertNotIn(token, self.helper)

    def test_testing_build_has_exact_source_provenance(self):
        gradle = (ROOT / "launcher/build.gradle.kts").read_text()
        workflow = (ROOT / ".github/workflows/validate.yml").read_text()
        self.assertIn("SOURCE_REVISION", gradle)
        self.assertIn("SOURCE_REF", gradle)
        self.assertIn("source_sha=$SOURCE_SHA", workflow)
        self.assertIn("validate_run_id=$GITHUB_RUN_ID", workflow)


if __name__ == "__main__":
    unittest.main()
