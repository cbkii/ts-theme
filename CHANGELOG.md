# Changelog

## Unreleased

- Replaced the four application hotseat glyphs with matching Snow icon-pack vectors: Auxio, Bluetooth, Organic Maps and Video.
- Retained the project-authored app-drawer and playback controls where Snow has no verified semantic control match.
- Added exact Snow source revision, resource mapping and GPL-3.0 attribution under `third_party/snow/`.
- Updated asset generation so checked-in SVG sources, rather than duplicate hand-drawn Pillow paths, produce the theme rasters.

## 0.1.0 - 2026-08-22

- Audited the FYD and GB2 theme plug-ins and the installed DoFun
  `V9.7.2.367.260312` APK.
- Added the flat-black 1280 x 720 prototype with a continuous radio/music/date strip and completely
  unobstructed 1154 x 583 map.
- Changed date to `DD MMM`, removed album art/visualiser and adopted a warm coral/orange/brown
  accent palette.
- Defined compact radio, `Artist - Track Name` ticker and information-region app-launch behaviour.
- Reworked media integration as a broad Android/DoFun/Topway compatibility adapter informed by
  Auxio-TS.
- Added JSON/layout validation, tests, unsigned compatibility repackaging and read-only TS18 capture
  tooling.
