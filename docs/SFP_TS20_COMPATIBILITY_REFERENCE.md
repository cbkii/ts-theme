# SFP_TS20 compatibility reference and next activation candidate

## Purpose

The user-supplied `sfp_ts20.apk` is the strongest clean-room source reference currently available for the DoFun theme-import/parser contract because it is a known-working `sfp_*` package and was also observed on the exact TS18 to register through RePlugin as a normal `app_p_a` plug-in.

The APK itself, vendor resources, executable code and signing material are **not** committed. Only structural facts needed to make this repository interoperate with DoFun are recorded in `research/sfp-ts20-reference-audit.json`.

## Relevant reference facts

The supplied APK has:

- package `launcher.variety.theme.plugin.sfp_ts20` and plug-in ID `sfp_ts20`;
- Android versionCode `213`, versionName `213_241030172244.land`;
- min/target/compile SDK 16/26/29;
- one DEX, 63 classes, no native ABI;
- Qihoo360 RePlugin entry classes;
- generated `assets/.gen/c.json` metadata;
- `land` variant metadata and resolution roots for `1280x652` and `1920x1087`;
- `medias` as the actual DoFun widget/plugin type for the combined media/radio surface;
- `local_music`, `local_radio` and `bt_music` as modes handled by that widget, not independent top-level widget types.

The exact current-host built-in `kp.jar` inspected separately corroborates the two most important structural findings: it also carries `.gen/c.json`, and its generated plugin inventory uses `medias` rather than `local_music`/`local_radio` as a top-level widget type.

## Why the previous candidate was under-controlled

The previous release used top-level theme entries:

```text
local_radio
local_music|bt_music
```

Those names are media-source modes in the reference contract, not demonstrated theme widget types. The APK also omitted `.gen/c.json` and expressed the physical 55 px top system inset directly in theme-local coordinates.

Existing runtime evidence showed DoFun reaching the custom RePlugin classloader and entering `configTheme parser`, then loading a known vendor theme and falling back to the default parser. That makes parser-contract mismatch a higher-priority explanation than Android package installation itself.

## Controlled candidate in this branch

This branch intentionally changes only the strongest compatibility variables first:

1. **Host-proven widget vocabulary** — top-level `theme_config` is reduced to `desktop_window`, one `medias` widget, and `time`.
2. **Generated capability index** — project-authored `.gen/c.json` describes exactly those JSON widget types and the five `app` hotseat entries.
3. **Land/resolution contract** — `assets/layout-1280x652/` mirrors every referenced JSON dependency.
4. **Coordinate-space separation** — physical geometry remains authoritative in `config/ts18-layout.json`, while theme-local geometry starts at y=0 inside a 1280x652 viewport whose physical y-origin is 55. Thus theme-local map y=64 still projects to the established physical map y=119.
5. **No vendor impersonation** — package/plug-in identity and independent signing stay unique. No vendor code, images, certificate or proprietary layout XML is copied.
6. **No speculative legacy toolchain change yet** — current AGP/D8 remains in place so parser vocabulary/metadata/viewport can be tested before introducing D8 1.4.77 as another variable.

The candidate deliberately collapses the separate radio + music cards into one wide `medias` surface. That is an activation/compatibility probe, not the final UI. Once DoFun reliably stays on the custom theme after **Use**, the final split presentation can be rebuilt using only widget semantics proven by the host.

## Physical success criteria

The candidate is successful only if all of the following are observed on the TS18:

1. local/U-disk import completes;
2. the custom tile remains available;
3. **Use/Apply** actually changes the launcher and does not immediately fall back;
4. the custom theme remains selected after returning HOME;
5. DoFun restart and reboot preserve or recover the selection normally;
6. the map surface appears in the intended physical area and is not hidden under the right SystemUI;
7. the unified `medias` widget renders without causing a parser fallback.

If activation still fails, the next variables should be tested separately rather than all at once: exact `.gen` shape against another working JSON-only theme, the `variety.theme` SDK envelope, then legacy D8 output, then signer/entitlement behavior or an LSPosed post-Jiagu discovery module if runtime logs show the failure remains inside protected host logic.
