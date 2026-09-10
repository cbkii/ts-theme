import json
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "theme" / "src" / "main" / "assets"
LEGACY = ROOT / "compat" / "legacy-d8"


class CompatibilityContractTests(unittest.TestCase):
    def test_minimal_fyd18_style_source_contract(self):
        self.assertFalse((ASSETS / ".gen").exists())
        self.assertFalse(any(path.is_dir() and path.name.startswith("layout-") for path in ASSETS.iterdir()))
        theme = json.loads((ASSETS / "theme_config.json").read_text(encoding="utf-8"))
        types = [item["soft_type"] for item in theme["config"][0]["page_config"]]
        self.assertEqual(
            ["desktop_window", "local_radio", "local_music|bt_music", "time"],
            types,
        )

    def test_reference_matrix_records_only_clean_room_facts(self):
        matrix = json.loads((ROOT / "research" / "working-theme-reference-matrix.json").read_text(encoding="utf-8"))
        refs = matrix["external_working_references"]
        self.assertEqual(5, len(refs))
        self.assertEqual({16}, {item["sdk"]["min"] for item in refs})
        self.assertEqual({26}, {item["sdk"]["target"] for item in refs})
        self.assertEqual({29}, {item["sdk"]["compile"] for item in refs})
        self.assertTrue(all(item["dex_files"] == 1 and not item["native_abis"] for item in refs))
        self.assertTrue(all(item["version_name"].endswith(".land") for item in refs))
        self.assertTrue(all(re.fullmatch(r"[0-9]{14}", item["theme_id"]) for item in refs))
        self.assertEqual(1, len({item["signer_certificate_sha256"] for item in refs}))
        fyd = next(item for item in refs if item["plugin_id"] == "sfp_fyd18")
        self.assertEqual("1132", fyd["oem_id"])
        self.assertEqual("1.2.46", fyd["d8_version"])
        self.assertEqual(48, fyd["dex_classes"])
        self.assertEqual(
            ["desktop_window", "local_radio", "local_music|bt_music", "time"],
            matrix["candidate_contract"]["primary_widget_types"],
        )

    def test_legacy_lane_pins_land_and_legacy_toolchain(self):
        build = (LEGACY / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("com.android.tools.build:gradle:3.4.0", build)
        self.assertIn("buildToolsVersion '28.0.3'", build)
        self.assertIn("compileSdkVersion 29", build)
        self.assertIn("minSdkVersion 16", build)
        self.assertIn("targetSdkVersion 26", build)
        self.assertIn("flavorDimensions 'theme'", build)
        self.assertIn("land {", build)
        self.assertIn('.land"', build)
        self.assertIn("v1SigningEnabled true", build)
        self.assertIn("v2SigningEnabled true", build)
        verifier = (ROOT / "tools" / "verify_legacy_compat_apk.py").read_text(encoding="utf-8")
        self.assertIn('EXPECTED_D8 = "1.4.77"', verifier)
        self.assertIn('Llibrary/d$1;', verifier)
        self.assertIn('$$ExternalSyntheticApiModelOutline', verifier)

    def test_legacy_manifest_and_import_overlay_match_working_shape(self):
        manifest = ElementTree.parse(LEGACY / "src" / "main" / "AndroidManifest.xml").getroot()
        android = "{http://schemas.android.com/apk/res/android}"
        self.assertEqual("launcher.variety.theme.plugin", manifest.get("package"))
        app = manifest.find("application")
        self.assertIsNotNone(app)
        assert app is not None
        self.assertEqual("@android:drawable/sym_def_app_icon", app.get(f"{android}icon"))
        self.assertEqual("true", app.get(f"{android}supportsRtl"))
        self.assertIsNone(app.get(f"{android}label"))
        self.assertIsNone(app.get(f"{android}allowBackup"))
        components = [child.tag for child in app if child.tag != "meta-data"]
        self.assertEqual([], components)

        identity = json.loads(
            (LEGACY / "src" / "land" / "assets" / "import_theme_info_config.json").read_text(encoding="utf-8")
        )
        self.assertEqual("20260822000100", identity["themeId"])
        self.assertRegex(identity["themeId"], r"^[0-9]{14}$")
        self.assertEqual("1132", identity["oemId"])

    def test_validate_workflow_builds_legacy_carrier_separately(self):
        workflow = (ROOT / ".github" / "workflows" / "validate.yml").read_text(encoding="utf-8")
        self.assertIn("legacy_compat:", workflow)
        self.assertIn('java-version: "8"', workflow)
        self.assertIn('gradle-version: "5.1.1"', workflow)
        self.assertIn('build-tools;28.0.3', workflow)
        self.assertIn('assembleLandRelease', workflow)
        self.assertIn('verify_legacy_compat_apk.py', workflow)
        self.assertIn('compatibility-apk-fyd18-legacy-d8-', workflow)


if __name__ == "__main__":
    unittest.main()
