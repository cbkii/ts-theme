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

    def test_identity_lengths_are_patchable(self):
        self.assertEqual(len(MODULE.ORIGINAL_PACKAGE), len(MODULE.CUSTOM_PACKAGE))
        self.assertEqual(len(MODULE.ORIGINAL_PLUGIN_ID), len(MODULE.CUSTOM_PLUGIN_ID))

    def test_unsafe_zip_names_are_rejected(self):
        for name in ("../escape", "/absolute", "dir\\file", "C:/drive"):
            with self.subTest(name=name):
                self.assertFalse(MODULE.safe_member_name(name))
        self.assertTrue(MODULE.safe_member_name("assets/theme_config.json"))

    def test_theme_json_is_parseable(self):
        paths = sorted((ROOT / "theme" / "src" / "main" / "assets").rglob("*.json"))
        self.assertGreater(len(paths), 0)
        for path in paths:
            with self.subTest(path=path):
                json.loads(path.read_text(encoding="utf-8"))

    def test_map_and_top_strip_do_not_overlap(self):
        config = json.loads(
            (ROOT / "theme" / "src" / "main" / "assets" / "theme_config.json").read_text(
                encoding="utf-8"
            )
        )
        surfaces = {
            item["soft_type"]: item for item in config["config"][0]["page_config"]
        }
        self.assertEqual(126, 55 + surfaces["time"]["height"])
        self.assertEqual("l_93|t_126", surfaces["desktop_window"]["gravity"])
        self.assertEqual(1154, surfaces["desktop_window"]["width"])
        self.assertEqual(576, surfaces["desktop_window"]["height"])

    def test_compact_information_formats(self):
        time_config = json.loads(
            (ROOT / "theme" / "src" / "main" / "assets" / "time" / "time2_config.json")
            .read_text(encoding="utf-8")
        )
        self.assertEqual("dd MMM", time_config["format"])
        media = MODULE.attributes_by_id(
            json.loads(
                (ROOT / "theme" / "src" / "main" / "assets" / "media" / "media_view_config.json")
                .read_text(encoding="utf-8")
            )
        )
        self.assertFalse(media["iv_media_icon_bg"]["visibility"])
        self.assertGreaterEqual(media["tv_media_name"]["width"], 430)
        self.assertFalse(media["tv_radio_unit"]["visibility"])

    def test_unknown_base_is_stopped(self):
        with tempfile.TemporaryDirectory() as directory:
            fake = Path(directory) / "fake.apk"
            fake.write_bytes(b"not an apk")
            with self.assertRaises(MODULE.ValidationError):
                MODULE.inspect_zip(fake)


if __name__ == "__main__":
    unittest.main()
