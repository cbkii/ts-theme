from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher"
HELPER = ROOT / "launcher/src/main/assets/nav/nav-window.sh"


class NavigationSurfaceExperimentContractTest(unittest.TestCase):
    def test_single_surface_policy_has_safe_default_and_legacy_leaflet_migration(self):
        source = (JAVA / "HomeNavigationSurfacePolicy.java").read_text()
        for value in ("fullscreen", "leaflet", "raw_freeform", "android_pip"):
            self.assertIn(value, source)
        self.assertIn("return FULLSCREEN", source)
        self.assertIn("LauncherPrefs.KEY_MAP_ENABLED", source)

    def test_settings_exposes_one_explicit_four_way_surface_selector(self):
        source = (JAVA / "SettingsActivity.java").read_text()
        self.assertIn('"HOME navigation surface"', source)
        for label in (
            "Fullscreen only · safe fallback",
            "Leaflet comparator",
            "Raw freeform task · experimental",
            "Android PiP · experimental",
        ):
            self.assertIn(label, source)
        self.assertIn("HomeNavigationSurfacePolicy.setMode(this, value)", source)
        self.assertNotIn('addSwitchRow(R.drawable.ic_map, "Experimental Leaflet map"', source)

    def test_freeform_and_pip_are_separate_backends(self):
        freeform = (JAVA / "RawFreeformTaskBackend.java").read_text()
        pip = (JAVA / "AndroidPipBackend.java").read_text()
        self.assertIn('return "freeform"', freeform)
        self.assertIn('return "pip"', pip)
        self.assertNotIn("TopwayFreeformBackend", freeform + pip)

    def test_controller_repairs_known_task_before_relaunch(self):
        source = (JAVA / "NavigationWindowController.java").read_text()
        self.assertIn("verifyOrRepair", source)
        self.assertIn("backend.present(pkg,target,taskId", source)
        self.assertIn('"TASK_AMBIGUOUS"', source)
        self.assertNotIn("backend.focus(activity.getPackageName()", source)

    def test_pip_never_enables_global_force_resizable_or_steals_foreign_pip(self):
        source = HELPER.read_text()
        self.assertIn("PIP_OCCUPIED_BY_OTHER_APP", source)
        self.assertIn("PIP_UNSUPPORTED", source)
        self.assertIn("move-top-activity-to-pinned-stack", source)
        self.assertNotIn("settings put global force_resizable_activities", source)
        self.assertNotIn("setprop", source)

    def test_helper_fails_ambiguous_package_acquisition_closed(self):
        source = HELPER.read_text()
        self.assertIn("TASK_AMBIGUOUS", source)
        self.assertIn('count>1&&hint=="0"', source)

    def test_success_checks_real_mode_display_bounds_component(self):
        source = (JAVA / "NavigationWindowController.java").read_text()
        self.assertIn("result.displayId!=0", source)
        self.assertIn('result.component.startsWith(pkg+"/")', source)
        self.assertIn("target.toString().equals(result.bounds)", source)
        self.assertIn("result.windowingMode==expectedMode", source)

    def test_testing_build_has_exact_source_provenance(self):
        build = (ROOT / "launcher/build.gradle.kts").read_text()
        workflow = (ROOT / ".github/workflows/validate.yml").read_text()
        panel = (JAVA / "NativeNavigationPanel.java").read_text()
        self.assertIn("SOURCE_REVISION", build)
        self.assertIn("build_source_revision", build)
        self.assertIn("github.event.pull_request.head.sha", workflow)
        self.assertIn("BuildIdentity.summary", panel)

    def test_video_binder_is_not_navigation_transport(self):
        source = (JAVA / "NavigationWindowController.java").read_text() + HELPER.read_text()
        self.assertNotIn("FLOATING_WINDOW_SERVER", source)


if __name__ == "__main__":
    unittest.main()
