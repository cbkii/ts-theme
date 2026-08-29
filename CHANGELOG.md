# Changelog

## Unreleased

- Harden the unique DoFun window/PIP identity to `launcher.variety.theme.plugin.sfp_cbk_black` / `sfp_cbk_black` while retaining independent signing and the audited SDK/RePlugin compatibility envelope.
- Replace the obsolete 18 px right-edge assumption with the exact-device 55 px Topway/SystemUI safe-area contract; keep all visible/interactive dashboard content at or left of x=1225.
- Rework the map/top strip around the 81 px hotseat so the navigation surface remains maximal without extending under the right SystemUI.
- Remove avoidable host-JSON parser surface and extend source/APK validators for identity, manifest, assets, geometry and no-native/no-component constraints.
- Add a Termux-first installation toolkit covering read-only preflight, direct package discovery, 4PDA U-disk import preparation, guarded rooted RePlugin donor substitution, post-install recording and standalone verified rollback.
- Add deterministic packaging of `TS18-Dashboard-Theme-vX.Y.Z-install-tools.zip` and include it in the same qualify-before-publish/remote-reverification release transaction as the APK, checksum and metadata assets.
- Preserve the Manual Release dispatch version as authoritative, deterministic blank-input version selection, SemVer-derived Android version codes, configuration-cache-safe signing validation and draft-first remote verification.

## 1.0.0 - 2026-08-22

- Added a clean Android/Gradle APK build with unique package and DoFun plug-in identities; release APKs no longer repack or inherit code/resources from a vendor theme.
- Cross-checked five additional Topway/Toparea/DoFun theme APKs and matched their shared manifest, API and Qihoo360 RePlugin 2.3.4 interoperability contract without copying their UI or assets.
- Corrected the layout to a 64 px radio/music/date strip and a completely unobstructed 1154 x 583 navigation window under the then-current layout assumptions.
- Added every selector, string and hidden fallback resource required for a closed clean build.
- Retained simple project-authored white hotseat and playback glyphs, with separately licensed Snow exact-app icons available only as optional alternate resources.
- Added a manual build-once release workflow with exact signing/package/version validation, deterministic checksums/metadata, draft-first publication, remote byte verification and safe interrupted-release repair.
- Added release planner, qualification and publication state-machine regression tests.
- Retained the flat-black 1280 x 720 design, compact radio, `DD MMM` date, warm palette, wide media title surface and broad generic-media integration boundary.
