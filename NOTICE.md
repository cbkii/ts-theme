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

Historical theme builds used the Apache-2.0 Qihoo360 RePlugin loader. The `theme/` module and its download/build path are retired; current launcher APKs include no RePlugin runtime. Historical hash/licence records remain in Git history and [research evidence](docs/RESEARCH_EVIDENCE.md).

Current launcher functional glyphs are Apache-2.0 Material Symbols Rounded; pinned provenance and adaptation details are in [icon sources](docs/ICON_SOURCES.md), with the notice shipped in `launcher/src/main/assets/licenses/MATERIAL_SYMBOLS_NOTICE.txt`. The radio reference dataset has its separate attribution in `launcher/src/main/assets/radio/NOTICE.txt`.

Auxio-TS is referenced as interoperability evidence. No Auxio-TS source or binary is copied into this repository.

## Optional Snow icon alternates

Four optional exact-app resources are derived from the FOSS **Snow** icon pack by
`baitmooth/snow`: Auxio, Bluetooth, Organic Maps and Video. These assets are
separately licensed under **GNU GPL v3** and are not covered by this repository's
Apache-2.0 licence. The exact source revision recorded by the supplied Snow 7 APK
is `edc24150a787c14e082e72f55bbce7dca6239cdd`.

Corresponding editable SVG source and a copy of GPL v3 are retained under `third_party/snow/`.
The four generated `alt_*` PNGs belonged to the retired theme module. Current launcher APKs do not package these Snow assets. Retained SVG source and GPL licence remain separately attributed; they must not be relabelled as Apache-2.0.
