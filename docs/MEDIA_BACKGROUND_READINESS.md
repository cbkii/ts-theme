# Background media readiness adapters

Current source contract: [Media](MEDIA.md) and [continuous readiness](CONTINUOUS_READINESS.md). The October PR #17/#24 routes are historical; they do not prohibit the implemented masked Radio recovery or authorise root-first service priming.

## Source contracts

| Source | Passive preparation | Explicit Play / cold recovery |
|---|---|---|
| Auxio-TS `com.tw.media` | Authenticated exported framework MediaBrowser; observed PREPARE, saved item and paused/playing state | Keep the original tap through capability publication. Unloaded Auxio requires advertised `HOME_PLAY_V1` and the original elapsed deadline; restoration/decoder/focus belong to Auxio |
| NavRadio+ `com.navimods.radio` | Observe existing session; creating its service stays gated until the installed API29 build proves no tuner/audio side effects | Exact session; real framework MediaBrowser handshake with one accessible browser/Media3 candidate; then, only before transport dispatch, one bounded masked source-Activity transaction |
| Stock TW Radio `com.tw.radio` | No service route found in the inspected exact APK | Existing exact session only; native tuner/Topway control remains separately unqualified |
| Other configured apps | One unambiguous exported/access-permitted browser/Media3 candidate, qualified by a real handshake and source-safe policy | Advertised source-specific directional Play; Auxio's custom action is not a universal requirement |

A candidate service declaration or successful plain bind is not a controller connection. `BrowserSessionStarter` resolves and connects; it sends no transport, starts no Activity and uses no root. `DirectSourceEngine` owns the single transport and bounded capability wait. The facade alone owns cross-source handover. Do not restore the rejected Auxio root `startservice` path or treat the old NavRadio root/FGS route as M2's current implementation.

## Readiness and lifecycle

A PID, token, successful `am` exit or title alone is not usable readiness. The coordinator observes a live source with no pending preparation/start and either PLAYING, or an identified PAUSED item with a Play action. A source can nevertheless expose a valid cold Play contract before it meets passive-ready criteria.

Maps is admitted first, followed by independent Music and Radio preparation. The media warm-up opt-out is preserved. Source/configuration loss, wake and late Music storage can rearm bounded recovery; healthy sessions are retained. Removable-media reconciliation does not scan Auxio's files, change its queue or claim SAF visibility. Screen-on is a wake hint, not proof of ACC.

Source-owned browser tokens are usable without notification-listener access. Listener-only discovery requires access. With both sources on M2, legacy process/listener observers and their visible tick are idle. Metadata follows the selected package/token; repeated identical text must not restart the marquee.

## Binary evidence, not installed-build assumptions

| Inspected input | Immutable facts | Limit |
|---|---|---|
| `NavRadio_Plus_v4_00_PREMIUM (1).apk` | SHA-256 `b68e96efbf98da957973604dfc5aafb3dcddd00dea4dd3a70c0476826fe5a937`; `com.navimods.radio`, 4.00/code 1032, minSdk 32 / targetSdk 35; exported `com.navimods.radio.RadioService` advertises `androidx.media3.session.MediaSessionService` | Incompatible reference bytes; not proof of the installed API29 handshake, passive safety or tuner readiness. Do not SDK-patch/install them |
| `Radio_TW_THEME.20240827.apk` | SHA-256 `93e93796c56b5e0758c4a143a1c428fcf1c0fa24e1d636f15151af2aeb489504`; `com.tw.radio`, code 75, API29; RadioActivity/PTYActivity, no declared service or boot receiver | Bundled media library classes do not create an exported contract; no guessed service or hidden stock-Activity activation |

Auxio's exported compatibility browser is `com.tw.media/com.tw.music.MusicService` in the inspected variant. Re-resolve the actual installed variant and final companion capability/signing/build identity before qualification. Preserve genuine stock `com.tw.music`.

## Diagnostics and acceptance

Use [the consolidated playbook](CONSOLIDATED_TESTING_PLAYBOOK.md) as the default campaign. Its read-only collector records installed identities, source/session/task evidence and the bounded local `TS18Media` timeline. `collect-fast-media-evidence.sh`, `collect-stock-radio-compare.sh` and `collect-media-lifecycle-evidence.sh` remain focused read-only tools; see [Termux tools](../scripts/termux/README.md).

`qualify-navradio-service-start.sh --qualify-navradio-service-start` is a separate **mutating** service-start experiment, not a routine collector or the production M2 route. Use only when that experiment is the intended task; an accepted start/session does not prove passive silence.

Current host/API29 tests qualify selected control flow, framework IPC and a real Media3 fixture; neither the fixture nor a loaded metadata title proves Auxio's restored decoder/USB state, NavRadio tuner output or physical sound. Exact TS18 cold/warm, handover, process restart, boot/cold power/ACC, late USB, permissions and loader non-disruption remain final acceptance. Collector failures make dependent evidence BLOCKED/UNKNOWN, not target FAIL.
