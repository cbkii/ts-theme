# Notice

This is an independent interoperability and research project for a user-owned Topway TS18. It is not affiliated with, endorsed by or distributed by Topway, DoFun, CarMate, Shenzhen Driving Control Technology, Qihoo 360 or the authors of the supplied theme APKs.

The following inputs were inspected locally but are **not distributed** in this repository:

- `launcher.variety.theme.plugin.sfp_fyd18.apk`
- `launcher.variety.theme.plugin.sfp_gb2.apk`
- `theme_spf_ts10.apk`
- `theme_ts10_tw.apk`
- `launcher.variety.theme.plugin.tw23.apk`
- `launcher.variety.theme.plugin.carplay.apk`
- `launcher.variety.theme.plugin.t7_theme.apk`
- `com.dofun.variety uid_10093.apk`
- their screenshots, fonts, signing material and image resources

Only hashes, interface facts, independently authored configurations, tools, documentation and visual assets are included. Reference APKs are never build inputs.

Qihoo360 RePlugin is a separate Apache-2.0 project. The build fetches the official
`replugin-plugin-lib` 2.3.4 AAR from Qihoo360's published repository and accepts it only when its
SHA-256 is `0c3132e90dc372056bd9601788ee67a1c97fb64d15f6074826825addadf6a89f`. The AAR is
not committed; its loader classes are incorporated in release APKs under RePlugin's Apache-2.0
licence. This repository's Apache-2.0 `LICENSE` accompanies each GitHub Release through the source
archive.

Auxio-TS is referenced as interoperability evidence. No Auxio-TS source or binary is copied into this repository.

## Snow hotseat icons

Four hotseat application icons are derived from the FOSS **Snow** icon pack by
`baitmooth/snow`: Auxio, Bluetooth, Organic Maps and Video. These assets are
separately licensed under **GNU GPL v3** and are not covered by this repository's
Apache-2.0 licence. The exact source revision recorded by the supplied Snow 7 APK
is `edc24150a787c14e082e72f55bbce7dca6239cdd`.

Corresponding editable SVG source, the mapping from the supplied APK resources,
and a copy of GPL v3 are retained under `third_party/snow/`. Generated PNGs in
`theme/src/main/res/mipmap-mdpi-v4/` that correspond to those four icons are
derived from those GPL-3.0 assets.
