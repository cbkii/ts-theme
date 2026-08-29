import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("ts18_theme", ROOT / "tools" / "ts18_theme.py")
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

class ProjectTests(unittest.TestCase):
    def test_repository_validates(self):
        self.assertEqual([], MODULE.validate_project())

    def test_strip_background_dimensions_match_geometry(self):
        resources = ROOT / "theme" / "src" / "main" / "res" / "mipmap-mdpi-v4"
        self.assertEqual((286, 64), MODULE.png_dimensions(resources / "radio_bg.png"))
        self.assertEqual((680, 64), MODULE.png_dimensions(resources / "media_bg.png"))
        self.assertEqual((178, 64), MODULE.png_dimensions(resources / "time_bg.png"))

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

    def test_identity_and_parser_surface(self):
        config = json.loads((ROOT / "release-config.json").read_text(encoding="utf-8"))
        self.assertEqual("launcher.variety.theme.plugin.sfp_cbk_black", config["application_id"])
        self.assertEqual("sfp_cbk_black", config["plugin_id"])
        for path in (ROOT / "theme" / "src" / "main" / "assets").rglob("*.json"):
            value = json.loads(path.read_text(encoding="utf-8"))
            self.assertFalse(MODULE.contains_empty_key(value), path)

    def test_unsafe_zip_names_are_rejected(self):
        for name in ("../escape", "/absolute", "dir\\file", "C:/drive"):
            self.assertFalse(MODULE.safe_member_name(name))
        self.assertTrue(MODULE.safe_member_name("assets/theme_config.json"))

    def test_malformed_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "fake.apk"; fake.write_bytes(b"not an apk")
            with self.assertRaises(MODULE.ValidationError):
                MODULE.inspect_zip(fake)

if __name__ == "__main__":
    unittest.main()
