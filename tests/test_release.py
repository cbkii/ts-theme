import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RELEASE_SCRIPTS = ROOT / "scripts" / "release"
sys.path.insert(0, str(RELEASE_SCRIPTS))

from release_lib import (  # noqa: E402
    GitHubClient,
    ReleaseError,
    ReleaseRecord,
    RemoteSnapshot,
    SemVer,
    TagRecord,
    classify_assets,
    load_manifest,
    parse_next_link,
    resolve_plan,
    source_version,
    validate_remote_for_plan,
)

QUALIFY_SPEC = importlib.util.spec_from_file_location("qualify", RELEASE_SCRIPTS / "qualify.py")
assert QUALIFY_SPEC and QUALIFY_SPEC.loader
QUALIFY = importlib.util.module_from_spec(QUALIFY_SPEC)
QUALIFY_SPEC.loader.exec_module(QUALIFY)


SHA_A = "a" * 40
SHA_B = "b" * 40
DIGEST_A = "a" * 64
DIGEST_B = "b" * 64


def snapshot(*, tags=(), releases=()):
    return RemoteSnapshot(tags=tuple(tags), releases=tuple(releases))


def plan(**overrides):
    values = dict(
        mode="create_new_release",
        requested_tag="v1.0.0",
        requested_state="stable",
        replace_existing=False,
        current_source_sha=SHA_A,
        current_version_name="1.0.0",
        current_version_code=1,
        snapshot=snapshot(),
    )
    values.update(overrides)
    return resolve_plan(**values)


def expected_assets():
    return [
        {"name": "Theme-v1.0.0.apk", "size": 100, "sha256": DIGEST_A},
        {"name": "Theme-v1.0.0.apk.sha256", "size": 90, "sha256": DIGEST_B},
    ]


class VersionPlanTests(unittest.TestCase):
    def test_semantic_order_not_lexicographic(self):
        self.assertGreater(SemVer.from_tag("v6.10.0"), SemVer.from_tag("v6.9.9"))
        self.assertIsNone(SemVer.from_tag("6.10.0"))
        self.assertIsNone(SemVer.from_tag("v01.0.0"))

    def test_create_uses_checked_in_version_and_source(self):
        resolved = plan(requested_state="draft")
        self.assertEqual("v1.0.0", resolved["tag"])
        self.assertEqual(SHA_A, resolved["source_sha"])
        self.assertEqual("draft", resolved["release_state"])

    def test_all_release_states_participate_in_authority(self):
        for release in (
            ReleaseRecord(1, "v1.0.0", True, False),
            ReleaseRecord(1, "v1.0.0", False, True),
            ReleaseRecord(1, "v1.0.0", False, False),
        ):
            with self.subTest(state=release.state), self.assertRaises(ReleaseError):
                plan(
                    requested_tag="v0.9.0",
                    current_version_name="0.9.0",
                    snapshot=snapshot(releases=(release,)),
                )

    def test_malformed_tags_are_ignored_but_stale_semver_is_rejected(self):
        remote = snapshot(
            tags=(TagRecord("nightly", SHA_A), TagRecord("v1.1.0", SHA_A))
        )
        with self.assertRaisesRegex(ReleaseError, "not newer"):
            plan(snapshot=remote)

    def test_tag_only_transaction_requires_repair(self):
        remote = snapshot(tags=(TagRecord("v1.0.0", SHA_B),))
        with self.assertRaisesRegex(ReleaseError, "use repair"):
            plan(snapshot=remote)
        repaired = plan(
            mode="repair_existing_release",
            snapshot=remote,
            current_version_name="2.0.0",
        )
        self.assertEqual(SHA_B, repaired["source_sha"])
        self.assertEqual("tag_only", repaired["observed_remote_state"])

    def test_release_without_tag_fails_closed(self):
        remote = snapshot(releases=(ReleaseRecord(1, "v1.0.0", True, False),))
        with self.assertRaisesRegex(ReleaseError, "without its matching tag"):
            plan(snapshot=remote)
        with self.assertRaisesRegex(ReleaseError, "without its matching tag"):
            plan(mode="repair_existing_release", snapshot=remote)

    def test_repair_accepts_draft_prerelease_and_stable(self):
        for release in (
            ReleaseRecord(1, "v1.0.0", True, False),
            ReleaseRecord(1, "v1.0.0", False, True),
            ReleaseRecord(1, "v1.0.0", False, False),
        ):
            resolved = plan(
                mode="repair_existing_release",
                snapshot=snapshot(tags=(TagRecord("v1.0.0", SHA_B),), releases=(release,)),
            )
            self.assertEqual(release.state, resolved["observed_remote_state"])

    def test_remote_recheck_detects_conflicting_tag(self):
        resolved = plan(
            mode="repair_existing_release",
            snapshot=snapshot(tags=(TagRecord("v1.0.0", SHA_A),)),
        )
        with self.assertRaisesRegex(ReleaseError, "resolves to"):
            validate_remote_for_plan(
                resolved, snapshot(tags=(TagRecord("v1.0.0", SHA_B),))
            )

    def test_pagination_link_is_bounded_to_github(self):
        value = '<https://api.github.com/example?page=2>; rel="next", <x>; rel="last"'
        self.assertEqual("https://api.github.com/example?page=2", parse_next_link(value))
        with self.assertRaises(ReleaseError):
            parse_next_link('<https://example.invalid/page>; rel="next"')

    def test_release_api_pagination_reads_all_pages(self):
        client = object.__new__(GitHubClient)
        pages = iter(
            (
                (b'[{"id":1}]', {"Link": '<https://api.github.com/page2>; rel="next"'}),
                (b'[{"id":2}]', {}),
            )
        )
        client.api = "https://api.github.com"
        client._request = lambda method, url: next(pages)
        self.assertEqual([{"id": 1}, {"id": 2}], client.paginate("/page1"))


class AssetStateTests(unittest.TestCase):
    def test_no_remote_assets_uploads_all(self):
        result = classify_assets(
            expected_assets=expected_assets(),
            remote_assets=[],
            mode="create_new_release",
            release_published=False,
            replace_existing=False,
        )
        self.assertEqual([item["name"] for item in expected_assets()], result["upload"])

    def test_identical_assets_are_reused_on_retry(self):
        remote = [
            {
                "name": item["name"],
                "size": item["size"],
                "state": "uploaded",
                "download_sha256": item["sha256"],
            }
            for item in expected_assets()
        ]
        first = classify_assets(
            expected_assets=expected_assets(),
            remote_assets=remote,
            mode="repair_existing_release",
            release_published=False,
            replace_existing=False,
        )
        second = classify_assets(
            expected_assets=expected_assets(),
            remote_assets=remote,
            mode="repair_existing_release",
            release_published=False,
            replace_existing=False,
        )
        self.assertEqual(first, second)
        self.assertEqual([], first["upload"])
        self.assertEqual(2, len(first["reuse"]))

    def test_partial_identical_set_uploads_only_missing_asset(self):
        item = expected_assets()[0]
        result = classify_assets(
            expected_assets=expected_assets(),
            remote_assets=[
                {
                    "name": item["name"],
                    "size": item["size"],
                    "state": "uploaded",
                    "download_sha256": item["sha256"],
                }
            ],
            mode="repair_existing_release",
            release_published=False,
            replace_existing=False,
        )
        self.assertEqual([expected_assets()[1]["name"]], result["upload"])

    def test_mismatch_fails_without_explicit_replacement(self):
        item = expected_assets()[0]
        remote = [{"name": item["name"], "size": 1, "state": "uploaded", "download_sha256": DIGEST_B}]
        with self.assertRaisesRegex(ReleaseError, "replacement is disabled"):
            classify_assets(
                expected_assets=expected_assets(),
                remote_assets=remote,
                mode="repair_existing_release",
                release_published=False,
                replace_existing=False,
            )

    def test_explicit_replacement_only_applies_to_draft(self):
        item = expected_assets()[0]
        remote = [{"name": item["name"], "size": 1, "state": "uploaded", "download_sha256": DIGEST_B}]
        result = classify_assets(
            expected_assets=expected_assets(),
            remote_assets=remote,
            mode="repair_existing_release",
            release_published=False,
            replace_existing=True,
        )
        self.assertEqual([item["name"]], result["replace"])
        with self.assertRaisesRegex(ReleaseError, "cannot be replaced"):
            classify_assets(
                expected_assets=expected_assets(),
                remote_assets=remote,
                mode="repair_existing_release",
                release_published=True,
                replace_existing=True,
            )

    def test_new_release_rejects_unplanned_asset(self):
        with self.assertRaisesRegex(ReleaseError, "unplanned"):
            classify_assets(
                expected_assets=expected_assets(),
                remote_assets=[{"name": "surprise.apk"}],
                mode="create_new_release",
                release_published=False,
                replace_existing=False,
            )

    def test_repair_preserves_unrelated_historical_asset(self):
        result = classify_assets(
            expected_assets=expected_assets(),
            remote_assets=[{"name": "old-notes.txt"}],
            mode="repair_existing_release",
            release_published=False,
            replace_existing=False,
        )
        self.assertEqual(["old-notes.txt"], result["preserve"])


class QualificationParserTests(unittest.TestCase):
    def test_aapt_badging_parser(self):
        parsed = QUALIFY.parse_aapt_badging(
            "package: name='launcher.variety.theme.plugin.cbk_black' versionCode='1' "
            "versionName='1.0.0' compileSdkVersion='29'\n"
            "sdkVersion:'16'\ntargetSdkVersion:'26'\n"
        )
        self.assertEqual(1, parsed["version_code"])
        self.assertEqual(29, parsed["compile_sdk"])

    def test_certificate_parser_accepts_label_variants(self):
        digest = ":".join("AA" for _ in range(32))
        for line in (
            f"Signer #1 certificate SHA-256 digest: {digest}",
            f"certificate SHA-256 digest: {digest}",
        ):
            self.assertEqual(
                "AA" * 32,
                QUALIFY.one_certificate_digest(
                    line, marker="certificate SHA-256 digest", source="fixture"
                ),
            )

    def test_certificate_parser_fails_on_ambiguous_signers(self):
        output = (
            f"certificate SHA-256 digest: {'AA' * 32}\n"
            f"certificate SHA-256 digest: {'BB' * 32}\n"
        )
        with self.assertRaisesRegex(ReleaseError, "unambiguous"):
            QUALIFY.one_certificate_digest(
                output, marker="certificate SHA-256 digest", source="fixture"
            )

    def test_release_requires_v1_and_v2_signing(self):
        QUALIFY.require_signing_schemes(
            "Verified using v1 scheme (JAR signing): true\n"
            "Verified using v2 scheme (APK Signature Scheme v2): true\n"
        )
        with self.assertRaisesRegex(ReleaseError, "both v1 and v2"):
            QUALIFY.require_signing_schemes(
                "Verified using v1 scheme (JAR signing): false\n"
                "Verified using v2 scheme (APK Signature Scheme v2): true\n"
            )

    def test_source_version_authority(self):
        config = json.loads((ROOT / "release-config.json").read_text(encoding="utf-8"))
        self.assertEqual(("1.0.0", 1), source_version(ROOT, config))

    def test_changelog_notes_extracts_one_version_section(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "CHANGELOG.md").write_text(
                "# Log\n\n## 1.0.0 - 2026-08-22\n\n- Candidate.\n\n"
                "## 0.9.0\n\n- Old.\n",
                encoding="utf-8",
            )
            notes = QUALIFY.changelog_notes(root, "1.0.0", "Theme")
            self.assertEqual(1, notes.count("- Candidate."))
            self.assertNotIn("- Old.", notes)
            self.assertIn("Validation boundary", notes)

    def test_missing_duplicate_and_retired_apk_candidates_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            expected = output / "theme-release.apk"
            with self.assertRaisesRegex(ReleaseError, "found: none"):
                QUALIFY.require_single_apk(expected)
            expected.write_bytes(b"one")
            QUALIFY.require_single_apk(expected)
            (output / "retired-release.apk").write_bytes(b"two")
            with self.assertRaisesRegex(ReleaseError, "retired-release"):
                QUALIFY.require_single_apk(expected)

    def test_manifest_closure_and_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            asset = root / "Theme-v1.0.0.apk"
            asset.write_bytes(b"apk")
            import hashlib

            manifest = {
                "schema_version": 1,
                "product_name": "Theme",
                "tag": "v1.0.0",
                "version_name": "1.0.0",
                "version_code": 1,
                "source_sha": SHA_A,
                "package_id": "launcher.variety.theme.plugin.cbk_black",
                "plugin_id": "cbk_black",
                "signer_sha256": "A" * 64,
                "release_mode": "create_new_release",
                "release_state": "stable",
                "replace_existing_assets": False,
                "assets": [
                    {
                        "name": asset.name,
                        "role": "installable_apk",
                        "destination": "release",
                        "size": 3,
                        "sha256": hashlib.sha256(b"apk").hexdigest(),
                    },
                    {
                        "name": "Theme-v1.0.0.apk.sha256",
                        "role": "sha256_sidecar",
                        "destination": "release",
                        "size": 1,
                        "sha256": hashlib.sha256(b"s").hexdigest(),
                    },
                    {
                        "name": "Theme-v1.0.0.apk.metadata.txt",
                        "role": "metadata_sidecar",
                        "destination": "release",
                        "size": 1,
                        "sha256": hashlib.sha256(b"m").hexdigest(),
                    },
                ],
            }
            (root / "Theme-v1.0.0.apk.sha256").write_bytes(b"s")
            (root / "Theme-v1.0.0.apk.metadata.txt").write_bytes(b"m")
            (root / "release-notes.md").write_text("notes", encoding="utf-8")
            manifest_path = root / "release-manifest.json"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            load_manifest(manifest_path)
            (root / "unexpected.txt").write_text("x", encoding="utf-8")
            with self.assertRaisesRegex(ReleaseError, "Unplanned"):
                load_manifest(manifest_path)


if __name__ == "__main__":
    unittest.main()
