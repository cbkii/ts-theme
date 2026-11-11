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

    def test_validator_rejects_a_returning_theme_module(self):
        original = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        try:
            (ROOT / "settings.gradle.kts").write_text(original + 'include(":theme")\n', encoding="utf-8")
            self.assertTrue(any("only :launcher" in e for e in MODULE.validate_project()))
        finally:
            (ROOT / "settings.gradle.kts").write_text(original, encoding="utf-8")

    def test_release_build_authority(self):
        config = json.loads((ROOT / "release-config.json").read_text(encoding="utf-8"))
        self.assertEqual("com.cbkii.ts18launcher", config["application_id"])
        self.assertNotIn("plugin_id", config)
        self.assertEqual({"min": 29, "target": 29, "compile": 29}, config["sdk"])
        self.assertEqual([], config["native_abis"])
        self.assertEqual(":launcher:assembleRelease", config["gradle_task"])
        self.assertEqual("launcher/build/outputs/apk/release/launcher-release.apk", config["apk_path"])
        # The retired DoFun theme product is not buildable from this repository.
        self.assertFalse((ROOT / "theme").exists())
        self.assertNotIn(":theme", (ROOT / "settings.gradle.kts").read_text(encoding="utf-8"))
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
