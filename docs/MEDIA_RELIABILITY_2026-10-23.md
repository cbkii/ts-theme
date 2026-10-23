# Selected MediaSession correctness — 23 October 2026

## Revision and target envelope

Writable: [ts-theme PR #31](https://github.com/cbkii/ts-theme/pull/31), branch
`fix/diagnostic-layout-followup-20261020`, initial HEAD
`03131deaa16519d5dd59a2fede7e36ca0c65852d`, base main
`2cd04022231064deec627bd1593fc4cfde5d94ce`. Read-only companion:
[Auxio-TS #281](https://github.com/cbkii/Auxio-TS/pull/281), HEAD
`19babc11e8ff7bbbcbcd86cce8d7701b16d903f8`, base dev
`6bd1e18de7378407f165d32b1e2001aa39586b65`. Live enumeration found no intervening
commits, formal reviews or review threads; both PRs were draft/open/unmerged and
existing head checks were successful. The machine-readable preflight beside this
file records all 123 launcher PR paths, all 20 Auxio PR paths and individual checks/runs.
Branch-protection enumeration returned integration permission 403; canonical Validate
is exercised, without claiming privileged visibility into protection configuration.
Final pushed SHA, exact-head CI and signed TESTING provenance are recorded in the PR body.

Confirmed by repository: standalone `com.cbkii.ts18launcher`, min/target/compile API29,
platform Java/Views, no runtime library dependency or native library; signed, minified
release and fixed-version TESTING lanes. Confirmed by owner target specification:
Topway TS18 Android 10, UIS8581A/SP9863A family, 4 GB RAM, 1280x720, concurrent music
and navigation. Density, installed firmware/peer versions, actual notification access,
Binder latency and ACC delivery in the replacement build are **unknown**. Ordinary
MediaSession access uses Android authorisation or a real browser token, not root/system
privilege. No physical device/emulator was attached for this task.

Guidance: project source index, context/evidence scan, code-health rules, Android
lifecycle/concurrency/API rules, severity acceptance and quick-fix/QA guidance were read.
These are five P1/high-confidence launcher correctness defects supported by production
paths; observed TS18 symptoms still require exact-binary physical qualification.

## Findings and pre-fix evidence

| Finding | Evidence at initial HEAD | Correction/disposition |
|---|---|---|
| F1 | `stabiliseSnapshot` retained old title/artist indefinitely on the same session token, without an item ID. Deterministic production-method test printed `FAIL F1: unverified blank transition retained Track A`. | Confirmed/fixed. Zero old-metadata retention; native media ID, active queue item, owner generation and observation time travel in the snapshot. Conflicting/absent item evidence yields empty/unknown text. |
| F2 | Snapshot `playing` called the command-side `usesPauseAction`; both buffering and connecting failed the new assertion. Activity rendered that Boolean as PAUSE. | Confirmed/fixed. Only native PLAYING sets `playing`; PAUSED/STOPPED show PLAY, BUFFERING/CONNECTING/ERROR/unknown have distinct state glyphs/descriptions. Command-side pause eligibility remains independent. |
| F3 | The process observer selected a callback owner, listener independently selected/enriched music by title equality, and Bootstrapper fell back to readiness/browser controllers. An unavailable process observation returned retained listener state. Delayed dispatch captured a controller without revalidating selection. | Confirmed by complete code paths/fixed. Process-selected registered owners exclusively supply generic music state and transport. Browser/readiness caches cannot override them; command dispatch and acknowledgement revalidate the exact wrapper. |
| F5 | Disconnect/destroy released controllers but left static `lastGeneric`/`lastRadio`; Activity could use them when process authority was absent. Queued listener membership work lacked a connection generation. | Confirmed by lifecycle code/fixed. Clear listener snapshots and radio associations, gate queued membership work by connection generation, and never fall back to listener music. Independent process access is not invalidated by listener disconnection. |
| F6 | `MediaSelection.pick` returned a remembered usable paused candidate before checking playing candidates; regression printed `FAIL F6: remembered paused A beats playing B`. | Confirmed/fixed. Confirmed playback first in AUTO; preference/remembered package break ties or provide fallback. Dedicated ownership never falls through to another package. Loading holds the selected source unless another eligible session really plays. |

The original regression ran **before production edits** and failed four assertions
(F1, F2 twice, F6). F3/F5 have exact code-path proof above; their newly structured
integrated/lifecycle regressions exercise the repaired publication boundaries.
Earlier tests explicitly requiring same-token stale retention and remembered-paused
priority were corrected to the new specification, rather than counted as proof of safety.

## Ownership and data flow

Auxio current item -> `MediaSessionHolder.onIndexMoved` -> synchronous initial
metadata including raw song UID -> native metadata/state/queue -> Android controller
-> `ProcessMediaSessionMonitor.Owner` -> `MediaItemObservation` -> immutable Snapshot
-> Activity merge -> metadata view/native-state indicator. Transport resolves the same
registered Owner through `selectedController`; delayed work rechecks it.

Auxio's actual publisher is **MediaSessionHolder**, not its transport callback class
MediaSessionInterface. It publishes text before asynchronous artwork and checks
`artworkRequestToken` before later artwork publication. Its queue uses
`MediaSessionUID.SingleItem` (`mi` + raw UID), while metadata uses raw UID. The launcher
has a narrowly scoped `com.tw.media` adapter for that exact difference; generic sessions
use exact media ID or exact media URI equality. No Auxio defect was demonstrated and
no companion branch was edited. Framework metadata/state/queue reads are separate IPCs,
not an atomic transaction: the reader verifies state/queue and metadata across two reads,
and declines conflicting evidence rather than choosing whichever text arrived last.
A follow-up integrated test also reproduced a failed membership query promoting a
suppressed stale browser token (`failed membership query promoted stale browser authority`).
That path now publishes unknown, retains registration ownership, and waits for a bounded
visible verification or a new callback/explicit refresh; it does not enter an immediate retry loop.

Architecture **A selected**: consolidate effective publication into the existing
application/process monitor. Keep listener notification/radio enrichment and browser
acquisition/recovery mechanisms. Alternative B (two independent music publishers with
cross-observer generation synchronisation) would add a second selection/freshness
protocol and still need command-cache unification. Removing music fallback is smaller
and makes missing access truthfully unknown. The listener retains the existing scoped
radio station/frequency contract; it cannot supply generic song text or playback authority.

Mutable owners/caches and lifetimes:

| Owner/cache | Lifetime and rule |
|---|---|
| Process watched token -> Owner/controller/callback | Main-owned; successful callback registration only; stable wrapper across enumeration; generation per registration; destruction tombstones the token. |
| Process selected package -> Owner, selected music/radio token | One verified selection per package; exact same-package playing token wins; UI and command lookup share it. Unknown UI during verification does not redirect a tap to a different configured app. |
| Process observation generation/revision | Callback payloads are invalidation signals only; reread current controller off-main. A stale result cannot commit across revision/owner change, timeout or destruction. |
| Single Binder-read worker | One in-flight batch, one coalesced follow-up, two-second publication timeout. A hung Binder call cannot be forcibly interrupted, but state/dispatch authority becomes unknown and no worker backlog grows. |
| Visible verification | Existing one-second HOME-only verification now reads the selected music/radio controllers; membership is callback/explicit-refresh driven. No periodic hidden/background enumeration, root media polling or new daemon. |
| Listener static snapshots, external registry, station association | Clear observations and station association on disconnect/destroy; connection generation rejects queued old work. Browser registration cache is acquisition evidence, not a fallback playback publisher. |
| Browser Connection/controller callback/generation/deadline | Existing bounded preparation/retry lease retained. Browser token is submitted to process authority; a live active package prevents a stale alternative browser token from replacing it. |
| Bootstrapper readiness/observedControllers | Readiness-only; cannot dispatch or override selection. Existing queue/readiness token comparison remains required before a cold Play. |
| Activity process/listener snapshots | Observe during onStart/onStop. Generic listener state is never effective; unavailable process means unknown. New observer replay is freshness checked. |
| Metadata view | Receives effective Snapshot, not diagnostics. Marquee identity includes token, owner generation, media ID and queue item; identical text on a different track resets it. |

Playback glyph and accessibility description update together without crossfading an old
PLAY/PAUSE claim into a new unknown/loading state.

Native PLAYING, supported actions, command intention and audible output are separate
facts. Loading states can still be interrupted by PAUSE through the existing directional
command policy. An already dispatched command is never replayed onto a replacement
controller. Rapid-double-tap semantics were not redesigned.

## Automated evidence and limits

- `test_media_reliability_regressions.py`: extracts actual Snapshot/stability methods and
  compiles actual MediaSelection. Demonstrated red before and green after.
- `test_media_authority_runtime.py`: compiles the **whole production monitor, reader,
  metadata view and policies**, plus the actual Activity merge method. Controlled main
  and worker queues/clock; no sleeps. Covers automatic A->blank B->B, same token/new ID,
  old callback and old artwork versus current queue, new token/similar text, native
  PLAYING/loading/PAUSED/STOPPED, same-package selection, AUTO/dedicated, stale browser,
  timeout/backlog, expired queued result, stale worker, torn Binder reads, identical-title
  marquee reset, actual listener-disconnect method alongside a valid process observation,
  removed/recreated Activity and fresh access/reconnect.
- API29 Robolectric `MediaObservationApi29Test`: actual Android metadata/queue/state
  objects and actual listener disconnect/destroy/static replay. Controller Binder is an
  explicit test boundary; this is not device IPC or an emulator result.
- `MediaSessionApi29Instrumentation` is a dependency-free test-APK runner for real
  metadata/state/queue Binder contracts and callback delivery. Canonical CI compiles the
  test APK; execution is **NOT_RUN** without an attached API29 target. After installing
  debug launcher and its test APK on a controlled API29 target, run
  `adb shell am instrument -w com.cbkii.ts18launcher.test/com.cbkii.ts18launcher.MediaSessionApi29Instrumentation`.
  It creates/releases only a test session and no audio player or playback service.
- Existing production coordinator host regression retains cold Auxio preparation,
  directional acknowledgement, no replay while buffering, source handover and browser
  retry/deadline assertions. Existing radio merge/directory and navigation/boot tests remain.
- Local source validation and host suite are available; the local environment has Java
  but no Android SDK/Gradle, so Android JVM/lint/debug/signed/minified checks run in canonical
  GitHub CI. Exact final results belong to the final SHA/PR body, not a historical green run.

Framework contracts (all used APIs available by API29):
[content media ID](https://developer.android.com/reference/android/media/MediaMetadata#METADATA_KEY_MEDIA_ID),
[active queue item/unknown ID and native states](https://developer.android.com/reference/android/media/session/PlaybackState),
[destroyed session invalidity](https://developer.android.com/reference/android/media/session/MediaController.Callback#onSessionDestroyed()),
[listener connection/disconnection](https://developer.android.com/reference/android/service/notification/NotificationListenerService).
No generic Android API can establish the physical speaker output or correct a publisher
that consistently lies about both its current item ID and its queue. ID-less music is
intentionally unknown instead of displaying unverifiable old text.

## Bounded TS18 qualification (all NOT_RUN)

Use only final-head signed TESTING APK and record its SHA/signature with the unchanged
paired Auxio/Maps versions. Keep the existing recovery HOME/rollback procedure.

| Physical scenario | Pass condition | Status |
|---|---|---|
| Automatic next, same session | Play a short A then let it end without touching HOME. B replaces A; if delayed, text is empty/unknown until B arrives; no A resurrection or old artwork text. Repeat with identical song titles. | NOT_RUN |
| Pause/resume and loading | External Auxio pause/resume changes icon correctly. Buffering/connecting never displays confirmed PAUSE/PLAY; loading can be paused without a duplicate PLAY. Accessibility label matches glyph/state. | NOT_RUN |
| AUTO versus dedicated | Remember paused A, start B externally: AUTO selects B. Dedicated A stays A. Verify playing/paused multiple tokens from the same package if supported. Telephone and configured Radio never become generic music. | NOT_RUN |
| Listener loss/reconnect | Disconnect/rebind notification listener while a valid process-authorised session remains: live state persists. Revoke all access/session ownership: old title/playing disappear. Regrant/reconnect restores fresh observation. | NOT_RUN |
| HOME lifecycle | Open drawer/another app; return/recreate HOME during an automatic transition. No stale replay, duplicate controller registration or transport redirection. | NOT_RUN |
| Genuine cold boot / ACC | Music remains silent until ordinary Play; Radio and Maps readiness and existing protected-window safeguards remain intact. Test actual ACC separately from screen on/off. | NOT_RUN |
| Navigation/USB/source death | Concurrent offline Maps navigation, late/remounted USB, player force-stop/session destruction and wake/reconnect: one playback owner and truthful unknown state during recovery. | NOT_RUN |

Residual/excluded work: no physical TS18 or instrumented emulator result; OEM/Binder
permission ordering needs the above physical gate. A stalled worker remains blocked until
Android returns, with bounded unknown publication and no accumulating work. No firmware,
SystemUI, signing, package/SDK/permission changes, second playback service, rapid-double-tap
redesign, general ticker diagnostics, production release or merge. PR remains draft for
physical acceptance and existing human privilege/signing review boundaries.
