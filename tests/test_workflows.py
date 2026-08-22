import re
import unittest
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
MANUAL = (ROOT / ".github" / "workflows" / "manual-release.yml").read_text(encoding="utf-8")
VALIDATE = (ROOT / ".github" / "workflows" / "validate.yml").read_text(encoding="utf-8")


class WorkflowContractTests(unittest.TestCase):
    def test_manual_release_is_dispatch_only_and_locked(self):
        trigger = MANUAL.split("permissions:", 1)[0]
        self.assertIn("workflow_dispatch:", trigger)
        self.assertNotIn("\n  push:", trigger)
        self.assertNotIn("\n  pull_request:", trigger)
        self.assertIn("group: manual-release-${{ github.repository }}", MANUAL)
        self.assertIn("cancel-in-progress: false", MANUAL)

    def test_permissions_are_split_by_job(self):
        self.assertRegex(MANUAL, r"(?m)^permissions:\n  contents: read$")
        qualify = MANUAL.split("  qualify:", 1)[1].split("  publish:", 1)[0]
        publish = MANUAL.split("  publish:", 1)[1]
        self.assertRegex(qualify, r"permissions:\n      contents: read")
        self.assertRegex(publish, r"permissions:\n      contents: write")
        self.assertNotIn("contents: write", qualify)

    def test_release_build_occurs_once_and_never_in_publish_job(self):
        self.assertEqual(1, MANUAL.count(":theme:assembleRelease"))
        self.assertEqual(1, MANUAL.count('-PVERSION_NAME="$RELEASE_VERSION_NAME"'))
        self.assertEqual(1, MANUAL.count('-PVERSION_CODE="$RELEASE_VERSION_CODE"'))
        publish = MANUAL.split("  publish:", 1)[1]
        self.assertNotIn("assemble", publish.lower())
        self.assertNotIn("gradle ", publish.lower())

    def test_dispatch_version_is_optional_and_controls_the_release_build(self):
        version_input = MANUAL.split("      version_tag:", 1)[1].split(
            "      release_state:", 1
        )[0]
        self.assertIn("required: false", version_input)
        self.assertIn("blank creates the next automatic version", version_input)
        for field in ("version_name", "version_code", "version_source"):
            self.assertIn(f"{field}: ${{{{ steps.plan.outputs.{field} }}}}", MANUAL)
        self.assertNotIn("must equal the checked-in version", MANUAL)

    def test_release_assembly_has_a_fail_closed_signing_gate(self):
        build = (ROOT / "theme" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn('it.name == "assembleRelease"', build)
        self.assertIn("dependsOn(verifyReleaseSigningEnvironment)", build)
        self.assertNotIn('tasks.named("preReleaseBuild")', build)

    def test_java_bytecode_remains_compatible_with_compile_sdk_29(self):
        build = (ROOT / "theme" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertEqual(2, build.count("JavaVersion.VERSION_1_8"))

    def test_lint_is_strict_for_owned_source_but_not_the_pinned_legacy_aar(self):
        build = (ROOT / "theme" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("warningsAsErrors = true", build)
        self.assertIn("checkDependencies = false", build)
        self.assertIn("lint-results-debug.txt", VALIDATE)
        lint = ElementTree.parse(ROOT / "lint.xml").getroot()
        ignored = {item.get("id") for item in lint.findall("issue")}
        self.assertEqual(
            {
                "ExpiredTargetSdkVersion",
                "OldTargetApi",
                "GradleDependency",
                "ObsoleteSdkInt",
                "IconDuplicates",
            },
            ignored,
        )
        self.assertNotIn("UnusedResources", ignored)
        self.assertTrue((ROOT / "theme" / "src" / "main" / "res" / "raw" / "keep.xml").is_file())

    def test_only_existing_signing_secrets_are_referenced(self):
        secrets = set(re.findall(r"secrets\.([A-Z0-9_]+)", MANUAL))
        self.assertEqual(
            {"KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"},
            secrets,
        )
        for forbidden in ("PAT", "DEPLOY_KEY", "GITHUB_PAT"):
            self.assertNotIn(f"secrets.{forbidden}", MANUAL)

    def test_external_actions_are_full_sha_pinned(self):
        allowed = {
            "actions/checkout": "3d3c42e5aac5ba805825da76410c181273ba90b1",
            "actions/setup-java": "b6effb05e454b25005698d916606bdc6ffcbf961",
            "gradle/actions/setup-gradle": "9c971963bec38e04b3d30dcc455b5382be2fdbfb",
            "actions/upload-artifact": "043fb46d1a93c77aae656e7c1c64a875d1fc6a0a",
            "actions/download-artifact": "3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c",
        }
        for workflow in (MANUAL, VALIDATE):
            for action, ref in re.findall(r"(?m)^\s+uses:\s+([^@\s]+)@([^\s]+)$", workflow):
                self.assertRegex(ref, r"^[0-9a-f]{40}$")
                self.assertEqual(allowed[action], ref)

    def test_publication_is_draft_first_and_no_tag_rewrite_primitive_exists(self):
        self.assertIn("scripts/release/preflight.py", MANUAL)
        self.assertIn("scripts/release/publish.py", MANUAL)
        for forbidden in (
            "--clobber",
            "git push --force",
            "git tag -f",
            "git push --delete",
            "gh release delete",
        ):
            self.assertNotIn(forbidden, MANUAL)

    def test_exact_bundle_crosses_job_boundary(self):
        self.assertIn("qualified-release-${{ github.run_id }}-${{ github.run_attempt }}", MANUAL)
        self.assertIn("verify_manifest.py qualified/release-manifest.json", MANUAL)
        self.assertIn("retention-days: 14", MANUAL)


if __name__ == "__main__":
    unittest.main()
