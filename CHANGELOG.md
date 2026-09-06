# Changelog

## Unreleased

- Fix `ts18-theme-runtime-diagnostic.sh --install` hanging silently in Termux: the installer now uses one foreground MagiskSU transaction behind a deterministic 20-second timeout, emits visible four-stage progress, fails fast when `coreutils`/a timeout command is unavailable, and verifies `INSTALL_OK` before reporting success. The generic timeout fallback also uses an elapsed-second counter instead of depending on Android `date +%s` support.
- Expand the bounded DoFun activation diagnostic into an LSPosed feasibility pass: re-check Magisk/Zygisk/zygote ABI state, identify the installed LSPosed implementation and module API metadata, query only enabled DoFun scope rows from LSPosed's database in read-only mode, correlate target-process injection with Jiagu/RePlugin/class-loader evidence, and emit an explicit `analysis/lsposed-feasibility.txt` boundary for deciding whether a later log-only DoFun hook is justified.
- Keep LSPosed collection diagnostic-only: no scope/database mutation, no raw LSPosed database or per-module config export, no unrestricted LSPosed/logcat persistence, and no automatic inference of API 100 support from framework/module names alone. The two-run worker remains bounded at 240 seconds hard cap with a 100-second interaction window per run; expensive static APK/JAR inspection runs only after that live window and shares a 45-second aggregate budget.
- Remove AGP 9's unintended built-in Kotlin runtime from the code-free theme APK, returning the executable payload to the minimal RePlugin 2.3.4 plus generated-resource envelope used by the audited working DoFun themes.
- Fail CI and Manual Release if the signed APK becomes multidex, exceeds the 64-class compatibility budget, loses `com.qihoo360.replugin.Entry`, or packages executable classes outside the pinned RePlugin/library and generated theme-resource namespaces.
- Record the exact-device activation failure boundary: while offline, DoFun can list and preview TS18 Dashboard Theme, but applying it fails and the custom tile then disappears until reinstalled. Physical activation remains unverified after the runtime-envelope fix.
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
