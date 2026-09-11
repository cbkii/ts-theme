import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class AutomotiveUiStateTests(unittest.TestCase):
    def read(self, relative: str) -> str:
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_home_rail_has_fixed_apps_top_navigation_bottom_and_configurable_middle(self):
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        self.assertLess(launcher.index("rail.addView(appsButton"), launcher.index("rail.addView(quickRail"))
        self.assertLess(launcher.index("rail.addView(quickRail"), launcher.index("rail.addView(navigationButton"))
        self.assertIn("RoleIconCatalog.icon(role)", launcher)
        self.assertIn("QUICK_ROLE_KEYS", prefs)
        self.assertIn("Math.max(3, Math.min(6", prefs)

    def test_drawer_keeps_search_visible_voice_prominent_and_targets_semantic(self):
        drawer = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerPanel.java")
        picker = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppDrawerActivity.java")
        self.assertIn('search.setHint("Search apps")', drawer)
        self.assertIn("R.drawable.ic_mic", drawer)
        self.assertIn("VoiceSearch.available(activity)", drawer)
        self.assertIn("DRAWER_QUICK_KEYS", drawer)
        self.assertIn("R.dimen.driver_target_min", drawer)
        self.assertIn("R.dimen.driver_target_min", picker)
        self.assertIn("label.setMaxLines(2)", drawer)
        self.assertIn("dismissKeyboard();", drawer)

    def test_slow_marquee_holds_five_seconds_and_repeats(self):
        marquee = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SlowMarqueeTextView.java")
        self.assertIn("HOLD_MS = 5000L", marquee)
        self.assertIn("SPEED_DP_PER_SECOND = 24f", marquee)
        self.assertIn("handler.postDelayed(restart, HOLD_MS)", marquee)
        self.assertIn("scrollTo(0, 0)", marquee)

    def test_appearance_supports_sensor_schedule_high_contrast_dim_and_night(self):
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        controller = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppearanceController.java")
        schedule = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AppearanceSchedule.java")
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        html = self.read("launcher/src/main/assets/map/map.html")
        for marker in ("APPEARANCE_DAY", "APPEARANCE_HIGH_CONTRAST", "APPEARANCE_DIM", "APPEARANCE_NIGHT"):
            self.assertIn(marker, prefs)
        self.assertIn("Sensor.TYPE_LIGHT", controller)
        self.assertIn("SENSOR_STALE_MS", controller)
        self.assertIn("AppearanceSchedule.resolve", controller)
        self.assertIn("deliveredMode = resolvedMode(this.context)", controller)
        self.assertIn("if (!currentMode.equals(deliveredMode))", controller)
        self.assertIn("DEFAULT_DAY_START_MINUTES = 7 * 60", prefs)
        self.assertIn("DEFAULT_NIGHT_START_MINUTES = 19 * 60", prefs)
        self.assertIn("TRANSITION_MINUTES = 45", schedule)
        self.assertIn('"Ambient light sensor", "Schedule"', settings)
        self.assertIn("setMapAppearance", html)
        self.assertIn("contrast(1.18)", html)

    def test_settings_expose_role_icons_and_map_control_hide(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        self.assertIn('"Map controls"', settings)
        self.assertIn("chooseRole(false, index)", settings)
        self.assertIn("chooseRole(true, index)", settings)
        self.assertIn("RoleIconCatalog.LABELS", settings)
        self.assertIn('addSection("Advanced HOME / recovery")', settings)

    def test_semantic_driver_tokens_and_focus_graph_exist(self):
        dimens = self.read("launcher/src/main/res/values/dimens.xml")
        ui = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AutomotiveUi.java")
        for token in ("driver_target_min", "driver_target_primary", "driver_icon_primary",
                      "driver_icon_secondary", "driver_gap", "driver_section_gap", "driver_radius"):
            self.assertIn(token, dimens)
        self.assertIn("linkVertical", ui)
        self.assertIn("linkHorizontal", ui)
        self.assertIn("ui_focus_stroke", ui)
        self.assertIn("FEEDBACK_MS = 140L", ui)

    def test_organic_maps_exact_modes_are_roadmap_only(self):
        roadmap = self.read("docs/ORGANIC_MAPS_MODES.md")
        for mode in ("PENDING_POSITION", "NOT_FOLLOW_NO_POSITION", "NOT_FOLLOW",
                     "FOLLOW", "FOLLOW_AND_ROTATE"):
            self.assertIn(mode, roadmap)
        self.assertIn("roadmap note only", roadmap)
        self.assertIn("explicit, versioned interface", roadmap)
        launcher_java = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (ROOT / "launcher/src/main/java").rglob("*.java")
        )
        self.assertNotIn("nativeSwitchToNextMode", launcher_java)
        self.assertNotIn("FOLLOW_AND_ROTATE", launcher_java)


if __name__ == "__main__":
    unittest.main()
