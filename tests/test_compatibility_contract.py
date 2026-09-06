import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "theme" / "src" / "main" / "assets"


class CompatibilityContractTests(unittest.TestCase):
    def test_generated_index_contains_exact_host_json_keys(self):
        generated = json.loads((ASSETS / ".gen" / "c.json").read_text(encoding="utf-8"))
        self.assertEqual(["1280x652"], generated["all_resolutions"])
        self.assertEqual("land", generated["variant"])
        self.assertEqual(1, len(generated["layouts"]))
        layout = generated["layouts"][0]
        self.assertEqual("1280x652", layout["resolution"])
        self.assertEqual(
            [{"type": "desktop_window", "count": 1}, {"type": "medias", "count": 1}, {"type": "time", "count": 1}],
            layout["theme_config_plugins"]["json"],
        )
        self.assertEqual(
            [{"type": "app", "count": 5}],
            layout["hotseat_config_plugins"]["json"],
        )

    def test_newer_generator_mirror_agrees_with_exact_host_index(self):
        """Keep the SFP_TS20-style mirror consistent while exact-host keys drive JSON compatibility."""
        generated = json.loads((ASSETS / ".gen" / "c.json").read_text(encoding="utf-8"))
        layout = generated["layouts"][0]
        self.assertEqual(layout["theme_config_plugins"], layout["plugins"]["theme_config"])
        self.assertEqual(layout["hotseat_config_plugins"], layout["plugins"]["hotseat_config"])

    def test_resolution_specific_config_is_complete(self):
        root = ASSETS / "layout-1280x652"
        required = {
            "theme_config.json",
            "hotseat_config.json",
            "home/view_config.json",
            "home/app_widget_view_config1.json",
            "media/media_config.json",
            "media/media_view_config.json",
            "time/time2_config.json",
            "time/view2_config.json",
        }
        self.assertEqual(required, {str(p.relative_to(root)) for p in root.rglob("*.json")})


if __name__ == "__main__":
    unittest.main()
