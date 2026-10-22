# Startup Readiness Loader — 22 October 2026

## Current-code audit and decision

The existing application-owned `StartupReadinessCoordinator` already provides a per-source
ledger, source-specific background preparation, guarded Activity staging and exact existing-HOME
return. `MediaSourceBootstrapper` owns the launcher side of browser/session commands;
`NavigationWindowController` owns Maps task admission, compact presentation and renderer recovery.
The loader uses these owners. It does not add a launcher service, player, session, Maps task,
watchdog daemon, root-input injector, vendor broadcast or persistent OEM setting.

A confirmed recovery gap was found: an originally READY media admission can become UNCERTAIN
after transient content loss. If its previously ready session then definitely dies, the persisted
ready journal was removed but the in-memory accepted claim could remain latched. The coordinator
now resets that source generation only when both a once-ready journal and authoritative session
absence establish source loss. Unknown observation or an uncertain initial launch cannot release it.

## Settings and operation

Settings → Media → **Startup Readiness Loader** is OFF by default. Enabling it opts into
preparation of the selected Music, Radio and Maps even when the separate passive media warm-up
switch is off. **Also prepare after sleep** is a subordinate toggle, also initially OFF; its
stored choice is retained when the parent is disabled. Both preferences participate in configuration
export/import. No overlay access is granted automatically. The adjacent display-over-other-apps
row opens the Android permission screen explicitly; denial leaves HOME progress usable.

The loader is once per observed Android boot, using BOOT_COUNT independently of the existing
startup ledger. HOME return or launcher process recreation alone does not replay it. The wake option
requires an observed screen-off/on pair or a same-boot increase of at least one second in
elapsedRealtime minus uptime (kernel suspend evidence), including process recreation across sleep.
Repeated screen-on/user-present signals coalesce. None of these observations claims a verified ACC
contract. A locked screen or call does not consume the pending loader trigger.

Music background preparation remains concurrent with read-only observations. The existing guarded
Music/Radio Activity turns are serialised before compact Maps presentation during this opt-in
session, removing Radio's usual dependency on an already-ready map. Existing sessions/tasks are
reused. Normal versus root callers remain alternatives only after a definite refusal; an accepted
or uncertain start is never blindly replayed. Auxio still requires its installed non-exported silent
preparation capability; root `startservice`, normal autoplay launches and invented stock-radio
service routes remain excluded. Existing/opposite playback, permissions and protected flows keep
their guards. Third-party Radio silence and actual tuner usability remain device qualification gates.

Readiness remains internal evidence: usable Music/Radio content and playback capability, and a
verified compact Maps task with fresh renderer evidence. It does not prove audible output or touch.
The visual presentation is now an opaque, theme-matched splash with one centred spinner, covering
the complete usable app area. There is no debug text or Continue button. It ends on readiness,
settled incomplete work, BACK, protected/foreign ownership, or **eight seconds**, whichever is first.
The original elapsed-time deadline survives triggers and Activity recreation. Eight seconds is a
visual ceiling, not a claim that all sources will be ready in that time.

## Overlay, BACK and cancellation

With existing overlay permission, the mask is established before staged app launch. Without that
permission it is confined to HOME and cannot conceal another Activity. SystemUI regions remain
outside its bounds. The overlay receives BACK directly; HOME handles BACK when no overlay exists.
No global key interceptor, accessibility service or protected input hook is added.

The mask has a random per-instance title. The existing private root pipes carry that title;
window checks additionally verify the exact focused window ID, launcher UID/user and application
overlay type. All underlying task/component, display, permission/call and peer checks remain in
force. Unknown or foreign ownership removes the mask; critical system windows retain priority.
The guard probes run only during the bounded loader session. These exact OEM window formats and
physical key behaviour still require TS18 qualification.

Dismissal/expiry revokes automatic foreground staging and pending Maps presentation, preserving
accepted/uncertain claims. Late results may update readiness but cannot request another automatic
focus change. A healthy, rendered compact map is preserved when the splash closes. Explicit later
user navigation remains available. Quiet background discovery may continue.

Paired Auxio advertises `PREPARE_HOME_DEADLINE_PROTOCOL=1`. The launcher passes the original
elapsed expiry to its non-exported preparation trampoline. Neither that trampoline nor its
one-use MainActivity nonce may renew the lease; expired preparation finishes without starting the
player. Install the matching Auxio candidate: older silent-preparation builds do not qualify for
this deadline contract. Normal MediaSession transport remains player-owned.

## External implementation research

Sources inspected 22 October 2026; analogous implementations do not qualify CB's unit.
Open Headunit source was inspected at `bafdcb2e4be22fda48770b733a038e73779dc8f2`.

| Original source | Relevant evidence | Applied decision / limit |
| --- | --- | --- |
| [Google Media Controller](https://github.com/googlesamples/android-media-controller/blob/master/README.md) | Explicit PREPARE and PLAY with metadata/playback callbacks and advertised actions | Keep readiness observation separate from transport dispatch; no Play merely to warm a source. |
| [Google UAMP MusicService](https://github.com/android/uamp/blob/main/common/src/main/java/com/example/android/uamp/media/MusicService.kt) | MediaBrowser entry, asynchronous source-ready callback and deferred browse results | Existing player service remains authority; a browser bind alone is insufficient. |
| [Open Headunit BootCompleteReceiver](https://github.com/andreknieriem/open-headunit/blob/bafdcb2e4be22fda48770b733a038e73779dc8f2/app/src/main/java/com/andrerinas/openheadunit/app/BootCompleteReceiver.kt) | Distinct opt-in boot/screen-on paths; locked-user deferral; refused starts caught | Separate boot/wake preferences and fail-open handling. Its enumerated FYT/MTCD/etc broadcasts are not evidence for Topway TS18, so none were copied. |
| [Open Headunit AutoStartOverlayPolicy](https://github.com/andreknieriem/open-headunit/blob/bafdcb2e4be22fda48770b733a038e73779dc8f2/app/src/main/java/com/andrerinas/openheadunit/main/AutoStartOverlayPolicy.kt) | Missing overlay permission is reported without erasing configuration | Preserve preferences and fall back to HOME-only progress when access is absent. |
| [Open Headunit auto-start discussion #1122](https://github.com/andreknieriem/open-headunit/issues/1122) | Maintainer/contributor discussion of head-unit kill/whitelist differences and HOME recovery | Useful field reports, not a proven TS18 keep-alive contract. No FYT whitelist/system edits. |
| [Topway TS10/TS18 XDA discussion](https://xdaforums.com/t/new-topway-ts10-uis7862-6gb-ram-128gb-head-unit-q-as.4227947/page-83) and [AppSwitcher developer thread](https://xdaforums.com/t/discontinued-appswitcher-smart-swc-launcher-for-junsun-xtrons-and-similar-devices-incl-autostart.4435555/) | Search-index excerpts describe Tasker/autostart and wake recovery | Full forum pages were blocked/inaccessible; treated only as leads, not qualified procedures. |
| [Android overlay API](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY), [focus flag](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE), [process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle) | Overlay access/focus limits; process lifetime belongs to Android | No claim of universal BACK interception or permanent residency; no protected window/input changes. |

## Validation and physical acceptance

Production-path regressions cover callback wrapper lifetime and registration failure, external
state changes, stale track/station enrichment, bounded listener reconnection, browser backoff,
uncertain admission observation, preparation aliases, owned/foreign mask identity, BACK, expiry,
wake settings and late-result cancellation. Canonical CI compiles/tests/lints the signed API29
candidate. See `CAPTURE_FIXES_2026-10-22.md` for the evidence and delivery matrix.

Physical acceptance must use the matched signed APKs. Test app-cold and sessions-present reboots;
actual ACC sleep with the option off/on; eight-second splash/BACK; full-page drawer search/app
launch during Maps admission; external Music track/PLAY/PAUSE; rapid Radio changes; delayed USB;
listener/source loss; permission/call/camera. Do not infer physical success from CI.
