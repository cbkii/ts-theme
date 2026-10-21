# Consolidated TS18 candidate — 21 October 2026

The final launcher candidate incorporates PRs #26, #27, #28, #29, #30 and #31 plus the current main checkpoint, preserving original heads as ancestors. It combines binary scheduled appearance, source-specific radio metadata, authenticated startup/task ownership and changed-edge-only navigation fallback layout.

The Settings conflict retains the approved N2 / guarded bridge / fullscreen-departure / immediate silent Auxio preparation profile. Startup diagnostics use the shared appearance-aware dialog owner; obsolete N1/N2/retain selectors are not restored. Three seconds remains a soft target.

No source owner, package, signer, privilege or production-version policy changes. The TESTING note reconciler is included in this candidate; its privileged publisher and update/rollback boundary still require human review before default-branch merge or signing.

## Mandatory final acceptance

- Review root/task and peer-authentication boundaries, publisher trust and signer continuity.
- Qualify one exact signed triplet with the final Auxio #281 and Maps #78 candidates; old parent APK evidence does not qualify this combination.
- Run untouched cold boot, silent preparation, one-tap audible Music/Radio, map pixels/touch, HOME/BACK, protected UI, late USB, process loss and actual ACC tests with the bounded v3 collector.
- Verify package/version/signing/hash provenance and a data-preserving rollback before installation. DoFun remains the recovery HOME.

The previous candidate has now failed physical Music/compact Maps acceptance; replacement qualification is pending. This candidate must remain open until those mandatory boundaries are satisfied. Consolidation is not physical approval.

## Physical regression corrective revision

The prior triplet failed physical acceptance: launcher PLAY did not resume Auxio, Maps cold presentation failed, and the panel fallback opened fullscreen. Those results supersede the earlier human-only handoff. The corrective head requires new exact-head CI and physical qualification.

- Prepared Auxio PLAY uses ordinary MediaSession transport. Cold PLAY joins the existing silent preparation owner, waits boundedly for prepared content, then uses one expiring V1 restore if necessary. Its transport deadline is shortened to the receiver's five-second maximum, never renewed. Native steering controls and the sole Auxio player/session/focus owner are unchanged.
- Session membership and queue readiness are collected by the existing bounded worker. Transport selection uses cached matching tokens; cold retries do not repeatedly enumerate sessions/queues on HOME's main thread.
- The map-panel button retries compact presentation in starting/failure/configured states. Fullscreen remains the explicit Navigation action. Initial and warm compact presentation both await authenticated renderer evidence after task geometry verification. Maps content dimensions may exclude caption/insets, but cannot exceed the verified task rectangle.
- Temporary renderer loss preserves task authority and uses bounded recovery. A definite pre-dispatch startup-focus refusal waits for eligible HOME focus; accepted/uncertain launches are adopted, never replayed. Drawer/app navigation invalidates pending presentation epochs and remains immediately dispatchable.
- The drawer appears before guarded Maps parking, even when HOME already reports View focus. Parking queues behind an admitted acquisition so its late task cannot cover the drawer; a newer external-app intent cancels that queued parking to prevent late HOME focus theft.
- Explicit cold Music preparation may run with passive warm-up disabled. It uses the existing non-exported capability, exact source/HOME task checks and bounded HOME return. It must not pause the user's requested playback or the opposite source merely to prepare.

The attached secondary review's claim that Activity preparation is wholly prohibited was stale: MediaSourceAdapter is background-only, while StartupReadinessCoordinator already owns Activity staging. Its deadline, fallback-routing and initial renderer-wait findings were substantiated. P0 labels were not adopted: these are P1 primary-flow failures under the Project's severity definitions.

Regression gates: actual panel click routing; warm ordinary PLAY; cold V1 admission at both 6.1/7.7-second originating budgets; expiry/newer intent; single preparation admission and exact HOME return; initial frame wait; focus refusal/re-arm; drawer/app escape during pending presentation; caption/inset dimensions. Qualify the replacement signed triplet on TS18 before merge. A local loading state must never intercept rail/drawer controls. Around three seconds remains a soft target; CI does not prove audible output, useful map pixels/touch or ACC behaviour.
