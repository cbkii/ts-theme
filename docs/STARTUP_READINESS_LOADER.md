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

Each source reports preparing, ready, not selected or needs attention. A usable Music/Radio session
requires content and playback capability; Maps requires verified compact task and renderer evidence.
These statuses do not prove audible output, GNSS fixes or OEM route readiness. Completion has no
minimum spinner dwell. It closes immediately on verified readiness or settled incomplete work,
and at most 45 seconds after activation; incomplete work produces a HOME notice. The elapsed
clock is never renewed by retries or duplicate triggers. BACK/dismiss cancels further loader-sponsored
Activity turns while preserving an already admitted transaction's bounded exact-HOME cleanup.

## Overlay and BACK contract

The HOME progress panel occupies the map rectangle and leaves the media strip, rail and residual
SystemUI insets available. With existing overlay app-op access, a TYPE_APPLICATION_OVERLAY mask may
cover that same screen rectangle only after fresh, exact foreground user/task/component evidence
identifies an owned staged source or managed Maps task. A same-package permission/settings Activity
is not an eligible entry. Unknown evidence hides the mask; foreign foreground, screen-off, call,
user app/drawer navigation and destruction remove it. The optional mask uses the existing Binder
bridge, at most one read-only check in flight and at most one per second, only during the bounded
session. If that bridge is unavailable the loader stays HOME-only; it does not add recurring dumpsys
work or change startup caller authority just for a cosmetic mask.

The mask deliberately uses FLAG_NOT_FOCUSABLE so it cannot invalidate the task/focus guards or
capture automotive keys. BACK on HOME dismisses immediately before drawer handling; an explicit
HOME intent also dismisses. BACK returning from a staged source to HOME dismisses unless the
coordinator itself requested that return. The visible Continue button dismisses at any stage.
A third-party app can consume BACK internally; in that case a non-focusable overlay cannot receive
that key. Universal BACK interception over another app would require changing input/focus authority
and is NOT implemented or claimed. No root input hook or accessibility service is introduced.

Because exact ownership is checked before masking, short transitions into a source Activity can
remain visible. Permission/protected UI visibility takes priority over an uninterrupted splash.
The loader never guarantees permanent process residency or success when an installed source,
permission, storage, root capability, surface or OEM contract is unavailable.

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

Production-coordinator boundary tests cover loader opt-in, serial media/Maps ownership, boot
coalescing, same-boot restart, wake opt-out/opt-in, suspend across process death, permission unmasking,
source BACK return, dismiss retaining admitted cleanup and the confirmed once-ready/source-loss gap.
Pure policy tests cover the original deadline, dismissal, unknown boot and incomplete states.
Canonical CI must compile/test/lint and qualify the signed/minified API29 candidate.

Physical tests, NOT_RUN until exercised on the unit:

1. OFF: existing startup/media/Maps behaviour and HOME controls remain unchanged.
2. ON: untouched cold boot with all selected apps cold; no unsolicited audio, duplicate task or
   repeated accepted launch. Observe usable session content and compact Maps pixels/touch separately.
3. BACK on HOME, during source preparation, during compact Maps acquisition and after an internal
   third-party BACK: verify the documented key ownership and Continue escape. Tap Apps/rail and
   protected SystemUI while loading; confirm immediate responsiveness and no late focus theft.
4. Allow, deny and revoke overlay access; trigger permission UI/call/camera. Overlay must fail open,
   disappear on foreign/unknown ownership and never obscure protected flows.
5. Same-boot HOME return/process death: no repeated cold loader; opted-in genuine sleep/wake runs
   once, opted-out wake does not. Screen toggle and actual ACC are recorded separately.
6. Missing app, denied access, unavailable USB/root and failed renderer: truthful incomplete result
   within the original 45-second window. Restore prerequisites and validate existing bounded recovery.
7. Freeze launcher/Auxio/Maps source SHAs, APK hashes, packages and signers before testing. No default
   merge, stable release or physical-success claim follows from CI.
