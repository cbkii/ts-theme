import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PALETTE = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/AccentPalette.java"


def black_contrast(hex_value: str) -> float:
    channels = [int(hex_value[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    def linear(value: float) -> float:
        return value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4
    luminance = 0.2126 * linear(channels[0]) + 0.7152 * linear(channels[1]) + 0.0722 * linear(channels[2])
    return (luminance + 0.05) / 0.05


class AccentPaletteContrastTests(unittest.TestCase):
    def test_base_and_pressed_focus_palette_clear_black_glyph_contrast(self):
        source = PALETTE.read_text(encoding="utf-8")
        for array_name in ("BASE", "DARK"):
            block = source.split(f"private static final int[] {array_name} = {{", 1)[1].split("};", 1)[0]
            values = re.findall(r"0xFF([0-9A-Fa-f]{6})", block)
            self.assertEqual(10, len(values), array_name)
            for value in values:
                ratio = black_contrast(value)
                self.assertGreaterEqual(ratio, 4.5, f"{array_name} #{value} contrast={ratio:.2f}:1")


if __name__ == "__main__":
    unittest.main()
