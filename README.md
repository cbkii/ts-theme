# TS18 Dashboard Theme

A research-led custom DoFun/Variety launcher theme for CB's Topway TS18.

The prototype now follows this exact composition:

- one uninterrupted 64 px top strip: radio left, music middle, date right;
- a 1154 x 583 embedded navigation window below the strip, with nothing drawn over it;
- radio text in the compact form `< 89.06 FM >`, with only previous/next controls;
- media previous/play-next controls followed by a scrolling `Artist - Track Name` field;
- tapping the radio or media information region opens its selected/default application;
- date formatted as `DD MMM`, with no duplicate clock;
- flat black surfaces, white glyphs and warm coral/orange/brown accents;
- no album art, visualiser, icon tiles or decorative icon backgrounds.

The supplied theme APKs prove that layout and visual styling are declarative. The media ticker,
generic-session selection and information-region click behaviour may require executable host
integration because they are not exposed by the observed theme JSON schema.

## Evidence status

| Area | Status |
| --- | --- |
| FYD and GB2 plug-in audit | Complete |
| Installed DoFun `V9.7.2.367.260312` static audit | Complete for unprotected manifest/configuration |
| Revised top-strip and unobstructed-map prototype | Included |
| User-signed theme discovery | Requires the physical TS18 |
| `DD MMM` date format | Configured; requires render validation |
| Radio saved-preset semantics | Requires stock radio and NavRadio+ validation |
| Generic media and ticker | Broad integration architecture defined; runtime path unproven |
| Information-region app launch | Required acceptance criterion; runtime path unproven |

The installed launcher is 360 Jiagu-protected. Static analysis therefore proves its manifest,
resources and unprotected configuration, but not its protected runtime implementation.

## Repository map

- `theme/src/main/assets/` — custom JSON overlay.
- `theme/src/main/res/` — generated compatibility resources.
- `design/` — project-authored layout and fallback/control vector assets.
- `third_party/snow/` — GPL-3.0 Snow hotseat icon sources and provenance.
- `tools/ts18_theme.py` — dependency-free APK audit, validation and template-repack tool.
- `tools/capture_dofun.sh` — bounded, read-only physical evidence collector.
- `research/` — hashes and static findings from the supplied binaries.
- `docs/` — feasibility, architecture, roadmap and device validation.

Vendor APKs, signing keys, fonts and vendor images are deliberately excluded.

## Quick start

Requires Python 3.9 or newer.

```bash
python3 tools/ts18_theme.py validate
python3 -m unittest discover -s tests -v
python3 tools/ts18_theme.py audit \
  --apk .local/launcher.variety.theme.plugin.sfp_fyd18.apk
```

To create an **unsigned development probe** from the exact FYD template:

```bash
python3 tools/ts18_theme.py package \
  --base-apk .local/launcher.variety.theme.plugin.sfp_fyd18.apk \
  --output build/ts18-dashboard-theme-unsigned.apk
```

Then align and sign it using Android SDK Build Tools:

```bash
zipalign -p -f 4 \
  build/ts18-dashboard-theme-unsigned.apk \
  build/ts18-dashboard-theme-aligned.apk

apksigner sign \
  --ks "$TS18_THEME_KEYSTORE" \
  --out build/ts18-dashboard-theme-dev.apk \
  build/ts18-dashboard-theme-aligned.apk

apksigner verify --verbose --print-certs \
  build/ts18-dashboard-theme-dev.apk
```

Do not commit the APK, reference input or keystore. The template route exists to test discovery and
geometry before investing in a clean RePlugin build.

## Development order

1. Prove the separately signed `cbk_black` plug-in is discovered.
2. Validate the top strip, completely unobstructed map and touch regions.
3. Query DoFun's exported selected-music provider and `RemoteMediaService` on the exact unit.
4. Test native generic-session handling with Auxio-TS and an unrelated media player.
5. If native behaviour is incomplete, build the broad compatibility adapter described in
   [Architecture](docs/ARCHITECTURE.md).
6. Validate exactly-once controls, ticker updates, app launching, reboot and ACC sleep/wake.

## Scope and safety

The project does not replace or delete `com.dofun.variety`, stock radio/music packages or any system
partition. The dashboard may consume several compatibility inputs, but it must dispatch each user
action to one selected playback authority and must not create another player, queue, MediaSession or
playback notification.

## Licence

Project-authored code, documentation and visual assets are Apache-2.0 licensed. The four Snow
hotseat icons (Auxio, Bluetooth, Organic Maps and Video) are separately GPL-3.0 licensed; their
editable source and licence are retained under `third_party/snow/`. Vendor APKs and extracted vendor
assets are not part of this project or licence. See [NOTICE](NOTICE.md).

## Current layout notes

- Default hotseat icons are generic and universal (`music`, `bt`, `navi`, `video`, `apps`).
- Limited exact-app alternates extracted from Snow remain bundled as optional assets (`alt_music_auxio`, `alt_bt_snow`, `alt_navi_organicmaps`, `alt_video_snow`).
- Geometry is intentionally conservative rather than truly dynamic: the hotseat is narrowed to 81 px and a 33 px right reserve is left for better tolerance of stock edge overlays.
