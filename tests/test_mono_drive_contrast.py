import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COLORS = ROOT / "launcher/src/main/res/values/colors.xml"


def channel(value: int) -> float:
    value /= 255.0
    return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    value = hex_color.lstrip("#")
    red, green, blue = (int(value[offset:offset + 2], 16) for offset in (0, 2, 4))
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)


def contrast(left: str, right: str) -> float:
    high, low = sorted((luminance(left), luminance(right)), reverse=True)
    return (high + 0.05) / (low + 0.05)


class MonoDriveContrastTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = ET.parse(COLORS).getroot()
        cls.colors = {node.attrib["name"]: (node.text or "").strip() for node in root.findall("color")}

    def assertContrast(self, foreground: str, background: str, minimum: float = 4.5):
        ratio = contrast(self.colors[foreground], self.colors[background])
        self.assertGreaterEqual(ratio, minimum, f"{foreground}/{background} contrast={ratio:.2f}:1")

    def test_day_text_and_secondary_clear_45_to_1(self):
        self.assertContrast("ui_text", "ui_surface")
        self.assertContrast("ui_text_secondary", "ui_surface")

    def test_night_text_and_secondary_clear_45_to_1(self):
        self.assertContrast("ui_night_text", "ui_night_surface")
        self.assertContrast("ui_night_secondary", "ui_night_surface")

    def test_high_contrast_text_and_secondary_clear_45_to_1(self):
        self.assertContrast("ui_high_text", "ui_high_surface")
        self.assertContrast("ui_high_secondary", "ui_high_surface")

    def test_primary_black_glyph_on_orange_clears_45_to_1(self):
        ratio = contrast("#000000", self.colors["ui_accent"])
        self.assertGreaterEqual(ratio, 4.5, f"black/ui_accent contrast={ratio:.2f}:1")


if __name__ == "__main__":
    unittest.main()
