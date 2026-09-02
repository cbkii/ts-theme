import importlib.util
import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RELEASE_SCRIPTS = ROOT / "scripts" / "release"
sys.path.insert(0, str(RELEASE_SCRIPTS))

SPEC = importlib.util.spec_from_file_location("runtime_envelope", RELEASE_SCRIPTS / "runtime_envelope.py")
assert SPEC and SPEC.loader
RUNTIME = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUNTIME)


class RuntimeEnvelopeTests(unittest.TestCase):
    def setUp(self):
        self.config = json.loads((ROOT / "release-config.json").read_text(encoding="utf-8"))
        self.good_classes = [
            "Lcom/qihoo360/replugin/Entry;",
            "Lcom/qihoo360/replugin/RePlugin;",
            "Lcom/qihoo360/replugin/library/R;",
            "Llibrary/a;",
            "Llauncher/variety/theme/plugin/R;",
        ]

    def audit(self, classes=None, *, dex_files=1):
        values = list(self.good_classes if classes is None else classes)
        return {
            "dex_file_count": dex_files,
            "dex_class_count": len(values),
            "dex_classes": values,
        }

    def test_minimal_replugin_envelope_passes(self):
        result = RUNTIME.validate_runtime_envelope(self.audit(), self.config)
        self.assertEqual(1, result["dex_file_count"])
        self.assertEqual(len(self.good_classes), result["dex_class_count"])

    def test_kotlin_or_other_runtime_payload_is_rejected(self):
        for extra in (
            "Lkotlin/Unit;",
            "Lorg/jetbrains/annotations/NotNull;",
            "Landroidx/core/content/ContextCompat;",
        ):
            with self.subTest(extra=extra), self.assertRaisesRegex(
                RUNTIME.RuntimeEnvelopeError, "unexpected executable classes"
            ):
                RUNTIME.validate_runtime_envelope(self.audit(self.good_classes + [extra]), self.config)

    def test_multidex_is_rejected(self):
        with self.assertRaisesRegex(RUNTIME.RuntimeEnvelopeError, "expected 1 DEX"):
            RUNTIME.validate_runtime_envelope(self.audit(dex_files=2), self.config)

    def test_class_budget_is_bounded(self):
        classes = ["Llibrary/C%03d;" % index for index in range(65)]
        classes[0] = "Lcom/qihoo360/replugin/Entry;"
        with self.assertRaisesRegex(RUNTIME.RuntimeEnvelopeError, "at most 64 DEX classes"):
            RUNTIME.validate_runtime_envelope(self.audit(classes), self.config)

    def test_agp_builtin_kotlin_is_disabled(self):
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        self.assertIn("android.builtInKotlin=false", properties.splitlines())

    def test_runtime_diagnostic_is_bundled_and_bounded(self):
        tools = self.config["install_tools"]
        script_path = "scripts/magisk/ts18-theme-runtime-diagnostic.sh"
        playbook_path = "docs/TS18_RUNTIME_DIAGNOSTIC.md"
        self.assertIn(script_path, tools)
        self.assertIn(playbook_path, tools)
        text = (ROOT / script_path).read_text(encoding="utf-8")
        self.assertIn("MAX_RUNS=2", text)
        self.assertIn("HARD_SECONDS=170", text)
        self.assertIn("RUNTIME_SECONDS=90", text)
        self.assertNotIn("am force-stop", text)
        self.assertNotIn("pm clear", text)
        self.assertNotIn("setenforce", text)
        self.assertIn("-name 'p.l'", text)


if __name__ == "__main__":
    unittest.main()
