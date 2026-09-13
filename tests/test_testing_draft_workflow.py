import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRAFT = (ROOT / ".github/workflows/testing-apk-draft.yml").read_text(encoding="utf-8")
PUBLISH = (ROOT / ".github/workflows/testing-apk-publish.yml").read_text(encoding="utf-8")
VALIDATE = (ROOT / ".github/workflows/validate.yml").read_text(encoding="utf-8")
HELPER = (ROOT / "scripts/ci/build-launcher-testing-input.sh").read_text(encoding="utf-8")


class TestingDraftWorkflowTests(unittest.TestCase):
    def test_fixed_release_title_and_version_contract_are_unchanged(self):
        self.assertIn("TEST_RELEASE_TITLE: 000 Testing Only Version", PUBLISH)
        self.assertIn("TEST_RELEASE_TAG: 000-testing-only", PUBLISH)
        self.assertIn('version_name="0.0.0-testing"', HELPER)
        self.assertIn('version_code="999999"', HELPER)

    def test_refresh_is_explicit_not_every_successful_validate_run(self):
        self.assertNotIn("workflow_run:", DRAFT)
        self.assertIn("pull_request_target:", DRAFT)
        self.assertIn("issue_comment:", DRAFT)
        self.assertIn("pull_request_review_comment:", DRAFT)
        self.assertIn("opened", DRAFT)
        self.assertIn("labeled", DRAFT)
        self.assertIn("synchronize", DRAFT)
        self.assertIn("/testing-apk", DRAFT)
        self.assertIn("testing-apk", DRAFT)

    def test_manual_source_is_distinct_from_workflow_ref(self):
        self.assertIn("source_to_build:", DRAFT)
        self.assertIn("Use workflow from", DRAFT)
        self.assertIn("GITHUB_REF_NAME", DRAFT)
        self.assertIn("DEFAULT_BRANCH", DRAFT)
        self.assertIn("PR #$pr_number was not found", DRAFT)
        self.assertIn("could not be resolved", DRAFT)

    def test_marker_requests_apply_visible_testing_label(self):
        self.assertIn("ensure_testing_label", DRAFT)
        self.assertIn("commit-command", DRAFT)
        self.assertIn("pr-comment-command", DRAFT)
        self.assertIn("review-comment-command", DRAFT)
        self.assertIn("OWNER|MEMBER|COLLABORATOR", DRAFT)

    def test_requested_source_build_is_read_only_and_handoff_is_seven_days(self):
        self.assertIn("cache-read-only: true", DRAFT)
        self.assertIn("retention-days: 7", DRAFT)
        self.assertIn("permissions:\n      contents: read", DRAFT)

    def test_shared_helper_owns_testing_build_and_release_only_checker(self):
        self.assertIn("build-launcher-testing-input.sh", DRAFT)
        self.assertIn("build-launcher-testing-input.sh", VALIDATE)
        self.assertEqual(HELPER.count("tools/launcher_apk_check.py"), 2)
        self.assertIn('python3 tools/launcher_apk_check.py "$release_apk"', HELPER)
        self.assertNotIn('python3 tools/launcher_apk_check.py "$debug_apk"', HELPER)

    def test_publisher_uses_explicit_signing_secrets(self):
        self.assertNotIn("secrets: inherit", DRAFT)
        for name in ("KEYSTORE_BASE64", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"):
            self.assertIn(name, DRAFT)
            self.assertIn(name, PUBLISH)

    def test_missing_or_duplicated_handoff_is_failure(self):
        self.assertIn("Expected exactly one unexpired", PUBLISH)
        self.assertNotIn("nothing to refresh", PUBLISH)
        self.assertIn("build_run_id", PUBLISH)
        self.assertIn("does not match source artifact run", PUBLISH)

    def test_publisher_retains_two_identified_snapshot_groups(self):
        self.assertIn('group_id="PR${pr_number}-${source_sha:0:7}"', PUBLISH)
        self.assertIn('group_id="SHA-${built_sha:0:7}"', PUBLISH)
        self.assertIn('for entry in "${groups[@]:2}"', PUBLISH)
        self.assertIn("newest two snapshot groups are retained", PUBLISH)
        self.assertIn("BUILD_INFO.txt", PUBLISH)  # legacy migration is intentionally supported

    def test_release_notes_are_real_markdown_with_links_and_provenance(self):
        self.assertIn("testing-release-body.md", PUBLISH)
        self.assertIn("## Current TESTING builds", PUBLISH)
        self.assertIn("browser_download_url", PUBLISH)
        self.assertIn("Current PR head", PUBLISH)
        self.assertIn("Built SHA", PUBLISH)
        self.assertIn("Managed by **Refresh Testing APK Draft**", PUBLISH)
        self.assertIn("testing-apk-draft-managed", PUBLISH)
        self.assertNotIn('body="Testing-only rolling launcher build.', PUBLISH)

    def test_validate_no_longer_duplicates_codex_branch_pushes(self):
        self.assertIn("- main", VALIDATE)
        self.assertNotIn('"codex/**"', VALIDATE)
        self.assertIn("pull_request:", VALIDATE)

    def test_changed_ci_jobs_pin_ubuntu_24_04(self):
        self.assertNotIn("ubuntu-latest", DRAFT)
        self.assertNotIn("ubuntu-latest", PUBLISH)
        self.assertNotIn("ubuntu-latest", VALIDATE)
        self.assertIn("ubuntu-24.04", DRAFT)
        self.assertIn("ubuntu-24.04", PUBLISH)
        self.assertIn("ubuntu-24.04", VALIDATE)


if __name__ == "__main__":
    unittest.main()
