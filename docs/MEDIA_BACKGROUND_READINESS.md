# Background media readiness adapters

This note records the evidence and implementation boundary for standalone-launcher fast media readiness. Vendor APK bytes are not committed. Static APK inspection establishes component contracts only; it does not replace installed-build/runtime qualification.

## Readiness model

The launcher keeps service start, controller connection, Play capability and playback acknowledgement separate.

- `UNAVAILABLE`: no configured/installed authority is available.
- `IDLE`: no preparation is in flight.
- `STARTING`: a bounded service/browser/session/command transaction is in flight.
- `CONNECTED`: a service/session surface exists, but Play capability is not yet proven.
- `READY`: the exact controller is reachable and advertises a usable Play action.
- `PLAYING`: playback state has acknowledged the requested Play.
- `BLOCKED`: the installed source has no qualified background route or a required prerequisite is absent.
- `FAILED`: a bounded attempt ended without satisfying the requested contract.

A PID, successful `am` exit, browser connection, token, or cached title is not by itself `READY`. Commands retain one monotonic deadline across root and normal fallbacks. Browser connect has its own bounded callback watchdog. Play/Pause is resolved once at tap time and Play/Pause success is reported only after the directional playback state is acknowledged; Previous/Next remain capability-gated one-shot commands.

MediaBrowser-acquired controllers are also observed for metadata through their actual `MediaSession.Token`, deduplicated against notification-listener active sessions, and removed when their session/browser dies.

## Local monotonic diagnostics

`MediaEventTrace` is a bounded process-local ring and `TS18Media` logcat tag. It records launcher lifecycle, readiness phases, root/service/browser routes, exact-session observation/destruction, transport dispatch/acknowledgement, listener reconnects, opposite-source pause commits and removable-storage reconciliation. It stores no remote telemetry and performs no per-event filesystem writes.

Settings > Advanced > Media diagnostics shows the active MediaSession view followed by the latest bounded event timeline. A playback-state acknowledgement remains distinct from physical audible output: the trace can establish dispatch and Android session state, not speaker onset.

## Supplied NavRadio+ reference

File supplied for analysis: `NavRadio_Plus_v4_00_PREMIUM (1).apk`

- SHA-256: `b68e96efbf98da957973604dfc5aafb3dcddd00dea4dd3a70c0476826fe5a937`
- package: `com.navimods.radio`
- version: `4.00` / versionCode `1032`
- manifest reports minSdk 32 / targetSdk 35, so these exact bytes are reference evidence rather than proof of the API-29 package currently installed on the TS18.
- declared service: `com.navimods.radio.RadioService`
- service is enabled, exported, persistent and declares media-playback foreground-service type.
- service intent action: `androidx.media3.session.MediaSessionService`
- boot receiver declares BOOT_COMPLETED, LOCKED_BOOT_COMPLETED and quick-boot actions.

The runtime adapter re-resolves the installed component before using it. Root-first service activation remains available for an interactive Play request, with normal `startForegroundService()` fallback. Passive HOME warm-up is deliberately **disabled for NavRadio** until the actual installed API-29 build proves that service start alone does not activate radio, switch routing or otherwise interrupt another playing source. The supplied 4.00 bytes must not be installed on the API-29 unit or SDK-patched as part of this work.

## Supplied exact stock TW Radio

File supplied for analysis: `Radio_TW_THEME.20240827.apk`

- SHA-256: `93e93796c56b5e0758c4a143a1c428fcf1c0fa24e1d636f15151af2aeb489504`
- package: `com.tw.radio`
- version: `TW_THEME.20240827` / versionCode `75`
- minSdk/targetSdk: API 29
- the hash matches the current exact-unit stock-radio pin.
- manifest declares `com.tw.radio.RadioActivity` and `PTYActivity`.
- the manifest declares no Android service and no boot receiver.

The APK contains MediaBrowser/MediaSession support-library classes, but library payload is not an exported component contract. The launcher therefore does not invent a stock-radio service or private Topway command and does not hide-launch `RadioActivity`. An already-existing exact `com.tw.radio` MediaSession may be observed. A separate exact Topway service/callback/control route can be added only after it is recovered and qualified on the unit.

## Auxio-TS and removable storage

Current Auxio-TS repository code exposes `com.tw.media/com.tw.music.MusicService` as an exported `android.media.browse.MediaBrowserService` wrapper around the single Auxio playback authority. The launcher may bounded-root-prime that exact service and then bind the real browser/controller; ordinary binding remains the fallback/control path. The launcher does not scan the music library or own Auxio's queue. Saved-queue/USB restoration remains a player-side physical qualification gate.

The launcher Application dynamically observes public removable-media availability. `MEDIA_MOUNTED` may request one debounced, bounded warm of the configured music source only while `LauncherActivity` is resumed and startup warm-up is enabled. It does not scan files, change Auxio source membership, mutate the queue, issue Play or replay an expired HOME command. Unmount/eject/removal refreshes session state and records diagnostics but does not claim the committed Auxio library is invalid. Actual `/storage/usbdiskN` visibility, SAF grants and queue usability remain Auxio/device authority.

## Adapter policy

| Source | Passive HOME preparation | Interactive Play preparation | Readiness/control |
| --- | --- | --- | --- |
| Auxio-TS `com.tw.media` | permitted: bounded root service prime, then exported MediaBrowser bind | same route, normal bind still works when root fails | bound/exact controller; Play action or playing state required |
| NavRadio+ `com.navimods.radio` | **off until exact-device non-disruption is proven** | bounded root `am start-foreground-service`, then normal FGS fallback | exact observable MediaSession; Play action or acknowledged playing state |
| Stock TW Radio `com.tw.radio` | unsupported from the Radio APK | existing exact session only; external Topway route unqualified | no invented service/Activity fallback |
| Other apps | exported standard MediaBrowser when present | same exported standard interface | actual browser token/controller and advertised actions |

Root service commands run off the UI thread and target the launcher's current Android user rather than hard-coding user 0. Root does not imply platform signing, UID 1000 or OEM vehicle-service authority. Root acceptance, normal-Android fallback and final readiness are separately recorded in the local event trace.

## Masked Activity fallback

The implementation contains no source-Activity fallback in its readiness/transport coordinator. A future masked fallback is capability-gated and currently returns unqualified/off. This is deliberate: repository/static evidence does not prove `TYPE_APPLICATION_OVERLAY` permission/app-op availability, source controllability after Activity loss, navigation handoff serialisation, or OEM reverse-camera/call/SystemUI z-order on this TS18. A denied/missing overlay capability must never degrade into an unmasked launch.

## Metadata/ticker

The shared metadata display remains observation-only. Repeated identical snapshots no longer call `setText()` on unchanged primary/secondary content, so the one-second session reconciliation poll does not continually restart the marquee's five-second hold. `SlowMarqueePolicy` makes the semantic reset/overflow decision directly JVM-testable. The marquee cancels when hidden/detached and restarts after real text/width/visibility changes. Metadata fallback treats empty/whitespace TITLE/ARTIST fields as absent and continues to DISPLAY_TITLE/ALBUM_ARTIST/DISPLAY_SUBTITLE when supplied by the real source.

## Qualification tools

All output is under `/storage/emulated/0/Download/ts-theme/`.

- `scripts/termux/collect-fast-media-evidence.sh` remains read-only. It captures identity, package/service/process state, MediaSessions, focus/route evidence, resumed task, notification-listener state, removable mounts and the bounded `TS18Media` timeline, then writes a dependency-aware playbook.
- `scripts/termux/qualify-navradio-service-start.sh --qualify-navradio-service-start` is **explicitly mutating**. It discovers exactly one installed NavRadio Media3 session service, takes a before snapshot, performs one root-first service start with ordinary Termux-caller fallback, and captures bounded after snapshots. It never issues Play, changes routing, stops or force-stops NavRadio. A successful command/PID/session does not by itself qualify passive warm-up.
- `scripts/termux/collect-stock-radio-compare.sh --session NAME --phase cold|opened` is read-only. Run `cold`, manually open stock Radio normally and return HOME, then run `opened` with the same session name. It captures the Android/vendor surfaces needed to identify what manual Activity initialisation actually changes without guessing an XTService/Binder route.
- `scripts/termux/collect-media-lifecycle-evidence.sh [30..300]` is read-only and bounded. Run it while physically performing the intended ACC/reboot lifecycle transition. It correlates uptime/power, resumed task, processes, MediaSessions, storage and filtered Topway/ACC/sleep/wake logs. Display/screen-on alone is not classified as ACC.

## Physical qualification

Physical audible onset, installed NavRadio service side effects, stock TW Radio cold-control authority and ACC lifecycle are not claimed by repository CI. Treat a failed root/source prerequisite as BLOCKED for dependent tests, not as proof that every downstream media contract failed.

For audible-latency work, use the monotonic `TS18Media` events to measure HOME lifecycle, readiness request, root/browser/service result, exact-session observation, command dispatch, playback acknowledgement and metadata render; record physical audible onset separately by observation. For launcher/process death and notification-listener restart, capture the smallest lifecycle scenario rather than using force-stop as a substitute for ordinary process death.
