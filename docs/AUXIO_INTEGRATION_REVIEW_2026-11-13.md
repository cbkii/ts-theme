# Auxio / launcher integration: causal review and correction

Scope: the maintained `com.tw.media` player and the M2 Music path in ts-theme. The inspected starting heads were ts-theme `0054278428194e075b310a9c828958fb7f6ce5a0` (PR #44) and Auxio `bc17429a0577d3c54248fec37fe87f368e7770f5` (PR #287). These are evidence pins, not permanent latest revisions. No installation, audible output, cold power or ACC result is claimed here.

## Required behaviour and owners

- HOME automatically prepares Music after Maps admission without blocking independent sources. A committed paused item and advertised controls, rather than process/bind/token existence, establish readiness.
- One explicit Play survives source initialisation. The launcher forwards one directional transport action to Auxio's own session. Auxio retains its queue, decoder and audio-focus authority.
- An unloaded Auxio session must advertise HOME_PLAY_V1. Its original elapsedRealtime deadline is shortened to the receiver's five-second maximum, never extended or replaced with unbounded Play. A loaded session uses ordinary directional transport.
- Source-owned browser admission works independently of notification-listener discovery. Exact package/token checks remain mandatory, and ambiguity never licenses guessing.
- Metadata follows the current source-owned raw or hydrated item. Real source loss clears it; command feedback must not overwrite it. Skip availability follows the source's advertised actions.
- If the evidenced service method fails before dispatch, a different, bounded preparation method may recover the original tap. A dispatched/unconfirmed Play is never replayed.

## Findings checked against current source

| Finding | Evidence / disposition |
| --- | --- |
| “Auxio refused the connection” | `BrowserSessionStarter.onFailed` emitted this text on framework `onConnectionFailed`. It does not establish an Auxio root rejection or audio-focus failure. Failed bind/admission and source service creation remain the physical discriminators. Wording now says connection failed. |
| Browser admission coupled to global discovery | Confirmed and reproduced: `DirectSessionRegistry.refresh` returned on a runtime discovery error before accepting a browser offer. Last successful listener membership is now separate from current browser offers. Unknown discovery cannot veto a valid offer, prevent a source-owned bind or revive a withdrawn browser token. |
| Failed passive readiness kept waiting | Confirmed and reproduced: `onStartFailed` ended only pending user Play, leaving passive preparation pending until its timer. It now cancels preparation and publishes `CONNECTION_FAILED`. Empty/global unknown discovery cannot overwrite that stronger source-specific failure. |
| No alternate Music preparation | Confirmed: both masked admission checks allowed only Radio, despite the paired Auxio advertising a non-exported, deadline-aware prepare entry. M2 now admits that exact Auxio contract after observed browser failure, automatically once per awake/configuration cycle, or for a non-dispatched explicit Play. It never launches the normal autoplay-capable alias. |
| Artist-only equality allegedly missing | False for the inspected head: `MediaSourceState.samePresentation` already compares `subtitle`. A regression assertion preserves that behaviour. |
| Raw metadata cleared by index notification | Confirmed in Auxio: `onIndexMoved` called `updateMediaMetadata(null, ...)` before rich Song hydration. That helper now publishes current primitive metadata when present, and clears only when both identities are absent. The companion test checks the intermediate publication, a new raw item, and genuine clearing. |
| Direction change swallowed by pending acknowledgement | Confirmed in the engine: all directional operations joined the pending operation regardless of direction. Same-direction requests still coalesce; an opposite direction supersedes the old acknowledgement and is sent once. |
| Live command errors invisible / dead skip controls | Confirmed: status-only `setMetadata` is intentionally a no-op, and all buttons were enabled. Failures now use a short independent Toast; M2 skips and directional controls follow observed capabilities. A newly publishing session retains bounded first-Play waiting. |
| Connector resource race | A synchronous failed callback could remove the attempt before its returned connection was assigned. Returned orphan connections are now disconnected. |
| Five-second receiver window too short | Not established by timing evidence. It remains unchanged. Reliable preparation must precede any protocol expansion. |

The attached independent review was supporting analysis. Its blanket prohibition on Activity recovery is unsuitable for the already authorised exact-device fallback: historical physical evidence records repeated browser failures without source process creation, while this paired Auxio has an explicit prepare-only contract. That does not prove the fallback works on the current TS18 binary; it justifies testing this different method instead of repeating the failed one.

## Preparation transaction

The existing source transaction remains the sole owner. It validates the installed capability and current configuration, default HOME/root consent/unlocked state, exact task/display and a drawn opaque mask. It waits boundedly for accepted navigation work, then launches only `com.tw.media/org.oxycblt.auxio.headunit.LauncherPreparationActivity`. The shell edge independently rejects an ordinary Auxio alias. Auxio issues its one-use internal nonce and restores without autoplay or new scan authority. The transient preparation Activity is observed through its handoff to MainActivity.

Once admitted, the launcher reconnects the same package's browser once; it does not create a player/session or send Play during automatic preparation. It waits for a usable source-owned item, returns the existing HOME task, and verifies the same source token survives backgrounding. User demand can join that transaction, then resume with the original request deadline. Foreign permission/setup Activities remain visible; timeout, cancellation or uncertain admission never authorise a second Activity launch. Accepted/uncertain automatic recovery is not repeated merely because a callback remains negative.

## Qualification and remaining boundary

Host tests execute the production registry, M2 state machine, coordinator and masked transaction against controlled discovery/root/window/time boundaries. These cover global discovery failure with a valid source-owned token; withdrawal; immediate failed preparation; original deadlines; single alternate-method admission; user yield/join; exact Auxio entry; loaded readiness; HOME restoration; permission yield and cleanup. They are not TS18 Binder/window/audio evidence.

Auxio adds a framework-MediaBrowser Android test against its actual exported wrapper with null client hints, alongside the existing AndroidX client test. This is real server/client protocol coverage but runs under the instrumentation target UID. The launcher additionally supplies the separate `auxio_browser` instrumentation mode for the actual paired Auxio APK: it asserts different UIDs, exact service/session ownership, HOME_PLAY_V1, PREPARE and reconnect. It does not manufacture a saved queue, issue Play or claim audible decoder coverage. On an authorised API29 test emulator with both matching APKs installed:

```bash
adb shell am instrument -w -e auxio_browser 1 -e auxio_package com.tw.media \
  com.cbkii.ts18launcher.test/com.cbkii.ts18launcher.MediaSessionApi29Instrumentation
```

Use `com.tw.media.debug` only for the maintained debug build. This optional actual-pair probe must be reported NOT_RUN until executed; fixture success does not substitute for it. Local Android execution is blocked when the Gradle/SDK prerequisites are unavailable; exact-head GitHub checks are the build authority.

The underlying physical cold-browser refusal remains unresolved until source-side create/bind/root/token diagnostics are correlated with the same installed APK identities. Updated Auxio logs distinguish those phases without queue paths or intent extras. The campaign collector now includes TS18Browser and retains ActivityManager service-admission lines only for the exact maintained Auxio/launcher packages, while dropping unrelated framework entries. Current TS18 acceptance must verify automatic silent preparation, first Play, launcher Pause/Previous/Next, continuous metadata, Music/Radio handover, process restart, late USB, Android reboot, cold power and genuine ACC. Preserve the previous signed pair and all app data for rollback; do not install a mixed new-launcher/old-Auxio pair as though both corrections were present.
