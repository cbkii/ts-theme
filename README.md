# TS18 Dashboard Theme

A clean-build, research-led DoFun/Variety window/PIP theme for CB's Topway TS18.

The compatibility-hardening target keeps the design deliberately small and host-native:

- one uninterrupted 64 px top strip: radio left, music middle, date right;
- the largest navigation window that fits the exact-device safe area without entering the right Topway/SystemUI region;
- exact-device safe-right boundary x=1225 on the 1280 x 720 panel, based on the last direct capture;
- compact radio text in the form `< 89.06 FM >`, with previous/next controls;
- media previous/play-next controls and a wide `Artist - Track Name` field;
- date formatted as `DD MMM`, with no duplicate clock;
- flat black surfaces, white glyphs and warm coral/orange/brown accents;
- no album art, visualiser, icon tiles or decorative icon backgrounds.

The repository builds its own independently signed APK. It does not repack a vendor theme or copy supplied themes' code, layouts, images, certificate or styling. Static comparison with seven independently selected DoFun theme plug-ins established the compatibility contract: package/metadata convention, SDK levels, declarative JSON/resources and the small Qihoo360 RePlugin loader payload. The clean build obtains the open-source RePlugin dependency from its official repository and verifies its pinned SHA-256 before use.

## Compatibility-hardening contract

The next release uses the unique window/PIP identity:

```text
application id: launcher.variety.theme.plugin.sfp_cbk_black
plug-in id:     sfp_cbk_black
```

The `sfp_*` prefix follows the audited/4PDA window-theme convention without impersonating any existing vendor theme. This is an evidence-backed compatibility choice, not proof that current DoFun accepts an independently signed plug-in directly.

The maintained envelope is minSdk 16, targetSdk 26, compileSdk 29, Qihoo360 RePlugin 2.3.4, no native ABI payload, no launcher Activity and no extra service/media implementation. The installed launcher remains `com.dofun.variety`.

## Exact-device safe area

Reference panel: **1280 x 720**. Last exact-device evidence showed an app-visible right boundary at **x=1225**, leaving a 55 px right Topway/SystemUI region, plus a roughly 55 px top system region.

The hardened layout therefore treats x=1225 as a hard maximum for theme-controlled visible/interactive content unless newer present-state evidence proves a different geometry. The current design profile starts dashboard content immediately after the 81 px hotseat and keeps the map/top strip inside the safe area. See [Layout specification](docs/LAYOUT_SPEC.md).

## Installation routes

Physical discovery remains unproven until tested on the target TS18. The supported attempts are intentionally ordered from least to most invasive:

1. ordinary Android package installation/discovery;
2. the original DoFun/4PDA local or U-disk theme import path;
3. rooted RePlugin donor-slot substitution for the historical `damaged file`/signature-rejection case.

The Termux toolkit performs read-only preflight first, exports diagnostics/backups to `/storage/emulated/0/Download/TS18-theme-install/`, uses root only for the narrow operations that require it, leaves DoFun `p.l` unchanged by default and provides standalone rollback. See [TS18 installation](docs/INSTALL_TS18.md).

4PDA donor precedent: <https://4pda.to/forum/index.php?showtopic=1015856&st=29360>

Official DoFun product information: <https://www.dofun.cc/car-desktop/car-desktop-en.html>

## Evidence boundary

| Area | Status |
| --- | --- |
| Seven supplied theme plug-ins statically cross-checked | Complete |
| Installed DoFun `V9.7.2.367.260312` statically audited | Complete for unprotected manifest/configuration |
| Safe-right x=1225 for the exact device | Last-observed direct device evidence; re-check before mutation |
| Clean Gradle APK, unique package and declarative resources | Source/CI validated after implementation |
| Separately signed theme discovery and persistence | Requires physical TS18 |
| U-disk import of the project APK | Requires physical TS18 |
| Rooted donor substitution | Historical 4PDA precedent; project helper must still be validated on exact device |
| Saved-station semantics | Requires stock radio and NavRadio+ validation |
| True marquee, generic media routing and information-region app launch | Host behaviour unproven; separate integration lane if native DoFun is insufficient |

The installed launcher is 360 Jiagu-protected. Static inspection cannot establish its protected runtime behaviour. This theme remains intentionally declarative: it does not add a player, playback service, queue, MediaSession or notification and does not modify `com.dofun.variety`.

## Repository map

- `theme/` — Android application module, manifest, declarative JSON and project-authored resources.
- `release-config.json` — release identity/build contract and exact layout/install bundle contract.
- `design/` — editable layout and fallback/control vector sources.
- `third_party/snow/` — optional GPL-3.0 Snow hotseat icon sources and provenance.
- `tools/ts18_theme.py` — dependency-free source/APK compatibility and geometry validation.
- `tools/fetch_replugin.py` — bounded, checksum-pinned open-source dependency fetch.
- `scripts/termux/` — preflight, interactive install and standalone rollback for the physical TS18.
- `scripts/release/` — qualify-before-publish release planner, verifier and state machine.
- `research/` — hashes and static findings for locally supplied evidence; no vendor binaries.
- `docs/` — architecture, installation, evidence, release operation and physical-device validation.

Vendor APKs, extracted vendor assets, signing keys, fonts and device data are deliberately excluded.

## Local development

The repository validators need Python 3.9 or newer:

```bash
python3 tools/ts18_theme.py validate
python3 -m unittest discover -s tests -v
```

Termux helpers must also pass Bash syntax checks:

```bash
bash -n scripts/termux/*.sh scripts/termux/lib/*.sh
```

A local Android build needs JDK 17, Gradle 9.5.0, Android platform 29 and Android Build Tools 36.0.0. The dependency fetch is explicit and checksum-verified:

```bash
python3 tools/fetch_replugin.py
gradle :theme:lintDebug :theme:assembleDebug
```

For a signed local release build, provide the same four GitHub secret values plus a decoded keystore path through `TS_THEME_KEYSTORE_FILE`, then run `gradle :theme:assembleRelease`. Never commit the keystore, APK or fetched AAR. GitHub Actions performs the supported signed release path.

## Releasing

Use the repository's **Manual Release** workflow. It builds the signed APK exactly once, qualifies the exact bytes, builds a deterministic installation-tools ZIP around the qualified release metadata, transfers only a closed release bundle to a separate publisher job, creates a draft first, verifies every remote asset by download/hash, and changes the requested release state last.

The optional dispatch version is authoritative for the tag and APK; a blank create value resolves a deterministic next version. The workflow never moves or deletes a tag. See [Releasing](docs/RELEASING.md).

## Physical validation order

1. Run the Termux preflight and compare current DoFun/display state with the release profile.
2. Try direct package discovery.
3. If required, try the official/4PDA U-disk import path.
4. Only if direct import is rejected and current RePlugin storage matches the known contract, use the rooted donor-slot method with verified backup.
5. Validate right-edge layout/map touch, media/radio behaviour, launcher restart, reboot, cold boot and ACC sleep/wake.
6. If native DoFun media behaviour is incomplete, investigate the broad compatibility adapter as a separate guarded component rather than enlarging the theme APK.

## Safety and licence

The project does not replace or delete the launcher, stock radio/music packages or any system partition. The installer does not modify the DoFun APK or vendor signer. Radio remains separate from Android media. Any later adapter must select one playback authority per action and remain fail-open and reversible.

Project-authored code, documentation and visual assets are Apache-2.0 licensed. The optional Snow hotseat icons are separately GPL-3.0 licensed with editable source retained under `third_party/snow/`. Qihoo360 RePlugin is Apache-2.0 licensed. Vendor evidence is not part of this project or licence. See [NOTICE](NOTICE.md).
