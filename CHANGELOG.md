# Changelog

## 1.0.0 - 2026-08-22

- Added a clean Android/Gradle APK build with unique package and DoFun plug-in identities; release
  APKs no longer repack or inherit code/resources from a vendor theme.
- Cross-checked five additional Topway/Toparea/DoFun theme APKs and matched their shared manifest,
  API and Qihoo360 RePlugin 2.3.4 interoperability contract without copying their UI or assets.
- Corrected the layout to a 64 px radio/music/date strip and a completely unobstructed 1154 x 583
  navigation window.
- Added every selector, string and hidden fallback resource required for a closed clean build.
- Replaced the four application hotseat glyphs with matching Snow icon-pack vectors: Auxio,
  Bluetooth, Organic Maps and Video, while retaining project-authored app-drawer and playback
  controls.
- Added a manual build-once release workflow with exact signing/package/version validation,
  deterministic checksums/metadata, draft-first publication, remote byte verification and safe
  interrupted-release repair.
- Added release planner, qualification and publication state-machine regression tests.
- Retained the flat-black 1280 x 720 design, compact radio, `DD MMM` date, warm palette, wide media
  title surface and broad generic-media integration boundary.
