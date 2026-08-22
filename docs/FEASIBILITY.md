# Feasibility assessment

## Conclusion

The revised dashboard is feasible, but the final media behaviour crosses the boundary between a
declarative skin and the protected DoFun host.

Seven audited skins contain JSON/resources plus the same small RePlugin loader scaffolding. None has
a feature service or custom media controller. The installed DoFun APK supplies the runtime widgets
and media adapters.

## Feature matrix

| Requirement | Finding |
| --- | --- |
| Unobstructed large map | Theme-only. `desktop_window` has explicit geometry. The prototype gives it 1154 x 583 px below the strip. |
| One unbroken top strip | Theme-only. Three adjacent widgets occupy the same 64 px row with no gaps and a larger right-side reserve. |
| Compact radio | Theme-only for layout. Known fields expose frequency, band and previous/next; play/pause and MHz are hidden. |
| Previous/next saved station | Runtime semantics unknown. FYD's `step` mode may mean preset, seek or frequency step. |
| `DD MMM` date | Theme-only configuration using `format: "dd MMM"`; physical render still needs validation. |
| Flat black/white with warm accents | Theme-only. Coral `#FF6B57`, orange `#FF9F43` and brown `#8A4F3D` replace the earlier cool palette; black background keeps negative polarity for night use. |
| No album art or visualiser | Theme-only. Both artwork fields are hidden. |
| `Artist - Track Name` ticker | The wide title view is theme-controlled, but no marquee/ticker key exists in the supplied JSON schemas. Host behaviour or an adapter is required. |
| Tap information region to open app | Likely host-owned for stock widgets, but not proven by static JSON. It is a physical acceptance criterion and adapter responsibility if absent. |
| Generic Android media | Host capability is strongly suggested but physical fixed-strip parity remains unproven. |

## Installed DoFun host findings

The supplied installed APK is:

```text
package:      com.dofun.variety
version:      V9.7.2.367.260312 (367)
release time: 2026-03-12 11:47:52
SHA-256:      75e7ea9b46d68754253aa385e6ac750aae957a5b72196fec5449ccf2782c60b1
certificate:  5B88187716D74BCA641B35DA261CFA52F86467DDDA0AC6099083184DF842999D
```

It is signed by the same vendor certificate as FYD/GB2 and is 360 Jiagu-protected. Its visible DEX
contains only the loader, so claims about protected method behaviour require runtime evidence.

The unprotected manifest/configuration proves:

- requested `MEDIA_CONTENT_CONTROL`, `RECORD_AUDIO` and `MODIFY_AUDIO_SETTINGS`;
- notification listener `cn.cardoor.basic.media.NotifyService`;
- exported `cn.cardoor.libs.media.impl.MediaSourceService`;
- bind action `cn.cardoor.libs.media.RemoteMediaService`;
- exported selection provider `content://com.dofun.variety.ExportedProvider/...`;
- fixed music matches for both
  `com.tw.media/com.tw.music.MusicActivity` and
  `com.tw.music/com.tw.music.MusicActivity`;
- built-in media headers for local music, Bluetooth, Spotify, Apple Music, YouTube Music and other
  sources.

This is materially stronger evidence for a generic host media layer than the theme APKs alone, but
it does not prove which source is selected or whether the compact FYD widget receives it.

## Media implementation decision

First test DoFun's native source service, notification listener and selected-target provider. If
that path supplies the required state and controls, use it.

If incomplete, the correct fallback is a **broad compatibility adapter**, not a narrow stock-app
shim. It may consume all evidence-backed paths:

- Android active MediaSessions and MediaController transport controls;
- DoFun notification/media-source services;
- public Android music metadata/play-state broadcasts;
- observed Topway metadata, progress and command broadcasts;
- verified Topway CommandService callbacks;
- optional exact-version DoFun view hooks for ticker/click rendering;
- the existing Auxio-TS stock relay only where genuine stock selection is proven.

“Broad” refers to input compatibility. The adapter must still select one target controller for each
action and must not create another playback service, queue, session or notification.

## Signing and discovery

All seven reference themes and the installed host share a vendor certificate. Static analysis cannot
tell whether DoFun enforces that signer or only discovers:

```text
meta-data name  = launcher.variety.theme.plugin
meta-data value = <plugin id>
package         = launcher.variety.theme.plugin.<plugin id>
```

The clean project build therefore uses:

```text
plugin id = cbk_black
package   = launcher.variety.theme.plugin.cbk_black
```

The project does not copy that vendor certificate or use a vendor APK as a template. Independent
distribution remains unknown until install, catalogue discovery, apply, restart and reboot all pass
on the TS18.
