# Manual APK releases

The supported release path is `.github/workflows/manual-release.yml`. It is manual-only and uses the repository `GITHUB_TOKEN`; no PAT, deploy key, environment bypass or ruleset bypass is required.

## Authorities and prerequisites

- The Manual Release form is the release version authority. An explicit `version_tag` accepts strict `X.Y.Z` or `vX.Y.Z`; the workflow normalises it to tag `vX.Y.Z` and builds the APK with `versionName=X.Y.Z`.
- Android `versionCode` is derived as `major × 1,000,000 + minor × 1,000 + patch` within Android's supported range.
- `release-config.json` owns the application ID `launcher.variety.theme.plugin.sfp_cbk_black`, plug-in ID `sfp_cbk_black`, SDK contract, expected APK path, release asset stem and install-tools bundle contract.
- A new release must be dispatched from the latest default-branch HEAD and use a version newer than every SemVer tag/Release.
- Existing signing secrets are `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`; no vendor signer is used or accepted into the repository.

The workflow has a repository-wide release lock and does not cancel an in-progress publication.

## Qualification contract

The qualify job checks out the immutable source SHA, fetches only the checksum-pinned RePlugin AAR, validates sources, decodes the ephemeral keystore, builds `:theme:assembleRelease` exactly once and qualifies those exact APK bytes.

Qualification fails unless all of the following agree:

- application ID `launcher.variety.theme.plugin.sfp_cbk_black` and plug-in ID `sfp_cbk_black`;
- requested `versionName`, `versionCode`, min/target/compile SDK;
- v1/v2 APK signature validity and expected release certificate;
- ZIP integrity/alignment, empty native ABI set, required theme assets and RePlugin entry class;
- compatibility/geometry validators, including safe-right x=1225 for the exact release profile;
- install manifest identity/version/source SHA/APK SHA-256.

The installation-tools ZIP is built deterministically from repository scripts/docs plus the qualified release/install manifest. It does **not** duplicate the APK.

## Public assets

For resolved version `vX.Y.Z` the planned public assets are exactly:

- `TS18-Dashboard-Theme-vX.Y.Z.apk`
- `TS18-Dashboard-Theme-vX.Y.Z.apk.sha256`
- `TS18-Dashboard-Theme-vX.Y.Z.apk.metadata.txt`
- `TS18-Dashboard-Theme-vX.Y.Z-install-tools.zip`

Every planned public asset participates in remote state verification. The publisher creates/uses a draft, uploads/reuses only expected bytes, waits for GitHub's uploaded state, checks GitHub's digest when supplied, downloads and hashes every expected asset, and changes the Release state only as the final API write.

A mismatch must leave the Release safely draft; expanding the asset set must not weaken the existing transaction guarantees.

## Normal release

1. Merge the release-ready change to the default branch and confirm **Validate** is green there.
2. Open **Actions → Manual Release → Run workflow** and select the default branch.
3. Choose `create_new_release`.
4. Enter the intended version, or leave it blank for deterministic next-version resolution.
5. Choose `stable`, `prerelease` or `draft`.
6. Leave replacement disabled for a normal new release and run the workflow.
7. Confirm all four public assets are present and the published release state matches the requested state.

## Repair an interrupted release

Use repair only after inspecting the failed workflow and exact immutable tag/Release. Repair binds to that tag, rebuilds/requalifies from its commit and never moves or deletes the tag.

Identical existing assets are reused; missing assets are uploaded. Replacement of an expected mismatched asset is permitted only for an existing draft and only when explicitly requested. Published release assets are not blindly replaced.

If a run fails after tag creation, retain the tag as recovery evidence. Do not delete/move it merely to make create mode pass.

## Failure and retry semantics

- Read-only GitHub calls use bounded retry for known transient failures; mutating API calls are not blindly replayed.
- Fresh remote inventory is taken before publication and again inside the publisher.
- Mismatched tags, duplicate/unplanned assets, changed preflight state, incomplete uploads, size/digest/download-hash mismatches and illegal state transitions fail closed.
- A valid tag is never updated or deleted.
- Draft-first ordering prevents a public partial Release.
- Failed-run qualification/preflight artifacts remain available for bounded recovery inspection.

## Installation-tools boundary

The ZIP contains Termux scripts, a concise README/install document and an immutable release/install manifest with tag/version, source SHA, application ID, plug-in ID, safe-area profile and expected APK SHA-256. Runtime scripts read that manifest rather than guessing release identity.

No APK, keystore, vendor asset, device log or donor bytes are packaged into the public install-tools ZIP.

## Validation boundary

CI validates source, geometry, Termux scripts/tests and debug/release configuration. The manual workflow validates the exact production-signed release bytes and all public assets. The theme has no launcher Activity and depends on the protected DoFun host, so emulator launch success would not prove the product.

Installation, catalogue discovery, U-disk import acceptance, donor substitution, map interaction, right-SystemUI behaviour, media/radio behaviour and lifecycle persistence must be tested on the exact TS18 using [TS18 physical validation](TS18_VALIDATION.md).
