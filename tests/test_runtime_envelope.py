import importlib.util
import io
import json
import re
import sys
import unittest
from pathlib import Path
from unittest import mock

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
        envelope = self.config["runtime_envelope"]
        self.expected_dex_files = envelope["expected_dex_files"]
        self.max_dex_classes = envelope["max_dex_classes"]
        self.good_classes = [
            "Lcom/qihoo360/replugin/Entry;",
            "Lcom/qihoo360/replugin/RePlugin;",
            "Lcom/qihoo360/replugin/library/R;",
            "Llibrary/a;",
            "Llauncher/variety/theme/plugin/R;",
        ]

    def audit(self, classes=None, *, dex_files=None):
        values = list(self.good_classes if classes is None else classes)
        return {
            "dex_file_count": self.expected_dex_files if dex_files is None else dex_files,
            "dex_class_count": len(values),
            "dex_classes": values,
        }

    def test_minimal_replugin_envelope_passes(self):
        result = RUNTIME.validate_runtime_envelope(self.audit(), self.config)
        self.assertEqual(self.expected_dex_files, result["dex_file_count"])
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

    def test_multidex_contract_is_rejected(self):
        found = self.expected_dex_files + 1
        with self.assertRaisesRegex(
            RUNTIME.RuntimeEnvelopeError,
            rf"expected {self.expected_dex_files} DEX file\(s\), found {found}",
        ):
            RUNTIME.validate_runtime_envelope(self.audit(dex_files=found), self.config)

    def test_class_budget_is_bounded(self):
        classes = ["Llibrary/C%03d;" % index for index in range(self.max_dex_classes + 1)]
        classes[0] = self.config["required_dex_classes"][0]
        with self.assertRaisesRegex(
            RUNTIME.RuntimeEnvelopeError,
            rf"at most {self.max_dex_classes} DEX classes",
        ):
            RUNTIME.validate_runtime_envelope(self.audit(classes), self.config)

    def test_cli_reports_apk_audit_errors_without_traceback(self):
        stderr = io.StringIO()
        argv = ["runtime_envelope.py", "--apk", "broken.apk", "--config", str(ROOT / "release-config.json")]
        with (
            mock.patch.object(RUNTIME, "audit_apk", side_effect=RUNTIME.ThemeError("malformed APK")),
            mock.patch.object(sys, "argv", argv),
            mock.patch("sys.stderr", stderr),
        ):
            self.assertEqual(1, RUNTIME.main())
        self.assertEqual("ERROR: malformed APK\n", stderr.getvalue())

    def test_agp_builtin_kotlin_is_disabled(self):
        properties = (ROOT / "gradle.properties").read_text(encoding="utf-8")
        self.assertIn("android.builtInKotlin=false", properties.splitlines())

    def test_runtime_diagnostic_is_bundled_bounded_and_parse_safe(self):
        tools = self.config["install_tools"]
        script_path = "scripts/magisk/ts18-theme-runtime-diagnostic.sh"
        playbook_path = "docs/TS18_RUNTIME_DIAGNOSTIC.md"
        self.assertIn(script_path, tools)
        self.assertIn(playbook_path, tools)
        text = (ROOT / script_path).read_text(encoding="utf-8")

        # Bounded boot/runtime work and private active state.
        self.assertIn("MAX_RUNS=2", text)
        self.assertIn("HARD_SECONDS=210", text)
        self.assertIn("RUNTIME_SECONDS=100", text)
        self.assertIn("STORAGE_WAIT_SECONDS=90", text)
        self.assertIn("SAMPLER_INTERVAL_SECONDS=4", text)
        self.assertIn("SAMPLER_SAMPLES=25", text)
        self.assertIn("MAX_PL_FILES=64", text)
        self.assertIn("MAX_PL_DISCOVERED=512", text)
        self.assertIn("MAX_PL_BYTES=262144", text)
        self.assertIn("MAX_LSPOSED_LOG_FILES=8", text)
        self.assertIn("MAX_LSPOSED_LOG_LINES=8000", text)
        self.assertIn("MAX_XPOSED_MODULES=96", text)
        self.assertIn("MAX_FRAMEWORK_FILES=512", text)
        self.assertIn('/data/adb/ts18-theme-runtime-diag-state', text)

        # Existing RePlugin evidence remains covered.
        self.assertIn("-name 'p.l'", text)
        self.assertIn('dd if="$pl_path" bs=4096 count=64', text)
        self.assertIn('p.l-${tag}-coverage.txt', text)
        self.assertIn('p.l-${tag}-omitted.txt', text)
        self.assertIn('print "MAP\\t"', text)
        self.assertNotIn('or int "MAP\\t"', text)
        self.assertNotIn("Kill -TERM", text)
        self.assertIn("launcher\\\\.variety", text)
        self.assertNotIn("launcher\\\\\\\\.variety", text)
        self.assertIn("com\\\\.dofun\\\\.variety", text)

        # LSPosed/libxposed feasibility evidence is explicit rather than inferred.
        self.assertIn("/data/adb/lspd", text)
        self.assertIn("modules_config.db", text)
        self.assertIn("sqlite_readonly_ok", text)
        self.assertIn('"$sqlite" -readonly', text)
        self.assertIn("dofun_scope_query_exit", text)
        self.assertIn("META-INF/xposed/java_init.list", text)
        self.assertIn("META-INF/xposed/native_init.list", text)
        self.assertIn("META-INF/xposed/scope.list", text)
        self.assertIn("META-INF/xposed/module.prop", text)
        self.assertIn("assets/xposed_init", text)
        self.assertIn("capture_magisk_state", text)
        self.assertIn("capture_lsposed_state", text)
        self.assertIn("lsposed/framework-files.tsv", text)
        self.assertIn("capture_zygotes", text)
        self.assertIn("capture_dofun_static", text)
        self.assertIn("capture_logcat_history", text)
        self.assertIn("zygote=$(getprop ro.zygote", text)
        self.assertIn("abilist32=$(getprop ro.product.cpu.abilist32", text)
        self.assertIn("abilist64=$(getprop ro.product.cpu.abilist64", text)
        self.assertIn("Lcom/stub/StubApp;", text)
        self.assertIn("jiagu", text.lower())
        self.assertIn("Unsupported class loader", text)
        self.assertIn("analysis/lsposed-feasibility.txt", text)
        self.assertIn("log-only LSPosed discovery module", text)

        # Logs are filtered during collection; the raw LSPosed DB is never copied.
        self.assertIn('LOGCAT_FIFO="$STATE_DIR/', text)
        self.assertIn('LOGCAT_FILTERED_TMP="$STATE_DIR/', text)
        self.assertIn("make_fifo", text)
        self.assertIn("clean_stale_runtime_temp", text)
        self.assertNotIn("LOGCAT_RAW=", text)
        self.assertNotIn('logcat -b all -v threadtime -T 1 >"$LOGCAT_FILTERED_TMP"', text)
        self.assertIn("SHARING_NOTICE.txt", text)
        self.assertNotIn('cp "$db"', text)
        self.assertNotIn('dd if="$db"', text)
        self.assertNotRegex(text, re.compile(r"(?i)\b(?:UPDATE|INSERT|DELETE)\s+(?:modules|scope|configs)\b"))

        # Collector remains read-only against protected state.
        self.assertNotIn("am force-stop", text)
        self.assertNotIn("pm clear", text)
        self.assertNotIn("setenforce", text)
        self.assertNotRegex(text, re.compile(r"rm\s+-rf\s+[^\n]*lspd", re.IGNORECASE))
        self.assertNotRegex(text, re.compile(r"(?:chmod|chown)\s+[^\n]*lspd", re.IGNORECASE))

        worker = text[text.index("run_worker() {") : text.index("\nservice_entry() {")]
        self.assertLess(worker.index("acquire_slot;"), worker.index("trap cleanup EXIT"))
        self.assertLess(worker.index("trap cleanup EXIT"), worker.index("clean_stale_runtime_temp"))


if __name__ == "__main__":
    unittest.main()
