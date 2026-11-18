# Manual APK releases

For nonproduction TESTING snapshots and exact-ref selection see [the launcher guide](STANDALONE_LAUNCHER.md#testing-build-workflow). This document describes the manual production/release transaction; it does not authorise merging, signing/publication or installation. Follow the current user task. Workflow definitions and `release-config.json` are the build/release authorities; inspect the selected revision before dispatch.

The supported release path is `.github/workflows/manual-release.yml`. It is manual-only and uses the repository `GITHUB_TOKEN`; no PAT, deploy key, environment bypass or ruleset bypass is required.

## Authorities and prerequisites

- The Manual Release form is the release version authority. An explicit `version_tag` accepts strict `X.Y.Z` or `vX.Y.Z`; the workflow normalises it to tag `vX.Y.Z` and builds the APK with `versionName=X.Y.Z`.
- Android `versionCode` is derived as `major × 1,000,000 + minor × 1,000 + patch` within Android's supported range.
- `release-config.json` owns the launcher release identity: application ID `com.cbkii.ts18launcher`, module `launcher`, task `:launcher:assembleRelease`, SDK 29/29/29, no native ABIs, required APK entries and launcher classes, and the install-tools ZIP inputs.
- A new release must be dispatched from the latest default-branch HEAD and use a version newer than every SemVer tag/Release.
- Existing signing secrets are `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`; no vendor signer is used or accepted into the repository.

The workflow has a repository-wide release lock and does not cancel an in-progress publication.

## Qualification contract

The qualify job checks out the immutable source SHA, validates sources, runs that source's `tools/launcher_apk_check.py` envelope, decodes the ephemeral keystore, builds `:launcher:assembleRelease` exactly once and qualifies those exact APK bytes.

Qualification fails unless all of the following agree:

- application ID `com.cbkii.ts18launcher` and **no** DoFun plug-in metadata;
- requested `versionName`, `versionCode`, min/target/compile SDK;
- v1/v2 APK signature validity and expected release certificate;
- ZIP integrity/alignment, empty native ABI set, required launcher entries and classes, v2 signing (v1 is required only below minSdk 24);
- launcher source/envelope validators; actual SystemUI geometry and touch are separately qualified from live display/decor/insets;
- install manifest identity/version/source SHA/APK SHA-256.

The installation-tools ZIP is built deterministically from repository scripts/docs plus the qualified release/install manifest. It does **not** duplicate the APK.

## Public assets

For resolved version `vX.Y.Z` the planned public assets are exactly:

- `TS18-Launcher-vX.Y.Z.apk`
- `TS18-Launcher-vX.Y.Z.apk.sha256`
- `TS18-Launcher-vX.Y.Z.apk.metadata.txt`
- `TS18-Launcher-vX.Y.Z-install-tools.zip`

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

The ZIP contains Termux scripts, a concise README/install document and an immutable release/install manifest with tag/version, source SHA, launcher application ID and expected APK SHA-256. Current launcher manifests do not carry a DoFun plug-in ID. Runtime scripts read that manifest rather than guessing release identity.

No APK, keystore, vendor asset, device log or donor bytes are packaged into the public install-tools ZIP.

## Validation boundary

CI validates source, geometry, Termux scripts/tests and debug/release configuration. The manual workflow validates the exact production-signed release bytes and all public assets. The current product has its own launcher Activity and is independent of DoFun at runtime; DoFun remains installed/enabled for recovery. Emulator launch success does not prove TS18 presentation or audio.

Installation/update, HOME/drawer, map pixels/touch, fullscreen/HOME return, SystemUI geometry, first-tap media/radio, loaders and lifecycle persistence need the exact TS18 [consolidated campaign](CONSOLIDATED_TESTING_PLAYBOOK.md). DoFun catalogue discovery, U-disk import and donor substitution are retired-theme work, not launcher release steps.


## Retired theme releases

Releases created before the theme retirement (package `launcher.variety.theme.plugin.sfp_cbk_black`) stay published and their manifests remain valid input to `verify_manifest.py`, including the `plugin_id` they carry. Rebuilding one is no longer possible from current workflows; `repair_existing_release` applies to launcher releases only.
