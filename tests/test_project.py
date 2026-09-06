import importlib.util
import json
import re
import subprocess
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("ts18_theme", ROOT / "tools" / "ts18_theme.py")
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ProjectTests(unittest.TestCase):
    def test_repository_validates(self):
        self.assertEqual([], MODULE.validate_project())

    def test_strip_background_dimensions_match_known_resources(self):
        main_resources = ROOT / "theme" / "src" / "main" / "res" / "mipmap-mdpi-v4"
        release_resources = ROOT / "theme" / "src" / "release" / "res" / "mipmap-mdpi-v4"
        self.assertEqual((286, 64), MODULE.png_dimensions(main_resources / "radio_bg.png"))
        self.assertEqual((680, 64), MODULE.png_dimensions(main_resources / "media_bg.png"))
        self.assertEqual((680, 64), MODULE.png_dimensions(release_resources / "media_bg.png"))
        self.assertEqual((178, 64), MODULE.png_dimensions(main_resources / "time_bg.png"))

    def test_safe_area_geometry_and_theme_viewport_projection(self):
        profile = json.loads((ROOT / "config" / "ts18-layout.json").read_text(encoding="utf-8"))
        self.assertEqual(2, profile["schema_version"])
        self.assertEqual(1225, profile["safe_right"])
        self.assertEqual(81, profile["content_left"])
        self.assertEqual(1144, profile["surfaces"]["map"]["width"])
        for surface in profile["surfaces"].values():
            self.assertLessEqual(surface["x"] + surface["width"], profile["safe_right"])
        radio = profile["surfaces"]["radio"]
        music = profile["surfaces"]["music"]
        date = profile["surfaces"]["date"]
        self.assertEqual(radio["x"] + radio["width"], music["x"])
        self.assertEqual(music["x"] + music["width"], date["x"])
        self.assertEqual(date["x"] + date["width"], profile["safe_right"])

        viewport = profile["theme_viewport"]
        self.assertEqual("1280x652", viewport["resolution"])
        self.assertEqual((1280, 652), (viewport["width"], viewport["height"]))
        self.assertEqual((0, 55), (viewport["physical_origin_x"], viewport["physical_origin_y"]))
        self.assertEqual(647, viewport["safe_bottom"])
        compat = profile["compatibility_surfaces"]
        self.assertEqual({"x": 81, "y": 0, "width": 966, "height": 64}, compat["medias"])
        self.assertEqual({"x": 1047, "y": 0, "width": 178, "height": 64}, compat["date"])
        self.assertEqual({"x": 81, "y": 64, "width": 1144, "height": 583}, compat["map"])
        self.assertEqual(profile["surfaces"]["map"]["y"], compat["map"]["y"] + viewport["physical_origin_y"])
        self.assertEqual(profile["surfaces"]["date"]["y"], compat["date"]["y"] + viewport["physical_origin_y"])

    def test_theme_json_is_parseable_and_host_schema_clean(self):
        paths = sorted((ROOT / "theme" / "src" / "main" / "assets").rglob("*.json"))
        self.assertGreater(len(paths), 0)
        for path in paths:
            with self.subTest(path=path):
                value = json.loads(path.read_text(encoding="utf-8"))
                self.assertFalse(MODULE.contains_empty_key(value))

    def test_host_proven_generated_land_contract_and_medias_widget(self):
        asset_root = ROOT / "theme" / "src" / "main" / "assets"
        generated = json.loads((asset_root / ".gen" / "c.json").read_text(encoding="utf-8"))
        self.assertEqual("land", generated["variant"])
        self.assertEqual(["1280x652"], generated["all_resolutions"])
        self.assertEqual(["app", "desktop_window", "medias", "time"], generated["all_plugins"])
        self.assertFalse(generated["support_systemui"])
        self.assertFalse(generated["support_systemui_night"])
        self.assertTrue(generated["support_night"])
        layout = generated["layouts"][0]
        self.assertEqual("1280x652", layout["resolution"])
        self.assertEqual(
            [
                {"type": "desktop_window", "count": 1},
                {"type": "medias", "count": 1},
                {"type": "time", "count": 1},
            ],
            layout["plugins"]["theme_config"]["json"],
        )
        self.assertEqual([{"type": "app", "count": 5}], layout["plugins"]["hotseat_config"]["json"])

        for relative in (Path("theme_config.json"), Path("layout-1280x652/theme_config.json")):
            theme = json.loads((asset_root / relative).read_text(encoding="utf-8"))
            types = [item["soft_type"] for item in theme["config"][0]["page_config"]]
            self.assertEqual(["desktop_window", "medias", "time"], types)
            self.assertFalse({"local_radio", "local_music", "bt_music", "local_music|bt_music"} & set(types))

        for relative in (
            "theme_config.json",
            "hotseat_config.json",
            "home/view_config.json",
            "home/app_widget_view_config1.json",
            "media/media_config.json",
            "media/media_view_config.json",
            "time/time2_config.json",
            "time/view2_config.json",
        ):
            with self.subTest(relative=relative):
                generic = json.loads((asset_root / relative).read_text(encoding="utf-8"))
                specific = json.loads((asset_root / "layout-1280x652" / relative).read_text(encoding="utf-8"))
                self.assertEqual(generic, specific)

    def test_dot_gen_asset_is_intentionally_packaged(self):
        gradle = (ROOT / "theme" / "build.gradle.kts").read_text(encoding="utf-8")
        match = re.search(r'ignoreAssetsPattern\s*=\s*"([^"]+)"', gradle)
        self.assertIsNotNone(match)
        assert match is not None
        self.assertNotIn(":.*:", f":{match.group(1)}:")
        config = json.loads((ROOT / "release-config.json").read_text(encoding="utf-8"))
        self.assertIn("assets/.gen/c.json", config["required_apk_entries"])
        self.assertIn("assets/layout-1280x652/theme_config.json", config["required_apk_entries"])

    def test_compact_date_and_media_presentation(self):
        asset_root = ROOT / "theme" / "src" / "main" / "assets"
        time_config = json.loads((asset_root / "time" / "time2_config.json").read_text(encoding="utf-8"))
        time_view = MODULE.attributes_by_id(
            json.loads((asset_root / "time" / "view2_config.json").read_text(encoding="utf-8"))
        )
        media = MODULE.attributes_by_id(
            json.loads((asset_root / "media" / "media_view_config.json").read_text(encoding="utf-8"))
        )

        self.assertEqual("dd MMM", time_config["format"])
        self.assertTrue(time_view["tv_time_day"]["visibility"])
        for view_id in ("tv_time_week", "tv_time_hour", "tv_ap"):
            with self.subTest(view_id=view_id):
                self.assertFalse(time_view[view_id]["visibility"])

        for view_id in ("iv_media_icon", "iv_media_icon_bg", "iv_media_head_img", "tv_media_type"):
            with self.subTest(view_id=view_id):
                self.assertFalse(media[view_id]["visibility"])
        self.assertGreaterEqual(media["tv_media_name"]["width"], 430)
        for view_id in ("iv_media_pre", "iv_media_pp", "iv_media_next"):
            with self.subTest(view_id=view_id):
                self.assertGreaterEqual(media[view_id]["width"], 56)
                self.assertGreaterEqual(media[view_id]["height"], 56)

        for view_id in ("iv_radio_pp", "tv_radio_type", "tv_radio_unit"):
            with self.subTest(view_id=view_id):
                self.assertFalse(media[view_id]["visibility"])
        for view_id in ("iv_radio_pre", "iv_radio_next"):
            with self.subTest(view_id=view_id):
                self.assertGreaterEqual(media[view_id]["width"], 54)
                self.assertGreaterEqual(media[view_id]["height"], 54)

    def test_visual_policy_and_required_resources(self):
        theme_root = ROOT / "theme" / "src" / "main"
        text_paths = sorted((theme_root / "assets").rglob("*.json"))
        text_paths += sorted((theme_root / "res").rglob("*.xml"))
        text_paths += sorted((ROOT / "design").rglob("*.svg"))
        styling_text = "\n".join(path.read_text(encoding="utf-8") for path in text_paths).upper()
        self.assertIn("#FF6B57", styling_text)
        for cool_colour in ("#00E5FF", "#00B4D8", "#B8C1CC"):
            with self.subTest(cool_colour=cool_colour):
                self.assertNotIn(cool_colour, styling_text)
        self.assertFalse((ROOT / "design" / "icons" / "visualiser.svg").exists())

        required_resources = {
            "res/values/strings.xml",
            "res/values/aliases.xml",
            "res/drawable/selector_media_previous.xml",
            "res/drawable/selector_media_play.xml",
            "res/drawable/selector_media_stop.xml",
            "res/drawable/selector_media_next.xml",
            "res/drawable/selector_media_bt_pp.xml",
            "res/drawable/selector_media_radio_pp.xml",
        }
        for relative in sorted(required_resources):
            with self.subTest(relative=relative):
                self.assertTrue((theme_root / relative).is_file())

    def test_release_build_authority(self):
        config = json.loads((ROOT / "release-config.json").read_text(encoding="utf-8"))
        self.assertEqual("launcher.variety.theme.plugin.sfp_cbk_black", config["application_id"])
        self.assertEqual("sfp_cbk_black", config["plugin_id"])
        self.assertEqual({"min": 16, "target": 26, "compile": 29}, config["sdk"])
        self.assertEqual([], config["native_abis"])
        self.assertEqual(":theme:assembleRelease", config["gradle_task"])
        self.assertEqual("theme/build/outputs/apk/release/theme-release.apk", config["apk_path"])
        self.assertEqual("gradle.properties", config["version_file"])
        self.assertEqual("VERSION_NAME", config["version_name_property"])
        self.assertEqual("VERSION_CODE", config["version_code_property"])
        for entry in (
            "assets/.gen/c.json",
            "assets/layout-1280x652/theme_config.json",
            "assets/layout-1280x652/hotseat_config.json",
        ):
            self.assertIn(entry, config["required_apk_entries"])

        properties = {}
        for raw in (ROOT / config["version_file"]).read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if line and not line.startswith(("#", "!")) and "=" in line:
                key, value = line.split("=", 1)
                properties[key.strip()] = value.strip()
        self.assertRegex(properties[config["version_name_property"]], r"^[0-9]+\.[0-9]+\.[0-9]+$")
        self.assertGreater(int(properties[config["version_code_property"]]), 0)

    def test_android_resources_and_design_are_parseable(self):
        allowed = {".xml", ".png", ".jpg", ".jpeg", ".webp"}
        for source_set in ("main", "release"):
            resource_root = ROOT / "theme" / "src" / source_set / "res"
            if not resource_root.exists():
                continue
            for path in sorted(resource_root.rglob("*")):
                if not path.is_file():
                    continue
                with self.subTest(source_set=source_set, path=path):
                    self.assertIn(path.suffix.lower(), allowed)
                    if path.suffix.lower() == ".xml":
                        ElementTree.parse(path)
        for svg in sorted((ROOT / "design").rglob("*.svg")):
            with self.subTest(svg=svg):
                ElementTree.parse(svg)

    def test_tracked_tree_has_no_generated_release_artifacts(self):
        result = subprocess.run(
            ["git", "ls-files", "-z"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            timeout=10,
        )
        tracked = [Path(value.decode("utf-8")) for value in result.stdout.split(b"\0") if value]
        forbidden_suffixes = {".apk", ".aab", ".aar", ".jks", ".keystore", ".p12", ".pem"}
        for path in tracked:
            with self.subTest(path=path):
                self.assertNotIn(path.suffix.lower(), forbidden_suffixes)
                self.assertNotIn("build", path.parts)
                self.assertNotIn("dist", path.parts)

    def test_unsafe_zip_names_are_rejected(self):
        for name in ("../escape", "/absolute", "dir\\file", "C:/drive"):
            with self.subTest(name=name):
                self.assertFalse(MODULE.safe_member_name(name))
        self.assertTrue(MODULE.safe_member_name("assets/theme_config.json"))
        self.assertTrue(MODULE.safe_member_name("assets/.gen/c.json"))

    def test_malformed_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "fake.apk"
            fake.write_bytes(b"not an apk")
            with self.assertRaises(MODULE.ValidationError):
                MODULE.inspect_zip(fake)


if __name__ == "__main__":
    unittest.main()
