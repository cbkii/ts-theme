# DoFun / TS18 working-theme compatibility reference

This document records clean-room interoperability conclusions from the user-supplied themes that are known to run on the target TS18. The APKs, vendor resources, executable code, certificates, and signing keys are **not** committed.

The controlling machine-readable evidence is `research/working-theme-reference-matrix.json`.

## What the expanded reference set proves

Five externally runnable themes share minSdk 16, targetSdk 26, compileSdk 29, one DEX, no native ABI, a generated `BuildConfig`, a `.land` version suffix, a 14-digit timestamp-form `themeId`, and the same DoFun/Zheng signing certificate.

The references also prove several earlier assumptions were too strict:

- `.gen/c.json` is optional: SFP_FYD18 and SFP_T5 run without it.
- `variety.theme` classes are optional: SFP_FYD18 and SFP_T5 contain none.
- `medias` is not the only valid media widget model: SFP_FYD18 uses top-level `local_radio` and `local_music|bt_music`.
- `oemId=1132` is valid on the target family: SFP_FYD18 uses it.

The strongest common executable distinction from current `ts-theme` is therefore the legacy Android dexing/build envelope. Every working reference uses D8 1.x; the newer working generation and the exact-host built-in `kp.jar` use D8 1.4.77. Current AGP 9 produces D8 9.x and a different `library/*` synthetic class topology.

## Controlled PR #8 candidate

PR #8 therefore keeps the normal modern release path intact and adds a separate physical-test carrier under `compat/legacy-d8/`:

- Gradle 5.1.1 + AGP 3.4.0, yielding D8 1.4.77;
- real `land` product flavor and `BuildConfig`;
- internal version name `<versionCode>_<YYMMDDHHMMSS>.land`;
- project-owned package/plugin identity and independent V1+V2 signing;
- a stable 14-digit project `themeId` (`20260822000100`) with evidence-backed `oemId=1132`;
- project-authored root JSON assets using the original `desktop_window`, `local_radio`, `local_music|bt_music`, `time` layout;
- no `.gen/c.json`, resolution mirror, `variety.theme` SDK, native ABI, or Android component.

CI fails if the compatibility APK loses the legacy D8 1.4.77 marker, the eight-class legacy `library/*` topology, `BuildConfig`, RePlugin `Entry`, one-DEX/no-native shape, or the intended import metadata. CI also checks V1 and V2 signing and preserves the exact compatibility APK as a short-lived Actions artifact.

## Physical decision gates

Do not conflate import and rendering.

**Gate A — import/registration** passes only when DoFun adds `sfp_cbk_black` to `p.l` and the resulting numeric `app_p_a/*.jar` has the exact APK SHA-256.

**Gate B — activation** is tested only after Gate A and passes only when Use/Apply actually loads the project theme without fallback and it survives launcher restart/reboot as expected.

If the legacy carrier still fails Gate A, after the `.land`/BuildConfig/themeId/manifest/toolchain differences have been removed, the signer gate becomes the dominant remaining hypothesis because every known-working external reference and the host share the same vendor certificate. The project must not copy or forge that signer; the next preferred rooted direction is the already-roadmapped narrow DoFun-only LSPosed redirect, with guarded donor substitution retained as a fallback/diagnostic route.
