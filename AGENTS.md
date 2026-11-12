# TS18 launcher engineering rules

## Current authority

- Reconcile live repository, PR/base/head, ancestry, review feedback, relevant workflows and artefact identity before mutation. Current code and original exact-device evidence outrank historical reports.
- Primary integration remains PR #44. Consolidation work is a reviewable child; do not merge or publish production without a separate instruction. Preserve integration ancestry and previous signed TESTING recovery artefacts.
- The owner approved M2 defaults, source-owned browser sessions without notification access, removal of inert n3host, and a bounded N3 investigation. Physical TS18 testing is final acceptance, not an implementation prerequisite. Isolate uncertain contracts and continue independent work.

- Local/offline/private by default. No analytics, tracking, remote configuration or automatic uploads. Diagnostics are explicit, bounded and local.
- Source adapters must not retry the rejected Auxio root `startservice` route; use its evidenced browser/deadline contract.

## Product and UI

- `launcher/`, package `com.cbkii.ts18launcher`, is the only product. Android 10/API29, framework Java/Views, one DEX and no native runtime. No AndroidX runtime, playback framework, networking stack, analytics or cloud dependency.
- Retired DoFun theme and launcher Leaflet/WebView product paths stay removed. The installed OEM DoFun host remains protected and available as recovery HOME.
- Preserve the landscape TS18 UI: runtime SystemUI safe area, fixed 96 px rail and 88 px media strip; radio/music controls and shared metadata/date strip; five-column drawer; App/Icon separation; bounded accent palette and current settings/layout.
- Insets and display geometry are live inputs, not fixed status/nav-bar assumptions. Never double-subtract decor insets or render/touch inside protected SystemUI.
- HOME remains explicitly selected by the owner. Setup is skippable and does not silently grant HOME, listener or root consent. Unknown configuration keys may be previewed/ignored; recognised values remain strict and device-local consent cannot be imported.
- One application-scoped background catalogue owns launcher entries and cached icons. Shortcuts may address components/aliases; media/navigation sources retain exact package identities. Missing components fall back only within that package before a truthful failure.

## Ownership

- M2 Direct is the intended TESTING default per source. Explicit M1 Legacy is a compatibility rollback, not an additional active engine. Deselected engines must not observe, command or publish for that source; legacy global observers require demand.
- A source's own exported/unambiguous MediaBrowser may supply its authenticated package/token without notification access. Listener-discovered sessions still require that access. Deduplicate by token; preserve generation, ambiguity, disconnect, revocation and lifecycle handling.
- The player owns playback service, queue, MediaSession and audio focus. The launcher only observes and forwards one directional transport operation.
- Auxio cold Play requires advertised HOME_PLAY_V1 and its original elapsedRealtime deadline. Standard third-party players use their qualified directional Play contract. Wait for late capabilities without consuming the tap. Expired bounded Play must not fall back to unbounded PLAY; an unconfirmed send never authorises replay.
- Pause the opposite source only after observed replacement playback. Never create a second playback authority, send blind media keys, switch the tuner for readiness or foreground a source secretly.
- Maps, Music and Radio maintain independent observed readiness, admitted in that priority order. A user request may use the bounded authenticated overlay/source-task transaction when service preparation is insufficient; never launch an automatic source without a drawn mask or steal unrelated focus. Passive readiness must remain silent and must not select the tuner.
- NavRadio's installed service protocol must be substantiated; a plain Media3 service bind does not establish a controller/session. An incompatible reference APK is not exact-device proof. Qualify its real legacy browser handshake; the authorised bounded mask may initialise its Activity on explicit Radio demand without a second Play tap.
- N2 task/window control is primary. Reuse valid warm tasks; preserve user/display/task/bounds authority, legitimate permission/settings flows and unrelated foreground tasks. A transient missing task or failed focus read is inconclusive. Retain the stateful shell cold-claim/fullscreen compatibility path until broker parity covers its responsibilities. No repeated uncertain mutation or launch.
- N3 remains a default-off isolated public-display experiment. The privileged n3host skeleton and its protocol are retired. Do not infer input/embedding privileges from root or from a created VirtualDisplay.

## Protected boundaries

- DoFun, TWCore, stock radio/music/BT/EQ, SystemUI and vehicle services are protected. Root is not platform signing, UID1000, SELinux, MCU/CAN or safe firmware-write authority.
- No protected data clearing, stock APK deletion/replacement, broad chmod/policy, SELinux weakening, partition/AVB/MCU/CAN/panel writes or speculative tuner commands.
- Private callbacks require matching binaries and inspected registration/storage semantics. Exact XTService callback coexistence is statically established in `docs/PRIVATE_CONTRACT_REVIEW_2026-11-12.md`; runtime admission remains separate and observation stays operator-requested/hash-gated.
- Stop a specific protected operation when compatibility/recovery/authority is inadequate; continue unrelated app-level work.

## Verification and delivery

- Run `python3 tools/ts18_theme.py validate`, `python3 -m unittest discover -s tests -v`, relevant shell syntax/ShellCheck, and `:launcher:testDebugUnitTest :launcher:lintDebug :launcher:assembleDebug :launcher:assembleDebugAndroidTest`.
- `media-fixture/` is a CI-only companion application (not an instrumentation module, product/release or launcher dependency), using real AndroidX Media3 for legacy handshake and decoder qualification. Keep the launcher runtime dependency graph empty.
- Canonical Validate also runs real API29 IPC and signed/minified release dependency/envelope checks. No runtime dependencies may enter the launcher release.
- Prefer behavioural tests for changed contracts; source rules should protect architecture rather than count implementation callsites.
- TESTING snapshots use the established `/testing-apk` PR trigger or exact-ref workflow. Match final source/built/head SHA, package/version, signer and SHA-256; reject stale/mixed artefacts. Never commit keys, vendor APKs/code, private captures, device identifiers or local paths.
- Diagnostics are bounded, local, explicit and read-only unless a separate action is authorised. Collector failure is not target failure; failed prerequisites produce BLOCKED dependants. Preserve raw Termux su and distinguish execution identities/namespaces.
- Report code, tests, CI, artefact, install and TS18 lifecycle qualification separately. Session PLAYING is not audible output; geometry is not map pixels/touch; screen-on is not verified ACC. Claim only checks actually completed.
