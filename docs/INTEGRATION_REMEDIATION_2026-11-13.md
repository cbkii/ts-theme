# Integration remediation (2026-11-13)

Scope: the three-repository TS18 integration review of ts-theme PR #44 head `768fd36`, Auxio-TS PR #287 head `96d6e0b` and Organic Maps `dedfe2b`. This note records what changed in the launcher, why, and what still needs physical evidence. It is a source contract, not proof of TS18 behaviour.

## Media (M2)

| Finding | Change | Owner symbol |
|---|---|---|
| F1: a failed bind showed "Press Play to start" and the next tap repeated the same bind | `CONNECTION_FAILED` renders "Not responding · tap Play to retry". A refused bind is not repeated passively. One explicit retry is admitted. Further retries wait for a prerequisite change (user unlock, storage mount, wake, HOME return, the source package installed/replaced/changed) or 30 s. A timed-out bind is not a refusal and stays retryable. | `DirectSourceEngine.bindRetryAdmitted`, `onPrerequisitesChanged`; `MediaFeatureFacade.prerequisitesChanged`; `StartupReadinessCoordinator.onTrigger`; `Ts18LauncherApplication.mediaPackageReceiver`; `DirectMediaPresentation.absentText` |
| F2: the bind failure cause was unobserved | Each browser attempt logs `start-diagnostic outcome=… elapsedMs=… class=bind-refused\|root-null-or-late unlocked=…` (`TS18Media`). No bind is attempted before user unlock; the source reports "waiting for the device to unlock" and `USER_UNLOCKED` re-admits it. | `BrowserSessionStarter` |
| F3: a second tap could not cancel an owed cold Play | Taps within 400 ms coalesce. A later primary tap while a cold start is pending ends it without dispatch. A later tap after an unconfirmed Play sends Pause, which Auxio already treats as cancellation of its HOME gate. | `DirectSourceEngine.commandUntil`, `REPEAT_TAP_MS` |
| F4: a loaded Auxio received unbounded Play | Every Play to a session advertising `HOME_PLAY_V1` uses it, loaded or not. | `DirectSourceEngine.dispatch` |
| F5: fragmented deadlines and silent expiry | One tap-anchored budget (`LauncherActivity.TAP_BUDGET_MS`, 22 s) is passed through `MediaFeatureFacade.commandUntil` on the first attempt as well as staged continuation. Legacy sources keep their unbounded call. A sent Play that never reported playing shows "… didn't start in time". | `LauncherActivity.dispatchSourceCommand`, `MediaCommandPolicy.silentPlayExpiry` |
| F9: "Media service not answering" flashed at startup | An in-flight first membership read is pending, not discovery failure. | `DirectSessionRegistry.refresh` |
| F10: metadata blanked during a reconnect | The last same-package item stays visible for at most 2 s, never as playing and with no controls; the absence status is suppressed for that window. | `DirectMetadataRetention`, `LauncherActivity.applyEffectiveMediaState` |

## Navigation (N2)

| Finding | Change | Owner symbol |
|---|---|---|
| F6: Maps' own Settings/Downloader/Bookmarks/Editor screens latched "Navigation unavailable · BOOTSTRAP_PENDING" | One classifier: `MwmActivity` = MAP; Splash/DownloadResources = BOOTSTRAP_PENDING; any other Organic Maps Activity = USER_ACTIVITY. The broker and shell report `USER_ACTIVITY` before any mutation. The controller holds the task (no repair, no recovery budget, no latch) and re-presents when the map returns. Explicit fullscreen during user activity opens Maps normally. | `NavigationTaskStage`, `NavTaskBridge.requireMap`, `nav-window.sh require_bootstrap_ready`, `NavigationWindowController.holdForUserActivity` |
| F7: windowed success omitted visibility | The broker reports `visible=` from the API 29 stack info, or `unknown` when unreadable. A verify with intact geometry but `visible=0` escalates to resume instead of re-declaring the map healthy. Unknown never fails a request. | `NavTaskBridge.resolve`, `NavigationWindowController.startVerify/observeRecovery` |
| F8: masked Music staging changed Maps focus without the controller | After a transaction that parked the map or started a source Activity, the controller marks presentation unproven and re-presents it. Automatic (non-user) Music recovery never parks a map the driver is using. | `MaskedSourcePreparation.finish/automatic`, `NavigationWindowController.restorePresentationAfterStaging` |

## Unchanged ownership and compatibility

- Auxio still owns service, queue, session and focus. No Auxio protocol change is required: HOME_PLAY v1 with current media and Pause-as-cancel are already implemented at Auxio `96d6e0b`.
- Organic Maps is unchanged.
- M1 Legacy is frozen as an install-time rollback. No new behaviour is added to it; remove it after M2 physical qualification.

## Deferred until physical evidence

- **F2 cause:** whether TS18 refusals are pre-unlock, OEM or package state.
- **F5:** whether Auxio's 5 s HOME_PLAY window is long enough for a cold restore from USB. A tap-anchored protocol v2 in Auxio is planned only if timing shows it is needed.
- **F7:** whether `StackInfo.visible` is meaningful for the freeform map on this ROM.
- **Multi-resume:** whether Maps stays resumed while HOME has focus.

Collect all of these with `scripts/termux/collect-integration-evidence.sh` (see the [Termux README](../scripts/termux/README.md)).

## TS18 validation capture (2026-11-14 AEDT, one cold boot)

Installed builds, identified from APK SHA-256 and trace strings:
- Auxio TESTING `PR287-bc17429`: exact SHA-256 match.
- Launcher: a PR #44 build older than `5e95806`. It still emits "Auxio refused the connection", and was installed before both current snapshots.
- Organic Maps InCar from `master` of the same day.

None of the launcher fixes above were installed.

| Observation | Disposition |
|---|---|
| Music binds at 33.5 s (passive), 47.8 s and 51.9 s (taps) failed in 64–140 ms. The user was unlocked at 29.4 s. No ActivityManager line and no Auxio process start appeared. A bind at 61.7 s succeeded in 18 ms, after the user had opened Auxio itself (process started for its Activity at 55.8 s). | F2 cause narrowed: a bind cannot start a non-running Auxio on this ROM (OEM start control suspected), and binds to a running Auxio work. The prepare-only Activity path is the correct cold method. HOME return now re-admits a refused bind. The evidence script greps OEM start-control lines. |
| A Music tap at 47.8 s landed while the just-resumed map awaited renderer evidence. The map rendered at 48.6 s but was verified only at 73.2 s. | Fixed: HOME input keeps a pending renderer-evidence wait for an already-presented task (`NavigationWindowController.cancelAutomaticColdStart`). |
| 201 Maps evidence reads in about 90 s, including about 25 while another app was in front. 13 root consent reads in 8 s before Maps ran. 7 dropped peer notifications. | Fixed: consent-only answers back off (`CompactStartupPollPolicy`); polling is suspended while HOME is stopped with nothing outstanding; repeat notifications from an authenticated peer skip the trust worker. |
| The notification listener never connected (its pre-unlock bind failed). 15 rebind requests were made while both sources were on M2. | Fixed: rebinds only while a source is on Legacy (`MediaNotificationAccessBootstrapper.ensureConnected`). |
| Cold Maps bootstrap reported `BOOTSTRAP_PENDING` on `DownloadResourcesActivity` and then resumed. Five warm Next/Previous commands were acknowledged in 70–735 ms. | Confirms the F6 classifier and warm transport. |
| HOME departure returned `GEOMETRY_NOT_VERIFIED` once (60.2 s). The task was retained and re-presented on HOME return. | Deferred: the result lacks the observed bounds. Collect again before changing departure verification. |
| Playback started at 67.8 s with no launcher Play. | Auxio's own autoplay-on-launch preference after the user opened Auxio; not a launcher defect. |

Collector-side items (the external campaign collector, not in these repositories):
- help probes printed valid output with rc=255;
- `ps | head` returned rc=141;
- the second screen recording ran 45 s against a 25 s cap and was incomplete.
