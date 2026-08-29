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

    def test_strip_background_dimensions_match_geometry(self):
        main_resources = ROOT / "theme" / "src" / "main" / "res" / "mipmap-mdpi-v4"
        release_resources = ROOT / "theme" / "src" / "release" / "res" / "mipmap-mdpi-v4"
        self.assertEqual((286, 64), MODULE.png_dimensions(main_resources / "radio_bg.png"))
        self.assertEqual((680, 64), MODULE.png_dimensions(main_resources / "media_bg.png"))
        self.assertEqual((680, 64), MODULE.png_dimensions(release_resources / "media_bg.png"))
        self.assertEqual((178, 64), MODULE.png_dimensions(main_resources / "time_bg.png"))

    def test_safe_area_geometry(self):
        profile = json.loads((ROOT / "config" / "ts18-layout.json").read_text(encoding="utf-8"))
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

    def test_theme_json_is_parseable_and_host_schema_clean(self):
        paths = sorted((ROOT / "theme" / "src" / "main" / "assets").rglob("*.json"))
        self.assertGreater(len(paths), 0)
        for path in paths:
            with self.subTest(path=path):
                value = json.loads(path.read_text(encoding="utf-8"))
                self.assertFalse(MODULE.contains_empty_key(value))

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

    def test_malformed_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "fake.apk"
            fake.write_bytes(b"not an apk")
            with self.assertRaises(MODULE.ValidationError):
                MODULE.inspect_zip(fake)


if __name__ == "__main__":
    unittest.main()
