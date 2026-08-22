# Snow icon source and licence

This directory contains the four Snow icons used by the TS18 dashboard hotseat.
They are kept separate from the Apache-2.0 project-authored assets because Snow
is licensed under **GNU GPL v3**.

Source project: `baitmooth/snow`

Source revision recorded inside the supplied Snow 7 APK:
`edc24150a787c14e082e72f55bbce7dca6239cdd`

Supplied package: `org.baitmooth.snow_7.apk.zip` (an APK supplied with a `.zip`
suffix for this private project).

| Theme use | Snow drawable | Compiled APK vector | Extracted source here |
| --- | --- | --- | --- |
| Music / Auxio | `auxio` | `res/tG1.xml` | `icons/auxio.svg` |
| Bluetooth | `bluetooth` | `res/_01.xml` | `icons/bluetooth.svg` |
| Navigation / Organic Maps | `organicmaps` | `res/_t.xml` | `icons/organicmaps.svg` |
| Video | `video` | `res/6T.xml` | `icons/video.svg` |

The SVGs preserve the vector viewport, path data, fill and stroke attributes from
the compiled Android vector drawables. They were converted only from Android
VectorDrawable XML representation to ordinary SVG and renamed for clarity.

The app-drawer and playback-control glyphs remain project-authored assets because
the supplied Snow pack does not provide a verified semantic match for those theme
controls.

See `LICENSE` in this directory for GPL v3. The rest of this repository remains
under its existing licence except where a file or generated asset explicitly
states otherwise.
