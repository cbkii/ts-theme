# Android 10 InCar paired finalisation — 19 October 2026

Implementation follows the owner's updated section 16 prompt, on PR #26, above main
`2cd04022231064deec627bd1593fc4cfde5d94ce`. Maps peer is the final #69 after #72 integration.

| Requirement | Disposition and owner |
|---|---|
| IC-01 focus lane | Fixed in `StartupReadinessCoordinator`: concurrent background observations, bounded reservation for qualified pending media staging before compact Maps presentation. Sources that are ready, unavailable, disabled or terminal release reservation; original queue/stage deadlines remain. No additional Maps/task owner or generic HOME intent. |
| IC-02 manual admission | Fixed in `NavigationWindowController`: separate pending explicit request and consuming transaction IDs; accepted/uncertain completion, terminal refusal/cancellation and warm success expire authority. Queued Retry/Open retains its own request. No journal expiry/replay. |
| IC-06 Music default | Actual `TopwayAdapter` resolves installed Auxio `com.tw.media` before stock-only fallback `com.tw.music`; explicit user choice always wins, including temporarily unavailable selections. No preference migration or protected package modification. |
| IC-07 dedicated source | Missing selection-mode preference defaults to configured Music. Existing explicit `auto` remains supported as Follow active media. Dedicated transport, session selection, metadata, labels and opposite-source Pause nomination use the configured package/token; no fallback to unrelated active sessions. Settings states both modes explicitly. |
| Source ownership | Application-owned bootstrapper and existing token/session monitor remain the only media authorities. Identity changes, including Android user changes, replace the old bootstrapper. Notification-access gating is retained. |
| Historical T11/T13/T14 | Remain physical failures/unqualified, never cleared by CI or closed parent PRs. |

## Regression evidence

The focus-aware coordinator and completed-explicit-action/opt-out controller tests fail
against `345d5fa` and pass with these fixes. Tests model HOME losing focus when Maps
reconciles and regaining focus only through exact-task restoration. The actual adapter
and actual Music-recipient methods are compiled for both/one/no installed apps,
explicit player, late app appearance, package observation failure and intentional generic mode.
Existing owner, handover, callback/token, notification-listener, source-icon and lifecycle
coverage remains active. Full software results are recorded in the PR for its exact final head.

## Preserved requirements / decisions

- Retain #24's source-specific bounded normal/root staging and one automatic cold claim per boot.
  Accepted/uncertain claims remain durable after process/task loss; explicit Retry is required
  when retirement is not positively proven. Timeout/parser failure is not absence.
- Keep #21/#22 intentionally superseded; do not merge the old competing coordinators.
- Preserve immediate explicit icons independent of Maps recovery, 45-second end-to-end recovery,
  N0/N1/N2 and Bridge/Intent comparisons, no repeated unchanged mutation, exact existing HOME
  restoration, user/permission/call/reverse yielding and no startup Play.
- Three-source terminal readiness means playable library, silent usable tuner and interactive
  Maps pixels/input in the same startup session. PID/token/title/task are narrower observations.
- Direct Browser PREPARE without notification access remains a decision boundary, not authorised
  policy. Real ACC still needs exact-firmware evidence; screen-on is only a hint.
- Loader/Magisk companions are conditional future mechanisms. Auxio #264, firmware/remount,
  Topway/DoFun/SystemUI/MCU changes are separate owners and excluded.

## Handoff

Only a final exact-head signed APK pair may enter physical qualification. The validation kit
must pin both packages/signers/source and APK hashes, use a new campaign namespace, require fresh
A0 on every Compact guide visit, capture before Retry and preserve P/F/N/comments and blocked
parents. Film genuine boot/ACC before collecting; never reboot a live collector.

All physical TS18 startup, audio, silent tuner, real compact pixels/touch, installed identity,
performance/thermal, process/reboot/cold boot and ACC acceptance is **NOT_RUN**. No installation,
production release, default merge or protected write is part of this implementation.
