import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DRAFT = (ROOT / ".github/workflows/testing-apk-draft.yml").read_text(encoding="utf-8")
PUBLISH = (ROOT / ".github/workflows/testing-apk-publish.yml").read_text(encoding="utf-8")


class TestingDraftWorkflowTests(unittest.TestCase):
    def test_fixed_release_is_always_draft_with_exact_title(self):
        self.assertIn("TEST_RELEASE_TITLE: 000 Testing Only Version", PUBLISH)
        self.assertIn("TEST_RELEASE_TAG: 000-testing-only", PUBLISH)
        self.assertGreaterEqual(PUBLISH.count("-F draft=true"), 2)
        self.assertIn('[[ "$(jq -r .draft <<<"$final")" == true ]]', PUBLISH)
        self.assertIn('[[ "$(jq -r .name <<<"$final")" == "$TEST_RELEASE_TITLE" ]]', PUBLISH)

    def test_privileged_publisher_never_checks_out_or_executes_source(self):
        self.assertNotIn("actions/checkout", PUBLISH)
        self.assertNotIn("gradle ", PUBLISH)
        self.assertIn("permissions:\n  actions: read\n  contents: write", PUBLISH)
        self.assertIn("Validate input envelope", PUBLISH)

    def test_automatic_refresh_accepts_only_successful_same_repo_pr_validation(self):
        self.assertIn("github.event.workflow_run.event == 'pull_request'", DRAFT)
        self.assertIn("github.event.workflow_run.conclusion == 'success'", DRAFT)
        self.assertIn("github.event.workflow_run.head_repository.full_name == github.repository", DRAFT)
        self.assertIn("standalone-launcher-testing-input", DRAFT)

    def test_manual_build_accepts_arbitrary_ref_and_keeps_install_version_below_1_0_0(self):
        self.assertIn('description: "PR number (#9 or 9), branch, tag, or commit SHA to build"', DRAFT)
        self.assertIn("-PVERSION_NAME=0.0.0-testing -PVERSION_CODE=999999", DRAFT)
        self.assertIn("TS18-Standalone-Launcher-TESTING.apk", PUBLISH)
        self.assertIn("TS18-Standalone-Launcher-DEBUG.apk", PUBLISH)

    def test_draft_assets_are_replaced_by_release_id(self):
        self.assertIn("releases/$release_id/assets?per_page=100", PUBLISH)
        self.assertIn("releases/assets/$asset_id", PUBLISH)
        self.assertIn("https://uploads.github.com/repos/$GITHUB_REPOSITORY/releases/$release_id/assets?name=$name", PUBLISH)
        self.assertNotIn("gh release upload", PUBLISH)

    def test_draft_identity_uses_title_and_cleans_only_exact_draft_duplicates(self):
        self.assertIn('.draft == true and .name == \\"$TEST_RELEASE_TITLE\\"', PUBLISH)
        self.assertIn('matching_drafts', PUBLISH)
        self.assertIn('duplicate_id=', PUBLISH)
        self.assertIn('Expected one canonical testing draft', PUBLISH)
        self.assertNotIn('.tag_name == \\"$TEST_RELEASE_TAG\\"', PUBLISH)


if __name__ == "__main__":
    unittest.main()
