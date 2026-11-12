# Continuous Maps and media readiness

## Runtime contract

`StartupReadinessCoordinator` admits Maps through the existing N2 owner first, then Music, then Radio. Process creation gives the not-yet-attached HOME/Maps host up to 750 ms to take first admission; HOME resume ends this grace immediately. A missing host then yields to safe media preparation. Maps permission screens or unavailable Music storage do not hold other sources hostage. A boot journal classifies the transition and never stands in for a readiness receipt.

Each media source has one in-flight preparation, a 14-second observation window and at most three attempts separated by 15 seconds after failure. Observed session loss, configuration change, screen-on wake and late Music storage can rearm recovery. Screen-off cancels pending recovery. No verified vendor ACC broadcast is wired: an ACC campaign must independently establish the vehicle transition and the resulting screen-on/process/HOME event. `ACC_ON` remains a reserved test/integration trigger, not proof of an OEM ACC contract. Healthy sources are observed rather than repeatedly prepared. These are active-session readiness guarantees, not a promise to keep Android processes immortal or a parked vehicle awake.

Music uses the source-owned exported MediaBrowser connection, observed PREPARE action and paused restored item. Early session publication is not completion. First Play retains its pending direction while the source publishes actions; Auxio's unloaded path uses HOME_PLAY_V1 with its original deadline, leaving queue restore, decoder, focus and actual playback inside Auxio. The launcher adds no player or session. An in-progress restore with an identified BUFFERING/CONNECTING item is not restarted. PREPARE never becomes Play.

M2 Direct service discovery accepts one unambiguous, exported, enabled and accessible component declared for the framework browser or Media3 session/library action. A Media3 declaration alone is a candidate: the real framework browser handshake still has to succeed. The AndroidX implementation is confined to the non-distributable `media-fixture` app; launcher release runtime remains dependency-free.

NavRadio service creation can activate the tuner. Passive creation is therefore gated until evidence from the installed API29 build proves that it is silent and preserves current audio. A user Radio request first tries its supported session/service path. A non-dispatched failure can automatically enter one masked Activity transaction. This is the supported on-demand recovery path; the user need not manually open and return from Radio. Dispatched/unconfirmed transport commands never trigger another method.

## Masked Radio transaction

Only the configured `com.navimods.radio` launch component is admitted. The device must be unlocked, interactive, API29, with ts-theme as default HOME, normal audio mode, overlay permission and known HOME/task authority. No guessed tuner, protected package or private vendor command is used.

1. Wait boundedly for an already accepted navigation transaction; defer new automatic presentation.
2. Attach the opaque full-display application overlay beneath SystemUI and wait for its real draw barrier. Its dark underlay covers app-drawn status-bar backgrounds; controls remain padded into the measured safe area. SystemUI retains its higher window/input authority. Root dispatch rechecks the exact mask title, owner UID and window type against the focused window.
3. Launch the assigned exported component once. Accepted/uncertain outcomes are observed, never replayed.
4. Observe the intended Activity and source-owned session/actions. Foreign permission/setup/login Activities yield visibly; nothing auto-accepts or dismisses them.
5. Focus the existing HOME task through the broker, retaining the source task. Verify HOME and the same session token surviving backgrounding before completion.
6. Remove the mask, release navigation and resume the original Play direction. If the source already plays, acknowledge that state without toggling Pause; commit any opposite-source handover only after replacement playback is observed.

Staging has a 12-second absolute budget inside the original user request, plus at most 1.5 seconds for guarded cleanup. Cancellation retains the mask while an accepted launch is in flight and then parks only the exact source task. Failure, changed source, geometry/permission loss and owner destruction release resources; foreign/unknown tasks are never repaired blindly. An ambiguous/shared stack without an authoritative top component is refused rather than guessed. Existing routes and warm Maps tasks remain under the navigation owner.

## Evidence and limits

Source references: [AOSP Car Media's action-readiness fix](https://android.googlesource.com/platform/packages/apps/Car/Media/+/ef9a2a9b6947191d146db67ba6ba551be5fdbeec%5E%21/) separates connection from supported PREPARE/PLAY actions. [Taskbar's pinned Android10-capable implementation](https://github.com/farmerbb/Taskbar/tree/e2a00b9a3f3adda069059abb9aebcb8b4e18954f) informs overlay permission and lifecycle handling. [Media3's service contract](https://developer.android.com/reference/androidx/media3/session/MediaSessionService) supplies the legacy browser edge, exercised here with the actual pinned implementation. These references do not establish TS18 privileges or NavRadio runtime compatibility.

Host tests execute production media, coordinator, mask and broker code against controlled boundary doubles. They cover delayed capabilities, deadline expiry, joined requests, bounded backoff, wake rearm, draw admission, permission yield, cancellation and source/session loss. They do not prove TS18 Binder/window/tuner behaviour.

Canonical CI builds launcher debug/release, unit tests and lint, asserts an empty launcher release runtime dependency graph, and runs actual API29 framework IPC. The separate fixture exercises a real Media3 1.3.1 service with only its Media3 manifest action and the legacy browser handshake. The overlay probe starts a bright cross-UID splash under the actual WindowManager overlay, samples 12 frames and exports PNGs before launch, during launch, backgrounded and on HOME restoration. These samples qualify that fixture; exact NavRadio splash, permissions, tuner and audibility still require physical evidence.

## One physical campaign

Use the [consolidated playbook](CONSOLIDATED_TESTING_PLAYBOOK.md) and its collector. Install only the final-head signed TESTING launcher and matching Auxio listed in the handoff, retaining data and a rollback pair. Enable normal notification/overlay permissions once and leave Organic Maps at the fixed compatible build. Do not force-stop protected stock apps or clear data to manufacture cold conditions.

Record one continuous phone video of startup/HOME and first Music/Radio taps, with a clock or touch indicator. Capture the actual app identities and the launcher's retained timeline after each boot/wake; a Termux process cannot span reboot. Mark audible playback separately from session PLAYING. Check Maps first, Music restore next, then first Radio activation; preserve any legitimate prerequisite screen and capture the earliest failing boundary before Retry.

Repeat the same short scenario after launcher restart, ordinary source process recreation, Android reboot, genuine cold power and genuine ACC sleep/wake, identifying each separately. Also test Music-to-Radio and Radio-to-Music, immediate user override/cancel, late USB, normal permission/setup and calls. Missing conditions are NOT_RUN; failed collection is BLOCKED/UNKNOWN rather than a source failure.

Rollback is installation of the previous signed app updates with data retained, or explicit Legacy selection for a source. No stock deletion, system partition, HOME replacement, broad SELinux change or vehicle-service mutation is part of this change.
