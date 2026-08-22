# Manual APK releases

The supported release path is `.github/workflows/manual-release.yml`. It is manual-only and uses the
repository `GITHUB_TOKEN`; no PAT, deploy key, environment bypass or ruleset bypass is required.

## Authorities and prerequisites

- `gradle.properties` is the version authority. `VERSION_NAME` is strict `X.Y.Z`; `VERSION_CODE` is
  a positive integer. For v1, these are `1.0.0` and `1`.
- `release-config.json` owns the application ID, plug-in ID, Gradle task, expected APK path, SDK
  contract and exact public asset stem.
- `CHANGELOG.md` must contain a non-empty section for the exact `VERSION_NAME`.
- A new release must be dispatched from the latest default-branch HEAD with a strict matching tag,
  such as `v1.0.0`. The tag must be newer than every SemVer tag or Release in the repository.
- The repository must have the existing secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
  and `KEY_PASSWORD`. `KEYSTORE_BASE64` is the base64 text of the whole keystore, without a data-URL
  prefix. No additional secret is used.

The workflow has a repository-wide release lock and does not cancel an in-progress publication.

## Normal release

1. Merge the release-ready change to the default branch and confirm **Validate** is green there.
2. Open **Actions → Manual Release → Run workflow** and select the default branch.
3. Choose `create_new_release`.
4. Enter the exact tag matching `VERSION_NAME`, for v1: `v1.0.0`.
5. Choose `stable`, `prerelease` or `draft` as the final state.
6. Leave `replace_existing_assets` disabled and run the workflow.

The qualify job has read-only repository permission. It resolves the transaction, checks out the
immutable source SHA, fetches only the checksum-pinned RePlugin AAR, validates the sources, decodes
the ephemeral keystore, builds `:theme:assembleRelease` exactly once and qualifies that exact APK.

Qualification fails unless all of the following agree exactly:

- one maintained APK and no retired APK variants in the output directory;
- application ID `launcher.variety.theme.plugin.cbk_black`;
- `versionName`, `versionCode`, min/target/compile SDK and requested tag;
- v1/v2 APK signature validity and keystore-alias certificate SHA-256;
- ZIP integrity, alignment, the exact empty native-ABI set, required theme assets, plug-in metadata
  and RePlugin entry class.

The publisher receives only the closed, checksummed qualification bundle. It creates the immutable
tag at the qualified source, creates a draft Release, uploads the exact assets, waits for GitHub's
uploaded state, checks GitHub's digest when supplied, downloads and hashes every asset, then changes
the Release to the requested state as its final API call.

The public asset set is deterministic:

- `TS18-Dashboard-Theme-v1.0.0.apk`
- `TS18-Dashboard-Theme-v1.0.0.apk.sha256`
- `TS18-Dashboard-Theme-v1.0.0.apk.metadata.txt`

Release notes are generated from the matching changelog section. Internal plan, manifest and notes
files remain in the 14-day workflow recovery artifact and are not uploaded as public assets.

## Repair an interrupted release

Use repair only after inspecting the failed workflow and the exact tag/Release on GitHub. Typical
recoverable states are an immutable tag with no Release, a draft with missing assets, or a draft with
some identical assets already uploaded.

1. Open **Actions → Manual Release → Run workflow**.
2. Choose `repair_existing_release` and enter the existing exact tag.
3. Select the intended final state. A stable Release cannot be demoted; a published prerelease
   cannot return to draft.
4. Keep `replace_existing_assets` disabled for normal retry. Identical assets are reused and only
   missing assets are uploaded.
5. Enable replacement only when an expected asset in an existing **draft** differs and you have
   deliberately chosen to replace it. The workflow downloads the old expected asset into a 14-day
   recovery artifact before deletion. Published Release assets are never replaced.

Repair always rebuilds from the commit already named by the immutable tag, then requalifies those
bytes. It never changes the tag to the current branch. Unrelated historical assets on a repaired
Release are preserved; new-release mode rejects every unplanned asset.

If a run fails after tag creation, the tag is intentionally retained as recovery evidence. Inspect
the draft and rerun in repair mode. Do not delete or move the tag to make create mode pass.

## Failure and retry semantics

- Read-only GitHub calls use bounded retry for rate limits, transient 5xx responses and transport
  failures. Mutating POST/PATCH/DELETE calls are not blindly replayed.
- A fresh remote inventory is taken before publication and again inside the publisher to detect a
  race or changed tag/Release identity.
- Mismatched tags, missing tags for Releases, duplicate assets, changed preflight assets, incomplete
  uploads, size/digest/download-hash mismatches and illegal state transitions fail closed.
- The valid tag is never updated or deleted. Draft-first ordering prevents a public partial Release.
- A failed run's `qualified-release-*` and `release-preflight-*` artifacts are retained for 14 days.

Do not manually mutate the same Release while the workflow is running. If remote state is unclear,
leave it untouched and inspect it before selecting repair or explicit draft replacement.

## Validation boundary

CI can validate and assemble the debug APK. The manual workflow validates the exact signed release
APK, but the theme has no launcher Activity and depends on the protected DoFun host, so an emulator
launch smoke test would not exercise the product. Installation, catalogue discovery, apply/restart,
reboot persistence, map interaction and media/radio behaviour must be tested on the TS18 using
[TS18 physical validation](TS18_VALIDATION.md).
