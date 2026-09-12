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

    def test_functional_icons_use_one_pinned_material_symbols_rounded_family(self):
        sources = self.read("docs/ICON_SOURCES.md")
        notice = self.read("launcher/src/main/assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt")
        self.assertIn("Material Symbols Rounded", sources)
        self.assertIn("40a7a292a79d9394157e1ea24f83d52d5e17c556", sources)
        self.assertIn("Apache-2.0", sources)
        self.assertIn("40a7a292a79d9394157e1ea24f83d52d5e17c556", notice)
        functional = (
            "ic_apps.xml", "ic_bluetooth.xml", "ic_chevron_right.xml", "ic_close.xml",
            "ic_mic.xml", "ic_music.xml", "ic_my_location.xml", "ic_navigation.xml",
            "ic_next.xml", "ic_pause.xml", "ic_phone.xml", "ic_play.xml",
            "ic_previous.xml", "ic_radio.xml", "ic_search.xml", "ic_settings.xml",
            "ic_shortcut.xml", "ic_star.xml", "ic_utility.xml", "ic_zoom_in.xml",
            "ic_zoom_out.xml",
        )
        for name in functional:
            xml = self.read("launcher/src/main/res/drawable/" + name)
            self.assertIn('android:width="36dp"', xml, name)
            self.assertIn('android:height="36dp"', xml, name)
            self.assertIn('android:viewportWidth="960"', xml, name)
            self.assertIn('android:viewportHeight="960"', xml, name)
            self.assertNotIn("?attr/colorControlNormal", xml, name)

    def test_media_cards_are_two_level_and_cluster_side_is_independent(self):
        launcher = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherActivity.java")
        metadata = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MediaMetadataView.java")
        prefs = self.read("launcher/src/main/java/com/cbkii/ts18launcher/LauncherPrefs.java")
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        self.assertIn("MediaMetadataView", launcher)
        self.assertIn("SlowMarqueeTextView", metadata)
        self.assertIn("secondary.setSingleLine(true)", metadata)
        self.assertIn("TruncateAt.END", metadata)
        self.assertIn("KEY_MEDIA_CONTROLS_SIDE", prefs)
        self.assertIn("MEDIA_CONTROLS_LEFT", prefs)
        self.assertIn("MEDIA_CONTROLS_RIGHT", prefs)
        self.assertIn('"Media controls side"', settings)
        self.assertIn("independent of rail", settings)
        self.assertIn("LauncherPrefs.mediaControlsOnLeft(this)", launcher)
        self.assertNotIn("railOnRight(context)", prefs[prefs.index("static String mediaControlsSide"):])

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

    def test_map_follow_state_has_redundant_non_colour_cue_and_controls_can_hide(self):
        ui = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AutomotiveUi.java")
        panel = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MapPanel.java")
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        self.assertIn("followModeBackground", ui)
        self.assertIn("state_selected", ui)
        self.assertIn("R.color.ui_accent, 3", ui)
        self.assertIn("recenter.setBackground(AutomotiveUi.followModeBackground(activity))", panel)
        self.assertIn("recenter.setImageTintList(AutomotiveUi.followTint(activity))", panel)
        self.assertIn('"Map controls"', settings)
        self.assertIn("mapControlsEnabled", panel)

    def test_motion_contract_uses_confirmed_micro_timings(self):
        ui = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AutomotiveUi.java")
        panel = self.read("launcher/src/main/java/com/cbkii/ts18launcher/MapPanel.java")
        html = self.read("launcher/src/main/assets/map/map.html")
        self.assertIn("STATE_CROSSFADE_MS = 120L", ui)
        self.assertIn("FEEDBACK_MS = 140L", ui)
        self.assertIn("PANEL_REVEAL_MS = 160L", ui)
        self.assertIn("DRAWER_MS = 180L", ui)
        self.assertIn("setIconWithCrossfade", ui)
        self.assertIn("PANEL_REVEAL_MS", panel)
        self.assertIn("STATE_CROSSFADE_MS", panel)
        self.assertIn("transition:filter 120ms linear", html)

    def test_settings_expose_role_icons_and_map_control_hide(self):
        settings = self.read("launcher/src/main/java/com/cbkii/ts18launcher/SettingsActivity.java")
        self.assertIn('"Map controls"', settings)
        self.assertIn("chooseRole(false, index)", settings)
        self.assertIn("chooseRole(true, index)", settings)
        self.assertIn("RoleIconCatalog.LABELS", settings)
        self.assertIn('addSection("Advanced HOME / recovery")', settings)

    def test_used_driver_tokens_and_focus_graph_exist(self):
        dimens = self.read("launcher/src/main/res/values/dimens.xml")
        ui = self.read("launcher/src/main/java/com/cbkii/ts18launcher/AutomotiveUi.java")
        for token in ("driver_target_min", "driver_target_primary", "driver_gap",
                      "driver_gap_large", "ui_play_visual", "ui_icon_button_padding",
                      "ui_corner_radius", "ui_metadata_secondary_text"):
            self.assertIn(token, dimens)
        self.assertIn("linkVertical", ui)
        self.assertIn("linkHorizontal", ui)
        self.assertIn("ui_focus_stroke", ui)

    def test_quantitative_acceptance_and_emulator_physical_protocol_are_explicit(self):
        acceptance = self.read("docs/MONO_DRIVE_UX_ACCEPTANCE.md")
        for marker in (
            "median glance <= 1.0 s", "1 tap; >=98% correct target",
            "first visible response begins <100 ms", "95th percentile <=2.0 s",
            "<2%", "4.5:1", "76dp", "1280 x 720", "Physical TS18 acceptance",
            "Right rail + Left media controls", "no sustained CPU regression greater than 10 percentage points",
            "no launcher PSS regression greater than 25%",
        ):
            self.assertIn(marker, acceptance)
        self.assertIn("every launcher feature remains available at all times", acceptance)
        self.assertIn("BASELINE REQUIRED", acceptance)

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
