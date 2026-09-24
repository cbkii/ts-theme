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

MediaBrowser-acquired controllers are also observed for metadata through their actual `MediaSession.Token`, deduplicated against notification-listener active sessions, and removed when their session/browser dies. If HOME stops while an interactive command is pending or the configured media package changes, launcher-owned preparation/controller state is invalidated so late callbacks cannot dispatch stale transport.

## Process-local diagnostic timeline

The launcher maintains a bounded in-memory `MediaEventTrace` ring using monotonic `SystemClock.uptimeMillis()` timestamps. It is local, non-telemetric and does not write a file per event. A matching local logcat tag, `TS18MediaTrace`, allows the Termux evidence collector to export the same transitions when log access permits.

The timeline records high-value boundaries rather than every poll: HOME/process lifecycle and focus, warm requests, adapter resolution, root result category, ordinary service fallback, MediaBrowser connect/fail, exact/bound session observation/destruction, readiness phase, Play/Pause intent/coalescing, transport dispatch/acknowledgement/timeout, deferred opposite-source pause, notification-listener reconnect, semantic metadata changes and removable-storage reconciliation.

Playback acknowledgement is explicitly labelled as **not proof of audible output**. Audible onset and latency remain physical observations.

`Settings → Advanced → Diagnostics & system → Media diagnostics` shows configured source packages, adapter route, active/bound controller state and the bounded current-process timeline. This diagnostic surface performs no playback or source launch.

## Removable-media reconciliation

Auxio remains authoritative for source configuration, app-visible storage, indexing and queue restoration. The launcher does not scan storage and never treats root/global mount visibility as proof that Auxio can access a source.

While HOME is started, the launcher listens only to public removable-media mount/unmount/eject/remove broadcasts. A mount event may trigger one bounded ordinary `MediaBrowser` reconciliation for the configured music app **only** when that app already exposes a passive-safe browser contract. The reconciliation does not use root, does not call Play and does not start an Activity. Removal events cancel any launcher-owned warm bind and refresh observed sessions; they do not clear the library or declare the saved queue invalid.

Current Auxio-TS `dev` separately owns DirectFS removable-media policy. In manual observation mode `ACTION_MEDIA_MOUNTED` is availability information and preserves the committed generation; automatic mounted-source refresh is bounded to the player modes that explicitly allow it. The launcher must not override that policy.

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

The explicitly mutating qualification tool `scripts/termux/qualify-navradio-service-start.sh --qualify-navradio-service-start` discovers the installed Media3 service first, captures package/process/session/audio/task state, performs exactly one bounded service-start request, then captures the same state again. It never sends Play and does not stop/force-stop NavRadio afterwards. Its result can qualify or reject passive warm-up only when combined with physical audible/routing observation on the unit.

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

`scripts/termux/collect-stock-radio-evidence.sh` is read-only and compares a cold state against a known-good state after the user manually opens stock Radio and returns HOME. It captures exact package/path/hash evidence where accessible, process/task/session/audio state, relevant Topway processes/services and a bounded filtered log window around user-performed actions. Static names such as `radioPre`, `radioSetChannel` and `radioOpenChannel` remain search leads only; the script does not call them.

## Auxio-TS

Current Auxio-TS repository code exposes `com.tw.media/com.tw.music.MusicService` as an exported `android.media.browse.MediaBrowserService` wrapper around the single Auxio playback authority. The launcher may bounded-root-prime that exact service and then bind the real browser/controller; ordinary binding remains the fallback/control path. The launcher does not scan the music library or own Auxio's queue. Saved-queue/USB restoration remains a player-side physical qualification gate.

## Adapter policy

| Source | Passive HOME preparation | Interactive Play preparation | Readiness/control |
| --- | --- | --- | --- |
| Auxio-TS `com.tw.media` | permitted: bounded root service prime, then exported MediaBrowser bind; storage mount uses ordinary non-playing browser reconciliation | same route, normal bind still works when root fails | bound/exact controller; Play action or playing state required |
| NavRadio+ `com.navimods.radio` | **off until exact-device non-disruption is proven** | bounded root `am start-foreground-service`, then normal FGS fallback | exact observable MediaSession; Play action or acknowledged playing state |
| Stock TW Radio `com.tw.radio` | unsupported from the Radio APK | existing exact session only; external Topway route unqualified | no invented service/Activity fallback |
| Other apps | exported standard MediaBrowser when present | same exported standard interface | actual browser token/controller and advertised actions |

Root service commands run off the UI thread and target the launcher's current Android user rather than hard-coding user 0. Root does not imply platform signing, UID 1000 or OEM vehicle-service authority.

## Metadata/ticker

The shared metadata display remains observation-only. Repeated identical snapshots no longer call `setText()` on unchanged primary/secondary content, so the one-second session reconciliation poll does not continually restart the marquee's five-second hold. The marquee cancels when hidden/detached and restarts after real text/width/visibility changes. Its fit/overflow/duration decisions are factored into a pure JVM-tested policy. Metadata fallback treats empty/whitespace TITLE/ARTIST fields as absent and continues to DISPLAY_TITLE/ALBUM_ARTIST/DISPLAY_SUBTITLE when supplied by the real source. Browser-owned and listener-owned controllers are deduplicated by the actual session token.

These tests establish scheduling/data semantics, not physical pixel motion or whether a third-party radio publishes useful station/frequency metadata on this unit.

## Reboot / ACC evidence

No speculative ACC runtime receiver is added. OEM strings such as `YZS_ACC_ON`/`YZS_ACC_OFF` are not sufficient to establish the owning lifecycle contract.

`scripts/termux/collect-acc-media-lifecycle.sh --seconds 120` performs a bounded **read-only** sample while the user physically performs the transition. It correlates Android uptime/power/wakefulness, resumed task, launcher/player/radio processes, MediaSessions, storage and filtered relevant Topway/ACC/sleep logs. Screen-on is one observation only and is not classified as ACC.

## Masked Activity fallback

The implementation contains no source-Activity fallback in its readiness/transport coordinator. `maskedFallbackQualified()` remains false. A future masked fallback is capability-gated and currently unqualified/off. This is deliberate: repository/static evidence does not prove `TYPE_APPLICATION_OVERLAY` permission/app-op availability, source controllability after Activity loss, navigation handoff serialisation, or OEM reverse-camera/call/SystemUI z-order on this TS18. A denied/missing overlay capability must never degrade into an unmasked launch.

The read-only fast-media collector captures overlay app-op/task state only as preflight evidence. Production masked launch remains blocked until draw-before-launch, no touch-through, HOME-before-dismiss, source survival, navigation serialisation and critical OEM window ordering are all physically proven.

## Qualification commands

From a current repository checkout on the TS18:

```bash
bash scripts/termux/collect-fast-media-evidence.sh --label baseline
bash scripts/termux/qualify-navradio-service-start.sh --qualify-navradio-service-start
bash scripts/termux/collect-stock-radio-evidence.sh --phase cold
# manually open stock Radio, return HOME, then:
bash scripts/termux/collect-stock-radio-evidence.sh --phase opened --log-seconds 20
bash scripts/termux/collect-acc-media-lifecycle.sh --seconds 120
```

Additional fast-media labelled captures should bracket the one-tap, root-denied and Auxio USB-present/late-mounted/unavailable scenarios. Do not clear app data, SAF grants or queues simply to create a cold state.

All substantial outputs stay under `/storage/emulated/0/Download/ts-theme/`. The default collectors use PASS/BLOCKED/UNVERIFIED for capture authority; a missing root/logcat/source prerequisite blocks dependent evidence rather than proving downstream media behavior failed.

## Completion boundary

Repository CI may prove API-29 compilation, deterministic policies, bounded cancellation/fallback behavior and diagnostic-script contracts. It cannot prove speaker output, vehicle audio routing, ACC lifecycle, installed NavRadio side effects, stock-radio OEM authority or overlay safety.

Current classification after this hardening pass:

- launcher/player/listener transaction hardening: **software complete**, device lifecycle validation still required;
- Auxio removable-storage launcher reconciliation: **software complete**, app-visible storage/SAF/queue behavior still physical;
- root-denied normal fallback and opposite-source deferred pause: **software complete**, audible routing still physical;
- ticker/metadata scheduling and fallback: **software complete**, on-panel/source metadata still physical;
- cold/warm audible onset and latency: **physical validation required**;
- NavRadio passive service-start safety: **diagnostic ready / device evidence required**;
- stock TW Radio cold readiness: **diagnostic ready, blocked on OEM contract**;
- reboot/cold-boot/ACC: **diagnostic ready / physical validation required**;
- masked Activity fallback: **blocked on physical/OEM safety qualification and intentionally disabled**.
