# TS18 Dashboard Theme

A clean-build, research-led DoFun/Variety launcher theme for CB's Topway TS18.

The v1.0.0 candidate follows this composition:

- one uninterrupted 64 px top strip: radio left, music middle, date right;
- a 1154 x 583 embedded navigation window below the strip, with nothing drawn over it;
- compact radio text in the form `< 89.06 FM >`, with only previous/next controls;
- media previous/play-next controls and a wide `Artist - Track Name` field;
- date formatted as `DD MMM`, with no duplicate clock;
- flat black surfaces, white glyphs and warm coral/orange/brown accents;
- no album art, visualiser, icon tiles or decorative icon backgrounds.

The repository builds its own APK. It does not repack a vendor theme or copy the supplied themes'
code, layouts, images or styling. Static comparison with seven independently selected DoFun theme
plug-ins established the compatibility contract: the plug-in metadata, package convention, SDK
levels, declarative JSON/resources and small Qihoo360 RePlugin loader payload. The clean build obtains
that open-source loader from its official repository and verifies its pinned SHA-256 before use.

## What v1.0.0 does—and does not—prove

| Area | Status |
| --- | --- |
| Seven supplied theme plug-ins statically cross-checked | Complete |
| Installed DoFun `V9.7.2.367.260312` statically audited | Complete for unprotected manifest/configuration |
| Clean Gradle APK, unique package and declarative resources | Implemented; CI-qualified for release |
| 64 px strip and unobstructed 1154 x 583 map | Source-validated; physical rendering pending |
| `DD MMM`, compact radio and media field | Configured; physical rendering pending |
| Separately signed theme discovery and persistence | Requires the physical TS18 |
| Saved-station semantics | Requires stock radio and NavRadio+ validation |
| True marquee, generic media routing and information-region app launch | Host behaviour unproven; reserved for the broad integration lane if native DoFun is insufficient |

The installed launcher is 360 Jiagu-protected. Static inspection cannot establish its protected
runtime behaviour. This v1 theme is intentionally declarative: it does not add a player, playback
service, queue, MediaSession or notification and does not modify `com.dofun.variety`.

## Repository map

- `theme/` — Android application module, manifest, custom JSON and project-authored resources.
- `release-config.json` and `gradle.properties` — release identity and version authorities.
- `design/` — editable layout and fallback/control vector sources.
- `third_party/snow/` — optional GPL-3.0 Snow hotseat icon sources and provenance.
- `tools/ts18_theme.py` — dependency-free source/APK audit and source validation.
- `tools/fetch_replugin.py` — bounded, checksum-pinned open-source dependency fetch.
- `scripts/release/` — qualify-before-publish release planner, verifier and state machine.
- `research/` — hashes and static findings for locally supplied evidence; no vendor binaries.
- `docs/` — architecture, evidence, release operation and physical-device validation.

Vendor APKs, extracted vendor assets, signing keys, fonts and device data are deliberately excluded.

## Local development

The repository validators need Python 3.9 or newer:

```bash
python3 tools/ts18_theme.py validate
python3 -m unittest discover -s tests -v
```

A local Android build needs JDK 17, Gradle 9.5.0, Android platform 29 and Android Build Tools 36.0.0.
The dependency fetch is explicit and checksum-verified:

```bash
python3 tools/fetch_replugin.py
gradle :theme:lintDebug :theme:assembleDebug
```

For a signed local release build, provide the same four GitHub secret values plus a decoded keystore
path through `TS_THEME_KEYSTORE_FILE`, then run `gradle :theme:assembleRelease`. Never commit the
keystore, APK or fetched AAR. GitHub Actions performs the supported signed release path.

## Releasing

Use the repository's **Manual Release** workflow. It builds the signed APK exactly once, qualifies
the exact bytes, transfers only a closed release bundle to a separate publisher job, creates a draft
first, verifies every remote asset by download/hash, and changes the requested release state last.
It never moves or deletes a tag.

Normal and repair procedures, asset names, failure semantics and secret requirements are documented
in [Releasing](docs/RELEASING.md). Do not create releases manually in parallel with the workflow.

## Development order after v1.0.0

1. Install the signed release APK and prove `cbk_black` is discovered, selectable and persistent.
2. Validate the top strip, unobstructed map and touch regions.
3. Query DoFun's selected-music provider and `RemoteMediaService` on the exact unit.
4. Test native handling with Auxio-TS and an unrelated Android media player.
5. If native behaviour is incomplete, develop the broad compatibility adapter described in
   [Architecture](docs/ARCHITECTURE.md) as a separate, guarded component.
6. Validate exactly-once controls, ticker updates, app launching, reboot and ACC sleep/wake.

## Safety and licence

The project does not replace or delete the launcher, stock radio/music packages or any system
partition. Radio remains separate from Android media. Any later adapter must select one playback
authority per action and remain fail-open and reversible.

Project-authored code, documentation and visual assets are Apache-2.0 licensed. The optional Snow
hotseat icons are separately GPL-3.0 licensed with editable source retained under
`third_party/snow/`. Qihoo360 RePlugin is Apache-2.0 licensed. Vendor evidence is not part of this
project or licence. See [NOTICE](NOTICE.md).
